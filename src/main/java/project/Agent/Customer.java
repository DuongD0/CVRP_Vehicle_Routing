package project.Agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.domain.FIPANames;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import project.General.CustomerRequest;
import project.Utils.AgentLogger;

import java.util.Random;

/*
 * Customer agent:
 * - Send requests to the depot
 * - Receive responses from the depot
 */
public class Customer extends Agent {
    private String customerId;
    private String customerName;
    private double x;
    private double y;
    private Random random;
    
    // Logger for conversations
    private AgentLogger logger; // to generate time window requirement
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 4) {
            this.customerId = (String) args[0];
            this.customerName = (String) args[1];
            this.x = (Double) args[2];
            this.y = (Double) args[3];
        } else {
            // Default values for testing
            this.customerId = "customer-" + getLocalName();
            this.customerName = "Customer " + getLocalName();
            this.x = 800.0 + (Math.random() * 200);
            this.y = 600.0 + (Math.random() * 200);
        }
        
        this.random = new Random();
        
        System.out.println("Customer Agent " + customerName + " (" + customerId + ") started at (" + x + ", " + y + ")");
        
        // Initialize logger
        logger = new AgentLogger("Customer-" + customerId);
        logger.setAgentAID(this);  // Set agent AID for proper logging
        logger.logEvent("Agent started");
        logger.log("Customer name: " + customerName);
        logger.log("Position: (" + x + ", " + y + ")");
        
        // Register with DF
        registerWithDF();
        logger.logEvent("Registered with DF as 'customer-service'");
        
        // Simulate real-life customer behavior
        addBehaviour(new RequestGeneratorBehaviour(this, 5000 + random.nextInt(10000))); // the customer will request every 5000 to 15000 milliseconds
        
        // Handle responses from Depot (notify if their request was accepted or rejected)
        addBehaviour(new ResponseHandlerBehaviour());
    }
    
    /*
     * A prototype item system to demonstrate cvrp with multi-agent
     */
    private class RequestGeneratorBehaviour extends TickerBehaviour {
        private final String[] ITEMS = {"ItemA", "ItemB", "ItemC", "ItemD"};
        
        public RequestGeneratorBehaviour(Agent a, long period) {
            super(a, period);
        }
        
        @Override
        protected void onTick() {
            // Generate random request
            String itemName = ITEMS[random.nextInt(ITEMS.length)];
            int quantity = 5 + random.nextInt(10); // 5-15 units
            
            CustomerRequest request = new CustomerRequest(customerId, customerName, x, y, itemName, quantity);
            
            // Find Depot via DF
            AID depotAgent = findDepotViaDF();
            if (depotAgent == null) {
                System.err.println("Customer " + customerName + ": Depot not found via DF");
                return;
            }
            
            // Send FIPA-REQUEST to Depot
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(depotAgent);
            msg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            msg.setConversationId("req-" + request.requestId);
            msg.setReplyWith("rw-" + System.currentTimeMillis());
            
            // Format: REQUEST:customerId|customerName|x|y|itemName|quantity
            String content = String.format("REQUEST:%s|%s|%.2f|%.2f|%s|%d",
                request.customerId, request.customerName, request.x, request.y,
                request.itemName, request.quantity);
            msg.setContent(content);
            
            // Log conversation start
            logger.logConversationStart(msg.getConversationId(), 
                "Customer request: " + quantity + "x " + itemName + " to " + depotAgent.getLocalName());
            
            logger.logSent(msg);
            send(msg);
            System.out.println("Customer " + customerName + ": Requested " + quantity + " units of " + itemName);
            logger.logEvent("Sent request: " + quantity + " units of " + itemName);
            
            // Reset period for next request
            reset(30000 + random.nextInt(30000));
        }
    }
    
    /*
     * Handles responses from Depot (item availability status)
     */
    private class ResponseHandlerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                logger.logReceived(msg);
                String content = msg.getContent();
                
                if (msg.getPerformative() == ACLMessage.INFORM) {
                    // Response about request status
                    if (content.startsWith("ITEM_AVAILABLE:")) {
                        System.out.println("Customer " + customerName + ": ✓ Request accepted - " + content.substring("ITEM_AVAILABLE:".length()));
                        logger.logEvent("Request accepted: " + content.substring("ITEM_AVAILABLE:".length()));
                        logger.logConversationEnd(msg.getConversationId(), "Request accepted - item available");
                    } else if (content.startsWith("ITEM_UNAVAILABLE:")) {
                        System.out.println("Customer " + customerName + ": ✗ Request rejected - " + content.substring("ITEM_UNAVAILABLE:".length()));
                        logger.logEvent("Request rejected: " + content.substring("ITEM_UNAVAILABLE:".length()));
                        logger.logConversationEnd(msg.getConversationId(), "Request rejected - item unavailable");
                    } else if (content.startsWith("ROUTE_ASSIGNED:")) {
                        System.out.println("Customer " + customerName + ": Route assigned - " + content.substring("ROUTE_ASSIGNED:".length()));
                        logger.logEvent("Route assigned: " + content.substring("ROUTE_ASSIGNED:".length()));
                    } else if (content.startsWith("DELIVERY_COMPLETE:")) {
                        System.out.println("Customer " + customerName + ": ✓✓✓ DELIVERY COMPLETE - " + content.substring("DELIVERY_COMPLETE:".length()));
                        logger.logEvent("DELIVERY COMPLETE: " + content.substring("DELIVERY_COMPLETE:".length()));
                        // Log delivery conversation if it has a conversation ID
                        if (msg.getConversationId() != null && msg.getConversationId().startsWith("delivery-")) {
                            logger.logConversationEnd(msg.getConversationId(), "Delivery completed successfully");
                        }
                    }
                } else if (msg.getPerformative() == ACLMessage.REFUSE) {
                    System.out.println("Customer " + customerName + ": Request was refused - " + content);
                    logger.logEvent("Request refused: " + content);
                    logger.logConversationEnd(msg.getConversationId(), "Request refused");
                }
            } else {
                block();
            }
        }
    }
    // Why only handle responses from Depot but not the Vehicle agent as well (delivered successfully)
    
    /**
     * Finds Depot agent via DF
     */
    private AID findDepotViaDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("depot-service");
            dfd.addServices(sd);
            
            DFAgentDescription[] results = DFService.search(this, dfd);
            if (results.length > 0) {
                System.out.println("Customer " + customerName + ": Found depot via DF");
                logger.log("DF Search: Found depot via 'depot-service'");
                return results[0].getName();
            }
        } catch (FIPAException fe) {
            System.err.println("Customer " + customerName + ": Error searching DF for depot: " + fe.getMessage());
        }
        return null;
    }
    
    /**
     * Registers Customer agent with DF
     */
    private void registerWithDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            
            ServiceDescription sd = new ServiceDescription();
            sd.setType("customer-service");
            sd.setName("VRP-Customer-" + customerId);
            sd.setOwnership("VRP-System");
            dfd.addServices(sd);
            
            DFService.register(this, dfd);
            System.out.println("Customer " + customerName + ": Registered with DF as 'customer-service'");
            logger.logEvent("DF Registration successful");
        } catch (FIPAException fe) {
            System.err.println("Customer " + customerName + ": Failed to register with DF: " + fe.getMessage());
        }
    }
    
    @Override
    protected void takeDown() {
        logger.logEvent("Agent terminating");
        try {
            DFService.deregister(this);
            logger.logEvent("Deregistered from DF");
        } catch (FIPAException fe) {
            logger.log("ERROR: Failed to deregister from DF: " + fe.getMessage());
        }
        System.out.println("Customer Agent " + customerName + " terminating.");
        logger.close();
    }
}
