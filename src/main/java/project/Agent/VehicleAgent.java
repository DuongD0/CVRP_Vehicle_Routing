package project.Agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import java.util.Random;

public class VehicleAgent extends Agent {
    private String vehicleName;
    private int capacity;
    private String state; // "free" or "absent"
    private Random random;
    private String assignedRouteId;
    
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            this.vehicleName = (String) args[0];
            this.capacity = (Integer) args[1];
        } else {
            this.vehicleName = getLocalName();
            this.capacity = 20; // Default capacity
        }
        
        this.random = new Random();
        this.state = "free"; // Start as free
        this.assignedRouteId = null;
        
        System.out.println("Vehicle Agent " + vehicleName + " started with capacity " + capacity);
        
        // Add behavior to randomly change state every 10-20 seconds
        addBehaviour(new StateChangeBehaviour(this, 10000 + random.nextInt(10000)));
        
        // Add behavior to respond to state queries and route assignments
        addBehaviour(new MessageHandlerBehaviour());
    }
    
    private class StateChangeBehaviour extends TickerBehaviour {
        public StateChangeBehaviour(Agent a, long period) {
            super(a, period);
        }
        
        @Override
        protected void onTick() {
            // Randomly change state between free and absent
            if (random.nextBoolean()) {
                state = "free";
            } else {
                state = "absent";
            }
            System.out.println("Vehicle " + vehicleName + " state changed to: " + state);
            
            // Reset period for next state change
            reset(10000 + random.nextInt(10000));
        }
    }
    
    private class MessageHandlerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                String content = msg.getContent();
                
                if (content.equals("QUERY_STATE")) {
                    // Respond with current state
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("STATE:" + state + "|CAPACITY:" + capacity + "|NAME:" + vehicleName);
                    send(reply);
                    System.out.println("Vehicle " + vehicleName + " reported state: " + state);
                    
                } else if (content.startsWith("ASSIGN_ROUTE:")) {
                    // Route assignment message
                    assignedRouteId = content.substring("ASSIGN_ROUTE:".length());
                    
                    // Confirm assignment
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.CONFIRM);
                    reply.setContent("ROUTE_ACCEPTED:" + assignedRouteId + "|VEHICLE:" + vehicleName);
                    send(reply);
                    System.out.println("Vehicle " + vehicleName + " accepted route: " + assignedRouteId);
                }
            } else {
                block();
            }
        }
    }
    
    @Override
    protected void takeDown() {
        System.out.println("Vehicle Agent " + vehicleName + " terminating.");
    }
}

