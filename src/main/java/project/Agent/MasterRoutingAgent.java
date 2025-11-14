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
import java.util.concurrent.CountDownLatch;

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
    
    // Problem data (from config)
    private List<CustomerInfo> customers;
    
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
    
    // Backend mode support
    private CountDownLatch solutionLatch;
    private Object solutionHolder; // Will be cast to Main.SolutionHolder
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            this.config = (JsonConfigReader.CVRPConfig) args[0];
            this.configName = (String) args[1];
            
            // Check if backend mode (has solutionLatch and solutionHolder)
            if (args.length >= 4 && args[2] instanceof CountDownLatch) {
                this.solutionLatch = (CountDownLatch) args[2];
                this.solutionHolder = args[3];
            }
        } else {
            System.err.println("ERROR: MRA requires config and configName as arguments");
            doDelete();
            return;
        }
        
        System.out.println("Master Routing Agent (MRA) " + getAID().getName() + " is ready.");
        
        // Initialize logger
        logger = new AgentLogger("MRA");
        logger.setAgentAID(this);
        logger.logEvent("Agent started");
        
        // DEBUG: Print request details from frontend/backend
        System.out.println("\n=== MRA: DEBUG - Request Details from Frontend/Backend ===");
        System.out.println("Request ID/Config Name: " + configName);
        System.out.println("\nDepot:");
        System.out.println("  Name: " + config.depot.name);
        System.out.println("  Location: (" + config.depot.x + ", " + config.depot.y + ")");
        System.out.println("\nVehicles (" + config.vehicles.size() + "):");
        for (int i = 0; i < config.vehicles.size(); i++) {
            JsonConfigReader.VehicleConfig v = config.vehicles.get(i);
            System.out.println("  Vehicle " + (i + 1) + ":");
            System.out.println("    Name: " + v.name);
            System.out.println("    Capacity: " + v.capacity + " items");
            System.out.println("    Max Distance: " + v.maxDistance);
        }
        System.out.println("\nCustomers (" + config.customers.size() + "):");
        int totalDemand = 0;
        for (int i = 0; i < config.customers.size(); i++) {
            JsonConfigReader.CustomerConfig c = config.customers.get(i);
            totalDemand += c.demand;
            System.out.println("  Customer " + (i + 1) + ":");
            System.out.println("    ID: " + c.id);
            System.out.println("    Location: (" + c.x + ", " + c.y + ")");
            System.out.println("    Demand: " + c.demand + " items");
            if (c.timeWindow != null && c.timeWindow.length >= 2) {
                System.out.println("    Time Window: [" + c.timeWindow[0] + ", " + c.timeWindow[1] + "]");
            } else {
                System.out.println("    Time Window: None");
            }
        }
        int totalCapacity = config.vehicles.stream().mapToInt(v -> v.capacity).sum();
        System.out.println("\nSummary:");
        System.out.println("  Total Demand: " + totalDemand + " items");
        System.out.println("  Total Capacity: " + totalCapacity + " items");
        if (totalDemand > totalCapacity) {
            System.out.println("  ⚠ WARNING: Total demand exceeds total capacity (capacity shortfall)");
        }
        System.out.println("========================================================\n");
        
        // Initialize depot location from config
        depotX = config.depot.x;
        depotY = config.depot.y;
        
        // Initialize customers from config
        customers = new ArrayList<>();
        for (JsonConfigReader.CustomerConfig customerConfig : config.customers) {
            CustomerInfo customer = new CustomerInfo(
                Integer.parseInt(customerConfig.id.replaceAll("[^0-9]", "")), // Extract numeric ID
                customerConfig.x,
                customerConfig.y,
                customerConfig.demand,
                customerConfig.id
            );
            customers.add(customer);
        }
        
        // Initialize collections
        registeredVehicles = new HashMap<>();
        expectedVehicleCount = 0;
        receivedVehicleCount = 0;
        allVehiclesReceived = false;
        
        // Initialize solver
        solver = new ORToolsSolver();
        problemAssembler = new DepotProblemAssembler(solver, logger);
        
        System.out.println("MRA: Depot located at (" + depotX + ", " + depotY + ")");
        System.out.println("MRA: Problem loaded - " + customers.size() + " customers");
        logger.logEvent("Depot at (" + depotX + ", " + depotY + ")");
        logger.logEvent("Problem loaded: " + customers.size() + " customers");
        
        // Register with DF for automatic discovery
        registerWithDF();
        logger.logEvent("Registered with DF as 'mra-service'");
        
        // Wait a bit for DAs to register, then query them and solve
        addBehaviour(new WakerBehaviour(this, 3000) {
            @Override
            protected void onWake() {
                queryVehiclesAndSolve();
            }
        });
        
        // Add behavior to handle vehicle info responses
        addBehaviour(new VehicleInfoResponseHandler());
        
        // Add behavior to handle route assignment responses from DAs
        addBehaviour(new RouteAssignmentResponseHandler());
    }
    
    /**
     * Queries all Delivery Agents for their vehicle information, then solves and assigns routes
     */
    private void queryVehiclesAndSolve() {
        System.out.println("\n=== MRA: Querying Delivery Agents ===");
        logger.logEvent("Querying Delivery Agents for vehicle information");
        
        // Find DAs via DF
        List<AID> daAIDs = findDeliveryAgentsViaDF();
        if (daAIDs.isEmpty()) {
            System.err.println("MRA: ERROR - No Delivery Agents found via DF");
            logger.logEvent("ERROR: No Delivery Agents found via DF");
            return;
        }
        
        System.out.println("MRA: Found " + daAIDs.size() + " Delivery Agents via DF");
        logger.logEvent("Found " + daAIDs.size() + " Delivery Agents via DF");
        
        // Set expected vehicle count
        expectedVehicleCount = daAIDs.size();
        receivedVehicleCount = 0;
        allVehiclesReceived = false;
        
        System.out.println("MRA: Expecting " + expectedVehicleCount + " vehicle information responses");
        logger.logEvent("Expecting " + expectedVehicleCount + " vehicle information responses");
        
        // Query each DA for vehicle information using FIPA-Request
        for (AID daAID : daAIDs) {
            String daName = daAID.getLocalName();
            try {
                System.out.println("MRA: Querying DA: " + daName);
                logger.logEvent("Querying DA: " + daName);
                
                ACLMessage query = new ACLMessage(ACLMessage.REQUEST);
                query.addReceiver(daAID);
                query.setContent("QUERY_VEHICLE_INFO");
                query.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                String queryConversationId = "vehicle-info-query-" + daName + "-" + System.currentTimeMillis();
                query.setConversationId(queryConversationId);
                
                logger.logConversationStart(queryConversationId, 
                    "Vehicle info query to DA " + daName);
                
                logger.logSent(query);
                send(query);
                
                System.out.println("MRA: Sent vehicle info query to " + daName);
                
            } catch (Exception e) {
                System.err.println("MRA: Error querying DA " + daName + ": " + e.getMessage());
                logger.log("ERROR: Failed to query DA " + daName + ": " + e.getMessage());
            }
        }
        
        // Wait for all vehicles to respond (with timeout)
        // Check every 500ms if all vehicles have responded
        addBehaviour(new WaitForVehiclesBehaviour(this, 500, 10000)); // Check every 500ms, timeout after 10 seconds
    }
    
    /**
     * Handles vehicle information responses from DAs
     */
    private class VehicleInfoResponseHandler extends CyclicBehaviour {
        @Override
        public void action() {
            // Use a more specific template to match vehicle info responses
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
                
                if (conversationId != null && conversationId.startsWith("route-assignment-")) {
                    // This is a route assignment response, skip it (will be handled by RouteAssignmentResponseHandler)
                    continue;
                }
                
                if (content != null && 
                    (content.startsWith("ROUTE_ACCEPTED:") || content.startsWith("ROUTE_REJECTED:"))) {
                    // This is a route assignment response, skip it
                    continue;
                }
                
                // This should be a vehicle info response
                logger.logReceived(msg);
                if (content == null) {
                    System.err.println("MRA: WARNING - Received vehicle info message with null content from " + 
                                     (msg.getSender() != null ? msg.getSender().getLocalName() : "unknown"));
                    logger.log("WARNING: Received vehicle info message with null content");
                    continue;
                }
                
                System.out.println("MRA: Processing vehicle info response from " + 
                                 (msg.getSender() != null ? msg.getSender().getLocalName() : "unknown") + 
                                 ": " + content);
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
                            System.err.println("MRA: ERROR - Failed to parse CAPACITY: " + part);
                            logger.log("ERROR: Failed to parse CAPACITY: " + part);
                        }
                    } else if (part.startsWith("MAX_DISTANCE:")) {
                        try {
                            maxDistance = Double.parseDouble(part.substring("MAX_DISTANCE:".length()).trim());
                        } catch (NumberFormatException e) {
                            System.err.println("MRA: ERROR - Failed to parse MAX_DISTANCE: " + part);
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
                        System.out.println("MRA: Using sender name as vehicle name: " + name);
                        logger.log("Using sender name as vehicle name: " + name);
                    } else {
                        System.err.println("MRA: ERROR - Cannot determine vehicle name from message");
                        logger.log("ERROR: Cannot determine vehicle name from message");
                        continue;
                    }
                } else {
                    // Verify that the name in content matches the sender name (they should match)
                    // If they don't match, use the sender name as it's the authoritative source
                    if (senderName != null && !name.equals(senderName)) {
                        System.out.println("MRA: WARNING - Name in content (" + name + ") doesn't match sender name (" + 
                                         senderName + "). Using sender name as authoritative.");
                        logger.log("WARNING: Name mismatch - content: " + name + ", sender: " + senderName + ". Using sender name.");
                        name = senderName;
                    }
                }

                if (msg.getConversationId() != null) {
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
                    System.out.println("MRA: Registered vehicle " + name +
                                     " (capacity: " + initialCapacity + ", maxDistance: " + initialMaxDistance + ")");
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
                        System.out.println("MRA: Updated vehicle " + name + " capacity/maxDistance");
                        logger.logEvent("Updated vehicle " + name + ": capacity=" + vehicle.capacity +
                                      ", maxDistance=" + vehicle.maxDistance);
                    }
                }
                
                // Increment received count only for new vehicles (to avoid counting duplicates)
                if (isNewVehicle) {
                    receivedVehicleCount++;
                    System.out.println("MRA: Received vehicle info " + receivedVehicleCount + "/" + expectedVehicleCount + 
                                     " (Vehicle: " + name + ")");
                    logger.logEvent("Received vehicle info " + receivedVehicleCount + "/" + expectedVehicleCount + 
                                  " (Vehicle: " + name + ")");
                    
                    // Check if all vehicles have responded
                    if (receivedVehicleCount >= expectedVehicleCount) {
                        allVehiclesReceived = true;
                        System.out.println("MRA: All " + expectedVehicleCount + " vehicles have responded");
                        logger.logEvent("All " + expectedVehicleCount + " vehicles have responded");
                    }
                } else {
                    System.out.println("MRA: Received duplicate/update for vehicle " + name + 
                                     " (already registered, not counting)");
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
                    System.out.println("MRA: All vehicles responded. Proceeding to solve after " + 
                                     (elapsedTime / 1000.0) + " seconds");
                    logger.logEvent("All vehicles responded. Proceeding to solve after " + 
                                  (elapsedTime / 1000.0) + " seconds");
                    shouldStop = true;
                    myAgent.removeBehaviour(this);
                    solveAndAssignRoutes();
                } else {
                    // Still waiting for minimum wait time
                    long remainingMs = minWaitMs - elapsedTime;
                    System.out.println("MRA: All vehicles responded, waiting " + 
                                     (remainingMs / 1000.0) + " more seconds before solving...");
                }
            } else if (elapsedTime >= timeoutMs) {
                // Timeout reached - proceed with whatever vehicles we have
                System.out.println("MRA: Timeout reached (" + (timeoutMs / 1000.0) + 
                                 " seconds). Received " + receivedVehicleCount + "/" + expectedVehicleCount + 
                                 " vehicle responses. Proceeding to solve...");
                logger.logEvent("Timeout reached. Received " + receivedVehicleCount + "/" + expectedVehicleCount + 
                              " vehicle responses. Proceeding to solve");
                shouldStop = true;
                myAgent.removeBehaviour(this);
                solveAndAssignRoutes();
            } else {
                // Still waiting for vehicles (only log every few ticks to avoid spam)
                if (getTickCount() % 2 == 0) {  // Log every 2 ticks (every 1 second)
                    System.out.println("MRA: Waiting for vehicles... (" + receivedVehicleCount + "/" + 
                                     expectedVehicleCount + " received, " + 
                                     String.format("%.1f", (timeoutMs - elapsedTime) / 1000.0) + " seconds remaining)");
                }
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
                    
                    System.out.println("\n=== MRA: Received Route Assignment Response ===");
                    System.out.println("MRA: Response from DA: " + senderName);
                    System.out.println("MRA: Conversation ID: " + (msg.getConversationId() != null ? msg.getConversationId() : "N/A"));
                    System.out.println("MRA: Response Content: " + content);
                    
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
                    
                    System.out.println("MRA: Parsed Response:");
                    System.out.println("  Route ID: " + (routeId != null ? routeId : "N/A"));
                    System.out.println("  Vehicle: " + (vehicleName != null ? vehicleName : "N/A"));
                    System.out.println("  Status: " + (status != null ? status : "N/A"));
                    if (reason != null) {
                        System.out.println("  Reason: " + reason);
                    }
                    if (details != null) {
                        System.out.println("  Details: " + details);
                    }
                    if (demand != null) {
                        System.out.println("  Demand: " + demand + " items");
                    }
                    if (distance != null) {
                        System.out.println("  Distance: " + distance);
                    }
                    System.out.println("==============================================\n");
                    
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
                        System.out.println("MRA: ✓ Route " + routeId + " ACCEPTED by vehicle " + vehicleName);
                        logger.logEvent("Route " + routeId + " ACCEPTED by vehicle " + vehicleName + 
                                      " (demand: " + (demand != null ? demand : "N/A") + 
                                      ", distance: " + (distance != null ? distance : "N/A") + ")");
                    } else if (isRejected && routeId != null && vehicleName != null) {
                        System.out.println("MRA: ✗ Route " + routeId + " REJECTED by vehicle " + vehicleName + 
                                         (reason != null ? " - Reason: " + reason : ""));
                        logger.logEvent("Route " + routeId + " REJECTED by vehicle " + vehicleName + 
                                      (reason != null ? " - Reason: " + reason : "") +
                                      (details != null ? " - Details: " + details : ""));
                    } else {
                        System.out.println("MRA: Route assignment response received from " + senderName);
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
     */
    private void solveAndAssignRoutes() {
        System.out.println("\n=== MRA: Solving CVRP Problem ===");
        logger.logEvent("Solving CVRP problem");
        
        // Collect all registered vehicles
        List<VehicleInfo> availableVehicles = new ArrayList<>(registeredVehicles.values());
        
        if (availableVehicles.isEmpty()) {
            System.err.println("MRA: ERROR - No vehicles registered");
            logger.logEvent("ERROR: No vehicles registered");
            return;
        }
        
        System.out.println("MRA: Using " + availableVehicles.size() + " available vehicles");
        logger.logEvent("Using " + availableVehicles.size() + " available vehicles");
        
        // Convert customers to CustomerRequest format for problem assembler
        List<CustomerRequest> customerRequests = new ArrayList<>();
        for (int i = 0; i < customers.size(); i++) {
            CustomerInfo customer = customers.get(i);
            CustomerRequest req;
            
            // Check if config has time window for this customer
            if (i < config.customers.size() && config.customers.get(i).timeWindow != null) {
                req = new CustomerRequest(
                    customer.name,
                    customer.name,
                    customer.x,
                    customer.y,
                    "package", // Item name (not used in CVRP)
                    customer.demand,
                    config.customers.get(i).timeWindow
                );
            } else {
                req = new CustomerRequest(
                    customer.name,
                    customer.name,
                    customer.x,
                    customer.y,
                    "package", // Item name (not used in CVRP)
                    customer.demand
                );
            }
            customerRequests.add(req);
        }
        
        // Solve
        System.out.println("MRA: Calling VRP solver with " + availableVehicles.size() + 
                         " vehicles and " + customers.size() + " customers");
        logger.logEvent("Calling VRP solver: " + availableVehicles.size() + 
                      " vehicles, " + customers.size() + " customers");
        
        SolutionResult result = problemAssembler.assembleAndSolve(
            depotX,
            depotY,
            customerRequests,
            availableVehicles
        );
        
        if (result == null) {
            System.err.println("MRA: ERROR - Solver returned null result");
            logger.logEvent("ERROR: Solver returned null result");
            return;
        }
        
        // Update unserved customers with proper coordinates and names
        for (CustomerInfo unserved : result.unservedCustomers) {
            for (CustomerInfo originalCustomer : customers) {
                if (originalCustomer.id == unserved.id) {
                    unserved.x = originalCustomer.x;
                    unserved.y = originalCustomer.y;
                    unserved.name = originalCustomer.name;
                    break;
                }
            }
        }
        
        System.out.println("\n=== MRA: VRP Solution Summary ===");
        if (result.routes.isEmpty()) {
            System.out.println("MRA: No solution found - no routes generated");
            System.out.println("MRA: All customers are unserved: " + result.unservedCustomers.size());
            logger.logEvent("No solution found - all " + result.unservedCustomers.size() + " customers unserved");
        } else {
            System.out.println("MRA: Solution found with " + result.routes.size() + " routes");
            System.out.println("MRA: Items delivered: " + result.itemsDelivered + "/" + result.itemsTotal);
            System.out.println("MRA: Total distance: " + String.format("%.2f", result.totalDistance));
            System.out.println("MRA: Unserved customers: " + result.unservedCustomers.size());
            logger.logEvent("VRP solution found: " + result.routes.size() + " routes, " + 
                           result.itemsDelivered + "/" + result.itemsTotal + " items delivered, " +
                           "total distance: " + String.format("%.2f", result.totalDistance) +
                           ", unserved: " + result.unservedCustomers.size());
        }
        
        // Set vehicle names for all routes (needed for both backend and file mode)
        // Use original vehicle names from config, not the registered names (which may have request ID suffix)
        for (RouteInfo route : result.routes) {
            int vehicleIndex = route.vehicleId - 1;
            if (vehicleIndex >= 0 && vehicleIndex < availableVehicles.size()) {
                // Get the original vehicle name from config (not the registered name which may have suffix)
                if (vehicleIndex < config.vehicles.size()) {
                    route.vehicleName = config.vehicles.get(vehicleIndex).name;
                } else {
                    // Fallback to registered vehicle name if config doesn't have it
                    VehicleInfo targetVehicle = availableVehicles.get(vehicleIndex);
                    // Remove request ID suffix if present (format: "name-request-id")
                    String vehicleName = targetVehicle.name;
                    if (vehicleName.contains("-") && vehicleName.lastIndexOf("-") > 0) {
                        // Try to extract original name by removing last segment after last dash
                        // But be careful - only do this if it looks like a request ID pattern
                        int lastDash = vehicleName.lastIndexOf("-");
                        String possibleSuffix = vehicleName.substring(lastDash + 1);
                        // If suffix looks like a UUID or request ID, remove it
                        if (possibleSuffix.length() > 10 || possibleSuffix.matches(".*[0-9a-f]{8}.*")) {
                            route.vehicleName = vehicleName.substring(0, lastDash);
                        } else {
                            route.vehicleName = vehicleName;
                        }
                    } else {
                        route.vehicleName = vehicleName;
                    }
                }
            } else {
                route.vehicleName = "unknown";
            }
        }
        
        // Always log result as JSON, even if no routes (will show empty routes array and all unserved customers)
        JsonResultLogger.logResult(result, configName);
        
        // If in backend mode, submit solution first, then assign routes, then signal completion
        if (solutionLatch != null && solutionHolder != null) {
            try {
                // Use reflection to set solution in holder
                java.lang.reflect.Field solutionField = solutionHolder.getClass().getDeclaredField("solution");
                solutionField.setAccessible(true);
                solutionField.set(solutionHolder, result);
                
                // Submit to backend
                System.out.println("MRA: Submitting solution to backend...");
                logger.logEvent("Submitting solution to backend");
                boolean success = BackendClient.submitSolution(configName, result, configName);
                if (success) {
                    System.out.println("MRA: Solution submitted to backend successfully");
                    logger.logEvent("Solution submitted to backend successfully");
                } else {
                    System.err.println("MRA: Failed to submit solution to backend");
                    logger.logEvent("Failed to submit solution to backend");
                }
            } catch (Exception e) {
                System.err.println("MRA: Error submitting to backend: " + e.getMessage());
                logger.log("ERROR: Failed to submit solution to backend: " + e.getMessage());
                e.printStackTrace();
            }
            
            // After submitting to backend, assign routes to vehicles so they can deliver
            // This allows vehicles to execute deliveries and log all delivery events
            // We assign routes BEFORE signaling completion to ensure agents are still alive
            if (!result.routes.isEmpty()) {
                System.out.println("MRA: Assigning routes to vehicles for delivery execution...");
                logger.logEvent("Assigning routes to vehicles for delivery execution");
                assignRoutes(result, availableVehicles);
                
                // Use WakerBehaviour to wait for route assignment responses without blocking agent thread
                // This allows RouteAssignmentResponseHandler to process responses during the wait
                System.out.println("MRA: Waiting for route assignment responses (non-blocking)...");
                logger.logEvent("Waiting for route assignment responses (non-blocking)");
                
                // Add a WakerBehaviour that will signal completion after a delay
                // This doesn't block the agent thread, so RouteAssignmentResponseHandler can process messages
                addBehaviour(new WakerBehaviour(this, 8000) { // Wait 8 seconds for responses
                    @Override
                    protected void onWake() {
                        System.out.println("MRA: Route assignment wait period completed");
                        logger.logEvent("Route assignment wait period completed");
                        
                        // Signal completion to Main AFTER routes have been assigned and wait period completed
                        // This ensures agents are still alive to receive route assignments
                        if (solutionLatch != null && solutionLatch.getCount() > 0) {
                            solutionLatch.countDown();
                            System.out.println("MRA: Signaled completion to Main (solution ready, routes assigned)");
                            logger.logEvent("Signaled completion to Main");
                        }
                    }
                });
                
                // Don't signal completion here - let the WakerBehaviour do it after the wait
                // This allows RouteAssignmentResponseHandler to process responses during the wait
                return; // Exit this method, let behaviors handle the rest
            } else {
                System.out.println("MRA: No routes to assign - all customers unserved");
                logger.logEvent("No routes to assign - all customers unserved");
            }
            
            // Signal completion to Main (only if no routes to assign)
            // If routes were assigned, WakerBehaviour will signal completion after wait period
            solutionLatch.countDown();
            System.out.println("MRA: Signaled completion to Main (solution ready, no routes to assign)");
            logger.logEvent("Signaled completion to Main");
        } else {
            // File mode: assign routes to DAs if there are routes
            if (!result.routes.isEmpty()) {
                assignRoutes(result, availableVehicles);
            } else {
                System.out.println("MRA: No routes to assign - all customers unserved");
                logger.logEvent("No routes to assign - all customers unserved");
            }
        }
    }
    
    /**
     * Assigns routes to Delivery Agents
     */
    private void assignRoutes(SolutionResult result, List<VehicleInfo> availableVehicles) {
        System.out.println("\n=== MRA: Assigning Routes to Delivery Agents ===");
        logger.logEvent("Starting route assignment to " + result.routes.size() + " routes");

        for (int i = 0; i < result.routes.size(); i++) {
            RouteInfo route = result.routes.get(i);
            String routeId = String.valueOf(i + 1);
            int vehicleIndex = route.vehicleId - 1;

            if (vehicleIndex < 0 || vehicleIndex >= availableVehicles.size()) {
                System.err.println("MRA: ERROR - Vehicle index " + vehicleIndex + " out of range for route " + routeId);
                logger.log("ERROR: Route " + routeId + " vehicle index " + vehicleIndex + " out of range");
                continue;
            }

            VehicleInfo targetVehicle = availableVehicles.get(vehicleIndex);
            String targetVehicleName = targetVehicle.name;
            route.vehicleName = targetVehicleName;
            
            System.out.println("MRA: Assigning route " + routeId + " to vehicle index " + vehicleIndex + 
                             " (registered name: " + targetVehicleName + ")");
            logger.logEvent("Assigning route " + routeId + " to vehicle: " + targetVehicleName);

            // Update customer details for the route
            List<String> customerAgentIds = new ArrayList<>();
            for (int j = 0; j < route.customers.size(); j++) {
                CustomerInfo customer = route.customers.get(j);
                // Find matching customer from our list
                for (CustomerInfo originalCustomer : customers) {
                    if (originalCustomer.id == customer.id) {
                        customer.x = originalCustomer.x;
                        customer.y = originalCustomer.y;
                        customer.name = originalCustomer.name;
                        customerAgentIds.add(originalCustomer.name);
                        break;
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
                System.err.println("MRA: ERROR - Could not find DA for vehicle " + targetVehicleName);
                logger.log("ERROR: Could not find DA for vehicle " + targetVehicleName);
                System.err.println("MRA: Available vehicles in registeredVehicles: " + 
                                 registeredVehicles.keySet().toString());
                logger.log("Available vehicles in registeredVehicles: " + 
                          registeredVehicles.keySet().toString());
                continue;
            }
            
            String daName = daAID.getLocalName();
            System.out.println("MRA: Found DA " + daName + " for vehicle " + targetVehicleName);
            logger.logEvent("Found DA " + daName + " for vehicle " + targetVehicleName);

            // Create route assignment message
            ACLMessage routeAssignment = new ACLMessage(ACLMessage.REQUEST);
            routeAssignment.addReceiver(daAID);
            routeAssignment.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            routeAssignment.setOntology("route-assignment");
            String conversationId = "route-assignment-" + routeId + "-" + targetVehicleName + "-" + System.currentTimeMillis();
            routeAssignment.setConversationId(conversationId);
            routeAssignment.setContent("ROUTE_ASSIGNMENT:" + routeContent);

            // Log route assignment details before sending
            System.out.println("\n=== MRA: Assigning Route " + routeId + " to DA ===");
            System.out.println("MRA: Route Assignment Message Details:");
            System.out.println("  Route ID: " + routeId);
            System.out.println("  Vehicle: " + targetVehicleName);
            System.out.println("  DA: " + daName);
            System.out.println("  To: " + daAID.getName());
            System.out.println("  Performative: REQUEST");
            System.out.println("  Protocol: " + FIPANames.InteractionProtocol.FIPA_REQUEST);
            System.out.println("  Ontology: route-assignment");
            System.out.println("  Conversation ID: " + conversationId);
            System.out.println("  Customers: " + route.customers.size());
            System.out.println("  Total Demand: " + route.totalDemand + " items");
            System.out.println("  Total Distance: " + String.format("%.2f", route.totalDistance));
            
            // List customer details
            System.out.println("  Customer List:");
            for (int j = 0; j < route.customers.size(); j++) {
                CustomerInfo customer = route.customers.get(j);
                System.out.println("    " + (j + 1) + ". Customer " + customer.name + 
                                 " (ID: " + customer.id + ") at (" + 
                                 String.format("%.2f", customer.x) + ", " + 
                                 String.format("%.2f", customer.y) + 
                                 "), demand: " + customer.demand);
            }
            
            // Log the message content (truncated if too long)
            String fullContent = "ROUTE_ASSIGNMENT:" + routeContent;
            if (fullContent.length() > 500) {
                System.out.println("  Message Content (first 500 chars): " + fullContent.substring(0, 500) + "...");
                System.out.println("  Message Content Length: " + fullContent.length() + " characters");
            } else {
                System.out.println("  Message Content: " + fullContent);
            }
            System.out.println("=====================================\n");

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

            System.out.println("MRA: ✓ Route assignment message sent to DA " + daName + 
                             " for route " + routeId);
            logger.logEvent("Route assignment message sent successfully to DA " + daName + 
                          " for route " + routeId);
        }

        System.out.println("MRA: Completed route assignment for " + result.routes.size() + " routes");
        logger.logEvent("Completed route assignment for " + result.routes.size() + " routes");
    }
    
    /**
     * Finds Delivery Agent by vehicle name
     * Vehicle names should match exactly with DA local names (both include request ID)
     */
    private AID findDAByName(String vehicleName) {
        try {
            List<AID> daAIDs = findDeliveryAgentsViaDF();
            System.out.println("MRA: Searching for DA with vehicle name: " + vehicleName);
            System.out.println("MRA: Found " + daAIDs.size() + " DAs via DF:");
            for (AID daAID : daAIDs) {
                String daName = daAID.getLocalName();
                System.out.println("MRA:   - DA: " + daName);
                
                // Exact match (most common case - both have request ID)
                if (daName.equals(vehicleName)) {
                    System.out.println("MRA: Found exact match: " + daName + " = " + vehicleName);
                    logger.logEvent("Found DA by exact name match: " + daName);
                    return daAID;
                }
                
                // Check if vehicle name is a prefix of DA name (e.g., "DA1" in "DA1-request-id")
                // This handles cases where vehicle name doesn't include request ID
                if (daName.startsWith(vehicleName + "-")) {
                    System.out.println("MRA: Found prefix match: " + daName + " starts with " + vehicleName);
                    logger.logEvent("Found DA by prefix match: " + daName + " starts with " + vehicleName);
                    return daAID;
                }
            }
            
            System.err.println("MRA: ERROR - Could not find DA with vehicle name: " + vehicleName);
            System.err.println("MRA: Available DAs: " + daAIDs.stream()
                .map(AID::getLocalName)
                .collect(java.util.stream.Collectors.joining(", ")));
            logger.log("ERROR: Could not find DA with vehicle name: " + vehicleName);
            logger.log("Available DAs: " + daAIDs.stream()
                .map(AID::getLocalName)
                .collect(java.util.stream.Collectors.joining(", ")));
        } catch (Exception e) {
            System.err.println("MRA: Error finding DA by name: " + e.getMessage());
            e.printStackTrace();
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
            System.out.println("MRA: Registered with DF as 'mra-service'");
            logger.logEvent("DF Registration successful");
        } catch (FIPAException fe) {
            System.err.println("MRA: Failed to register with DF: " + fe.getMessage());
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
            System.out.println("MRA: Found " + daAIDs.size() + " Delivery Agents via DF");
            logger.log("DF Search: Found " + daAIDs.size() + " Delivery Agents via 'da-service'");
        } catch (FIPAException fe) {
            System.err.println("MRA: Error searching DF for Delivery Agents: " + fe.getMessage());
        }
        return daAIDs;
    }
    
    @Override
    protected void takeDown() {
        logger.logEvent("Agent terminating");
        try {
            DFService.deregister(this);
            System.out.println("MRA: Deregistered from DF");
            logger.logEvent("Deregistered from DF");
        } catch (FIPAException fe) {
            System.err.println("MRA: Error deregistering from DF: " + fe.getMessage());
            logger.log("ERROR: Failed to deregister from DF: " + fe.getMessage());
        }
        System.out.println("Master Routing Agent " + getAID().getName() + " terminating.");
        logger.close();
    }
}

