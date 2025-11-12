package project;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import project.Utils.JsonConfigReader;
import project.Utils.JsonConfigReader.CVRPConfig;

import java.io.File;

/**
 * Main entry point for the CVRP Multi-Agent System
 * Creates and starts MRA (Master Routing Agent) and DAs (Delivery Agents)
 * Agents discover each other via DF (Yellow Pages) using service types
 * Reads configuration from JSON file
 */
public class Main {
    private static final String DEFAULT_CONFIG = "config/case_capacity_shortfall.json";
    
    public static void main(String[] args) {
        System.out.println("===============================================");
        System.out.println("  CVRP MULTI-AGENT SYSTEM - STARTING");
        System.out.println("===============================================\n");
        
        // Determine config file
        String configFile = DEFAULT_CONFIG;
        if (args.length > 0) {
            configFile = args[0];
        }
        
        // Read configuration from JSON file
        CVRPConfig config;
        try {
            System.out.println("Reading configuration from: " + configFile);
            config = JsonConfigReader.readConfig(configFile);
            System.out.println("✓ Configuration loaded successfully\n");
        } catch (Exception e) {
            System.err.println("ERROR: Failed to read configuration file: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        
        // Extract config name from filename
        String configName = new File(configFile).getName().replace(".json", "");
        
        // Validate configuration
        if (config.depot == null) {
            System.err.println("ERROR: Invalid configuration - depot not found!");
            return;
        }
        if (config.vehicles == null || config.vehicles.isEmpty()) {
            System.err.println("ERROR: Invalid configuration - no vehicles found!");
            return;
        }
        if (config.customers == null || config.customers.isEmpty()) {
            System.err.println("ERROR: Invalid configuration - no customers found!");
            return;
        }
        
        System.out.println("\n=== Configuration Summary ===");
        System.out.println("Depot: " + config.depot.name + " at (" + config.depot.x + ", " + config.depot.y + ")");
        System.out.println("Vehicles: " + config.vehicles.size());
        for (JsonConfigReader.VehicleConfig v : config.vehicles) {
            System.out.println("  - " + v.name + " (capacity: " + v.capacity + ", maxDistance: " + v.maxDistance + ")");
        }
        System.out.println("Customers: " + config.customers.size());
        int totalDemand = 0;
        for (JsonConfigReader.CustomerConfig c : config.customers) {
            System.out.println("  - " + c.id + " (demand: " + c.demand + ") at (" + c.x + ", " + c.y + ")");
            totalDemand += c.demand;
        }
        int totalCapacity = config.vehicles.stream().mapToInt(v -> v.capacity).sum();
        System.out.println("\nTotal Demand: " + totalDemand);
        System.out.println("Total Capacity: " + totalCapacity);
        if (totalDemand > totalCapacity) {
            System.out.println("⚠ WARNING: Total demand exceeds total capacity (edge case)");
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
            // Create and start MRA (Master Routing Agent)
            Object[] mraArgs = new Object[]{config, configName};
            AgentController mraController = mainContainer.createNewAgent(
                config.depot.name, 
                "project.Agent.MasterRoutingAgent", 
                mraArgs
            );
            mraController.start();
            System.out.println("✓ Master Routing Agent (MRA) started: " + config.depot.name);
            
            // Wait a moment for MRA to initialize and register with DF
            Thread.sleep(1000);
            
            // Create and start Delivery Agents (DAs) from configuration
            for (JsonConfigReader.VehicleConfig vehicleConfig : config.vehicles) {
                Object[] daArgs = new Object[]{
                    vehicleConfig.name, 
                    vehicleConfig.capacity, 
                    vehicleConfig.maxDistance
                };
                AgentController daController = mainContainer.createNewAgent(
                    vehicleConfig.name, 
                    "project.Agent.DeliveryAgent", 
                    daArgs
                );
                daController.start();
                System.out.println("✓ Delivery Agent (DA) started: " + vehicleConfig.name + 
                                 " (capacity: " + vehicleConfig.capacity + 
                                 ", maxDistance: " + vehicleConfig.maxDistance + ")");
            }
            
            // Wait for all agents to initialize
            Thread.sleep(2000);
            
            System.out.println("\n===============================================");
            System.out.println("  CVRP MULTI-AGENT SYSTEM READY");
            System.out.println("===============================================");
            System.out.println("Agents:");
            System.out.println("  • Master Routing Agent (MRA): Solves CVRP and assigns routes");
            System.out.println("  • Delivery Agents (DAs): Execute assigned routes");
            System.out.println("\nDiscovery:");
            System.out.println("  • All agents registered with DF (Yellow Pages)");
            System.out.println("  • MRA finds DAs via 'da-service' type");
            System.out.println("  • DAs find MRA via 'mra-service' type");
            System.out.println("\nWorkflow:");
            System.out.println("  1. MRA reads problem from config (customers with id, demand, coordinates)");
            System.out.println("  2. MRA queries DAs for vehicle information (capacity, maxDistance)");
            System.out.println("  3. MRA solves CVRP using Google OR-Tools:");
            System.out.println("     - Prioritizes packages delivered over distance");
            System.out.println("     - Handles cases where demand > capacity");
            System.out.println("  4. MRA assigns routes to DAs via FIPA-Request");
            System.out.println("  5. DAs execute routes and return to depot");
            System.out.println("  6. Results logged as JSON to results/ directory");
            System.out.println("\nAll communications follow FIPA protocols (FIPA-Request)");
            System.out.println("===============================================\n");
            
        } catch (Exception e) {
            System.err.println("ERROR: Error creating agents: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
