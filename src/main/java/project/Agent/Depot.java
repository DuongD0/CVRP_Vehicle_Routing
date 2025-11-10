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
 * Implements Basic Requirements 1 & 2:
 * - Basic Requirement 1: Prioritizes number of items delivered over total travel distance
 * - Basic Requirement 2: Enforces maximum distance constraint per vehicle
 */
public class Depot extends Agent {
    // Inventory management
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
    
    // Problem data for solver
    private double[] x;
    private double[] y;
    private int[] demand;
    private int[][] distance;
    
    // Logger for conversations
    private AgentLogger logger;
    
    // Track route assignments to handle multiple acceptances
    private Map<String, String> routeAssignments;  // routeId -> vehicleName (first acceptance wins)
    private Map<String, Long> routeAssignmentTimes;  // routeId -> assignment timestamp
    
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
        
        // Initialize route assignment tracking
        routeAssignments = new HashMap<>();
        routeAssignmentTimes = new HashMap<>();
        
        // Initialize solver
        solver = new ORToolsSolver();
        
        System.out.println("Depot: Inventory initialized with " + inventory.size() + " item types");
        System.out.println("Depot: Located at (" + depotX + ", " + depotY + ")");
        
        // Register with DF for automatic discovery
        registerWithDF();
        logger.logEvent("Registered with DF as 'depot-service'");
        
        // Add behavior to handle customer requests
        addBehaviour(new CustomerRequestHandler());
        
        // Add behavior to handle route acceptances
        addBehaviour(new RouteAcceptanceHandler());
        
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
        
        // Build problem for solver
        buildProblemFromRequests(currentBatch, availableVehicles);
        
        // Prepare vehicle capacities and max distances for solver
        int numVehicles = availableVehicles.size();
        int[] vehicleCapacities = new int[numVehicles];
        double[] vehicleMaxDistances = new double[numVehicles];
        
        System.out.println("Depot: Preparing vehicle data for solver:");
        for (int i = 0; i < numVehicles; i++) {
            VehicleInfo vehicle = availableVehicles.get(i);
            vehicleCapacities[i] = vehicle.capacity;
            vehicleMaxDistances[i] = vehicle.maxDistance;
            System.out.println("Depot: Vehicle " + (i + 1) + " (" + vehicle.name + "): " +
                             "Capacity=" + vehicle.capacity + ", MaxDistance=" + vehicle.maxDistance);
            logger.logEvent("Vehicle " + (i + 1) + " (" + vehicle.name + "): " +
                          "Capacity=" + vehicle.capacity + ", MaxDistance=" + vehicle.maxDistance);
        }
        
        System.out.println("Depot: Calling VRP solver with " + numVehicles + " vehicles and " + 
                         currentBatch.size() + " customer requests");
        logger.logEvent("Calling VRP solver: " + numVehicles + " vehicles, " + 
                      currentBatch.size() + " customers");
        
        // Solve CVRP with capacity and maximum distance constraints
        SolutionResult result = solver.solve(
            x.length,           // numNodes
            x.length - 1,       // numCustomers
            numVehicles,        // numVehicles
            vehicleCapacities,  // vehicle capacities
            vehicleMaxDistances, // vehicle max distances
            demand,             // demand array
            distance            // distance matrix
        );
        
        if (result.routes.isEmpty()) {
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
                // Format: STATE:free|CAPACITY:50|MAX_DISTANCE:1000.0|NAME:Vehicle1|X:800.0|Y:600.0
                String[] parts = content.split("\\|");
                String state = null;
                String name = null;
                int capacity = 50;
                double maxDistance = 1000.0;
                
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
                
                // Log conversation end for state query response
                if (msg.getConversationId() != null && name != null) {
                    logger.logConversationEnd(msg.getConversationId(), 
                        "Vehicle state received - " + name + ": State=" + state + 
                        ", Capacity=" + capacity + ", MaxDistance=" + maxDistance);
                }
                
                if (name != null) {
                    VehicleInfo vehicle = registeredVehicles.get(name);
                    if (vehicle == null) {
                        vehicle = new VehicleInfo(name, capacity, maxDistance);
                        registeredVehicles.put(name, vehicle);
                        System.out.println("Depot: Registered new vehicle " + name + 
                                         " (capacity: " + capacity + ", maxDistance: " + maxDistance + ")");
                        logger.logEvent("Registered new vehicle " + name + 
                                      ": capacity=" + capacity + ", maxDistance=" + maxDistance);
                    } else {
                        vehicle.capacity = capacity;
                        vehicle.maxDistance = maxDistance;
                        System.out.println("Depot: Updated vehicle " + name + " capacity and maxDistance");
                        logger.logEvent("Updated vehicle " + name + ": capacity=" + capacity + ", maxDistance=" + maxDistance);
                    }
                    vehicle.state = state;
                    System.out.println("Depot: Updated vehicle " + name + " state: " + state + 
                                     " (capacity: " + capacity + ", maxDistance: " + maxDistance + ")");
                    logger.logEvent("Updated vehicle " + name + " state: " + state + 
                                  " (capacity: " + capacity + ", maxDistance: " + maxDistance + ")");
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
     * Builds problem data structures from customer requests
     */
    private void buildProblemFromRequests(List<CustomerRequest> requests, List<VehicleInfo> vehicles) {
        int numCustomers = requests.size();
        int numNodes = numCustomers + 1;  // +1 for depot
        
        x = new double[numNodes];
        y = new double[numNodes];
        demand = new int[numNodes];
        
        // Depot at index 0
        x[0] = depotX;
        y[0] = depotY;
        demand[0] = 0;
        
        // Customers
        int idx = 1;
        for (CustomerRequest req : requests) {
            x[idx] = req.x;
            y[idx] = req.y;
            demand[idx] = req.quantity;
            idx++;
        }
        
        // Build distance matrix (straight-line distance)
        distance = new int[numNodes][numNodes];
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                double dx = x[i] - x[j];
                double dy = y[i] - y[j];
                distance[i][j] = (int) Math.round(Math.hypot(dx, dy));
            }
        }
    }
    
    /**
     * Broadcasts routes to all available vehicles for self-evaluation
     * Vehicles will self-check and accept routes they can handle
     */
    private void assignRoutes(SolutionResult result, List<VehicleInfo> availableVehicles) {
        System.out.println("\n=== Depot: Broadcasting Routes to Vehicle Service for Self-Evaluation ===");
        logger.logEvent("Starting route broadcast to " + availableVehicles.size() + " vehicles for self-evaluation");
        
        // Clear route assignment tracking for new batch
        routeAssignments.clear();
        routeAssignmentTimes.clear();
        
        // Find all vehicle AIDs via DF
        List<AID> vehicleAIDs = findVehiclesViaDF();
        if (vehicleAIDs.isEmpty()) {
            System.err.println("Depot: ERROR - No vehicles found via DF for route broadcast");
            logger.log("ERROR: No vehicles found via DF for route broadcast");
            return;
        }
        
        System.out.println("Depot: Found " + vehicleAIDs.size() + " vehicles via DF for route broadcast");
        logger.logEvent("Found " + vehicleAIDs.size() + " vehicles via DF for route broadcast");
        
        // Process each route
        for (int i = 0; i < result.routes.size(); i++) {
            RouteInfo route = result.routes.get(i);
            String routeId = String.valueOf(i + 1);
            
            // Update route with proper customer coordinates and store customer IDs
            List<String> customerAgentIds = new ArrayList<>();
            for (int j = 0; j < route.customers.size(); j++) {
                CustomerInfo customer = route.customers.get(j);
                // Find corresponding request by matching customer ID with request index
                int requestIndex = customer.id - 1;  // Customer ID is 1-indexed, requests are 0-indexed
                if (requestIndex >= 0 && requestIndex < currentBatch.size()) {
                    CustomerRequest req = currentBatch.get(requestIndex);
                    customer.x = req.x;
                    customer.y = req.y;
                    customer.name = req.customerName;
                    // Store the actual customer agent ID (e.g., "customer-1", "customer-2")
                    customerAgentIds.add(req.customerId);
                    System.out.println("Depot: Route " + routeId + " - Customer " + customer.id + 
                                     ": " + req.customerName + " at (" + req.x + ", " + req.y + ")");
                    logger.logEvent("Route " + routeId + " customer " + customer.id + 
                                  ": " + req.customerName + " at (" + req.x + ", " + req.y + ")");
                } else {
                    // Fallback: use numeric ID if request not found
                    customerAgentIds.add("customer-" + customer.id);
                    System.out.println("Depot: WARNING - Route " + routeId + " customer " + customer.id + 
                                     " not found in batch (using fallback ID)");
                    logger.log("WARNING: Route " + routeId + " customer " + customer.id + " not found in batch");
                }
            }
            
            // Create route broadcast message (without VEHICLE_NAME - vehicles self-select)
            StringBuilder routeContent = new StringBuilder();
            routeContent.append("ROUTE:").append(routeId).append("|");
            routeContent.append("CUSTOMERS:");
            for (int j = 0; j < route.customers.size(); j++) {
                if (j > 0) routeContent.append(",");
                routeContent.append(route.customers.get(j).id);
            }
            routeContent.append("|CUSTOMER_IDS:");  // Add actual customer agent IDs
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
            
            // Broadcast route to all vehicles (they will self-check and accept if they can handle it)
            ACLMessage routeBroadcast = new ACLMessage(ACLMessage.REQUEST);
            String conversationId = "route-broadcast-" + routeId + "-" + System.currentTimeMillis();
            for (AID vehicleAID : vehicleAIDs) {
                routeBroadcast.addReceiver(vehicleAID);
            }
            routeBroadcast.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            routeBroadcast.setConversationId(conversationId);
            routeBroadcast.setContent("ROUTE_AVAILABLE:" + routeContent.toString());
            
            // Log conversation start for route broadcast
            logger.logConversationStart(conversationId, 
                "Route " + routeId + " broadcast to " + vehicleAIDs.size() + " vehicles (" + 
                route.customers.size() + " customers, " + route.totalDemand + " items)");
            
            logger.logSent(routeBroadcast);
            send(routeBroadcast);
            
            System.out.println("Depot: Broadcasted route " + routeId + " to " + vehicleAIDs.size() + 
                             " vehicles (demand: " + route.totalDemand + " items, " + 
                             "distance: " + String.format("%.2f", route.totalDistance) + ")");
            logger.logEvent("Broadcasted route " + routeId + " to " + vehicleAIDs.size() + 
                          " vehicles (" + route.customers.size() + " customers, " + 
                          route.totalDemand + " items, " + String.format("%.2f", route.totalDistance) + " distance)");
            
            // Initialize route assignment tracking
            routeAssignments.put(routeId, null);
            routeAssignmentTimes.put(routeId, System.currentTimeMillis());
        }
        
        System.out.println("Depot: Completed route broadcast for " + result.routes.size() + " routes");
        logger.logEvent("Completed route broadcast for " + result.routes.size() + " routes. Waiting for vehicle self-evaluation...");
    }
    
    /**
     * Handles route acceptance responses from vehicles
     * Tracks which vehicle accepted which route (first acceptance wins)
     */
    private class RouteAcceptanceHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.AGREE),
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST)
            );
            
            ACLMessage msg = receive(template);
            if (msg != null) {
                logger.logReceived(msg);
                String content = msg.getContent();
                
                if (content != null && content.startsWith("ROUTE_ACCEPTED:")) {
                    // Format: ROUTE_ACCEPTED:ROUTE:routeId|VEHICLE:vehicleName
                    String[] parts = content.substring("ROUTE_ACCEPTED:".length()).split("\\|");
                    String routeId = null;
                    String vehicleName = null;
                    
                    for (String part : parts) {
                        if (part.startsWith("ROUTE:")) {
                            routeId = part.substring("ROUTE:".length());
                        } else if (part.startsWith("VEHICLE:")) {
                            vehicleName = part.substring("VEHICLE:".length());
                        }
                    }
                    
                    if (routeId != null && vehicleName != null) {
                        // Check if route is already assigned
                        String assignedVehicle = routeAssignments.get(routeId);
                        if (assignedVehicle == null) {
                            // First acceptance wins
                            routeAssignments.put(routeId, vehicleName);
                            System.out.println("Depot: Route " + routeId + " accepted by vehicle " + vehicleName);
                            logger.logEvent("Route " + routeId + " accepted by vehicle " + vehicleName + 
                                          " (first acceptance)");
                            
                            // Log conversation end for accepted route
                            logger.logConversationEnd(msg.getConversationId(), 
                                "Route " + routeId + " accepted by vehicle " + vehicleName);
                        } else {
                            // Route already assigned to another vehicle
                            System.out.println("Depot: Route " + routeId + " already assigned to vehicle " + 
                                             assignedVehicle + ". Rejecting acceptance from " + vehicleName);
                            logger.logEvent("Route " + routeId + " already assigned to " + assignedVehicle + 
                                          ". Rejecting " + vehicleName);
                            
                            // Send rejection to this vehicle
                            ACLMessage rejection = msg.createReply();
                            rejection.setPerformative(ACLMessage.REFUSE);
                            rejection.setContent("ROUTE_ALREADY_ASSIGNED:Route " + routeId + " already assigned to " + assignedVehicle);
                            
                            // Log conversation end for rejected route
                            logger.logConversationEnd(msg.getConversationId(), 
                                "Route " + routeId + " rejected - already assigned to " + assignedVehicle);
                            
                            logger.logSent(rejection);
                            send(rejection);
                        }
                    }
                }
            } else {
                block();
            }
        }
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

