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
            // Create and start Depot agent
            AgentController depotController = mainContainer.createNewAgent(
                "depot-agent", 
                "project.Agent.Depot", 
                null
            );
            depotController.start();
            
            // Create and start MRA agent
            AgentController mraController = mainContainer.createNewAgent(
                "mra-agent", 
                "project.Agent.MRA", 
                null
            );
            mraController.start();
            
            System.out.println("Agents started successfully!");
            System.out.println("Depot Agent: depot-agent");
            System.out.println("MRA Agent: mra-agent");
            
            // Wait a moment for agents to initialize
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                System.out.println("Interrupted while waiting for agents to initialize");
            }
            
            System.out.println("\n=== SYSTEM READY FOR CONTINUOUS OPERATION ===");
            System.out.println("✓ Agents are running continuously");
            System.out.println("✓ Depot Agent: Polling API at http://localhost:8000/api/solve-cvrp");
            System.out.println("✓ MRA Agent: Waiting for problem data");
            System.out.println("✓ Both agents will process requests indefinitely");
            System.out.println("\nAPI Integration:");
            System.out.println("  - Depot polls API every 2 seconds for new requests");
            System.out.println("  - Solutions are sent back to the same API endpoint");
            System.out.println("  - Frontend can send requests to: http://localhost:8000/api/solve-cvrp");
            System.out.println("===============================================\n");
            
        } catch (StaleProxyException e) {
            System.err.println("Error creating agents: " + e.getMessage());
            e.printStackTrace();
        }
	}
}

