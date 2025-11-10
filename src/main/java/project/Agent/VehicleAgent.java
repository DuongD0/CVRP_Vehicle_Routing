package project.Agent;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.FIPANames;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import project.General.CustomerInfo;
import project.Utils.AgentLogger;

import java.util.List;
import java.util.ArrayList;

/**
 * Vehicle Agent that accepts direct route assignments from depot
 * Moves to customers and notifies them upon arrival
 */
public class VehicleAgent extends Agent {
    private String vehicleName;
    private int capacity;
    private double maxDistance;  // Maximum distance vehicle can travel (Basic Requirement 2)
    private String state; // "free", "absent", "busy"
    
    // Current position (not always at depot)
    private double currentX;
    private double currentY;
    private double depotX;
    private double depotY;
    
    // Current assignment
    private String assignedRouteId;
    private java.util.List<CustomerInfo> currentRoute;
    
    // Movement state
    private int currentCustomerIndex;  // Index of customer currently moving to (-1 means returning to depot)
    private double targetX;            // Target X coordinate
    private double targetY;            // Target Y coordinate
    private boolean isMoving;          // Whether vehicle is currently moving
    private MovementBehaviour currentMovementBehaviour;  // Track current movement behavior instance
    private static final double MOVEMENT_SPEED = 10.0;  // Units per second
    private static final double ARRIVAL_THRESHOLD = 1.0;  // Distance threshold to consider arrived
    
    // Logger for conversations
    private AgentLogger logger;
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 3) {
            this.vehicleName = (String) args[0];
            this.capacity = (Integer) args[1];
            this.maxDistance = (Double) args[2];
        } else {
            this.vehicleName = getLocalName();
            this.capacity = 30; // Default capacity
            this.maxDistance = 1000.0; // Default max distance
        }
        
        // Initialize position at depot
        this.depotX = 0.0;
        this.depotY = 0.0;
        this.currentX = depotX;  // Start at depot
        this.currentY = depotY;
        
        this.state = "free"; // There are 2 states: free and absent, simulating real-world availability
        this.assignedRouteId = null;
        this.currentRoute = null;
        this.currentCustomerIndex = -1;
        this.isMoving = false;
        this.currentMovementBehaviour = null;
        
        System.out.println("Vehicle Agent " + vehicleName + " started:");
        System.out.println("  Capacity: " + capacity + " items");
        System.out.println("  Max Distance: " + maxDistance);
        System.out.println("  Initial position: (" + currentX + ", " + currentY + ")");
        System.out.println("  Depot: (" + depotX + ", " + depotY + ")");
        
        // Initialize logger
        logger = new AgentLogger("Vehicle-" + vehicleName);
        logger.setAgentAID(this);  // Set agent AID for proper logging
        logger.logEvent("Agent started");
        logger.log("Capacity: " + capacity + ", Max Distance: " + maxDistance);
        logger.log("Initial position: (" + currentX + ", " + currentY + ")");
        
        // Register with DF (Yellow Pages)
        registerWithDF();
        logger.logEvent("Registered with DF as 'vehicle-service'");
        
        // Add behavior to handle state queries
        addBehaviour(new QueryHandlerBehaviour());
        
        // Add behavior to handle route availability broadcasts from depot
        addBehaviour(new RouteAssignmentHandler());
        
        // Add behavior to handle depot rejections (e.g., route already assigned to another vehicle)
        addBehaviour(new RouteRejectionHandler());
        
        // Add behavior to return to depot when free
        addBehaviour(new ReturnToDepotBehaviour(this, 5000));  // Check every 5 seconds
    }
    
    /**
     * Handles state queries from Depot
     */
    private class QueryHandlerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.QUERY_REF),
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_QUERY)
            );
            
            ACLMessage msg = receive(template);
            if (msg != null) {
                logger.logReceived(msg);
                
                // Log conversation start for state query
                if (msg.getConversationId() != null) {
                    logger.logConversationStart(msg.getConversationId(), 
                        "State query from " + (msg.getSender() != null ? msg.getSender().getLocalName() : "unknown"));
                }
                
                if (msg.getContent().equals("QUERY_STATE")) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setProtocol(FIPANames.InteractionProtocol.FIPA_QUERY);
                    reply.setContent("STATE:" + state + "|CAPACITY:" + capacity + "|MAX_DISTANCE:" + maxDistance + 
                                   "|NAME:" + vehicleName + "|X:" + currentX + "|Y:" + currentY);
                    
                    // Log conversation end
                    logger.logConversationEnd(msg.getConversationId(), 
                        "State query responded - State: " + state + ", Capacity: " + capacity + 
                        ", MaxDistance: " + maxDistance);
                    
                    logger.logSent(reply);
                    send(reply);
                    System.out.println("Vehicle " + vehicleName + " reported state: " + state + 
                                     " at (" + currentX + ", " + currentY + "), " +
                                     "capacity: " + capacity + ", maxDistance: " + maxDistance);
                }
            } else {
                block();
            }
        }
    }
    
    /**
     * Route Assignment Handler
     * Handles route availability broadcasts from depot
     * Vehicles self-evaluate routes and accept if they can handle them
     */
    private class RouteAssignmentHandler extends CyclicBehaviour {
        @Override
        public void action() {
            // Check for route availability messages from depot
            MessageTemplate routeAvailableTemplate = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                MessageTemplate.MatchContent("ROUTE_AVAILABLE:")
            );
            
            ACLMessage routeAvailable = receive(routeAvailableTemplate);
            if (routeAvailable != null) {
                logger.logReceived(routeAvailable);
                
                // Log conversation start for route availability
                if (routeAvailable.getConversationId() != null) {
                    // Extract route ID for logging
                    String routeId = "unknown";
                    try {
                        String content = routeAvailable.getContent();
                        if (content != null && content.startsWith("ROUTE_AVAILABLE:")) {
                            String routeData = content.substring("ROUTE_AVAILABLE:".length());
                            String[] parts = routeData.split("\\|");
                            for (String part : parts) {
                                if (part.startsWith("ROUTE:")) {
                                    routeId = part.substring("ROUTE:".length());
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore parsing errors
                    }
                    logger.logConversationStart(routeAvailable.getConversationId(), 
                        "Route availability broadcast for route " + routeId + " from " + 
                        (routeAvailable.getSender() != null ? routeAvailable.getSender().getLocalName() : "unknown"));
                }
                
                handleRouteAvailability(routeAvailable);
                return;
            }
            
            block();
        }
        
        /**
         * Handles route availability broadcast from depot
         * Vehicle self-evaluates if it can handle the route
         */
        private void handleRouteAvailability(ACLMessage routeAvailable) {
            String content = routeAvailable.getContent();
            // Format: ROUTE_AVAILABLE:ROUTE:routeId|CUSTOMERS:...|CUSTOMER_IDS:...|COORDS:...|DEMAND:...|DISTANCE:...
            
            System.out.println("\n=== Vehicle " + vehicleName + ": Received Route Availability ===");
            logger.logEvent("Received route availability broadcast from depot");
            
            // Parse route data
            String routeData = content.substring("ROUTE_AVAILABLE:".length());
            String[] parts = routeData.split("\\|");
            
            String routeId = null;
            int routeDemand = 0;
            double routeDistance = 0.0;
            List<CustomerInfo> routeCustomers = new ArrayList<>();
            
            try {
                for (String part : parts) {
                    if (part.startsWith("ROUTE:")) {
                        routeId = part.substring("ROUTE:".length());
                    } else if (part.startsWith("DEMAND:")) {
                        routeDemand = Integer.parseInt(part.substring("DEMAND:".length()));
                    } else if (part.startsWith("DISTANCE:")) {
                        routeDistance = Double.parseDouble(part.substring("DISTANCE:".length()));
                    } else if (part.startsWith("COORDS:")) {
                        // Parse customer coordinates
                        String coordsString = part.substring("COORDS:".length());
                        if (!coordsString.isEmpty()) {
                            String[] coordPairs = coordsString.split(";");
                            for (int i = 0; i < coordPairs.length; i++) {
                                String[] coords = coordPairs[i].split(",");
                                if (coords.length == 2) {
                                    double x = Double.parseDouble(coords[0]);
                                    double y = Double.parseDouble(coords[1]);
                                    CustomerInfo customer = new CustomerInfo(i + 1, x, y, 0);
                                    routeCustomers.add(customer);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Vehicle " + vehicleName + ": Error parsing route availability: " + e.getMessage());
                logger.log("ERROR: Failed to parse route availability: " + e.getMessage());
                sendRejection(routeAvailable, "Invalid route format");
                return;
            }
            
            if (routeId == null) {
                System.err.println("Vehicle " + vehicleName + ": Invalid route format - missing route ID");
                logger.log("ERROR: Invalid route format - missing route ID");
                sendRejection(routeAvailable, "Invalid route format");
                return;
            }
            
            System.out.println("Vehicle " + vehicleName + ": Evaluating route " + routeId + 
                             " (demand: " + routeDemand + ", distance: " + String.format("%.2f", routeDistance) + ")");
            logger.logEvent("Evaluating route " + routeId + ": demand=" + routeDemand + 
                          ", distance=" + String.format("%.2f", routeDistance));
            
            // Self-check 1: Vehicle state must be "free"
            if (!"free".equals(state)) {
                System.out.println("Vehicle " + vehicleName + ": Cannot accept route " + routeId + 
                                 " - current state: " + state + " (must be free)");
                logger.logEvent("REJECTED route " + routeId + ": Vehicle state is " + state + " (must be free)");
                sendRejection(routeAvailable, "Vehicle not available. Current state: " + state);
                return;
            }
            
            // Self-check 2: Route demand must fit vehicle capacity
            if (routeDemand > capacity) {
                System.out.println("Vehicle " + vehicleName + ": Cannot accept route " + routeId + 
                                 " - demand " + routeDemand + " exceeds capacity " + capacity);
                logger.logEvent("REJECTED route " + routeId + ": Demand " + routeDemand + 
                              " exceeds capacity " + capacity);
                sendRejection(routeAvailable, "Route demand " + routeDemand + " exceeds vehicle capacity " + capacity);
                return;
            }
            
            // Self-check 3: Calculate total distance (from current position to first customer + route distance + return to depot)
            double totalDistance = routeDistance;
            if (!routeCustomers.isEmpty()) {
                // Distance from current position to first customer
                CustomerInfo firstCustomer = routeCustomers.get(0);
                double distanceToFirst = Math.hypot(currentX - firstCustomer.x, currentY - firstCustomer.y);
                totalDistance += distanceToFirst;
                
                // Distance from last customer back to depot
                CustomerInfo lastCustomer = routeCustomers.get(routeCustomers.size() - 1);
                double distanceToDepot = Math.hypot(lastCustomer.x - depotX, lastCustomer.y - depotY);
                totalDistance += distanceToDepot;
            } else {
                // If no customers, just distance from current position to depot
                totalDistance = Math.hypot(currentX - depotX, currentY - depotY);
            }
            
            // Self-check 4: Total distance must not exceed vehicle max distance
            if (totalDistance > maxDistance) {
                System.out.println("Vehicle " + vehicleName + ": Cannot accept route " + routeId + 
                                 " - total distance " + String.format("%.2f", totalDistance) + 
                                 " exceeds max distance " + maxDistance);
                logger.logEvent("REJECTED route " + routeId + ": Total distance " + 
                              String.format("%.2f", totalDistance) + " exceeds max distance " + maxDistance);
                sendRejection(routeAvailable, "Route total distance " + String.format("%.2f", totalDistance) + 
                            " exceeds vehicle max distance " + maxDistance);
                return;
            }
            
            // All checks passed - accept the route
            System.out.println("Vehicle " + vehicleName + ": ✓ All self-checks passed for route " + routeId);
            System.out.println("Vehicle " + vehicleName + ": Capacity: " + capacity + " >= Demand: " + routeDemand);
            System.out.println("Vehicle " + vehicleName + ": Max Distance: " + maxDistance + " >= Total Distance: " + 
                             String.format("%.2f", totalDistance));
            logger.logEvent("ACCEPTED route " + routeId + ": Capacity=" + capacity + " (demand=" + routeDemand + 
                          "), MaxDistance=" + maxDistance + " (total=" + String.format("%.2f", totalDistance) + ")");
            
            // Send acceptance
            ACLMessage acceptance = routeAvailable.createReply();
            acceptance.setPerformative(ACLMessage.AGREE);
            acceptance.setContent("ROUTE_ACCEPTED:ROUTE:" + routeId + "|VEHICLE:" + vehicleName);
            
            // Log conversation response (conversation end will be logged by depot when it receives this)
            logger.logSent(acceptance);
            send(acceptance);
            
            System.out.println("Vehicle " + vehicleName + ": Sent route acceptance to depot for route " + routeId);
            logger.logEvent("Sent route acceptance to depot for route " + routeId);
            
            // Start the route immediately (depot will reject if another vehicle already accepted)
            parseRouteAndStartMovement(routeId, routeData);
        }
        
        /**
         * Sends rejection message to depot
         */
        private void sendRejection(ACLMessage routeAvailable, String reason) {
            // Extract route ID from message content for logging
            String routeId = "unknown";
            try {
                String content = routeAvailable.getContent();
                if (content != null && content.startsWith("ROUTE_AVAILABLE:")) {
                    String routeData = content.substring("ROUTE_AVAILABLE:".length());
                    String[] parts = routeData.split("\\|");
                    for (String part : parts) {
                        if (part.startsWith("ROUTE:")) {
                            routeId = part.substring("ROUTE:".length());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors, use "unknown" route ID
            }
            
            ACLMessage rejection = routeAvailable.createReply();
            rejection.setPerformative(ACLMessage.REFUSE);
            rejection.setContent("ROUTE_REJECTED:" + reason);
            
            // Log conversation end for rejected route
            logger.logConversationEnd(routeAvailable.getConversationId(), 
                "Route " + routeId + " rejected: " + reason);
            
            logger.logSent(rejection);
            send(rejection);
            System.out.println("Vehicle " + vehicleName + ": Sent route rejection to depot: " + reason);
            logger.logEvent("Sent route rejection to depot: " + reason);
        }
    }
    
    /**
     * Handles route rejection from depot (e.g., route already assigned to another vehicle)
     * Cancels the route if it was already started
     */
    private class RouteRejectionHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.REFUSE),
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST)
            );
            
            ACLMessage msg = receive(template);
            if (msg != null) {
                logger.logReceived(msg);
                String content = msg.getContent();
                
                // Log conversation end for route rejection
                if (msg.getConversationId() != null) {
                    logger.logConversationEnd(msg.getConversationId(), 
                        "Route rejected by depot: " + (content != null ? content : "unknown reason"));
                }
                
                if (content != null && content.startsWith("ROUTE_ALREADY_ASSIGNED:")) {
                    // Extract route ID from rejection message
                    String routeId = assignedRouteId;  // Use current assigned route ID
                    
                    System.out.println("\n=== Vehicle " + vehicleName + ": Received Route Rejection from Depot ===");
                    System.out.println("Vehicle " + vehicleName + ": " + content);
                    logger.logEvent("Received route rejection from depot: " + content);
                    
                    // Cancel the route if it was already started
                    if (routeId != null && routeId.equals(assignedRouteId)) {
                        System.out.println("Vehicle " + vehicleName + ": Cancelling route " + routeId + 
                                         " - already assigned to another vehicle");
                        logger.logEvent("Cancelling route " + routeId + " - already assigned to another vehicle");
                        
                        // Stop movement behavior if it's running
                        if (currentMovementBehaviour != null) {
                            System.out.println("Vehicle " + vehicleName + ": Stopping MovementBehaviour due to route cancellation");
                            logger.logEvent("Stopping MovementBehaviour due to route cancellation");
                            removeBehaviour(currentMovementBehaviour);
                            currentMovementBehaviour = null;
                        }
                        
                        // Reset vehicle state
                        state = "free";
                        assignedRouteId = null;
                        currentRoute = null;
                        currentCustomerIndex = -1;
                        isMoving = false;
                        
                        System.out.println("Vehicle " + vehicleName + ": State reset to 'free'");
                        logger.logEvent("State reset to 'free' after route cancellation");
                    }
                }
            } else {
                block();
            }
        }
    }
    
    /**
     * Parses route data and starts movement behavior
     * Called when vehicle accepts a route assignment
     */
    private void parseRouteAndStartMovement(String routeId, String routeData) {
        System.out.println("Vehicle " + vehicleName + ": Parsing route data for route " + routeId);
        logger.logEvent("Parsing route data for route " + routeId);
        
        // Update state
        assignedRouteId = routeId;
        state = "absent";
        
        System.out.println("Vehicle " + vehicleName + ": State changed to 'absent' (delivering)");
        logger.logEvent("State changed to 'absent' - starting delivery for route " + routeId);
        
        // Parse route data to extract customer information
        // Format: ROUTE:routeId|CUSTOMERS:numericId1,numericId2|CUSTOMER_IDS:customer-1,customer-2|COORDS:x1,y1;x2,y2|DEMAND:total|DISTANCE:total
        // Note: Customer IDs in route are numeric (1, 2, 3) from the solver
        // CUSTOMER_IDS contains the actual customer agent IDs (customer-1, customer-2, etc.)
        String[] parts = routeData.split("\\|");
        
        List<String> customerNumericIds = new ArrayList<>();
        List<String> customerAgentIds = new ArrayList<>();
        List<CustomerInfo> customers = new ArrayList<>();
        
        try {
            for (String part : parts) {
                if (part.startsWith("CUSTOMERS:")) {
                    String customerIdsStr = part.substring("CUSTOMERS:".length());
                    if (!customerIdsStr.isEmpty()) {
                        String[] ids = customerIdsStr.split(",");
                        for (String id : ids) {
                            customerNumericIds.add(id.trim());
                        }
                    }
                } else if (part.startsWith("CUSTOMER_IDS:")) {
                    // New field: actual customer agent IDs
                    String customerAgentIdsStr = part.substring("CUSTOMER_IDS:".length());
                    if (!customerAgentIdsStr.isEmpty()) {
                        String[] ids = customerAgentIdsStr.split(",");
                        for (String id : ids) {
                            customerAgentIds.add(id.trim());
                        }
                    }
                } else if (part.startsWith("COORDS:")) {
                    String coordsString = part.substring("COORDS:".length());
                    if (!coordsString.isEmpty()) {
                        String[] coordPairs = coordsString.split(";");
                        int numCustomers = Math.max(customerNumericIds.size(), customerAgentIds.size());
                        for (int i = 0; i < coordPairs.length && i < numCustomers; i++) {
                            String[] coords = coordPairs[i].split(",");
                            if (coords.length == 2) {
                                double x = Double.parseDouble(coords[0]);
                                double y = Double.parseDouble(coords[1]);
                                
                                // Get numeric ID and agent ID
                                String numericId = i < customerNumericIds.size() ? customerNumericIds.get(i) : String.valueOf(i + 1);
                                String agentId = i < customerAgentIds.size() ? customerAgentIds.get(i) : "customer-" + numericId;
                                
                                // Create CustomerInfo - numeric ID from solver (1, 2, 3, ...)
                                int customerIdNum = Integer.parseInt(numericId);
                                CustomerInfo customer = new CustomerInfo(customerIdNum, x, y, 0);
                                // Store the actual customer agent ID (e.g., "customer-1", "customer-2")
                                customer.name = agentId;
                                customers.add(customer);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Vehicle " + vehicleName + ": Error parsing route data: " + e.getMessage());
            e.printStackTrace();
            logger.log("ERROR: Failed to parse route data: " + e.getMessage());
            state = "free"; // Reset state if parsing fails
            return;
        }
        
        if (customers.isEmpty()) {
            System.err.println("Vehicle " + vehicleName + ": ERROR - No customers found in route data");
            logger.log("ERROR: No customers found in route data for route " + routeId);
            state = "free"; // Reset state if route parsing fails
            return;
        }
        
        // Store route
        currentRoute = customers;
        currentCustomerIndex = 0;
        isMoving = true;
        
        System.out.println("Vehicle " + vehicleName + ": Route " + routeId + " parsed successfully");
        System.out.println("Vehicle " + vehicleName + ": Route contains " + currentRoute.size() + " customers");
        logger.logEvent("Route " + routeId + " parsed: " + currentRoute.size() + " customers");
        
        // Log route details
        for (int i = 0; i < currentRoute.size(); i++) {
            CustomerInfo customer = currentRoute.get(i);
            System.out.println("Vehicle " + vehicleName + ": Route customer " + (i + 1) + ": " + 
                             customer.name + " (ID: " + customer.id + ") at (" + 
                             customer.x + ", " + customer.y + ")");
            logger.logEvent("Route customer " + (i + 1) + ": " + customer.name + 
                          " (ID: " + customer.id + ") at (" + customer.x + ", " + customer.y + ")");
        }
        
        // Set target to first customer
        if (currentRoute.size() > 0) {
            CustomerInfo firstCustomer = currentRoute.get(0);
            targetX = firstCustomer.x;
            targetY = firstCustomer.y;
            double distanceToFirst = Math.hypot(currentX - targetX, currentY - targetY);
            System.out.println("Vehicle " + vehicleName + ": Starting route with " + currentRoute.size() + 
                             " customers. Moving to customer " + firstCustomer.name + " at (" + 
                             targetX + ", " + targetY + ")");
            System.out.println("Vehicle " + vehicleName + ": Current position: (" + currentX + ", " + currentY + 
                             "), Distance to first customer: " + String.format("%.2f", distanceToFirst));
            logger.logEvent("Starting route: " + currentRoute.size() + " customers. Moving to " + 
                          firstCustomer.name + " at (" + targetX + ", " + targetY + 
                          "). Distance: " + String.format("%.2f", distanceToFirst));
        }
        
        // Stop any existing movement behavior
        if (currentMovementBehaviour != null) {
            System.out.println("Vehicle " + vehicleName + ": Stopping existing MovementBehaviour");
            logger.logEvent("Stopping existing MovementBehaviour");
            removeBehaviour(currentMovementBehaviour);
            currentMovementBehaviour = null;
        }
        
        // Start movement behavior (updates position every second)
        currentMovementBehaviour = new MovementBehaviour(this, 1000);  // Update every 1 second
        addBehaviour(currentMovementBehaviour);
        System.out.println("Vehicle " + vehicleName + ": MovementBehaviour started for route " + routeId);
        logger.logEvent("MovementBehaviour started for route " + routeId);
    }
    
    /**
     * Movement behavior that updates vehicle position every second
     * Moves vehicle towards customers and notifies them when arrived
     */
    private class MovementBehaviour extends TickerBehaviour {
        public MovementBehaviour(Agent a, long period) {
            super(a, period);
            System.out.println("Vehicle " + vehicleName + ": MovementBehaviour constructor called with period=" + period + "ms");
            logger.logEvent("MovementBehaviour constructor called with period=" + period + "ms");
        }
        
        @Override
        protected void onTick() {
            // Log every 10 ticks (every 10 seconds) to reduce verbosity
            if (getTickCount() % 10 == 0 || getTickCount() == 1) {
                System.out.println("Vehicle " + vehicleName + ": MovementBehaviour tick #" + getTickCount() + 
                                 " - isMoving=" + isMoving + ", customerIndex=" + currentCustomerIndex + 
                                 ", routeSize=" + (currentRoute != null ? currentRoute.size() : 0));
            }
            
            if (!isMoving) {
                if (getTickCount() == 1) {
                    System.out.println("Vehicle " + vehicleName + ": MovementBehaviour - isMoving=false, waiting for route");
                }
                return;
            }
            
            if (currentRoute == null) {
                System.out.println("Vehicle " + vehicleName + ": ERROR - MovementBehaviour - currentRoute is null!");
                logger.log("ERROR: MovementBehaviour - currentRoute is null");
                return;
            }
            
            // Check if we're moving to a customer or returning to depot
            if (currentCustomerIndex >= 0 && currentCustomerIndex < currentRoute.size()) {
                // Moving to a customer
                moveTowardsCustomer();
            } else if (currentCustomerIndex == -2) {
                // Returning to depot after completing all deliveries
                returnToDepot();
            } else if (currentCustomerIndex == -1 && currentRoute.isEmpty()) {
                // All customers visited, return to depot (edge case)
                returnToDepot();
            } else {
                System.out.println("Vehicle " + vehicleName + ": WARNING - MovementBehaviour - Unknown state: currentCustomerIndex=" + 
                                 currentCustomerIndex + ", routeSize=" + currentRoute.size());
                logger.log("WARNING: MovementBehaviour - Unknown state: currentCustomerIndex=" + currentCustomerIndex);
            }
        }
        
        /**
         * Moves vehicle towards current target customer
         */
        private void moveTowardsCustomer() {
            if (currentRoute == null || currentCustomerIndex < 0 || currentCustomerIndex >= currentRoute.size()) {
                System.out.println("Vehicle " + vehicleName + ": moveTowardsCustomer() - Invalid state: route=" + 
                                 (currentRoute != null ? "not null" : "null") + ", index=" + currentCustomerIndex);
                return;
            }
            
            CustomerInfo customer = currentRoute.get(currentCustomerIndex);
            targetX = customer.x;
            targetY = customer.y;
            
            // Calculate distance to target
            double dx = targetX - currentX;
            double dy = targetY - currentY;
            double distance = Math.hypot(dx, dy);
            
            // Log position every 5 ticks or on first tick
            if (getTickCount() % 5 == 0 || getTickCount() == 1) {
                System.out.println("Vehicle " + vehicleName + ": Moving to customer " + customer.name + 
                                 " at (" + String.format("%.1f", targetX) + ", " + String.format("%.1f", targetY) + 
                                 "), Current: (" + String.format("%.1f", currentX) + ", " + String.format("%.1f", currentY) + 
                                 "), Distance: " + String.format("%.2f", distance));
            }
            
            if (distance <= ARRIVAL_THRESHOLD) {
                // Arrived at customer
                currentX = targetX;
                currentY = targetY;
                System.out.println("\n=== Vehicle " + vehicleName + ": ARRIVED at Customer ===");
                System.out.println("Vehicle " + vehicleName + ": Customer: " + customer.name + 
                                 " (ID: " + customer.id + ")");
                System.out.println("Vehicle " + vehicleName + ": Arrival position: (" + currentX + ", " + currentY + ")");
                System.out.println("Vehicle " + vehicleName + ": Customer " + (currentCustomerIndex + 1) + 
                                 " of " + currentRoute.size() + " on route");
                logger.logEvent("ARRIVED at customer " + customer.name + " (ID: " + customer.id + 
                              ") at (" + currentX + ", " + currentY + ") - Customer " + 
                              (currentCustomerIndex + 1) + " of " + currentRoute.size());
                
                // Notify customer
                notifyCustomerArrival(customer);
                
                // Move to next customer
                currentCustomerIndex++;
                if (currentCustomerIndex >= currentRoute.size()) {
                    // All customers visited, return to depot
                    System.out.println("\n=== Vehicle " + vehicleName + ": All Customers Visited ===");
                    System.out.println("Vehicle " + vehicleName + ": All " + currentRoute.size() + 
                                     " customers delivered. Returning to depot.");
                    logger.logEvent("All " + currentRoute.size() + " customers visited. Returning to depot.");
                    currentCustomerIndex = -2;  // Special value to indicate returning to depot
                    targetX = depotX;
                    targetY = depotY;
                    double distanceToDepot = Math.hypot(currentX - depotX, currentY - depotY);
                    System.out.println("Vehicle " + vehicleName + ": Distance to depot: " + 
                                     String.format("%.2f", distanceToDepot));
                    logger.logEvent("Returning to depot. Distance: " + String.format("%.2f", distanceToDepot));
                } else {
                    // Move to next customer
                    CustomerInfo nextCustomer = currentRoute.get(currentCustomerIndex);
                    targetX = nextCustomer.x;
                    targetY = nextCustomer.y;
                    double distanceToNext = Math.hypot(currentX - targetX, currentY - targetY);
                    System.out.println("Vehicle " + vehicleName + ": Moving to next customer " + nextCustomer.name + 
                                     " (ID: " + nextCustomer.id + ") at (" + targetX + ", " + targetY + ")");
                    System.out.println("Vehicle " + vehicleName + ": Distance to next customer: " + 
                                     String.format("%.2f", distanceToNext));
                    logger.logEvent("Moving to customer " + nextCustomer.name + " (ID: " + nextCustomer.id + 
                                  ") at (" + targetX + ", " + targetY + "). Distance: " + 
                                  String.format("%.2f", distanceToNext));
                }
            } else {
                // Move towards target (10 units per second = 10 units per tick since tick is 1 second)
                double moveDistance = Math.min(MOVEMENT_SPEED, distance);
                double ratio = moveDistance / distance;
                
                double oldX = currentX;
                double oldY = currentY;
                currentX += dx * ratio;
                currentY += dy * ratio;
                
                double actualMovement = Math.hypot(currentX - oldX, currentY - oldY);
                
                // Log movement every 5 ticks or on significant movement
                if (getTickCount() % 5 == 0 || actualMovement > 5.0) {
                    System.out.println("Vehicle " + vehicleName + ": Moved " + String.format("%.1f", actualMovement) + 
                                     " units. Position: (" + String.format("%.1f", currentX) + ", " + 
                                     String.format("%.1f", currentY) + "), Remaining: " + String.format("%.1f", distance - moveDistance));
                    logger.logEvent("Moving to customer " + customer.name + ": position=(" + 
                                  String.format("%.2f", currentX) + ", " + String.format("%.2f", currentY) + 
                                  "), remaining=" + String.format("%.2f", distance - moveDistance));
                }
            }
        }
        
        /**
         * Moves vehicle back to depot
         */
        private void returnToDepot() {
            targetX = depotX;
            targetY = depotY;
            
            // Calculate distance to depot
            double dx = targetX - currentX;
            double dy = targetY - currentY;
            double distance = Math.hypot(dx, dy);
            
            if (distance <= ARRIVAL_THRESHOLD) {
                // Arrived at depot
                currentX = depotX;
                currentY = depotY;
                isMoving = false;
                state = "free";
                String completedRouteId = assignedRouteId;
                assignedRouteId = null;
                currentRoute = null;
                currentCustomerIndex = -1;
                
                System.out.println("\n=== Vehicle " + vehicleName + ": RETURNED TO DEPOT ===");
                System.out.println("Vehicle " + vehicleName + ": Arrived at depot at (" + currentX + ", " + currentY + ")");
                System.out.println("Vehicle " + vehicleName + ": Route " + completedRouteId + " completed");
                System.out.println("Vehicle " + vehicleName + ": State changed to 'free' - ready for next route");
                logger.logEvent("RETURNED to depot at (" + currentX + ", " + currentY + 
                              "). Route " + completedRouteId + " completed. State: free");
                
                // Stop movement behavior and remove it
                stop();
                if (currentMovementBehaviour != null) {
                    removeBehaviour(currentMovementBehaviour);
                    currentMovementBehaviour = null;
                }
                System.out.println("Vehicle " + vehicleName + ": MovementBehaviour stopped and removed");
                logger.logEvent("MovementBehaviour stopped and removed. Vehicle ready for next route assignment");
            } else {
                // Move towards depot
                double moveDistance = Math.min(MOVEMENT_SPEED, distance);
                double ratio = moveDistance / distance;
                
                double oldX = currentX;
                double oldY = currentY;
                currentX += dx * ratio;
                currentY += dy * ratio;
                
                // Log position update periodically (every 5 seconds) or on significant movement
                if (getTickCount() % 5 == 0 || Math.hypot(currentX - oldX, currentY - oldY) > 5.0) {
                    System.out.println("Vehicle " + vehicleName + ": Returning to depot. Position: (" + 
                                     String.format("%.2f", currentX) + ", " + String.format("%.2f", currentY) + 
                                     "), Distance remaining: " + String.format("%.2f", distance - moveDistance) + 
                                     ", Speed: " + String.format("%.2f", moveDistance) + " units/sec");
                    logger.logEvent("Returning to depot: position=(" + 
                                  String.format("%.2f", currentX) + ", " + String.format("%.2f", currentY) + 
                                  "), remaining=" + String.format("%.2f", distance - moveDistance));
                }
            }
        }
        
        /**
         * Notifies a customer that the vehicle has arrived
         */
        private void notifyCustomerArrival(CustomerInfo customer) {
            try {
                System.out.println("Vehicle " + vehicleName + ": Attempting to notify customer " + customer.name + 
                                 " (ID: " + customer.id + ")");
                logger.logEvent("Attempting to notify customer " + customer.name + " (ID: " + customer.id + ")");
                
                // Find customer via DF
                DFAgentDescription dfd = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("customer-service");
                dfd.addServices(sd);
                
                DFAgentDescription[] results = DFService.search(getAgent(), dfd);
                
                System.out.println("Vehicle " + vehicleName + ": Found " + results.length + " customers via DF");
                logger.logEvent("Found " + results.length + " customers via DF");
                
                // Find matching customer
                // customer.name now contains the actual customer agent ID (e.g., "customer-1", "customer-2")
                // Customer agents are registered with local name matching this ID
                AID customerAID = null;
                
                // Print all available customers for debugging
                System.out.println("Vehicle " + vehicleName + ": Available customers via DF:");
                for (DFAgentDescription result : results) {
                    String localName = result.getName().getLocalName();
                    System.out.println("  - " + localName + " (looking for: " + customer.name + ")");
                    logger.log("  Available customer: " + localName + " (looking for: " + customer.name + ")");
                }
                
                for (DFAgentDescription result : results) {
                    String localName = result.getName().getLocalName();
                    
                    // Try exact match with customer.name (which contains the agent ID)
                    if (localName.equals(customer.name)) {
                        customerAID = result.getName();
                        System.out.println("Vehicle " + vehicleName + ": Found exact match: " + localName);
                        logger.logEvent("Found exact match for customer: " + localName);
                        break;
                    }
                    
                    // Fallback 1: Try matching with customer ID (numeric)
                    // Customer name might be like "customer-1", and local name might be "customer-1" or just "1"
                    String customerIdStr = String.valueOf(customer.id);
                    if (localName.equals("customer-" + customerIdStr) || localName.equals(customerIdStr)) {
                        customerAID = result.getName();
                        System.out.println("Vehicle " + vehicleName + ": Found match by customer ID: " + localName + " (ID: " + customer.id + ")");
                        logger.logEvent("Found match by customer ID: " + localName);
                        break;
                    }
                    
                    // Fallback 2: Try partial match
                    if (localName.contains(customer.name) || customer.name.contains(localName)) {
                        customerAID = result.getName();
                        System.out.println("Vehicle " + vehicleName + ": Found partial match: " + localName + " matches " + customer.name);
                        logger.logEvent("Found partial match for customer: " + localName + " matches " + customer.name);
                        break;
                    }
                    
                    // Fallback 3: Try matching customer ID embedded in name
                    if (localName.contains(customerIdStr) && (localName.contains("customer") || localName.contains("Customer"))) {
                        customerAID = result.getName();
                        System.out.println("Vehicle " + vehicleName + ": Found match by embedded customer ID: " + localName);
                        logger.logEvent("Found match by embedded customer ID: " + localName);
                        break;
                    }
                }
                
                if (customerAID != null) {
                    ACLMessage deliveryMsg = new ACLMessage(ACLMessage.INFORM);
                    deliveryMsg.addReceiver(customerAID);
                    deliveryMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                    String deliveryConversationId = "delivery-" + System.currentTimeMillis();
                    deliveryMsg.setConversationId(deliveryConversationId);
                    deliveryMsg.setContent("DELIVERY_COMPLETE:Your order has been delivered by vehicle " + vehicleName + 
                                         ". Package arrived at (" + String.format("%.2f", currentX) + ", " + 
                                         String.format("%.2f", currentY) + ")");
                    
                    // Log delivery conversation start
                    logger.logConversationStart(deliveryConversationId, 
                        "Delivery notification to customer " + customer.name + " (ID: " + customer.id + ")");
                    
                    logger.logSent(deliveryMsg);
                    send(deliveryMsg);
                    System.out.println("Vehicle " + vehicleName + ": ✓✓✓ NOTIFIED customer " + customer.name + 
                                     " (ID: " + customer.id + ") about delivery completion");
                    logger.logEvent("NOTIFIED customer " + customer.name + " (ID: " + customer.id + ") about delivery");
                } else {
                    System.out.println("Vehicle " + vehicleName + ": ✗✗✗ ERROR - Could not find customer " + customer.name + 
                                     " via DF for notification");
                    logger.log("ERROR: Could not find customer " + customer.name + " via DF for notification");
                    System.out.println("Vehicle " + vehicleName + ": Customer name from route: '" + customer.name + "'");
                    System.out.println("Vehicle " + vehicleName + ": Customer ID from route: " + customer.id);
                }
            } catch (FIPAException fe) {
                System.err.println("Vehicle " + vehicleName + ": Error searching DF for customer: " + fe.getMessage());
                logger.log("ERROR: Failed to search DF for customer: " + fe.getMessage());
                fe.printStackTrace();
            }
        }
    }
    
    /**
     * Simulates route completion and returns vehicle to free state
     * Also notifies customers that their goods have arrived
     * DEPRECATED: Replaced by MovementBehaviour
     */
    private class RouteCompletionBehaviour extends WakerBehaviour {
        private List<String> customerIds;
        
        public RouteCompletionBehaviour(jade.core.Agent a, long timeout, List<String> customerIds) {
            super(a, timeout);
            this.customerIds = customerIds != null ? customerIds : new ArrayList<>();
        }
        
        @Override
        protected void onWake() {
            state = "free";  // Vehicle is now free again
            assignedRouteId = null;
            currentRoute = null;
            
            // Return exactly to depot
            currentX = depotX;
            currentY = depotY;
            
            System.out.println("Vehicle " + vehicleName + ": Route completed. Returning to depot.");
            System.out.println("Vehicle " + vehicleName + ": Position: (" + currentX + ", " + currentY + ")");
            logger.logEvent("Route completed. Returning to depot. Position: (" + currentX + ", " + currentY + ")");
            
            // Notify customers that their goods have arrived
            notifyCustomersDeliveryComplete(customerIds);
        }
        
        /**
         * Notifies customers that their delivery has been completed
         */
        private void notifyCustomersDeliveryComplete(List<String> customerIds) {
            if (customerIds == null || customerIds.isEmpty()) {
                return;
            }
            
            // Find customers via DF
            try {
                DFAgentDescription dfd = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("customer-service");
                dfd.addServices(sd);
                
                DFAgentDescription[] results = DFService.search(getAgent(), dfd);
                
                // Create a map of customer IDs to AIDs for quick lookup
                java.util.Map<String, AID> customerMap = new java.util.HashMap<>();
                for (DFAgentDescription result : results) {
                    String localName = result.getName().getLocalName();
                    // Match customer ID from local name (format: customer-{id})
                    for (String customerId : customerIds) {
                        if (localName.contains(customerId) || localName.equals("customer-" + customerId)) {
                            customerMap.put(customerId, result.getName());
                            break;
                        }
                    }
                }
                
                // Send delivery notifications to all customers on this route
                for (String customerId : customerIds) {
                    AID customerAID = customerMap.get(customerId);
                    if (customerAID != null) {
                        ACLMessage deliveryMsg = new ACLMessage(ACLMessage.INFORM);
                        deliveryMsg.addReceiver(customerAID);
                        deliveryMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                        deliveryMsg.setConversationId("delivery-" + System.currentTimeMillis());
                        deliveryMsg.setContent("DELIVERY_COMPLETE:Your order has been delivered by vehicle " + vehicleName);
                        
                        logger.logSent(deliveryMsg);
                        send(deliveryMsg);
                        System.out.println("Vehicle " + vehicleName + ": Notified customer " + customerId + " about delivery completion");
                    } else {
                        System.out.println("Vehicle " + vehicleName + ": Could not find customer " + customerId + " via DF for notification");
                    }
                }
            } catch (FIPAException fe) {
                System.err.println("Vehicle " + vehicleName + ": Error searching DF for customers: " + fe.getMessage());
                logger.log("ERROR: Failed to search DF for customers: " + fe.getMessage());
            }
        }
    }
    
    /**
     * Behavior that returns vehicle to depot when free and not at depot
     * Note: This is a safety mechanism. Real movement is handled by MovementBehaviour.
     * This only handles edge cases where vehicle is free but not at depot.
     */
    private class ReturnToDepotBehaviour extends CyclicBehaviour {
        private long checkInterval;
        
        public ReturnToDepotBehaviour(Agent a, long checkInterval) {
            super(a);
            this.checkInterval = checkInterval;
        }
        
        @Override
        public void action() {
            // Only return to depot if free, not moving, and not already at depot
            // MovementBehaviour handles the actual movement when vehicle is on a route
            if ("free".equals(state) && !isMoving) {
                double distanceToDepot = Math.hypot(currentX - depotX, currentY - depotY);
                // If not at depot (within arrival threshold), move back instantly (safety net)
                if (distanceToDepot > ARRIVAL_THRESHOLD) {
                    currentX = depotX;
                    currentY = depotY;
                    System.out.println("Vehicle " + vehicleName + ": Returned to depot (safety). Position: (" + currentX + ", " + currentY + ")");
                    logger.logEvent("Returned to depot (safety). Position: (" + currentX + ", " + currentY + ")");
                }
            }
            
            // Wait before next check
            block(checkInterval);
        }
    }
    
    /**
     * Registers Vehicle agent with DF (Directory Facilitator)
     */
    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            
            ServiceDescription sd = new ServiceDescription();
            sd.setType("vehicle-service");
            sd.setName("VRP-Vehicle-" + vehicleName);
            sd.setOwnership("VRP-System");
            dfd.addServices(sd);
            
            DFService.register(this, dfd);
            System.out.println("Vehicle " + vehicleName + ": Registered with DF as 'vehicle-service'");
            logger.logEvent("DF Registration successful");
        } catch (FIPAException fe) {
            System.err.println("Vehicle " + vehicleName + ": Failed to register with DF: " + fe.getMessage());
        }
    }
    
    @Override
    protected void takeDown() {
        logger.logEvent("Agent terminating");
        try {
            DFService.deregister(this);
            System.out.println("Vehicle " + vehicleName + ": Deregistered from DF");
            logger.logEvent("Deregistered from DF");
        } catch (FIPAException fe) {
            System.err.println("Vehicle " + vehicleName + ": Error deregistering from DF: " + fe.getMessage());
            logger.log("ERROR: Failed to deregister from DF: " + fe.getMessage());
        }
        System.out.println("Vehicle Agent " + vehicleName + " terminating.");
        logger.close();
    }
}
