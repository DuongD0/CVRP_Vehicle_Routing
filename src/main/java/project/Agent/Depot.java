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

import java.util.*;

/**
 * Depot Agent that manages inventory and routes vehicles
 */
public class Depot extends Agent {
    // Inventory management (simulate real-life warehouse)
    private Map<String, Integer> inventory;
    
    // Depot location
    private double depotX;
    private double depotY;
    
    // Vehicle management
    private Map<String, VehicleInfo> registeredVehicles;
    
    // Request queue and batching
    private List<CustomerRequest> pendingRequests;
    private List<CustomerRequest> currentBatch;
    private static final int BATCH_THRESHOLD = 5;  // threshold for batch processing
    
    // Solver interface
    private VRPSolver solver;
    private DepotProblemAssembler problemAssembler;
    
    // Logger for conversations
    private AgentLogger logger;
    
    @Override
    protected void setup() {
        System.out.println("Depot Agent " + getAID().getName() + " is ready.");
        
        // Initialize logger
        logger = new AgentLogger("Depot");
        logger.setAgentAID(this);  // Set agent AID for proper logging
        logger.logEvent("Agent started");
        
        // Initialize inventory
        inventory = new HashMap<>();
        inventory.put("ItemA", 100);
        inventory.put("ItemB", 100);
        inventory.put("ItemC", 100);
        inventory.put("ItemD", 100);
        
        // Depot location
        depotX = 0.0;
        depotY = 0.0;
        
        // Initialize collections
        registeredVehicles = new HashMap<>();
        pendingRequests = new ArrayList<>();
        currentBatch = new ArrayList<>();
        
        // Initialize solver
        solver = new ORToolsSolver();
        problemAssembler = new DepotProblemAssembler(solver, logger);
        
        System.out.println("Depot: Inventory initialized with " + inventory.size() + " item types");
        System.out.println("Depot: Located at (" + depotX + ", " + depotY + ")");
        
        // Register with DF for automatic discovery
        registerWithDF();
        logger.logEvent("Registered with DF as 'depot-service'");
        
        // Add behavior to handle customer requests
        addBehaviour(new CustomerRequestHandler());
        
        // Add behavior to process batches periodically
        addBehaviour(new BatchProcessor(this, 10000));  // Check every 10 seconds
    }
    
    /**
     * Handles FIPA-REQUEST messages from Customer agents
     */
    private class CustomerRequestHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST)
            );
            
            ACLMessage msg = receive(template);
            if (msg != null) {
                logger.logReceived(msg);
                
                // Log conversation start for customer request
                if (msg.getConversationId() != null) {
                    logger.logConversationStart(msg.getConversationId(), 
                        "Customer request from " + (msg.getSender() != null ? msg.getSender().getLocalName() : "unknown"));
                }
                
                handleCustomerRequest(msg);
            } else {
                block();
            }
        }
    }
    
    /**
     * Processes a customer request: checks inventory and responds
     */
    private void handleCustomerRequest(ACLMessage msg) {
        try {
            String content = msg.getContent();

            // Format: REQUEST:customerId|customerName|x|y|itemName|quantity
            String[] parts = content.split("\\|");
            
            if (parts.length < 6 || !parts[0].startsWith("REQUEST:")) {
                System.err.println("Depot: Invalid request format: " + content);
                sendRefusal(msg, "Invalid request format");
                return;
            }
            
            String customerId = parts[0].substring("REQUEST:".length());
            String customerName = parts[1];
            double x = Double.parseDouble(parts[2]);
            double y = Double.parseDouble(parts[3]);
            String itemName = parts[4];
            int quantity = Integer.parseInt(parts[5]);
            
            CustomerRequest request = new CustomerRequest(customerId, customerName, x, y, itemName, quantity);
            
            System.out.println("\n=== Depot: Processing Customer Request ===");
            System.out.println("Depot: Customer: " + customerName + " (" + customerId + ")");
            System.out.println("Depot: Item: " + itemName + ", Quantity: " + quantity);
            System.out.println("Depot: Location: (" + x + ", " + y + ")");
            System.out.println("Depot: Request ID: " + request.requestId);
            logger.logEvent("Processing request from " + customerName + " (" + customerId + "): " + 
                          quantity + "x " + itemName + " at (" + x + ", " + y + ")");
            
            // Check inventory
            int available = inventory.getOrDefault(itemName, 0);
            System.out.println("Depot: Checking inventory for " + itemName + ": Available = " + available + ", Requested = " + quantity);
            logger.logEvent("Inventory check for " + itemName + ": Available=" + available + ", Requested=" + quantity);
            
            if (available >= quantity) {
                // Item available - add to batch and respond
                inventory.put(itemName, available - quantity);
                pendingRequests.add(request);
                
                System.out.println("Depot: ✓ Item available. Reserved " + quantity + " units");
                System.out.println("Depot: Updated inventory: " + itemName + " = " + inventory.get(itemName) + 
                                 " (was " + available + ", reserved " + quantity + ")");
                System.out.println("Depot: Request added to queue. Queue size: " + pendingRequests.size());
                logger.logEvent("Item available. Reserved " + quantity + " units. " +
                              "Inventory: " + itemName + " = " + inventory.get(itemName) + 
                              " (was " + available + "). Queue size: " + pendingRequests.size());
                
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                reply.setConversationId(msg.getConversationId());
                reply.setContent("ITEM_AVAILABLE:Request accepted. Item will be delivered in next batch.");
                
                // Log conversation end
                logger.logConversationEnd(msg.getConversationId(), 
                    "Request accepted - item available, queued for delivery");
                
                logger.logSent(reply);
                send(reply);
                
                System.out.println("Depot: Sent acceptance message to customer " + customerName);
            } else {
                // Item unavailable - respond with refusal
                System.out.println("Depot: ✗ Item unavailable. Available: " + available + ", Requested: " + quantity);
                logger.logEvent("Item unavailable: Available=" + available + ", Requested=" + quantity + 
                              ". Request rejected.");
                
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                reply.setConversationId(msg.getConversationId());
                reply.setContent("ITEM_UNAVAILABLE:Insufficient inventory. Available: " + available + ", Requested: " + quantity);
                
                // Log conversation end
                logger.logConversationEnd(msg.getConversationId(), 
                    "Request rejected - insufficient inventory");
                
                logger.logSent(reply);
                send(reply);
                
                System.out.println("Depot: Sent rejection message to customer " + customerName);
            }
            
        } catch (Exception e) {
            System.err.println("Depot: Error handling customer request: " + e.getMessage());
            e.printStackTrace();
            sendRefusal(msg, "Internal error processing request");
        }
    }
    
    /**
     * Sends a REFUSE message to customer
     */
    private void sendRefusal(ACLMessage originalMsg, String reason) {
        ACLMessage refusal = originalMsg.createReply();
        refusal.setPerformative(ACLMessage.REFUSE);
        refusal.setContent(reason);
        logger.logSent(refusal);
        send(refusal);
    }
    
    /**
     * Processes batches when threshold is reached
     */
    private class BatchProcessor extends WakerBehaviour {
        public BatchProcessor(Agent a, long timeout) {
            super(a, timeout);
        }
        
        @Override
        protected void onWake() {
            if (pendingRequests.size() >= BATCH_THRESHOLD) {
                // Process batch
                processBatch();
            }
            
            // Reschedule
            reset(10000);
        }
    }
    
    /**
     * Processes a batch of requests: solves VRP and assigns routes via Contract-Net
     */
    private void processBatch() {
        if (pendingRequests.isEmpty()) {
            return;
        }
        
        System.out.println("\n=== Depot: Processing Batch ===");
        System.out.println("Depot: Batch size: " + pendingRequests.size() + " requests");
        logger.logEvent("Processing batch with " + pendingRequests.size() + " requests");
        
        // Log batch details
        for (int i = 0; i < pendingRequests.size(); i++) {
            CustomerRequest req = pendingRequests.get(i);
            System.out.println("Depot: Batch request " + (i + 1) + ": " + req.customerName + 
                             " (" + req.customerId + ") - " + req.quantity + "x " + req.itemName + 
                             " at (" + req.x + ", " + req.y + ")");
            logger.logEvent("Batch request " + (i + 1) + ": " + req.customerName + " - " + 
                          req.quantity + "x " + req.itemName + " at (" + req.x + ", " + req.y + ")");
        }
        
        // Move requests to current batch
        currentBatch = new ArrayList<>(pendingRequests);
        pendingRequests.clear();
        System.out.println("Depot: Moved " + currentBatch.size() + " requests to current batch");
        logger.logEvent("Moved " + currentBatch.size() + " requests to current batch for processing");
        
        // Query available vehicles
        List<VehicleInfo> availableVehicles = queryAvailableVehicles();
        
        if (availableVehicles.isEmpty()) {
            System.out.println("Depot: ERROR - No vehicles available. Requests will be queued again.");
            logger.logEvent("ERROR: No vehicles available. Queuing " + currentBatch.size() + " requests again");
            pendingRequests.addAll(currentBatch);
            return;
        }
        
        System.out.println("Depot: Found " + availableVehicles.size() + " available vehicles for route assignment");
        logger.logEvent("Found " + availableVehicles.size() + " available vehicles");
        
        System.out.println("Depot: Preparing vehicle data for solver:");
        for (int i = 0; i < availableVehicles.size(); i++) {
            VehicleInfo vehicle = availableVehicles.get(i);
            System.out.println("Depot: Vehicle " + (i + 1) + " (" + vehicle.name + "): " +
                             "Capacity=" + vehicle.capacity + ", MaxDistance=" + vehicle.maxDistance);
            logger.logEvent("Vehicle " + (i + 1) + " (" + vehicle.name + "): " +
                          "Capacity=" + vehicle.capacity + ", MaxDistance=" + vehicle.maxDistance);
        }
        
        System.out.println("Depot: Calling VRP solver with " + availableVehicles.size() + " vehicles and " + 
                         currentBatch.size() + " customer requests");
        logger.logEvent("Calling VRP solver: " + availableVehicles.size() + " vehicles, " + 
                      currentBatch.size() + " customers");
        
        SolutionResult result = problemAssembler.assembleAndSolve(
            depotX,
            depotY,
            currentBatch,
            availableVehicles
        );
        
        if (result == null || result.routes.isEmpty()) {
            System.out.println("Depot: No solution found. Requests will be queued again.");
            pendingRequests.addAll(currentBatch);
            return;
        }
        
        System.out.println("\n=== Depot: VRP Solution Summary ===");
        System.out.println("Depot: Solution found with " + result.routes.size() + " routes");
        System.out.println("Depot: Items delivered: " + result.itemsDelivered + "/" + result.itemsTotal);
        System.out.println("Depot: Total distance: " + String.format("%.2f", result.totalDistance));
        System.out.println("Depot: Solve time: " + result.solveTimeMs + " ms");
        logger.logEvent("VRP solution found: " + result.routes.size() + " routes, " + 
                       result.itemsDelivered + "/" + result.itemsTotal + " items delivered, " +
                       "total distance: " + String.format("%.2f", result.totalDistance) + ", solve time: " + result.solveTimeMs + " ms");
        
        // Log detailed route information
        for (int i = 0; i < result.routes.size(); i++) {
            RouteInfo route = result.routes.get(i);
            System.out.println("Depot: Route " + (i + 1) + " (Vehicle ID: " + route.vehicleId + "): " + 
                             route.customers.size() + " customers, " + 
                             route.totalDemand + " items, " + 
                             String.format("%.2f", route.totalDistance) + " distance");
            logger.logEvent("Route " + (i + 1) + " details: Vehicle ID=" + route.vehicleId + 
                          ", Customers=" + route.customers.size() + 
                          ", Demand=" + route.totalDemand + 
                          ", Distance=" + String.format("%.2f", route.totalDistance));
        }
        
        // Assign routes directly to vehicles (routes already have vehicle IDs from solver)
        assignRoutes(result, availableVehicles);
    }
    
    /**
     * Queries all vehicle agents discovered via DF for their state
     * Returns list of available (free) vehicles with their information
     */
    private List<VehicleInfo> queryAvailableVehicles() {
        List<VehicleInfo> available = new ArrayList<>();
        
        System.out.println("\n=== Depot: Querying Vehicle States ===");
        logger.logEvent("Querying vehicle states via DF");
        
        // Find vehicles via DF
        List<AID> vehicleAIDs = findVehiclesViaDF();
        if (vehicleAIDs.isEmpty()) {
            System.out.println("Depot: No vehicles found via DF");
            logger.logEvent("No vehicles found via DF");
            return available;
        }
        
        System.out.println("Depot: Found " + vehicleAIDs.size() + " vehicles via DF");
        logger.logEvent("Found " + vehicleAIDs.size() + " vehicles via DF");
        
        // Create response handler first to capture responses
        VehicleStateResponseHandler responseHandler = new VehicleStateResponseHandler();
        addBehaviour(responseHandler);
        
        // Query each vehicle
        for (AID vehicleAID : vehicleAIDs) {
            String vehicleIdentifier = vehicleAID.getLocalName();
            try {
                System.out.println("Depot: Querying vehicle: " + vehicleIdentifier);
                logger.logEvent("Querying vehicle: " + vehicleIdentifier);
                
                // Check if already registered
                VehicleInfo vehicle = registeredVehicles.get(vehicleIdentifier);
                if (vehicle == null) {
                    // Will be created from response
                    vehicle = new VehicleInfo(vehicleIdentifier, 50, 1000.0);
                    registeredVehicles.put(vehicleIdentifier, vehicle);
                    System.out.println("Depot: Created new VehicleInfo for " + vehicleIdentifier);
                    logger.logEvent("Created new VehicleInfo for " + vehicleIdentifier);
                }
                
                ACLMessage query = new ACLMessage(ACLMessage.QUERY_REF);
                query.addReceiver(vehicleAID);
                query.setContent("QUERY_STATE");
                query.setProtocol(FIPANames.InteractionProtocol.FIPA_QUERY);
                String queryConversationId = "vehicle-query-" + vehicleIdentifier + "-" + System.currentTimeMillis();
                query.setConversationId(queryConversationId);
                
                // Log conversation start for vehicle state query
                logger.logConversationStart(queryConversationId, 
                    "State query to vehicle " + vehicleIdentifier);
                
                logger.logSent(query);
                send(query);
                
                System.out.println("Depot: Sent state query to " + vehicleIdentifier);
                
            } catch (Exception e) {
                System.err.println("Depot: Error querying vehicle " + vehicleIdentifier + ": " + e.getMessage());
                logger.log("ERROR: Failed to query vehicle " + vehicleIdentifier + ": " + e.getMessage());
            }
        }
        
        // Wait for responses (give vehicles time to respond)
        try {
            System.out.println("Depot: Waiting for vehicle responses...");
            Thread.sleep(1000); // Wait 1 second for responses
        } catch (InterruptedException e) {
            System.err.println("Depot: Interrupted while waiting for vehicle responses");
        }
        
        // Collect available vehicles from registered vehicles
        System.out.println("Depot: Collecting available vehicles from responses...");
        for (VehicleInfo vehicle : registeredVehicles.values()) {
            if ("free".equals(vehicle.state)) {
                available.add(vehicle);
                System.out.println("Depot: Vehicle " + vehicle.name + " is available (free) - " +
                                 "Capacity: " + vehicle.capacity + ", MaxDistance: " + vehicle.maxDistance);
                logger.logEvent("Vehicle " + vehicle.name + " is available: capacity=" + vehicle.capacity + 
                              ", maxDistance=" + vehicle.maxDistance);
            } else {
                System.out.println("Depot: Vehicle " + vehicle.name + " is NOT available (state: " + vehicle.state + ")");
                logger.logEvent("Vehicle " + vehicle.name + " is not available: state=" + vehicle.state);
            }
        }
        
        System.out.println("Depot: Found " + available.size() + " available vehicles out of " + registeredVehicles.size() + " total");
        logger.logEvent("Found " + available.size() + " available vehicles out of " + registeredVehicles.size() + " total");
        
        return available;
    }
    
    /**
     * Handles vehicle state responses
     */
    private class VehicleStateResponseHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_QUERY)
            );
            
                ACLMessage msg = receive(template);
            if (msg != null) {
                logger.logReceived(msg);
                
                String content = msg.getContent();
                String[] parts = content != null ? content.split("\\|") : new String[0];
                String state = null;
                String name = null;
                Integer capacity = null;
                Double maxDistance = null;

                for (String part : parts) {
                    if (part.startsWith("STATE:")) {
                        state = part.substring("STATE:".length());
                    } else if (part.startsWith("NAME:")) {
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

                if (state == null) {
                    logger.log("WARNING: Received vehicle state without state value from " +
                              (name != null ? name : "unknown vehicle"));
                    return;
                }

                if (msg.getConversationId() != null) {
                    StringBuilder convSummary = new StringBuilder();
                    convSummary.append("Vehicle state received - ")
                               .append(name != null ? name : "unknown")
                               .append(": State=").append(state);
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
                        System.out.println("Depot: Registered new vehicle " + name +
                                         " (capacity: " + initialCapacity + ", maxDistance: " + initialMaxDistance + ")");
                        logger.logEvent("Registered new vehicle " + name +
                                      ": capacity=" + initialCapacity + ", maxDistance=" + initialMaxDistance);
                    } else {
                        if (capacity != null) {
                            vehicle.capacity = capacity;
                        }
                        if (maxDistance != null) {
                            vehicle.maxDistance = maxDistance;
                        }
                        if (capacity != null || maxDistance != null) {
                            System.out.println("Depot: Updated vehicle " + name + " capacity/maxDistance");
                            logger.logEvent("Updated vehicle " + name + ": capacity=" + vehicle.capacity +
                                          ", maxDistance=" + vehicle.maxDistance);
                        }
                    }
                    vehicle.state = state;

                    StringBuilder stateLog = new StringBuilder();
                    stateLog.append("Depot: Updated vehicle ").append(name).append(" state: ").append(state);
                    if (capacity != null || maxDistance != null) {
                        stateLog.append(" (capacity: ").append(vehicle.capacity)
                                .append(", maxDistance: ").append(vehicle.maxDistance).append(")");
                    }
                    System.out.println(stateLog);
                    logger.logEvent("Updated vehicle " + name + " state: " + state +
                                  ((capacity != null || maxDistance != null) ?
                                      " (capacity=" + vehicle.capacity + ", maxDistance=" + vehicle.maxDistance + ")" :
                                      ""));
                } else {
                    System.out.println("Depot: WARNING - Received vehicle state response without vehicle name");
                    logger.log("WARNING: Received vehicle state response without vehicle name");
                }
            } else {
                block();
            }
        }
    }
    
    /**
     * Sends the solver result routes directly to the designated vehicles.
     * Each route already contains the vehicle identifier selected by the solver.
     */
    private void assignRoutes(SolutionResult result, List<VehicleInfo> availableVehicles) {
        System.out.println("\n=== Depot: Dispatching Routes to Vehicles ===");
        logger.logEvent("Starting targeted route dispatch to " + result.routes.size() + " routes");

        for (int i = 0; i < result.routes.size(); i++) {
            RouteInfo route = result.routes.get(i);
            String routeId = String.valueOf(i + 1);
            int vehicleIndex = route.vehicleId - 1;

            if (vehicleIndex < 0 || vehicleIndex >= availableVehicles.size()) {
                System.err.println("Depot: ERROR - Vehicle index " + vehicleIndex + " out of range for route " + routeId);
                logger.log("ERROR: Route " + routeId + " vehicle index " + vehicleIndex + " out of range");
                continue;
            }

            VehicleInfo targetVehicle = availableVehicles.get(vehicleIndex);
            route.vehicleName = targetVehicle.name;

            // Update customer details for the route
            List<String> customerAgentIds = new ArrayList<>();
            for (int j = 0; j < route.customers.size(); j++) {
                CustomerInfo customer = route.customers.get(j);
                int requestIndex = customer.id - 1;
                if (requestIndex >= 0 && requestIndex < currentBatch.size()) {
                    CustomerRequest req = currentBatch.get(requestIndex);
                    customer.x = req.x;
                    customer.y = req.y;
                    customer.name = req.customerName;
                    customerAgentIds.add(req.customerId);
                    System.out.println("Depot: Route " + routeId + " - Customer " + customer.id +
                                     ": " + req.customerName + " at (" + req.x + ", " + req.y + ")");
                    logger.logEvent("Route " + routeId + " customer " + customer.id +
                                  ": " + req.customerName + " at (" + req.x + ", " + req.y + ")");
                } else {
                    customerAgentIds.add("customer-" + customer.id);
                    System.out.println("Depot: WARNING - Route " + routeId + " customer " + customer.id +
                                     " not found in batch (using fallback ID)");
                    logger.log("WARNING: Route " + routeId + " customer " + customer.id + " not found in batch");
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

            ACLMessage routeAssignment = new ACLMessage(ACLMessage.REQUEST);
            routeAssignment.addReceiver(new AID(targetVehicle.name, AID.ISLOCALNAME));
            routeAssignment.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            routeAssignment.setOntology("route-assignment");
            String conversationId = "route-assignment-" + routeId + "-" + System.currentTimeMillis();
            routeAssignment.setConversationId(conversationId);
            routeAssignment.setContent("ROUTE_ASSIGNMENT:" + routeContent);

            logger.logConversationStart(conversationId,
                "Route " + routeId + " sent to vehicle " + targetVehicle.name + " (" +
                route.customers.size() + " customers, demand " + route.totalDemand + ")");

            logger.logSent(routeAssignment);
            send(routeAssignment);

            System.out.println("Depot: Sent route " + routeId + " to vehicle " + targetVehicle.name +
                             " (demand: " + route.totalDemand + " items, " +
                             "distance: " + String.format("%.2f", route.totalDistance) + ")");
            logger.logEvent("Dispatched route " + routeId + " to vehicle " + targetVehicle.name +
                          " (" + route.customers.size() + " customers, " +
                          String.format("%.2f", route.totalDistance) + " distance)");
        }

        System.out.println("Depot: Completed route dispatch for " + result.routes.size() + " routes");
        logger.logEvent("Completed route dispatch for " + result.routes.size() + " routes");
    }
    
    /**
     * Registers Depot agent with DF (Directory Facilitator)
     */
    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            
            ServiceDescription sd = new ServiceDescription();
            sd.setType("depot-service");
            sd.setName("VRP-Depot");
            sd.setOwnership("VRP-System");
            dfd.addServices(sd);
            
            DFService.register(this, dfd); 
            System.out.println("Depot: Registered with DF as 'depot-service'");
            logger.logEvent("DF Registration successful");
        } catch (FIPAException fe) {
            System.err.println("Depot: Failed to register with DF: " + fe.getMessage());
        }
    }
    
    /**
     * Finds vehicles via DF
     */
    private List<AID> findVehiclesViaDF() {
        List<AID> vehicleAIDs = new ArrayList<>();
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("vehicle-service");
            dfd.addServices(sd);
            
            DFAgentDescription[] results = DFService.search(this, dfd);
            for (DFAgentDescription result : results) {
                vehicleAIDs.add(result.getName());
            }
            System.out.println("Depot: Found " + vehicleAIDs.size() + " vehicles via DF");
            logger.log("DF Search: Found " + vehicleAIDs.size() + " vehicles via 'vehicle-service'");
        } catch (FIPAException fe) {
            System.err.println("Depot: Error searching DF for vehicles: " + fe.getMessage());
        }
        return vehicleAIDs;
    }
    
    @Override
    protected void takeDown() {
        logger.logEvent("Agent terminating");
        try {
            DFService.deregister(this);
            System.out.println("Depot: Deregistered from DF");
            logger.logEvent("Deregistered from DF");
        } catch (FIPAException fe) {
            System.err.println("Depot: Error deregistering from DF: " + fe.getMessage());
            logger.log("ERROR: Failed to deregister from DF: " + fe.getMessage());
        }
        System.out.println("Depot Agent " + getAID().getName() + " terminating.");
        logger.close();
    }
}

