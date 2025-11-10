package project.Agent;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.FIPANames;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.proto.ContractNetResponder;
import project.General.CustomerInfo;
import project.Utils.AgentLogger;

import java.util.Random;
import java.util.List;
import java.util.ArrayList;

/**
 * Independent Vehicle Agent that bids for routes via Contract-Net
 * Tracks current position and calculates bids based on distance from first customer
 */
public class VehicleAgent extends Agent {
    private String vehicleName;
    private int capacity;
    private double maxDistance;  // Maximum distance vehicle can travel (Basic Requirement 2)
    private String state; // "free", "absent", "busy"
    private Random random;
    
    // Current position (not always at depot)
    private double currentX;
    private double currentY;
    private double depotX;
    private double depotY;
    
    // Current assignment
    private String assignedRouteId;
    private java.util.List<CustomerInfo> currentRoute;
    
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
        
        // Initialize position (random starting location)
        this.depotX = 0.0;
        this.depotY = 0.0;
        this.random = new Random();
        this.currentX = depotX + (random.nextDouble() * 200 - 100);  // Random within 100 units
        this.currentY = depotY + (random.nextDouble() * 200 - 100);
        
        this.state = "free"; // There are 2 states: free and absent, simulating real-world availability
        this.assignedRouteId = null;
        this.currentRoute = null;
        
        System.out.println("Vehicle Agent " + vehicleName + " started:");
        System.out.println("  Capacity: " + capacity + " items");
        System.out.println("  Max Distance: " + maxDistance);
        System.out.println("  Initial position: (" + currentX + ", " + currentY + ")");
        System.out.println("  Depot: (" + depotX + ", " + depotY + ")");
        
        // Initialize logger
        logger = new AgentLogger("Vehicle-" + vehicleName);
        logger.logEvent("Agent started");
        logger.log("Capacity: " + capacity + ", Max Distance: " + maxDistance);
        logger.log("Initial position: (" + currentX + ", " + currentY + ")");
        
        // Register with DF (Yellow Pages)
        registerWithDF();
        logger.logEvent("Registered with DF as 'vehicle-service'");
        
        // Add behavior to handle state queries
        addBehaviour(new QueryHandlerBehaviour());
        
        // Add Contract-Net responder for route bidding
        addBehaviour(new RouteContractNetResponder(this));
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
                if (msg.getContent().equals("QUERY_STATE")) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setProtocol(FIPANames.InteractionProtocol.FIPA_QUERY);
                    reply.setContent("STATE:" + state + "|CAPACITY:" + capacity + "|MAX_DISTANCE:" + maxDistance + 
                                   "|NAME:" + vehicleName + "|X:" + currentX + "|Y:" + currentY);
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
     * Contract-Net Responder that bids on routes
     */
    private class RouteContractNetResponder extends ContractNetResponder {
        public RouteContractNetResponder(Agent a) {
            super(a, MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.CFP),
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET)
            ));
        }
        
        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) {
            System.out.println("\n=== Vehicle " + vehicleName + ": Received CFP ===");
            System.out.println("CFP: " + cfp.getContent());
            
            logger.logReceived(cfp);
            
            // Only bid if free
            if (!"free".equals(state)) {
                System.out.println("Vehicle " + vehicleName + ": Not available (state: " + state + ")");
                logger.logEvent("Refusing route: state is " + state);
                ACLMessage refuse = cfp.createReply();
                refuse.setPerformative(ACLMessage.REFUSE);
                refuse.setContent("Vehicle not available: " + state);
                logger.logSent(refuse);
                return refuse;
            }
            
            // Parse route information
            String content = cfp.getContent();
            // Format: ROUTE:routeId|CUSTOMERS:customerId1,customerId2|COORDS:x1,y1;x2,y2|DEMAND:total|DISTANCE:total
            
            String[] parts = content.split("\\|");
            if (parts.length < 5) {
                ACLMessage refuse = cfp.createReply();
                refuse.setPerformative(ACLMessage.REFUSE);
                refuse.setContent("Invalid route format");
                return refuse;
            }
            
            int routeDemand = 0;
            double routeDistance = 0.0;
            double[] firstCustomerPos = null;
            double[] lastCustomerPos = null;
            
            try {
                for (String part : parts) {
                    if (part.startsWith("DEMAND:")) {
                        routeDemand = Integer.parseInt(part.substring("DEMAND:".length()));
                    } else if (part.startsWith("DISTANCE:")) {
                        routeDistance = Double.parseDouble(part.substring("DISTANCE:".length()));
                    } else if (part.startsWith("COORDS:")) {
                        // Parse customer coordinates from depot
                        String coordsString = part.substring("COORDS:".length());
                        if (!coordsString.isEmpty()) {
                            // Format: x1,y1;x2,y2
                            String[] coordPairs = coordsString.split(";");
                            if (coordPairs.length > 0) {
                                // First customer
                                String[] firstCoords = coordPairs[0].split(",");
                                if (firstCoords.length == 2) {
                                    firstCustomerPos = new double[]{
                                        Double.parseDouble(firstCoords[0]),
                                        Double.parseDouble(firstCoords[1])
                                    };
                                }
                                // Last customer
                                if (coordPairs.length > 1) {
                                    String[] lastCoords = coordPairs[coordPairs.length - 1].split(",");
                                    if (lastCoords.length == 2) {
                                        lastCustomerPos = new double[]{
                                            Double.parseDouble(lastCoords[0]),
                                            Double.parseDouble(lastCoords[1])
                                        };
                                    }
                                } else if (coordPairs.length == 1) {
                                    // Only one customer, so last customer is same as first
                                    lastCustomerPos = firstCustomerPos;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Vehicle " + vehicleName + ": Error parsing route: " + e.getMessage());
                ACLMessage refuse = cfp.createReply();
                refuse.setPerformative(ACLMessage.REFUSE);
                refuse.setContent("Error parsing route");
                return refuse;
            }
            
            // BASIC REQUIREMENT: Check capacity constraint
            if (routeDemand > capacity) {
                System.out.println("Vehicle " + vehicleName + ": Insufficient capacity (" + 
                                 routeDemand + " > " + capacity + ")");
                logger.logEvent("Refusing route: insufficient capacity (" + routeDemand + " > " + capacity + ")");
                ACLMessage refuse = cfp.createReply();
                refuse.setPerformative(ACLMessage.REFUSE);
                refuse.setContent("Insufficient capacity");
                logger.logSent(refuse);
                return refuse;
            }
            
            // BASIC REQUIREMENT 2: Check maximum distance constraint
            // Calculate total distance: from current position to first customer + route distance + return to depot
            double totalRouteDistance = routeDistance;
            if (firstCustomerPos != null) {
                // Distance from current position to first customer
                double toFirstCustomer = Math.hypot(
                    currentX - firstCustomerPos[0],
                    currentY - firstCustomerPos[1]
                );
                totalRouteDistance += toFirstCustomer;
            }
            if (lastCustomerPos != null) {
                // Distance from last customer back to depot
                double toDepot = Math.hypot(
                    lastCustomerPos[0] - depotX,
                    lastCustomerPos[1] - depotY
                );
                totalRouteDistance += toDepot;
            }
            
            if (totalRouteDistance > maxDistance) {
                System.out.println("Vehicle " + vehicleName + ": Route exceeds maximum distance (" + 
                                 String.format("%.2f", totalRouteDistance) + " > " + maxDistance + ")");
                logger.logEvent("Refusing route: exceeds maximum distance (" + 
                              String.format("%.2f", totalRouteDistance) + " > " + maxDistance + ")");
                ACLMessage refuse = cfp.createReply();
                refuse.setPerformative(ACLMessage.REFUSE);
                refuse.setContent("Route exceeds maximum distance");
                logger.logSent(refuse);
                return refuse;
            }
            
            // Calculate bid cost based on total distance
            double bidCost = totalRouteDistance;
            
            System.out.println("Vehicle " + vehicleName + ": Bidding with cost: " + String.format("%.2f", bidCost));
            System.out.println("  Route demand: " + routeDemand + "/" + capacity);
            System.out.println("  Route distance: " + String.format("%.2f", routeDistance));
            System.out.println("  Total distance (with travel): " + String.format("%.2f", totalRouteDistance) + "/" + maxDistance);
            logger.logEvent("Bidding for route: cost=" + String.format("%.2f", bidCost) + 
                          ", demand=" + routeDemand + "/" + capacity +
                          ", totalDistance=" + String.format("%.2f", totalRouteDistance) + "/" + maxDistance);
            
            // Create proposal
            ACLMessage propose = cfp.createReply();
            propose.setPerformative(ACLMessage.PROPOSE);
            propose.setContent("COST:" + String.format("%.2f", bidCost) + 
                            "|CAPACITY:" + capacity + 
                            "|MAX_DISTANCE:" + maxDistance +
                            "|AVAILABLE:" + (capacity - routeDemand));
            logger.logSent(propose);
            
            return propose;
        }
        
        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) {
            logger.logReceived(accept);
            System.out.println("\n=== Vehicle " + vehicleName + ": Route Accepted ===");
            logger.logEvent("Route accepted via Contract-Net");
            
            // Extract route info from CFP
            String content = cfp.getContent();
            String[] parts = content.split("\\|");
            String routeId = "R1";
            for (String part : parts) {
                if (part.startsWith("ROUTE:")) {
                    routeId = part.substring("ROUTE:".length());
                    break;
                }
            }
            
            assignedRouteId = routeId;
            state = "absent";  // Vehicle is now absent (delivering)
            
            // Extract customer IDs from CFP for notification later
            List<String> customerIds = new ArrayList<>();
            for (String part : parts) {
                if (part.startsWith("CUSTOMERS:")) {
                    String customerIdsStr = part.substring("CUSTOMERS:".length());
                    if (!customerIdsStr.isEmpty()) {
                        String[] ids = customerIdsStr.split(",");
                        for (String id : ids) {
                            customerIds.add(id.trim());
                        }
                    }
                    break;
                }
            }
            
            // Update position (simulate movement - in production would track actual route)
            // For now, move closer to depot
            currentX = depotX + (random.nextDouble() * 50 - 25);
            currentY = depotY + (random.nextDouble() * 50 - 25);
            
            System.out.println("Vehicle " + vehicleName + ": Assigned route " + routeId);
            System.out.println("Vehicle " + vehicleName + ": State changed to 'absent' (delivering)");
            System.out.println("Vehicle " + vehicleName + ": New position: (" + currentX + ", " + currentY + ")");
            logger.logEvent("State changed to 'absent' - starting delivery");
            
            // Confirm acceptance
            ACLMessage inform = accept.createReply();
            inform.setPerformative(ACLMessage.INFORM);
            inform.setContent("ROUTE_ACCEPTED:" + routeId + "|VEHICLE:" + vehicleName);
            logger.logSent(inform);
            
            // After some time, return to free state (simulating route completion)
            // Pass customer IDs to notify them when delivery completes
            addBehaviour(new RouteCompletionBehaviour(getAgent(), 30000, customerIds));  // Complete in 30 seconds
            
            return inform;
        }
        
        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            logger.logReceived(reject);
            System.out.println("Vehicle " + vehicleName + ": Proposal rejected");
            logger.logEvent("Proposal rejected by depot");
        }
    }
    
    /**
     * Simulates route completion and returns vehicle to free state
     * Also notifies customers that their goods have arrived
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
            
            // Return closer to depot
            currentX = depotX + (random.nextDouble() * 100 - 50);
            currentY = depotY + (random.nextDouble() * 100 - 50);
            
            System.out.println("Vehicle " + vehicleName + ": Route completed. Returning to free state.");
            System.out.println("Vehicle " + vehicleName + ": Position: (" + currentX + ", " + currentY + ")");
            logger.logEvent("Route completed. Returning to free state. New position: (" + currentX + ", " + currentY + ")");
            
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
