package project;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import project.Utils.ConfigReader;
import project.Utils.ConfigReader.AgentConfig;
import project.Utils.ConfigReader.VehicleConfig;
import project.Utils.ConfigReader.CustomerConfig;

/**
 * Main entry point for the VRP Multi-Agent System
 * Creates and starts all agents - agents discover each other via DF (Yellow Pages)
 * Reads configuration from web URL (if available) or local file as fallback
 */
public class Main {
    // Configuration: Web URL (set to null to disable) and local file path
    private static final String CONFIG_WEB_URL = System.getProperty("config.url", ""); // Can be set via -Dconfig.url=...
    private static final String CONFIG_LOCAL_FILE = "config/scenario_config.txt";
    
    public static void main(String[] args) {
        System.out.println("===============================================");
        System.out.println("  VRP MULTI-AGENT SYSTEM - STARTING");
        System.out.println("===============================================\n");
        
        // Read configuration from web or local file
        AgentConfig config = ConfigReader.readConfig(CONFIG_WEB_URL, CONFIG_LOCAL_FILE);
        
        if (config.vehicles.isEmpty() || config.customers.isEmpty()) {
            System.err.println("ERROR: Invalid configuration - no vehicles or customers found!");
            System.err.println("Please check the configuration file: " + CONFIG_LOCAL_FILE);
            return;
        }
        
        System.out.println("\n=== Configuration Loaded ===");
        System.out.println("Depot: " + config.depotName);
        System.out.println("Vehicles: " + config.vehicles.size());
        for (VehicleConfig v : config.vehicles) {
            System.out.println("  - " + v.name + " (capacity: " + v.capacity + ", maxDistance: " + v.maxDistance + ")");
        }
        System.out.println("Customers: " + config.customers.size());
        for (CustomerConfig c : config.customers) {
            System.out.println("  - " + c.id + " (" + c.name + ") at (" + c.x + ", " + c.y + ")");
        }
        System.out.println("============================\n");
        
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
                config.depotName, 
                "project.Agent.Depot", 
                null
            );
            depotController.start();
            System.out.println("✓ Depot Agent started: " + config.depotName);
            
            // Wait a moment for Depot to initialize and register with DF
            Thread.sleep(1000);
            
            // Create and start vehicle agents from configuration
            for (VehicleConfig vehicleConfig : config.vehicles) {
                Object[] vehicleArgs = new Object[]{
                    vehicleConfig.name, 
                    vehicleConfig.capacity, 
                    vehicleConfig.maxDistance
                };
                AgentController vehicleController = mainContainer.createNewAgent(
                    "vehicle-" + vehicleConfig.name, 
                    "project.Agent.VehicleAgent", 
                    vehicleArgs
                );
                vehicleController.start();
                System.out.println("✓ Vehicle Agent started: vehicle-" + vehicleConfig.name + 
                                 " (capacity: " + vehicleConfig.capacity + 
                                 ", maxDistance: " + vehicleConfig.maxDistance + ")");
            }
            
            // Wait for vehicles to initialize and register with DF
            Thread.sleep(2000);
            
            // Create and start customer agents from configuration
            for (CustomerConfig customerConfig : config.customers) {
                Object[] customerArgs = new Object[]{
                    customerConfig.id,
                    customerConfig.name,
                    customerConfig.x,
                    customerConfig.y
                };
                AgentController customerController = mainContainer.createNewAgent(
                    customerConfig.id,
                    "project.Agent.Customer",
                    customerArgs
                );
                customerController.start();
                System.out.println("✓ Customer Agent started: " + customerConfig.id + 
                                 " (" + customerConfig.name + ") at (" + 
                                 customerConfig.x + ", " + customerConfig.y + ")");
            }
            
            // Wait for all agents to initialize
            Thread.sleep(2000);
            
            System.out.println("\n===============================================");
            System.out.println("  MULTI-AGENT VRP SYSTEM READY");
            System.out.println("===============================================");
            System.out.println("Agents:");
            System.out.println("  • Depot Agent: Manages inventory and routes vehicles");
            System.out.println("  • Vehicle Agents: Receive routes directly from depot");
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
            System.out.println("  4. Depot sends routes directly to vehicles (routes already assigned by solver)");
            System.out.println("  5. Vehicles accept routes if vehicle ID matches, reject otherwise");
            System.out.println("  6. Vehicles complete routes with real movement and return to free state");
            System.out.println("\nAll communications follow FIPA protocols");
            System.out.println("===============================================\n");
            
        } catch (Exception e) {
            System.err.println("ERROR: Error creating agents: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
