package project;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public class Main {
    public static void main(String[] args) {
        // Get the JADE runtime instance
        Runtime rt = Runtime.instance();
        
        // Create a container profile
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.MAIN_PORT, "1099");
        p.setParameter(Profile.GUI, "true"); // Enable GUI for debugging
        
        // Create the main container
        AgentContainer mainContainer = rt.createMainContainer(p);
        
        try {
            // Create and start Delivery Agent first
            AgentController deliveryController = mainContainer.createNewAgent(
                "delivery-agent", 
                "project.Agent.Delivery", 
                null
            );
            deliveryController.start();
            System.out.println("Delivery Agent started: delivery-agent");
            
            // Wait a moment for Delivery Agent to initialize
            Thread.sleep(1000);
            
            // Create and start Depot agent
            AgentController depotController = mainContainer.createNewAgent(
                "depot-agent", 
                "project.Agent.Depot", 
                null
            );
            depotController.start();
            System.out.println("Depot Agent started: depot-agent");
            
            // Wait a moment for agents to initialize
            Thread.sleep(2000);
            
            System.out.println("\n=== MULTI-AGENT SYSTEM READY ===");
            System.out.println("✓ Depot Agent: Polls API and solves VRP");
            System.out.println("✓ Delivery Agent: Manages vehicle agents and assigns routes");
            System.out.println("✓ Vehicle Agents: Created dynamically based on request");
            System.out.println("\nWorkflow:");
            System.out.println("  1. API receives request → Depot Agent");
            System.out.println("  2. Depot → Delivery Agent (forwards vehicle data)");
            System.out.println("  3. Delivery Agent manages vehicle agents (create/query states)");
            System.out.println("  4. Delivery → Depot (reports available vehicles)");
            System.out.println("  5. Depot calculates routes → Delivery Agent");
            System.out.println("  6. Delivery Agent assigns routes to vehicles");
            System.out.println("  7. Delivery Agent → API (sends final solution)");
            System.out.println("\nAPI Endpoint: http://localhost:8000/api/solve-cvrp");
            System.out.println("===============================================\n");
            
        } catch (Exception e) {
            System.err.println("Error creating agents: " + e.getMessage());
            e.printStackTrace();
        }
	}
}

