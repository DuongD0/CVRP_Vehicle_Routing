package project;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

/**
 * Main entry point for the VRP Multi-Agent System
 * Creates and starts all agents - agents discover each other via DF (Yellow Pages)
 */
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
            System.out.println("✓ Depot Agent started: depot-agent");
            
            // Wait a moment for Depot to initialize and register with DF
            Thread.sleep(1000);
            
            // Create and start vehicle agents
            String[] vehicleNames = {"Vehicle1", "Vehicle2", "Vehicle3"};
            int[] capacities = {50, 40, 30};
            double[] maxDistances = {1000.0, 800.0, 600.0};  // Maximum distances for each vehicle (Basic Requirement 2)
            
            for (int i = 0; i < vehicleNames.length; i++) {
                Object[] vehicleArgs = new Object[]{vehicleNames[i], capacities[i], maxDistances[i]};
                AgentController vehicleController = mainContainer.createNewAgent(
                    "vehicle-" + vehicleNames[i], 
                    "project.Agent.VehicleAgent", 
                    vehicleArgs
                );
                vehicleController.start();
                System.out.println("✓ Vehicle Agent started: vehicle-" + vehicleNames[i] + 
                                 " (capacity: " + capacities[i] + ", maxDistance: " + maxDistances[i] + ")");
            }
            
            // Wait for vehicles to initialize and register with DF
            Thread.sleep(2000);
            
            // Create and start customer agents
            String[] customerIds = {"customer-1", "customer-2", "customer-3", "customer-4"};
            String[] customerNames = {"Customer1", "Customer2", "Customer3", "Customer4"};
            double[][] customerPositions = {
                {100.0, 150.0},
                {200.0, 100.0},
                {150.0, 200.0},
                {250.0, 180.0}
            };
            
            for (int i = 0; i < customerIds.length; i++) {
                Object[] customerArgs = new Object[]{
                    customerIds[i],
                    customerNames[i],
                    customerPositions[i][0],
                    customerPositions[i][1]
                };
                AgentController customerController = mainContainer.createNewAgent(
                    customerIds[i],
                    "project.Agent.Customer",
                    customerArgs
                );
                customerController.start();
                System.out.println("✓ Customer Agent started: " + customerIds[i] + 
                                 " at (" + customerPositions[i][0] + ", " + customerPositions[i][1] + ")");
            }
            
            // Wait for all agents to initialize
            Thread.sleep(2000);
            
            System.out.println("\n===============================================");
            System.out.println("  MULTI-AGENT VRP SYSTEM READY");
            System.out.println("===============================================");
            System.out.println("Agents:");
            System.out.println("  • Depot Agent: Manages inventory and routes vehicles");
            System.out.println("  • Vehicle Agents: Independent agents that bid for routes");
            System.out.println("  • Customer Agents: Send item requests to depot");
            System.out.println("\nDiscovery:");
            System.out.println("  • All agents registered with DF (Yellow Pages)");
            System.out.println("  • Agents discover each other automatically via DF");
            System.out.println("\nWorkflow:");
            System.out.println("  1. Customer agents find depot via DF and send requests");
            System.out.println("  2. Depot checks inventory and queues requests");
            System.out.println("  3. Depot batches requests and solves VRP with constraints:");
            System.out.println("     - Basic Requirement 1: Prioritizes items delivered over distance");
            System.out.println("     - Basic Requirement 2: Enforces maximum distance per vehicle");
            System.out.println("  4. Depot finds vehicles via DF and sends routes via Contract-Net");
            System.out.println("  5. Vehicles bid based on current position, capacity, and max distance");
            System.out.println("  6. Depot assigns routes to winning vehicles");
            System.out.println("  7. Vehicles complete routes and return to free state");
            System.out.println("\nAll communications follow FIPA protocols");
            System.out.println("===============================================\n");
            
        } catch (Exception e) {
            System.err.println("Error creating agents: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
