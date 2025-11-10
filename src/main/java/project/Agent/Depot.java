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
    
    @Override
    protected void setup() {
        System.out.println("Depot Agent " + getAID().getName() + " is ready.");
        
        // Initialize logger
        logger = new AgentLogger("Depot");
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
            
            System.out.println("\n=== Depot: Processing Request ===");
            System.out.println("Customer: " + customerName + " (" + customerId + ")");
            System.out.println("Item: " + itemName + ", Quantity: " + quantity);
            System.out.println("Location: (" + x + ", " + y + ")");
            
            // Check inventory
            int available = inventory.getOrDefault(itemName, 0);
            
            if (available >= quantity) {
                // Item available - add to batch and respond
                inventory.put(itemName, available - quantity);
                pendingRequests.add(request);
                
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                reply.setConversationId(msg.getConversationId());
                reply.setContent("ITEM_AVAILABLE:Request accepted. Item will be delivered in next batch.");
                logger.logSent(reply);
                send(reply);
                
                System.out.println("Depot: ✓ Item available. Inventory: " + itemName + " = " + inventory.get(itemName));
                System.out.println("Depot: Request added to queue. Queue size: " + pendingRequests.size());
            } else {
                // Item unavailable - respond with refusal
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                reply.setConversationId(msg.getConversationId());
                reply.setContent("ITEM_UNAVAILABLE:Insufficient inventory. Available: " + available + ", Requested: " + quantity);
                logger.logSent(reply);
                send(reply);
                
                System.out.println("Depot: ✗ Item unavailable. Available: " + available + ", Requested: " + quantity);
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
        System.out.println("Batch size: " + pendingRequests.size());
        logger.logEvent("Processing batch with " + pendingRequests.size() + " requests");
        
        // Move requests to current batch
        currentBatch = new ArrayList<>(pendingRequests);
        pendingRequests.clear();
        
        // Query available vehicles
        List<VehicleInfo> availableVehicles = queryAvailableVehicles();
        
        if (availableVehicles.isEmpty()) {
            System.out.println("Depot: No vehicles available. Requests will be queued again.");
            pendingRequests.addAll(currentBatch);
            return;
        }
        
        System.out.println("Depot: Found " + availableVehicles.size() + " available vehicles");
        
        // Build problem for solver
        buildProblemFromRequests(currentBatch, availableVehicles);
        
        // Prepare vehicle capacities and max distances
        int numVehicles = availableVehicles.size();
        int[] vehicleCapacities = new int[numVehicles];
        double[] vehicleMaxDistances = new double[numVehicles];
        
        for (int i = 0; i < numVehicles; i++) {
            vehicleCapacities[i] = availableVehicles.get(i).capacity;
            vehicleMaxDistances[i] = availableVehicles.get(i).maxDistance;
        }
        
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
        
        System.out.println("Depot: Solution found with " + result.routes.size() + " routes");
        System.out.println("Depot: Items delivered: " + result.itemsDelivered + "/" + result.itemsTotal);
        System.out.println("Depot: Total distance: " + String.format("%.2f", result.totalDistance));
        logger.logEvent("VRP solution found: " + result.routes.size() + " routes, " + 
                       result.itemsDelivered + "/" + result.itemsTotal + " items delivered, " +
                       "total distance: " + result.totalDistance);
        
        // Assign routes to vehicles via Contract-Net
        assignRoutesViaContractNet(result, availableVehicles);
    }
    
    /**
     * Queries all vehicle agents discovered via DF for their state
     */
    private List<VehicleInfo> queryAvailableVehicles() {
        List<VehicleInfo> available = new ArrayList<>();
        
        // Find vehicles via DF
        List<AID> vehicleAIDs = findVehiclesViaDF();
        if (vehicleAIDs.isEmpty()) {
            System.out.println("Depot: No vehicles found via DF");
            return available;
        }
        
        // Query each vehicle
        for (AID vehicleAID : vehicleAIDs) {
            String vehicleIdentifier = vehicleAID.getLocalName();
            try {
                // Check if already registered
                VehicleInfo vehicle = registeredVehicles.get(vehicleIdentifier);
                if (vehicle == null) {
                    // Will be created from response
                    vehicle = new VehicleInfo(vehicleIdentifier, 50, 1000.0);
                    registeredVehicles.put(vehicleIdentifier, vehicle);
                }
                
                ACLMessage query = new ACLMessage(ACLMessage.QUERY_REF);
                query.addReceiver(vehicleAID);
                query.setContent("QUERY_STATE");
                query.setProtocol(FIPANames.InteractionProtocol.FIPA_QUERY);
                logger.logSent(query);
                send(query);
                
                // Wait for response
                Thread.sleep(200);
                
                // Check state from response (will be updated by response handler)
                if ("free".equals(vehicle.state)) {
                    available.add(vehicle);
                }
            } catch (Exception e) {
                System.err.println("Depot: Error querying vehicle " + vehicleIdentifier + ": " + e.getMessage());
            }
        }
        
        // Handle responses asynchronously
        addBehaviour(new VehicleStateResponseHandler());
        
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
                
                if (name != null) {
                    VehicleInfo vehicle = registeredVehicles.get(name);
                    if (vehicle == null) {
                        vehicle = new VehicleInfo(name, capacity, maxDistance);
                        registeredVehicles.put(name, vehicle);
                    } else {
                        vehicle.capacity = capacity;
                        vehicle.maxDistance = maxDistance;
                    }
                    vehicle.state = state;
                    System.out.println("Depot: Updated vehicle " + name + " state: " + state + 
                                     ", capacity: " + capacity + ", maxDistance: " + maxDistance);
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
     * Assigns routes to vehicles using FIPA Contract-Net protocol
     */
    private void assignRoutesViaContractNet(SolutionResult result, List<VehicleInfo> availableVehicles) {
        System.out.println("\n=== Depot: Contract-Net Route Assignment ===");
        
        // Create CFP (Call for Proposal) messages for each route
        List<ACLMessage> cfps = new ArrayList<>();
        
        for (int i = 0; i < result.routes.size() && i < availableVehicles.size(); i++) {
            RouteInfo route = result.routes.get(i);
            
            // Update route with proper customer coordinates
            for (int j = 0; j < route.customers.size(); j++) {
                CustomerInfo customer = route.customers.get(j);
                // Find corresponding request by matching customer ID with request index
                int requestIndex = customer.id - 1;  // Customer ID is 1-indexed, requests are 0-indexed
                if (requestIndex >= 0 && requestIndex < currentBatch.size()) {
                    CustomerRequest req = currentBatch.get(requestIndex);
                    customer.x = req.x;
                    customer.y = req.y;
                    customer.name = req.customerName;
                }
            }
            
            // Create CFP for this route
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
            cfp.setConversationId("cn-" + System.currentTimeMillis() + "-" + i);
            
            // Format: ROUTE:routeId|CUSTOMERS:customerId1,customerId2|COORDS:x1,y1;x2,y2|DEMAND:total|DISTANCE:total|MAX_DISTANCE:maxDist
            StringBuilder content = new StringBuilder();
            content.append("ROUTE:").append(i + 1).append("|");
            content.append("CUSTOMERS:");
            for (int j = 0; j < route.customers.size(); j++) {
                if (j > 0) content.append(",");
                content.append(route.customers.get(j).id);
            }
            content.append("|COORDS:");
            for (int j = 0; j < route.customers.size(); j++) {
                if (j > 0) content.append(";");
                CustomerInfo customer = route.customers.get(j);
                content.append(String.format("%.2f", customer.x)).append(",").append(String.format("%.2f", customer.y));
            }
            content.append("|DEMAND:").append(route.totalDemand);
            content.append("|DISTANCE:").append(String.format("%.2f", route.totalDistance));
            
            cfp.setContent(content.toString());
            
            // Send to all available vehicles via DF (they will bid)
            List<AID> vehicleAIDs = findVehiclesViaDF();
            for (AID vehicleAID : vehicleAIDs) {
                String vehicleIdentifier = vehicleAID.getLocalName();
                for (VehicleInfo vehicle : availableVehicles) {
                    if (vehicle.name.equals(vehicleIdentifier)) {
                        cfp.addReceiver(vehicleAID);
                        break;
                    }
                }
            }
            
            cfps.add(cfp);
        }
        
        // Send CFP messages
        for (ACLMessage cfp : cfps) {
            logger.logSent(cfp);
            send(cfp);
        }
        
        // Add behavior to handle proposals
        addBehaviour(new ContractNetProposalHandler(this, result, cfps));
    }
    
    /**
     * Handles Contract-Net proposals from vehicles
     */
    private class ContractNetProposalHandler extends CyclicBehaviour {
        private SolutionResult solutionResult;
        private List<ACLMessage> cfps;
        private Map<String, ACLMessage> proposals;
        private int processedRoutes;
        
        public ContractNetProposalHandler(Agent a, SolutionResult result, List<ACLMessage> cfps) {
            super(a);
            this.solutionResult = result;
            this.cfps = cfps;
            this.proposals = new HashMap<>();
            this.processedRoutes = 0;
        }
        
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET)
            );
            
            ACLMessage propose = receive(template);
            if (propose != null) {
                logger.logReceived(propose);
                String convId = propose.getConversationId();
                proposals.put(convId, propose);
                
                System.out.println("Depot: Received proposal from " + propose.getSender().getName() + 
                                 " for conversation " + convId);
                
                // Find corresponding route
                int routeIndex = -1;
                for (int i = 0; i < cfps.size(); i++) {
                    if (cfps.get(i).getConversationId().equals(convId)) {
                        routeIndex = i;
                        break;
                    }
                }
                
                if (routeIndex >= 0 && routeIndex < solutionResult.routes.size()) {
                    // Accept the proposal
                    ACLMessage accept = propose.createReply();
                    accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                    accept.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
                    accept.setContent("ROUTE_ASSIGNED:" + (routeIndex + 1));
                    logger.logSent(accept);
                    send(accept);
                    
                    // Assign vehicle identifier to route
                    String vehicleIdentifier = propose.getSender().getLocalName();
                    solutionResult.routes.get(routeIndex).vehicleName = vehicleIdentifier;
                    
                    System.out.println("Depot: Assigned route " + (routeIndex + 1) + " to vehicle " + vehicleIdentifier);
                    processedRoutes++;
                    
                    if (processedRoutes >= solutionResult.routes.size()) {
                        System.out.println("Depot: All routes assigned via Contract-Net");
                        myAgent.removeBehaviour(this);
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
