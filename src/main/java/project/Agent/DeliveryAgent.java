package project.Agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
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
 * Delivery Agent (DA) for CVRP
 * - Has its own name, capacity & maximum travel distance
 * - Responds to MRA queries with vehicle information
 * - Accepts route assignments from MRA
 * - Executes routes and returns to depot
 */
public class DeliveryAgent extends Agent {
    private String vehicleName;
    private int capacity;
    private double maxDistance;
    
    // Current position
    private double currentX;
    private double currentY;
    private double depotX;
    private double depotY;
    
    // Route queue - stores routes that need to be processed
    private java.util.Queue<QueuedRoute> routeQueue;
    
    // Current assignment (route being executed)
    private String assignedRouteId;
    private List<CustomerInfo> currentRoute;
    
    // Movement state
    private int currentCustomerIndex;  // Index of customer currently moving to (-1 means returning to depot)
    private double targetX;            // Target X coordinate
    private double targetY;            // Target Y coordinate
    private boolean isMoving;          // Whether vehicle is currently moving
    private MovementBehaviour currentMovementBehaviour;  // Track current movement behavior instance
    
    /**
     * Inner class to store queued route information
     */
    @SuppressWarnings("unused")
    private static class QueuedRoute {
        String routeId;
        String routeData;
        int routeDemand;  // For logging/debugging
        double routeDistance;  // For logging/debugging
        List<String> customerIds;  // For logging/debugging
        
        QueuedRoute(String routeId, String routeData, int routeDemand, double routeDistance, List<String> customerIds) {
            this.routeId = routeId;
            this.routeData = routeData;
            this.routeDemand = routeDemand;
            this.routeDistance = routeDistance;
            this.customerIds = customerIds;
        }
    }
    private static final double MOVEMENT_SPEED = 1.0;  // Units per second
    private static final double ARRIVAL_THRESHOLD = 1.0;  // Distance threshold from the vehicle to the customer node to consider arrived
    
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
            this.capacity = 50; // Default capacity
            this.maxDistance = 1000.0; // Default max distance
        }
        
        // Initialize position at depot (will be set by MRA location)
        this.depotX = 0.0;
        this.depotY = 0.0;
        this.currentX = depotX;  // Start at depot
        this.currentY = depotY;
        
        // Initialize route queue
        this.routeQueue = new java.util.LinkedList<>();
        
        this.assignedRouteId = null;
        this.currentRoute = null;
        this.currentCustomerIndex = -1;
        this.isMoving = false;
        this.currentMovementBehaviour = null;
        
        // Report agent status to backend with capacity, speed, and max distance
        com.google.gson.JsonObject info = new com.google.gson.JsonObject();
        info.addProperty("capacity", capacity);
        info.addProperty("speed", MOVEMENT_SPEED);
        info.addProperty("maxDistance", maxDistance);
        project.Utils.BackendClient.reportAgentStatus(vehicleName, "da", "active", info);
        
        // Initialize logger
        logger = new AgentLogger("DA-" + vehicleName);
        logger.setAgentAID(this);
        logger.logEvent("Agent started");
        logger.log("Capacity: " + capacity + ", Max Distance: " + maxDistance);
        logger.log("Initial position: (" + currentX + ", " + currentY + ")");
        
        // Register with DF (Yellow Pages) as "da-service"
        registerWithDF();
        logger.logEvent("Registered with DF as 'da-service'");
        
        // Add behavior to handle vehicle info queries from MRA
        addBehaviour(new VehicleInfoQueryHandler());
        
        // Add behavior to handle route assignments from MRA
        addBehaviour(new RouteAssignmentHandler());
        
        // Add behavior to process route queue and return to depot when idle
        addBehaviour(new ReturnToDepotBehaviour(this, 5000));  // Check every 5 seconds
    }
    
    /**
     * Handles vehicle information queries from MRA using FIPA-Request
     */
    private class VehicleInfoQueryHandler extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST)
            );
            
            ACLMessage msg = receive(template);
            if (msg != null && "QUERY_VEHICLE_INFO".equals(msg.getContent())) {
                logger.logReceived(msg);
                
                // Log conversation start for vehicle info query
                if (msg.getConversationId() != null) {
                    logger.logConversationStart(msg.getConversationId(), 
                        "Vehicle info query from " + (msg.getSender() != null ? msg.getSender().getLocalName() : "unknown"));
                }
                
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                reply.setConversationId(msg.getConversationId());
                
                reply.setContent("CAPACITY:" + capacity + "|MAX_DISTANCE:" + maxDistance +
                               "|NAME:" + vehicleName + "|X:" + currentX + "|Y:" + currentY);
                
                logger.logConversationEnd(msg.getConversationId(), 
                    "Vehicle info responded - Capacity: " + capacity + 
                    ", MaxDistance: " + maxDistance);
                
                logger.logSent(reply);
                send(reply);
                
                logger.logEvent("Responded to vehicle info query from MRA");
            } else {
                block();
            }
        }
    }
    
    /**
     * Route Assignment Handler
     * Handles route assignments from MRA
     */
    private class RouteAssignmentHandler extends CyclicBehaviour {
        @Override
        public void action() {
            // Check for route assignment messages from MRA
            MessageTemplate routeAssignmentTemplate = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                MessageTemplate.MatchOntology("route-assignment")
            );
            
            ACLMessage routeAssignment = receive(routeAssignmentTemplate);
            if (routeAssignment != null) {
                // Log the received route assignment message immediately
                String senderName = (routeAssignment.getSender() != null) ? routeAssignment.getSender().getLocalName() : "unknown";
                
                // Log the received message to file
                logger.logReceived(routeAssignment);
                
                // Log conversation start for route assignment
                if (routeAssignment.getConversationId() != null) {
                    // Extract route ID for logging
                    String routeId = "unknown";
                    try {
                        String content = routeAssignment.getContent();
                        if (content != null && content.startsWith("ROUTE_ASSIGNMENT:")) {
                            String routeData = content.substring("ROUTE_ASSIGNMENT:".length());
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
                    logger.logConversationStart(routeAssignment.getConversationId(), 
                        "Route assignment for route " + routeId + " from " + senderName);
                }
                
                // Handle the route assignment
                handleRouteAssignment(routeAssignment);
                return;
            }
            
            block();
        }
        
        /**
         * Handles route assignment from MRA.
         * DA validates the assignment and starts executing if feasible.
         */
        private void handleRouteAssignment(ACLMessage routeAssignment) {
            String content = routeAssignment.getContent();
            String senderName = (routeAssignment.getSender() != null) ? routeAssignment.getSender().getLocalName() : "unknown";
            
            logger.logEvent("Received route assignment message from MRA: " + senderName);
            
            if (content == null || !content.startsWith("ROUTE_ASSIGNMENT:")) {
                logger.log("WARNING: Received route assignment with invalid content");
                return;
            }

            String routeData = content.substring("ROUTE_ASSIGNMENT:".length());
            String[] parts = routeData.split("\\|");

            String routeId = null;
            Integer assignedVehicleId = null;
            String assignedVehicleName = null;
            int routeDemand = 0;
            double routeDistance = 0.0;
            List<String> customerIds = new ArrayList<>();
            String depotXStr = null;
            String depotYStr = null;

            try {
                for (String part : parts) {
                    if (part.startsWith("ROUTE:")) {
                        routeId = part.substring("ROUTE:".length());
                    } else if (part.startsWith("VEHICLE_ID:")) {
                        assignedVehicleId = Integer.parseInt(part.substring("VEHICLE_ID:".length()));
                    } else if (part.startsWith("VEHICLE_NAME:")) {
                        assignedVehicleName = part.substring("VEHICLE_NAME:".length());
                    } else if (part.startsWith("DEMAND:")) {
                        routeDemand = Integer.parseInt(part.substring("DEMAND:".length()));
                    } else if (part.startsWith("DISTANCE:")) {
                        routeDistance = Double.parseDouble(part.substring("DISTANCE:".length()));
                    } else if (part.startsWith("CUSTOMERS:")) {
                        String customersStr = part.substring("CUSTOMERS:".length());
                        if (!customersStr.isEmpty()) {
                            String[] ids = customersStr.split(",");
                            for (String id : ids) {
                                customerIds.add(id.trim());
                            }
                        }
                    } else if (part.startsWith("DEPOT_X:")) {
                        depotXStr = part.substring("DEPOT_X:".length());
                        depotX = Double.parseDouble(depotXStr);
                        // Update current position to depot position when depot is received
                        currentX = depotX;
                    } else if (part.startsWith("DEPOT_Y:")) {
                        depotYStr = part.substring("DEPOT_Y:".length());
                        depotY = Double.parseDouble(depotYStr);
                        // Update current position to depot position when depot is received
                        currentY = depotY;
                    }
                }
            } catch (Exception e) {
                logger.log("ERROR: Failed to parse route assignment: " + e.getMessage());
                return;
            }

            // Log parsed route assignment details
            if (routeId == null) {
                logger.log("ERROR: Invalid route assignment - missing route ID");
                return;
            }

            if (assignedVehicleName != null && !assignedVehicleName.equals(vehicleName)) {
                logger.logEvent("Ignoring route " + routeId + " - assigned to " + assignedVehicleName + 
                              " (this vehicle: " + vehicleName + ")");
                
                // Send reject response (route not for this vehicle)
                sendRejectResponse(routeAssignment, routeId, "WRONG_VEHICLE", 
                    "Route assigned to " + assignedVehicleName + ", not " + vehicleName);
                return;
            }

            logger.logEvent("Evaluating route " + routeId + ": demand=" + routeDemand +
                          ", distance=" + String.format("%.2f", routeDistance) +
                          ", capacity=" + capacity + ", maxDistance=" + maxDistance);

            // No longer reject routes when already assigned - add to queue instead

            if (routeDemand > capacity) {
                logger.logEvent("Route " + routeId + " rejected locally - demand " + routeDemand +
                              " exceeds capacity " + capacity);
                
                // Send reject response
                sendRejectResponse(routeAssignment, routeId, "CAPACITY_EXCEEDED", 
                    "Demand " + routeDemand + " exceeds capacity " + capacity);
                return;
            }

            if (routeDistance > maxDistance) {
                logger.logEvent("Route " + routeId + " rejected locally - distance " +
                              String.format("%.2f", routeDistance) + " exceeds max distance " + maxDistance);
                
                // Send reject response
                sendRejectResponse(routeAssignment, routeId, "DISTANCE_EXCEEDED", 
                    "Distance " + String.format("%.2f", routeDistance) + " exceeds max distance " + maxDistance);
                return;
            }

            // Route is valid and accepted - add to queue
            logger.logEvent("ACCEPTED route " + routeId + ": capacity=" + capacity + " (demand=" + routeDemand +
                          "), maxDistance=" + maxDistance + " (route distance=" + String.format("%.2f", routeDistance) + ")");

            // Add route to queue
            QueuedRoute queuedRoute = new QueuedRoute(routeId, routeData, routeDemand, routeDistance, customerIds);
            routeQueue.offer(queuedRoute);
            logger.logEvent("Route " + routeId + " added to queue. Queue size: " + routeQueue.size());

            // Prepare acceptance response to MRA
            String responseContent = "ROUTE_ACCEPTED:" + routeId + "|VEHICLE:" + vehicleName + 
                                   "|STATUS:ACCEPTED|DEMAND:" + routeDemand + 
                                   "|DISTANCE:" + String.format("%.2f", routeDistance) +
                                   "|CUSTOMERS:" + customerIds.size();
            
            // Send response back to MRA (FIPA-Request protocol requires a response)
            ACLMessage response = routeAssignment.createReply();
            response.setPerformative(ACLMessage.INFORM);
            response.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            response.setConversationId(routeAssignment.getConversationId());
            response.setContent(responseContent);
            
            // Log the response before sending
            logger.logEvent("Sending route acceptance response to MRA: Route " + routeId + 
                          " ACCEPTED by vehicle " + vehicleName);
            logger.logSent(response);
            
            // Send the response
            send(response);
            logger.logEvent("Route acceptance response sent successfully to MRA for route " + routeId);
            
            // Log conversation end
            if (routeAssignment.getConversationId() != null) {
                logger.logConversationEnd(routeAssignment.getConversationId(), 
                    "Route " + routeId + " ACCEPTED and queued");
            }

            // Start processing queue if not already moving
            if (!isMoving && currentRoute == null) {
                processNextRouteInQueue();
            }
        }
        
        /**
         * Sends a reject response to MRA for a route assignment
         */
        private void sendRejectResponse(ACLMessage routeAssignment, String routeId, String reason, String details) {
            // Prepare rejection response
            String responseContent = "ROUTE_REJECTED:" + routeId + "|VEHICLE:" + vehicleName + 
                                   "|STATUS:REJECTED|REASON:" + reason + "|DETAILS:" + details;
            
            // Create response message
            ACLMessage response = routeAssignment.createReply();
            response.setPerformative(ACLMessage.REFUSE);
            response.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            response.setConversationId(routeAssignment.getConversationId());
            response.setContent(responseContent);
            
            // Log the response before sending
            logger.logEvent("Sending route rejection response to MRA: Route " + routeId + 
                          " REJECTED by vehicle " + vehicleName + " - Reason: " + reason);
            logger.logSent(response);
            
            // Send the response
            send(response);
            logger.logEvent("Route rejection response sent successfully to MRA for route " + routeId + ": " + reason);
            
            // Log conversation end
            if (routeAssignment.getConversationId() != null) {
                logger.logConversationEnd(routeAssignment.getConversationId(), 
                    "Route " + routeId + " REJECTED: " + reason + " - " + details);
            }
        }
    }
    
    /**
     * Notifies MRA that DA has arrived at depot
     */
    private void notifyMRAArrival() {
        try {
            // Find MRA via DF
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("mra-service");
            template.addServices(sd);
            
            DFAgentDescription[] results = DFService.search(this, template);
            if (results.length > 0) {
                jade.core.AID mraAID = results[0].getName();
                
                ACLMessage notification = new ACLMessage(ACLMessage.INFORM);
                notification.addReceiver(mraAID);
                notification.setContent("DA_ARRIVED_AT_DEPOT");
                notification.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                String conversationId = "da-arrival-" + vehicleName + "-" + System.currentTimeMillis();
                notification.setConversationId(conversationId);
                
                logger.logConversationStart(conversationId, "Notifying MRA of arrival at depot");
                logger.logSent(notification);
                send(notification);
                logger.logEvent("Notified MRA of arrival at depot - ready for next route");
            } else {
                logger.log("WARNING: MRA not found via DF, cannot notify arrival");
            }
        } catch (Exception e) {
            logger.log("ERROR: Failed to notify MRA: " + e.getMessage());
        }
    }
    
    /**
     * Processes the next route in the queue
     * Called when a route completes or when a new route is added to an empty queue
     */
    private void processNextRouteInQueue() {
        if (routeQueue.isEmpty()) {
            logger.logEvent("Route queue is empty. Waiting for new routes.");
            return;
        }
        
        QueuedRoute queuedRoute = routeQueue.poll();
        logger.logEvent("Processing route " + queuedRoute.routeId + " from queue. Remaining: " + routeQueue.size());
        
        parseRouteAndStartMovement(queuedRoute.routeId, queuedRoute.routeData);
    }
    
    /**
     * Parses route data and starts movement behavior
     * Called when DA starts executing a route from the queue
     */
    private void parseRouteAndStartMovement(String routeId, String routeData) {
        logger.logEvent("Parsing route data for route " + routeId);
        
        // Update assignment
        assignedRouteId = routeId;
        logger.logEvent("Starting delivery for route " + routeId);
        
        // Parse route data to extract customer information
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
                                
                                String numericId = i < customerNumericIds.size() ? customerNumericIds.get(i) : String.valueOf(i + 1);
                                String agentId = i < customerAgentIds.size() ? customerAgentIds.get(i) : "customer-" + numericId;
                                
                                int customerIdNum = Integer.parseInt(numericId);
                                CustomerInfo customer = new CustomerInfo(customerIdNum, x, y, 0);
                                customer.name = agentId;
                                customers.add(customer);
                            }
                        }
                    }
                }
            }
            } catch (Exception e) {
                logger.log("ERROR: Failed to parse route data: " + e.getMessage());
                assignedRouteId = null;
                return;
            }
        
            if (customers.isEmpty()) {
                logger.log("ERROR: No customers found in route data for route " + routeId);
                assignedRouteId = null;
                return;
            }
        
        // Store route
        currentRoute = customers;
        currentCustomerIndex = 0;
        isMoving = true;
        logger.logEvent("Route " + routeId + " parsed: " + currentRoute.size() + " customers");
        
        // Set target to first customer
        if (currentRoute.size() > 0) {
            CustomerInfo firstCustomer = currentRoute.get(0);
            targetX = firstCustomer.x;
            targetY = firstCustomer.y;
            double distanceToFirst = Math.hypot(currentX - targetX, currentY - targetY);
            logger.logEvent("Starting route: " + currentRoute.size() + " customers. Moving to " + 
                          firstCustomer.name + " at (" + targetX + ", " + targetY + 
                          "). Distance: " + String.format("%.2f", distanceToFirst));
        }
        
        // Stop any existing movement behavior
        if (currentMovementBehaviour != null) {
            logger.logEvent("Stopping existing MovementBehaviour");
            removeBehaviour(currentMovementBehaviour);
            currentMovementBehaviour = null;
        }
        
        // Start movement behavior (updates position every second)
        currentMovementBehaviour = new MovementBehaviour(this, 1000);  // Update every 1 second
        addBehaviour(currentMovementBehaviour);
        logger.logEvent("MovementBehaviour started for route " + routeId);
    }
    
    /**
     * Movement behavior that updates vehicle position every second
     * Moves vehicle towards customers and returns to depot
     */
    private class MovementBehaviour extends TickerBehaviour {
        public MovementBehaviour(Agent a, long period) {
            super(a, period);
        }
        
        @Override
        protected void onTick() {
            if (!isMoving) {
                return;
            }
            
            if (currentRoute == null) {
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
            }
        }
        
        /**
         * Moves vehicle towards current target customer
         */
        private void moveTowardsCustomer() {
            if (currentRoute == null || currentCustomerIndex < 0 || currentCustomerIndex >= currentRoute.size()) {
                return;
            }
            
            CustomerInfo customer = currentRoute.get(currentCustomerIndex);
            targetX = customer.x;
            targetY = customer.y;
            
            // Calculate distance to target
            double dx = targetX - currentX;
            double dy = targetY - currentY;
            double distance = Math.hypot(dx, dy);
            
            if (distance <= ARRIVAL_THRESHOLD) {
                // Arrived at customer
                currentX = targetX;
                currentY = targetY;
                logger.logEvent("ARRIVED at customer " + customer.name + " (ID: " + customer.id + 
                              ") at (" + currentX + ", " + currentY + ")");
                
                // Move to next customer
                currentCustomerIndex++;
                if (currentCustomerIndex >= currentRoute.size()) {
                    // All customers visited, return to depot
                    logger.logEvent("All " + currentRoute.size() + " customers visited. Returning to depot.");
                    currentCustomerIndex = -2;  // Special value to indicate returning to depot
                    targetX = depotX;
                    targetY = depotY;
                } else {
                    // Move to next customer
                    CustomerInfo nextCustomer = currentRoute.get(currentCustomerIndex);
                    targetX = nextCustomer.x;
                    targetY = nextCustomer.y;
                }
            } else {
                // Move towards target
                double moveDistance = Math.min(MOVEMENT_SPEED, distance);
                double ratio = moveDistance / distance;
                
                currentX += dx * ratio;
                currentY += dy * ratio;
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
                String completedRouteId = assignedRouteId;
                logger.logEvent("RETURNED to depot. Route " + completedRouteId + " completed.");
                
                // Clear current route
                assignedRouteId = null;
                currentRoute = null;
                currentCustomerIndex = -1;
                isMoving = false;
                
                // Stop current movement behavior
                stop();
                if (currentMovementBehaviour != null) {
                    removeBehaviour(currentMovementBehaviour);
                    currentMovementBehaviour = null;
                }
                
                // Notify MRA that DA has arrived at depot
                notifyMRAArrival();
                
                // Process next route in queue if available
                processNextRouteInQueue();
            } else {
                // Move towards depot
                double moveDistance = Math.min(MOVEMENT_SPEED, distance);
                double ratio = moveDistance / distance;
                
                currentX += dx * ratio;
                currentY += dy * ratio;
            }
        }
    }
    
    /**
     * Behavior that returns vehicle to depot when free and not at depot
     */
    private class ReturnToDepotBehaviour extends CyclicBehaviour {
        private long checkInterval;
        
        public ReturnToDepotBehaviour(Agent a, long checkInterval) {
            super(a);
            this.checkInterval = checkInterval;
        }
        
        @Override
        public void action() {
            // If no route is being executed and not moving, ensure we're at depot
            // and check if there are routes in queue to process
            if (assignedRouteId == null && !isMoving) {
                double distanceToDepot = Math.hypot(currentX - depotX, currentY - depotY);
                if (distanceToDepot > ARRIVAL_THRESHOLD) {
                    currentX = depotX;
                    currentY = depotY;
                }
                
                // Check if there are routes in queue to process
                if (!routeQueue.isEmpty()) {
                    processNextRouteInQueue();
                }
            }
            
            block(checkInterval);
        }
    }
    
    /**
     * Registers DA with DF (Directory Facilitator) as "da-service"
     */
    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            
            ServiceDescription sd = new ServiceDescription();
            sd.setType("da-service");
            sd.setName("CVRP-Delivery-Agent-" + vehicleName);
            sd.setOwnership("CVRP-System");
            dfd.addServices(sd);
            
            DFService.register(this, dfd);
            logger.logEvent("DF Registration successful");
        } catch (FIPAException fe) {
            logger.log("ERROR: Failed to register with DF: " + fe.getMessage());
        }
    }
    
    @Override
    protected void takeDown() {
        // Report agent status before terminating
        project.Utils.BackendClient.reportAgentStatus(vehicleName, "da", "terminated");
        logger.logEvent("Agent terminating");
        try {
            DFService.deregister(this);
            logger.logEvent("Deregistered from DF");
        } catch (FIPAException fe) {
            logger.log("ERROR: Failed to deregister from DF: " + fe.getMessage());
        }
        logger.close();
    }
}

