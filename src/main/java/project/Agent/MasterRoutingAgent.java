package project.Agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
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
    
    // Problem data (from config)
    private List<CustomerInfo> customers;
    
    // Vehicle management
    private Map<String, VehicleInfo> registeredVehicles;
    
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
            System.err.println("ERROR: MRA requires config and configName as arguments");
            doDelete();
            return;
        }
        
        System.out.println("Master Routing Agent (MRA) " + getAID().getName() + " is ready.");
        
        // Initialize logger
        logger = new AgentLogger("MRA");
        logger.setAgentAID(this);
        logger.logEvent("Agent started");
        
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
        
        // Wait for responses, then solve
        addBehaviour(new WakerBehaviour(this, 2000) {
            @Override
            protected void onWake() {
                solveAndAssignRoutes();
            }
        });
    }
    
    /**
     * Handles vehicle information responses from DAs
     */
    private class VehicleInfoResponseHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST)
            );
            
            ACLMessage msg = receive(template);
            if (msg != null) {
                logger.logReceived(msg);
                
                String content = msg.getContent();
                String[] parts = content != null ? content.split("\\|") : new String[0];
                String name = null;
                Integer capacity = null;
                Double maxDistance = null;

                for (String part : parts) {
                    if (part.startsWith("NAME:")) {
                        name = part.substring("NAME:".length());
                    } else if (part.startsWith("CAPACITY:")) {
                        capacity = Integer.parseInt(part.substring("CAPACITY:".length()));
                    } else if (part.startsWith("MAX_DISTANCE:")) {
                        maxDistance = Double.parseDouble(part.substring("MAX_DISTANCE:".length()));
                    }
                }

                if (name == null && msg.getSender() != null) {
                    name = msg.getSender().getLocalName();
                }

                if (msg.getConversationId() != null) {
                    StringBuilder convSummary = new StringBuilder();
                    convSummary.append("Vehicle info received - ")
                               .append(name != null ? name : "unknown");
                    if (capacity != null) {
                        convSummary.append(", Capacity=").append(capacity);
                    }
                    if (maxDistance != null) {
                        convSummary.append(", MaxDistance=").append(maxDistance);
                    }
                    logger.logConversationEnd(msg.getConversationId(), convSummary.toString());
                }

                if (name != null) {
                    VehicleInfo vehicle = registeredVehicles.get(name);
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
        for (CustomerInfo customer : customers) {
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
        
        if (result == null || result.routes.isEmpty()) {
            System.err.println("MRA: No solution found");
            logger.logEvent("No solution found");
            return;
        }
        
        System.out.println("\n=== MRA: VRP Solution Summary ===");
        System.out.println("MRA: Solution found with " + result.routes.size() + " routes");
        System.out.println("MRA: Items delivered: " + result.itemsDelivered + "/" + result.itemsTotal);
        System.out.println("MRA: Total distance: " + String.format("%.2f", result.totalDistance));
        logger.logEvent("VRP solution found: " + result.routes.size() + " routes, " + 
                       result.itemsDelivered + "/" + result.itemsTotal + " items delivered, " +
                       "total distance: " + String.format("%.2f", result.totalDistance));
        
        // Log result as JSON
        JsonResultLogger.logResult(result, configName);
        
        // Assign routes to DAs
        assignRoutes(result, availableVehicles);
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
            route.vehicleName = targetVehicle.name;

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
            routeContent.append("VEHICLE_NAME:").append(targetVehicle.name).append("|");
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

            // Find DA by vehicle name
            AID daAID = findDAByName(targetVehicle.name);
            if (daAID == null) {
                System.err.println("MRA: ERROR - Could not find DA for vehicle " + targetVehicle.name);
                logger.log("ERROR: Could not find DA for vehicle " + targetVehicle.name);
                continue;
            }

            ACLMessage routeAssignment = new ACLMessage(ACLMessage.REQUEST);
            routeAssignment.addReceiver(daAID);
            routeAssignment.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            routeAssignment.setOntology("route-assignment");
            String conversationId = "route-assignment-" + routeId + "-" + System.currentTimeMillis();
            routeAssignment.setConversationId(conversationId);
            routeAssignment.setContent("ROUTE_ASSIGNMENT:" + routeContent);

            logger.logConversationStart(conversationId,
                "Route " + routeId + " sent to DA " + targetVehicle.name + " (" +
                route.customers.size() + " customers, demand " + route.totalDemand + ")");

            logger.logSent(routeAssignment);
            send(routeAssignment);

            System.out.println("MRA: Sent route " + routeId + " to DA " + targetVehicle.name +
                             " (demand: " + route.totalDemand + " items, " +
                             "distance: " + String.format("%.2f", route.totalDistance) + ")");
            logger.logEvent("Dispatched route " + routeId + " to DA " + targetVehicle.name +
                          " (" + route.customers.size() + " customers, " +
                          String.format("%.2f", route.totalDistance) + " distance)");
        }

        System.out.println("MRA: Completed route assignment for " + result.routes.size() + " routes");
        logger.logEvent("Completed route assignment for " + result.routes.size() + " routes");
    }
    
    /**
     * Finds Delivery Agent by vehicle name
     */
    private AID findDAByName(String vehicleName) {
        try {
            List<AID> daAIDs = findDeliveryAgentsViaDF();
            for (AID daAID : daAIDs) {
                if (daAID.getLocalName().equals(vehicleName) || 
                    daAID.getLocalName().equals("da-" + vehicleName) ||
                    daAID.getLocalName().contains(vehicleName)) {
                    return daAID;
                }
            }
        } catch (Exception e) {
            System.err.println("MRA: Error finding DA by name: " + e.getMessage());
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

