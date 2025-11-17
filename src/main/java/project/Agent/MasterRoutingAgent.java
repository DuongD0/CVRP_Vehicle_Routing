package project.Agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.FIPANames;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import project.General.*;
import project.Solver.VRPSolver;
import project.Solver.ORToolsSolver;
import project.Utils.AgentLogger;
import project.Utils.JsonConfigReader;
import project.Utils.JsonResultLogger;
import project.Utils.BackendClient;

import java.util.*;

/**
 * Master Routing Agent (MRA) for CVRP
 * - Has its own location (depot)
 * - Reads problem from config (customers with id, demand, coordinates)
 * - Queries Delivery Agents (DAs) for vehicle information
 * - Solves routes using Google OR-Tools
 * - Assigns routes to DAs
 * - Outputs results as JSON
 */
public class MasterRoutingAgent extends Agent {
    // Depot location
    private double depotX;
    private double depotY;
    
    // Request queue - stores incoming requests to process
    private java.util.Queue<RequestQueueItem> requestQueue;
    
    // Accumulated unrouted nodes (up to 6 before processing)
    private List<CustomerInfo> accumulatedUnroutedNodes;
    private static final int MAX_ACCUMULATED_UNROUTED = 6;
    
    // Persistent tracking of unserved customers across solving sessions
    private List<CustomerInfo> unservedCustomers;
    
    // Request processing state
    private boolean isProcessingRequest;
    private RequestQueueItem currentRequest;
    
    // Vehicle management
    private Map<String, VehicleInfo> registeredVehicles;
    private int expectedVehicleCount;  // Number of vehicles expected to respond
    private int receivedVehicleCount;  // Number of vehicles that have responded
    private boolean allVehiclesReceived;  // Flag to indicate all vehicles have responded
    
    // Solver interface
    private VRPSolver solver;
    private DepotProblemAssembler problemAssembler;
    
    // Logger for conversations
    private AgentLogger logger;
    
    // Configuration data
    private JsonConfigReader.CVRPConfig config;
    private String configName;
    
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            this.config = (JsonConfigReader.CVRPConfig) args[0];
            this.configName = (String) args[1];
            
        } else {
            doDelete();
            return;
        }
        
        
        // Initialize logger
        logger = new AgentLogger("MRA");
        logger.setAgentAID(this);
        logger.logEvent("Agent started");
        
        // Initialize depot location from config
        depotX = config.depot.x;
        depotY = config.depot.y;
        
        // Report agent status to backend with depot coordinates
        com.google.gson.JsonObject info = new com.google.gson.JsonObject();
        info.addProperty("depot_x", depotX);
        info.addProperty("depot_y", depotY);
        BackendClient.reportAgentStatus(getLocalName(), "mra", "active", info);
        
        // Initialize unserved customers list (starts empty, accumulates over time)
        unservedCustomers = new ArrayList<>();
        
        // Initialize request queue and accumulated unrouted nodes
        requestQueue = new java.util.LinkedList<>();
        accumulatedUnroutedNodes = new ArrayList<>();
        isProcessingRequest = false;
        currentRequest = null;
        
        // Initialize collections
        registeredVehicles = new HashMap<>();
        expectedVehicleCount = 0;
        receivedVehicleCount = 0;
        allVehiclesReceived = false;
        
        // Initialize solver
        solver = new ORToolsSolver();
        problemAssembler = new DepotProblemAssembler(solver, logger);
        logger.logEvent("Depot at (" + depotX + ", " + depotY + ")");
        logger.logEvent("Ready to receive requests from backend API");
        
        // Register with DF for automatic discovery
        registerWithDF();
        logger.logEvent("Registered with DF as 'mra-service'");
        
        // Wait a bit for DAs to register, then start processing requests
        addBehaviour(new WakerBehaviour(this, 3000) {
            @Override
            protected void onWake() {
                // Start processing request queue
                logger.logEvent("Ready to process requests from queue");
            }
        });
        
        // Add behavior to handle vehicle info responses
        addBehaviour(new VehicleInfoResponseHandler());
        
        // Add behavior to handle route assignment responses from DAs
        addBehaviour(new RouteAssignmentResponseHandler());
        
        // Add behavior to handle DA arrival notifications
        addBehaviour(new DAArrivalNotificationHandler());
        
        // Add behavior to poll backend API for requests directly
        addBehaviour(new BackendAPIPoller());
        
        // Add behavior to process request queue
        addBehaviour(new RequestQueueProcessor());
    }
    
    /**
     * Inner class to represent a request in the queue
     */
    private static class RequestQueueItem {
        String requestId;
        List<CustomerInfo> customers;
        
        RequestQueueItem(String requestId, List<CustomerInfo> customers) {
            this.requestId = requestId;
            this.customers = new ArrayList<>(customers);
        }
    }
    
    /**
     * Handles vehicle information responses from DAs
     */
    private class VehicleInfoResponseHandler extends CyclicBehaviour {
        @Override
        public void action() {
            // Use a specific template to match vehicle info responses
            // Vehicle info responses have INFORM performative, FIPA_REQUEST protocol
            // We'll filter out route assignment responses manually by checking conversation ID and content
            MessageTemplate baseTemplate = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST)
            );
            
            // Process all pending vehicle info messages in one go
            ACLMessage msg = null;
            while ((msg = receive(baseTemplate)) != null) {
                // Filter: Check if this is a route assignment response (skip it)
                // Route assignment responses have conversation ID starting with "route-assignment-"
                // or content starting with "ROUTE_ACCEPTED:" or "ROUTE_REJECTED:"
                String conversationId = msg.getConversationId();
                String content = msg.getContent();
                
                // This should be a vehicle info response
                logger.logReceived(msg);
                if (content == null) {
                    logger.log("WARNING: Received vehicle info message with null content");
                    continue;
                }
                logger.log("Processing vehicle info response: " + content);
                
                String[] parts = content.split("\\|");
                String name = null;
                Integer capacity = null;
                Double maxDistance = null;

                for (String part : parts) {
                    if (part.startsWith("NAME:")) {
                        name = part.substring("NAME:".length()).trim();
                    } else if (part.startsWith("CAPACITY:")) {
                        try {
                            capacity = Integer.parseInt(part.substring("CAPACITY:".length()).trim());
                        } catch (NumberFormatException e) {
                            logger.log("ERROR: Failed to parse CAPACITY: " + part);
                        }
                    } else if (part.startsWith("MAX_DISTANCE:")) {
                        try {
                            maxDistance = Double.parseDouble(part.substring("MAX_DISTANCE:".length()).trim());
                        } catch (NumberFormatException e) {
                            logger.log("ERROR: Failed to parse MAX_DISTANCE: " + part);
                        }
                    }
                }

                // Use sender name as fallback if NAME is not in content
                // The sender's local name is the actual DA agent name (with request ID)
                String senderName = (msg.getSender() != null) ? msg.getSender().getLocalName() : null;
                
                if (name == null || name.isEmpty()) {
                    if (senderName != null) {
                        name = senderName;
                        logger.log("Using sender name as vehicle name: " + name);
                    } else {
                        logger.log("ERROR: Cannot determine vehicle name from message");
                        continue;
                    }
                } else {
                    // Verify that the name in content matches the sender name (they should match)
                    // If they don't match, use the sender name as it's the authoritative source
                    if (senderName != null && !name.equals(senderName)) {
                        logger.log("WARNING: Name mismatch - content: " + name + ", sender: " + senderName + ". Using sender name.");
                        name = senderName;
                    }
                }

                if (conversationId != null) {
                    StringBuilder convSummary = new StringBuilder();
                    convSummary.append("Vehicle info received - ").append(name);
                    if (capacity != null) {
                        convSummary.append(", Capacity=").append(capacity);
                    }
                    if (maxDistance != null) {
                        convSummary.append(", MaxDistance=").append(maxDistance);
                    }
                    logger.logConversationEnd(msg.getConversationId(), convSummary.toString());
                }

                // Register or update vehicle
                VehicleInfo vehicle = registeredVehicles.get(name);
                boolean isNewVehicle = (vehicle == null);
                
                if (vehicle == null) {
                    int initialCapacity = capacity != null ? capacity : 50;
                    double initialMaxDistance = maxDistance != null ? maxDistance : 1000.0;
                    vehicle = new VehicleInfo(name, initialCapacity, initialMaxDistance);
                    registeredVehicles.put(name, vehicle);
                    logger.logEvent("Registered vehicle " + name +
                                  ": capacity=" + initialCapacity + ", maxDistance=" + initialMaxDistance);
                } else {
                    // Update existing vehicle info
                    if (capacity != null) {
                        vehicle.capacity = capacity;
                    }
                    if (maxDistance != null) {
                        vehicle.maxDistance = maxDistance;
                    }
                    if (capacity != null || maxDistance != null) {
                        logger.logEvent("Updated vehicle " + name + ": capacity=" + vehicle.capacity +
                                      ", maxDistance=" + vehicle.maxDistance);
                    }
                }
                
                // Increment received count only for new vehicles (to avoid counting duplicates)
                if (isNewVehicle) {
                    receivedVehicleCount++;
                    logger.logEvent("Received vehicle info " + receivedVehicleCount + "/" + expectedVehicleCount + 
                                  " (Vehicle: " + name + ")");
                    
                    // Check if all vehicles have responded
                    if (receivedVehicleCount >= expectedVehicleCount) {
                        allVehiclesReceived = true;
                        logger.logEvent("All " + expectedVehicleCount + " vehicles have responded");
                    }
                } else {
                    logger.logEvent("Received duplicate/update for vehicle " + name);
                }
            }
            
            // Block only if no messages were processed
            block();
        }
    }
    
    /**
     * Behavior that waits for all vehicles to respond before solving
     * Checks periodically if all vehicles have responded, with a timeout
     */
    private class WaitForVehiclesBehaviour extends TickerBehaviour {
        private long startTime;
        private long timeoutMs;
        private long minWaitMs = 3000;  // Minimum wait time (3 seconds) even if all vehicles respond quickly
        private boolean shouldStop = false;
        
        public WaitForVehiclesBehaviour(Agent a, long period, long timeoutMs) {
            super(a, period);
            this.startTime = System.currentTimeMillis();
            this.timeoutMs = timeoutMs;
        }
        
        @Override
        protected void onTick() {
            if (shouldStop) {
                return;  // Behavior will be removed
            }
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            
            // Check if all vehicles have responded
            if (allVehiclesReceived) {
                // Ensure minimum wait time has passed
                if (elapsedTime >= minWaitMs) {
                    logger.logEvent("All vehicles responded. Proceeding to solve after " + 
                                  (elapsedTime / 1000.0) + " seconds");
                    shouldStop = true;
                    myAgent.removeBehaviour(this);
                    solveAndAssignRoutes();
                } else {
                    // Still waiting for minimum wait time
                    long remainingMs = minWaitMs - elapsedTime;
                }
            } else if (elapsedTime >= timeoutMs) {
                // Timeout reached - proceed with whatever vehicles we have
                logger.logEvent("Timeout reached. Received " + receivedVehicleCount + "/" + expectedVehicleCount + 
                              " vehicle responses. Proceeding to solve");
                shouldStop = true;
                myAgent.removeBehaviour(this);
                solveAndAssignRoutes();
            }
        }
    }
    
    /**
     * Handles route assignment responses from DAs
     */
    private class RouteAssignmentResponseHandler extends CyclicBehaviour {
        @Override
        public void action() {
            // Check for both INFORM (accept) and REFUSE (reject) messages
            MessageTemplate informTemplate = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST)
            );
            
            MessageTemplate refuseTemplate = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.REFUSE),
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST)
            );
            
            MessageTemplate template = MessageTemplate.or(informTemplate, refuseTemplate);
            ACLMessage msg = receive(template);
            
            if (msg != null && msg.getContent() != null) {
                boolean isAccepted = msg.getContent().startsWith("ROUTE_ACCEPTED:");
                boolean isRejected = msg.getContent().startsWith("ROUTE_REJECTED:");
                
                if (isAccepted || isRejected) {
                    // Log the received response message
                    logger.logReceived(msg);
                    
                    String content = msg.getContent();
                    String senderName = (msg.getSender() != null) ? msg.getSender().getLocalName() : "unknown";
                    
                    String[] parts = content != null ? content.split("\\|") : new String[0];
                    String routeId = null;
                    String vehicleName = null;
                    String status = null;
                    String reason = null;
                    String details = null;
                    String demand = null;
                    String distance = null;
                    
                    for (String part : parts) {
                        if (part.startsWith("ROUTE_ACCEPTED:") || part.startsWith("ROUTE_REJECTED:")) {
                            routeId = part.substring(part.indexOf(":") + 1);
                        } else if (part.startsWith("VEHICLE:")) {
                            vehicleName = part.substring("VEHICLE:".length());
                        } else if (part.startsWith("STATUS:")) {
                            status = part.substring("STATUS:".length());
                        } else if (part.startsWith("REASON:")) {
                            reason = part.substring("REASON:".length());
                        } else if (part.startsWith("DETAILS:")) {
                            details = part.substring("DETAILS:".length());
                        } else if (part.startsWith("DEMAND:")) {
                            demand = part.substring("DEMAND:".length());
                        } else if (part.startsWith("DISTANCE:")) {
                            distance = part.substring("DISTANCE:".length());
                        }
                    }
                    
                    if (msg.getConversationId() != null) {
                        StringBuilder convSummary = new StringBuilder();
                        convSummary.append("Route assignment response - Route ").append(routeId != null ? routeId : "unknown");
                        if (vehicleName != null) {
                            convSummary.append(", Vehicle: ").append(vehicleName);
                        }
                        if (status != null) {
                            convSummary.append(", Status: ").append(status);
                        }
                        if (reason != null) {
                            convSummary.append(", Reason: ").append(reason);
                        }
                        logger.logConversationEnd(msg.getConversationId(), convSummary.toString());
                    }
                    
                    if (isAccepted && routeId != null && vehicleName != null && "ACCEPTED".equals(status)) {
                        logger.logEvent("Route " + routeId + " ACCEPTED by vehicle " + vehicleName + 
                                      " (demand: " + (demand != null ? demand : "N/A") + 
                                      ", distance: " + (distance != null ? distance : "N/A") + ")");
                    } else if (isRejected && routeId != null && vehicleName != null) {
                        logger.logEvent("Route " + routeId + " REJECTED by vehicle " + vehicleName + 
                                      (reason != null ? " - Reason: " + reason : "") +
                                      (details != null ? " - Details: " + details : ""));
                    } else {
                        logger.logEvent("Route assignment response received from " + senderName + ": " + content);
                    }
                } else {
                    block();
                }
            } else {
                block();
            }
        }
    }
    
    /**
     * Solves the CVRP problem and assigns routes to DAs
     * Uses currentRequest from queue and accumulates unrouted nodes
     */
    private void solveAndAssignRoutes() {
        // Validate request and vehicles
        List<VehicleInfo> availableVehicles = validateAndPrepareSolving();
        if (availableVehicles == null) {
            return; // Error already logged
        }
        
        // Prepare customers for solving
        List<CustomerInfo> allCustomersToSolve = prepareCustomersForSolving();
        List<CustomerRequest> customerRequests = convertToCustomerRequests(allCustomersToSolve);
        
        // Solve the VRP problem
        SolutionResult result = solveVRPProblem(availableVehicles, customerRequests, allCustomersToSolve);
        if (result == null) {
            return; // Error already logged
        }
        
        // Update unserved customers tracking
        updateUnservedCustomersTracking(result);
        
        // Print solution summary
        printSolutionSummary(result);
        
        // Set vehicle names for routes
        setVehicleNamesForRoutes(result, availableVehicles);
        
        // Log result as JSON (use requestId if available, otherwise use configName)
        String resultName = (currentRequest != null && currentRequest.requestId != null) 
            ? currentRequest.requestId 
            : configName;
        JsonResultLogger.logResult(result, resultName);
        
        // Submit solution to backend
        submitSolutionToBackend(result);
        
        // Assign routes and complete request
        assignRoutesAndComplete(result, availableVehicles);
    }
    
    /**
     * Validates current request and vehicles, returns available vehicles or null if invalid
     */
    private List<VehicleInfo> validateAndPrepareSolving() {
        if (currentRequest == null) {
            logger.logEvent("ERROR: No current request to solve");
            isProcessingRequest = false;
            return null;
        }
        
        logger.logEvent("Solving CVRP problem for request: " + currentRequest.requestId);
        
        // Collect all registered vehicles
        List<VehicleInfo> availableVehicles = new ArrayList<>(registeredVehicles.values());
        
        if (availableVehicles.isEmpty()) {
            logger.logEvent("ERROR: No vehicles registered");
            isProcessingRequest = false;
            return null;
        }
        
        logger.logEvent("Using " + availableVehicles.size() + " available vehicles");
        
        return availableVehicles;
    }
    
    /**
     * Prepares customers for solving by combining request customers with unserved customers
     */
    private List<CustomerInfo> prepareCustomersForSolving() {
        List<CustomerInfo> allCustomersToSolve = new ArrayList<>();
        allCustomersToSolve.addAll(currentRequest.customers);
        allCustomersToSolve.addAll(unservedCustomers);
        
        logger.logEvent("Solving with " + currentRequest.customers.size() + " request + " + 
                      unservedCustomers.size() + " unserved = " + allCustomersToSolve.size() + " total");
        
        return allCustomersToSolve;
    }
    
    /**
     * Converts CustomerInfo list to CustomerRequest format for problem assembler
     */
    private List<CustomerRequest> convertToCustomerRequests(List<CustomerInfo> allCustomersToSolve) {
        List<CustomerRequest> customerRequests = new ArrayList<>();
        for (CustomerInfo customer : allCustomersToSolve) {
            CustomerRequest req = new CustomerRequest(
                customer.name,
                customer.name,
                customer.x,
                customer.y,
                "package", // Item name (not used in CVRP)
                customer.demand
            );
            customerRequests.add(req);
        }
        return customerRequests;
    }
    
    /**
     * Solves the VRP problem using the problem assembler
     */
    private SolutionResult solveVRPProblem(List<VehicleInfo> availableVehicles, 
                                          List<CustomerRequest> customerRequests,
                                          List<CustomerInfo> allCustomersToSolve) {
        logger.logEvent("Calling VRP solver: " + availableVehicles.size() + 
                      " vehicles, " + allCustomersToSolve.size() + " customers");
        
        SolutionResult result = problemAssembler.assembleAndSolve(
            depotX,
            depotY,
            customerRequests,
            availableVehicles
        );
        
        if (result == null) {
            logger.logEvent("ERROR: Solver returned null result");
            isProcessingRequest = false;
            currentRequest = null;
        }
        
        return result;
    }
    
    /**
     * Updates unserved customers tracking: updates coordinates, removes served customers, adds to accumulated list
     */
    private void updateUnservedCustomersTracking(SolutionResult result) {
        // Update unserved customers with proper coordinates and names
        updateUnservedCustomerCoordinates(result.unservedCustomers);
        
        // Remove customers that were served from unserved list
        removeServedCustomersFromUnservedList(result.routes);
        
        // Add new unserved customers to accumulated unrouted nodes
        addToAccumulatedUnroutedNodes(result.unservedCustomers);
        
        logger.logEvent("Accumulated unrouted nodes: " + accumulatedUnroutedNodes.size());
    }
    
    /**
     * Updates coordinates and names for unserved customers from result
     */
    private void updateUnservedCustomerCoordinates(List<CustomerInfo> unservedCustomers) {
        for (CustomerInfo unserved : unservedCustomers) {
            boolean found = false;
            // First check in request customers
            for (CustomerInfo originalCustomer : currentRequest.customers) {
                if (originalCustomer.id == unserved.id) {
                    unserved.x = originalCustomer.x;
                    unserved.y = originalCustomer.y;
                    unserved.name = originalCustomer.name;
                    found = true;
                    break;
                }
            }
            // If not found in request customers, check in previously unserved
            if (!found) {
                for (CustomerInfo prevUnserved : this.unservedCustomers) {
                    if (prevUnserved.id == unserved.id) {
                        unserved.x = prevUnserved.x;
                        unserved.y = prevUnserved.y;
                        unserved.name = prevUnserved.name;
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Removes customers that were served (in routes) from the unserved customers list
     */
    private void removeServedCustomersFromUnservedList(List<RouteInfo> routes) {
        List<CustomerInfo> servedCustomerIds = new ArrayList<>();
        for (RouteInfo route : routes) {
            for (CustomerInfo customer : route.customers) {
                servedCustomerIds.add(customer);
            }
        }
        
        unservedCustomers.removeIf(unserved -> {
            for (CustomerInfo served : servedCustomerIds) {
                if (served.id == unserved.id) {
                    logger.logEvent("Customer " + unserved.name + " (ID: " + unserved.id + 
                                  ") removed from unserved list - now served");
                    return true;
                }
            }
            return false;
        });
    }
    
    /**
     * Adds new unserved customers to accumulated unrouted nodes (up to threshold)
     */
    private void addToAccumulatedUnroutedNodes(List<CustomerInfo> newUnservedCustomers) {
        for (CustomerInfo newUnserved : newUnservedCustomers) {
            // Check if already in accumulated list
            boolean alreadyExists = false;
            for (CustomerInfo existing : accumulatedUnroutedNodes) {
                if (existing.id == newUnserved.id) {
                    alreadyExists = true;
                    break;
                }
            }
            
            if (!alreadyExists) {
                CustomerInfo unservedCopy = new CustomerInfo(newUnserved.id, newUnserved.x, newUnserved.y, newUnserved.demand, newUnserved.name);
                accumulatedUnroutedNodes.add(unservedCopy);
                logger.logEvent("Added customer " + newUnserved.name + " (ID: " + newUnserved.id + 
                              ") to accumulated unrouted nodes (count: " + accumulatedUnroutedNodes.size() + ")");
            }
        }
    }
    
    /**
     * Logs solution summary
     */
    private void printSolutionSummary(SolutionResult result) {
        if (result.routes.isEmpty()) {
            logger.logEvent("No solution found - all " + result.unservedCustomers.size() + " customers unserved");
        } else {
            logger.logEvent("VRP solution found: " + result.routes.size() + " routes, " + 
                           result.itemsDelivered + "/" + result.itemsTotal + " items delivered, " +
                           "total distance: " + String.format("%.2f", result.totalDistance) +
                           ", unserved: " + result.unservedCustomers.size());
        }
    }
    
    /**
     * Sets vehicle names for all routes using registered vehicle names from DAs
     * (Vehicles are no longer in requests - DAs are created when vehicles are confirmed)
     */
    private void setVehicleNamesForRoutes(SolutionResult result, List<VehicleInfo> availableVehicles) {
        for (RouteInfo route : result.routes) {
            int vehicleIndex = route.vehicleId - 1;
            if (vehicleIndex >= 0 && vehicleIndex < availableVehicles.size()) {
                // Use registered vehicle name from DAs (extract base name if it has suffix)
                VehicleInfo targetVehicle = availableVehicles.get(vehicleIndex);
                route.vehicleName = extractVehicleName(targetVehicle.name);
            } else {
                route.vehicleName = "unknown";
            }
        }
    }
    
    /**
     * Extracts vehicle name by removing request ID suffix if present
     */
    private String extractVehicleName(String vehicleName) {
        if (vehicleName.contains("-") && vehicleName.lastIndexOf("-") > 0) {
            int lastDash = vehicleName.lastIndexOf("-");
            String possibleSuffix = vehicleName.substring(lastDash + 1);
            // If suffix looks like a UUID or request ID, remove it
            if (possibleSuffix.length() > 10 || possibleSuffix.matches(".*[0-9a-f]{8}.*")) {
                return vehicleName.substring(0, lastDash);
            }
        }
        return vehicleName;
    }
    
    /**
     * Submits solution to backend API
     */
    private void submitSolutionToBackend(SolutionResult result) {
        if (currentRequest != null && currentRequest.requestId != null) {
            try {
                logger.logEvent("Submitting solution to backend for request " + currentRequest.requestId);
                boolean success = BackendClient.submitSolution(currentRequest.requestId, result, currentRequest.requestId);
                if (success) {
                    logger.logEvent("Solution submitted to backend successfully");
                } else {
                    logger.logEvent("Failed to submit solution to backend");
                }
            } catch (Exception e) {
                logger.log("ERROR: Failed to submit solution to backend: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Assigns routes to DAs and marks request as complete
     */
    private void assignRoutesAndComplete(SolutionResult result, List<VehicleInfo> availableVehicles) {
        if (!result.routes.isEmpty()) {
            logger.logEvent("Assigning routes to vehicles for delivery execution");
            assignRoutes(result, availableVehicles);
            
            logger.logEvent("Routes assigned. Moving to next request/unserved nodes");
        } else {
            logger.logEvent("No routes to assign - all customers unserved");
        }
        
        // Mark request processing as complete immediately
        // The RequestQueueProcessor will pick up the next request or unserved nodes
        isProcessingRequest = false;
        currentRequest = null;
    }
    
    /**
     * Assigns routes to Delivery Agents
     */
    private void assignRoutes(SolutionResult result, List<VehicleInfo> availableVehicles) {
        logger.logEvent("Starting route assignment to " + result.routes.size() + " routes");

        for (int i = 0; i < result.routes.size(); i++) {
            RouteInfo route = result.routes.get(i);
            String routeId = String.valueOf(i + 1);
            int vehicleIndex = route.vehicleId - 1;

            if (vehicleIndex < 0 || vehicleIndex >= availableVehicles.size()) {
                logger.log("ERROR: Route " + routeId + " vehicle index " + vehicleIndex + " out of range");
                continue;
            }

            VehicleInfo targetVehicle = availableVehicles.get(vehicleIndex);
            String targetVehicleName = targetVehicle.name;
            route.vehicleName = targetVehicleName;
            
            logger.logEvent("Assigning route " + routeId + " to vehicle: " + targetVehicleName);

            // Update customer details for the route
            List<String> customerAgentIds = new ArrayList<>();
            for (int j = 0; j < route.customers.size(); j++) {
                CustomerInfo customer = route.customers.get(j);
                // Find matching customer from current request
                if (currentRequest != null) {
                    for (CustomerInfo originalCustomer : currentRequest.customers) {
                        if (originalCustomer.id == customer.id) {
                            customer.x = originalCustomer.x;
                            customer.y = originalCustomer.y;
                            customer.name = originalCustomer.name;
                            customerAgentIds.add(originalCustomer.name);
                            break;
                        }
                    }
                }
            }

            StringBuilder routeContent = new StringBuilder();
            routeContent.append("ROUTE:").append(routeId).append("|");
            routeContent.append("VEHICLE_ID:").append(route.vehicleId).append("|");
            routeContent.append("VEHICLE_NAME:").append(targetVehicleName).append("|");
            routeContent.append("CUSTOMERS:");
            for (int j = 0; j < route.customers.size(); j++) {
                if (j > 0) routeContent.append(",");
                routeContent.append(route.customers.get(j).id);
            }
            routeContent.append("|CUSTOMER_IDS:");
            for (int j = 0; j < customerAgentIds.size(); j++) {
                if (j > 0) routeContent.append(",");
                routeContent.append(customerAgentIds.get(j));
            }
            routeContent.append("|COORDS:");
            for (int j = 0; j < route.customers.size(); j++) {
                if (j > 0) routeContent.append(";");
                CustomerInfo customer = route.customers.get(j);
                routeContent.append(String.format("%.2f", customer.x)).append(",").append(String.format("%.2f", customer.y));
            }
            routeContent.append("|DEMAND:").append(route.totalDemand);
            routeContent.append("|DISTANCE:").append(String.format("%.2f", route.totalDistance));
            routeContent.append("|DEPOT_X:").append(String.format("%.2f", depotX));
            routeContent.append("|DEPOT_Y:").append(String.format("%.2f", depotY));

            // Find DA by vehicle name (should match exactly with DA local name)
            AID daAID = findDAByName(targetVehicleName);
            if (daAID == null) {
                logger.log("ERROR: Could not find DA for vehicle " + targetVehicleName);
                logger.log("Available vehicles in registeredVehicles: " + 
                          registeredVehicles.keySet().toString());
                continue;
            }
            
            String daName = daAID.getLocalName();
            logger.logEvent("Found DA " + daName + " for vehicle " + targetVehicleName);

            // Create route assignment message
            ACLMessage routeAssignment = new ACLMessage(ACLMessage.REQUEST);
            routeAssignment.addReceiver(daAID);
            routeAssignment.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            routeAssignment.setOntology("route-assignment");
            String conversationId = "route-assignment-" + routeId + "-" + targetVehicleName + "-" + System.currentTimeMillis();
            routeAssignment.setConversationId(conversationId);
            routeAssignment.setContent("ROUTE_ASSIGNMENT:" + routeContent);

            
            // Log the message content (truncated if too long)
            String fullContent = "ROUTE_ASSIGNMENT:" + routeContent;

            // Log conversation start with detailed information
            logger.logConversationStart(conversationId,
                "Route " + routeId + " assignment to DA " + daName + 
                " (vehicle: " + targetVehicleName + 
                ", customers: " + route.customers.size() + 
                ", demand: " + route.totalDemand + 
                ", distance: " + String.format("%.2f", route.totalDistance) + ")");
            
            logger.logEvent("Sending route assignment message: Route " + routeId + 
                          " to DA " + daName + " (vehicle: " + targetVehicleName + ")");
            
            // Log the sent message with full details
            logger.logSent(routeAssignment);
            
            // Send the route assignment
            send(routeAssignment);

            logger.logEvent("Route assignment message sent successfully to DA " + daName + 
                          " for route " + routeId);
        }

        logger.logEvent("Completed route assignment for " + result.routes.size() + " routes");
    }
    
    /**
     * Finds Delivery Agent by vehicle name
     * Vehicle names should match exactly with DA local names (both include request ID)
     */
    private AID findDAByName(String vehicleName) {
        try {
            List<AID> daAIDs = findDeliveryAgentsViaDF();
            for (AID daAID : daAIDs) {
                String daName = daAID.getLocalName();
                
                // Exact match (most common case - both have request ID)
                if (daName.equals(vehicleName)) {
                    logger.logEvent("Found DA by exact name match: " + daName);
                    return daAID;
                }
                
                // Check if vehicle name is a prefix of DA name (e.g., "DA1" in "DA1-request-id")
                // This handles cases where vehicle name doesn't include request ID
                if (daName.startsWith(vehicleName + "-")) {
                    logger.logEvent("Found DA by prefix match: " + daName + " starts with " + vehicleName);
                    return daAID;
                }
            }
            logger.log("ERROR: Could not find DA with vehicle name: " + vehicleName);
            logger.log("Available DAs: " + daAIDs.stream()
                .map(AID::getLocalName)
                .collect(java.util.stream.Collectors.joining(", ")));
        } catch (Exception e) {
            logger.log("ERROR: Exception while finding DA by name: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Registers MRA with DF (Directory Facilitator)
     */
    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            
            ServiceDescription sd = new ServiceDescription();
            sd.setType("mra-service");
            sd.setName("CVRP-Master-Routing-Agent");
            sd.setOwnership("CVRP-System");
            dfd.addServices(sd);
            
            DFService.register(this, dfd); 
            logger.logEvent("DF Registration successful");
        } catch (FIPAException fe) {
            logger.log("ERROR: Failed to register with DF: " + fe.getMessage());
        }
    }
    
    /**
     * Finds Delivery Agents via DF
     */
    private List<AID> findDeliveryAgentsViaDF() {
        List<AID> daAIDs = new ArrayList<>();
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("da-service");
            dfd.addServices(sd);
            
            DFAgentDescription[] results = DFService.search(this, dfd);
            for (DFAgentDescription result : results) {
                daAIDs.add(result.getName());
            }
            logger.log("DF Search: Found " + daAIDs.size() + " Delivery Agents via 'da-service'");
        } catch (FIPAException fe) {
            logger.log("ERROR: Error searching DF for Delivery Agents: " + fe.getMessage());
        }
        return daAIDs;
    }
    
    @Override
    protected void takeDown() {
        // Report agent status before terminating
        BackendClient.reportAgentStatus(getLocalName(), "mra", "terminated");
        logger.logEvent("Agent terminating");
        try {
            DFService.deregister(this);
            logger.logEvent("Deregistered from DF");
        } catch (FIPAException fe) {
            logger.log("ERROR: Failed to deregister from DF: " + fe.getMessage());
        }
        logger.close();
    }
    
    /**
     * Behavior to handle DA arrival notifications
     * DAs notify MRA when they return to depot
     */
    private class DAArrivalNotificationHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchContent("DA_ARRIVED_AT_DEPOT")
            );
            
            ACLMessage msg = receive(template);
            if (msg != null) {
                String daName = msg.getSender().getLocalName();
                logger.logReceived(msg);
                logger.logEvent("DA " + daName + " arrived at depot - ready for next route");
                
                // DA is ready, can process next request if queue is not empty
                // The RequestQueueProcessor will handle this
            } else {
                block();
            }
        }
    }
    
    /**
     * Behavior to poll backend API for requests directly
     * Polls the backend API and adds requests to the queue
     */
    private class BackendAPIPoller extends TickerBehaviour {
        public BackendAPIPoller() {
            super(MasterRoutingAgent.this, 2000); // Poll every 2 seconds
        }
        
        @Override
        protected void onTick() {
            try {
                // Poll backend for requests
                BackendClient.BackendRequest backendRequest = BackendClient.pollForRequest();
                
                if (backendRequest != null) {
                    logger.logEvent("Received request from backend API: " + backendRequest.requestId);
                    
                    // Convert backend request to CVRPConfig
                    JsonConfigReader.CVRPConfig requestConfig = BackendClient.convertBackendRequestToConfig(backendRequest.data);
                    
                    // Note: Depot is NOT in requests - it's already set when MRA was created
                    // Note: DAs are already created when vehicles are confirmed in frontend
                    // Requests now only contain customer data
                    
                    // Convert customers to CustomerInfo
                    List<CustomerInfo> requestCustomers = new ArrayList<>();
                    for (JsonConfigReader.CustomerConfig customerConfig : requestConfig.customers) {
                        CustomerInfo customer = new CustomerInfo(
                            Integer.parseInt(customerConfig.id.replaceAll("[^0-9]", "")),
                            customerConfig.x,
                            customerConfig.y,
                            customerConfig.demand,
                            customerConfig.id
                        );
                        requestCustomers.add(customer);
                    }
                    
                    // Add to request queue
                    RequestQueueItem newRequest = new RequestQueueItem(backendRequest.requestId, requestCustomers);
                    requestQueue.offer(newRequest);
                    
                    logger.logEvent("Added request from API to queue: " + backendRequest.requestId);
                }
            } catch (Exception e) {
                // Silently handle errors (backend might not be running or no requests available)
                // Don't spam logs with connection errors
            }
        }
    }
    
    /**
     * Behavior to process requests from the queue
     * Processes requests one by one, accumulates unrouted nodes (up to 6) before processing
     */
    private class RequestQueueProcessor extends CyclicBehaviour {
        @Override
        public void action() {
            // Don't process if already processing a request
            if (isProcessingRequest) {
                block(1000); // Check every second
                return;
            }
            
            // Check if we should process accumulated unrouted nodes (if >= 6 or no more requests)
            boolean shouldProcessUnrouted = false;
            if (accumulatedUnroutedNodes.size() >= MAX_ACCUMULATED_UNROUTED) {
                shouldProcessUnrouted = true;
                logger.logEvent("Processing accumulated unrouted nodes: " + accumulatedUnroutedNodes.size());
            } else if (requestQueue.isEmpty() && !accumulatedUnroutedNodes.isEmpty()) {
                shouldProcessUnrouted = true;
                logger.logEvent("No more requests, processing accumulated unrouted nodes: " + 
                              accumulatedUnroutedNodes.size());
            }
            
            if (shouldProcessUnrouted) {
                // Process accumulated unrouted nodes
                List<CustomerInfo> nodesToProcess = new ArrayList<>(accumulatedUnroutedNodes);
                accumulatedUnroutedNodes.clear();
                
                RequestQueueItem unroutedRequest = new RequestQueueItem(
                    "unrouted-" + System.currentTimeMillis(), 
                    nodesToProcess
                );
                processRequest(unroutedRequest);
                block(1000);
                return;
            }
            
            // Process next request from queue
            if (!requestQueue.isEmpty()) {
                RequestQueueItem request = requestQueue.poll();
                logger.logEvent("Processing request from queue: " + request.requestId);
                processRequest(request);
            } else {
                block(1000); // Check every second if queue is empty
            }
        }
    }
    
    /**
     * Processes a single request: queries vehicles, solves, assigns routes
     */
    private void processRequest(RequestQueueItem request) {
        isProcessingRequest = true;
        currentRequest = request;
        
        logger.logEvent("Processing request " + request.requestId + ": " + request.customers.size() + " customers");
        
        // Query all vehicles (always query all, even if processing queue)
        queryAllVehicles();
    }
    
    /**
     * Queries all vehicles for information
     */
    private void queryAllVehicles() {
        // Find all DAs via DF
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("da-service");
        template.addServices(sd);
        
        List<AID> daAIDs = new ArrayList<>();
        try {
            DFAgentDescription[] results = DFService.search(this, template);
            for (DFAgentDescription result : results) {
                daAIDs.add(result.getName());
            }
        } catch (FIPAException fe) {
            logger.log("ERROR: Failed to search DF for DAs: " + fe.getMessage());
            isProcessingRequest = false;
            return;
        }
        
        if (daAIDs.isEmpty()) {
            logger.logEvent("No DAs found via DF");
            isProcessingRequest = false;
            return;
        }
        
        logger.logEvent("Found " + daAIDs.size() + " DAs, querying vehicle info");
        
        // Reset vehicle tracking
        registeredVehicles.clear();
        expectedVehicleCount = daAIDs.size();
        receivedVehicleCount = 0;
        allVehiclesReceived = false;
        
        // Query each DA
        for (AID daAID : daAIDs) {
            String daName = daAID.getLocalName();
            try {
                ACLMessage query = new ACLMessage(ACLMessage.REQUEST);
                query.addReceiver(daAID);
                query.setContent("QUERY_VEHICLE_INFO");
                query.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                String queryConversationId = "vehicle-info-query-" + daName + "-" + System.currentTimeMillis();
                query.setConversationId(queryConversationId);
                
                logger.logConversationStart(queryConversationId, "Vehicle info query to DA " + daName);
                logger.logSent(query);
                send(query);
                
            } catch (Exception e) {
                logger.log("ERROR: Failed to query DA " + daName + ": " + e.getMessage());
            }
        }
        
        // Wait for all vehicles to respond
        addBehaviour(new WaitForVehiclesBehaviour(this, 500, 10000));
    }
}

