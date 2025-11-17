package project;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import project.Utils.JsonConfigReader;
import project.Utils.JsonConfigReader.CVRPConfig;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Main entry point for the CVRP Multi-Agent System
 * Initializes the JADE platform and waits for vehicle confirmation from frontend.
 * MRA and DAs are created only after vehicles are confirmed, not at startup.
 * The MRA polls the backend API directly for requests and processes them.
 * 
 * This system only operates in backend API mode - no local file processing.
 */
public class Main {
    private static volatile boolean running = true;
    private static AgentContainer mainContainer;  // Made accessible for MRA to create DAs
    private static AgentController persistentMRA = null;  // Single persistent MRA
    private static String mraName = "mra-persistent";  // Name of persistent MRA
    private static Set<String> createdDAs = new HashSet<>();  // Track created DAs by base name
    
    /**
     * Gets the main container (for MRA to create DAs)
     */
    public static AgentContainer getMainContainer() {
        return mainContainer;
    }
    
    public static void main(String[] args) {
        runBackendMode();
    }
    
    /**
     * Runs in backend mode - initializes agents and lets MRA handle requests directly from API
     */
    private static void runBackendMode() {
        System.out.println("===============================================");
        System.out.println("  CVRP MULTI-AGENT SYSTEM - BACKEND MODE");
        System.out.println("===============================================\n");
        System.out.println("Backend URL: http://localhost:8000");
        System.out.println("Initializing agents...\n");
        
        // Initialize JADE runtime
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.MAIN_PORT, "1099");
        p.setParameter(Profile.GUI, "false"); // Disable GUI in backend mode
        
        mainContainer = rt.createMainContainer(p);
        
        // Start polling for vehicle configuration to create MRA and DAs
        // MRA and DAs are NOT created at startup - only after vehicle confirmation
        startVehicleConfigPoller();
        
        System.out.println("\n✓ System initialized");
        System.out.println("  Waiting for vehicle confirmation from frontend...");
        System.out.println("  MRA and DAs will be created after vehicles are confirmed");
        System.out.println("\nPress Ctrl+C to stop\n");
        
        // Add shutdown hook
        java.lang.Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            running = false;
            try {
                if (mainContainer != null) {
                    mainContainer.kill();
                }
            } catch (Exception e) {
                System.err.println("Error shutting down: " + e.getMessage());
            }
        }));
        
        // Keep main thread alive (agents run in separate threads)
        try {
            while (running) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted");
        }
        
        System.out.println("System stopped");
    }
    
    
    /**
     * Starts a background thread to poll for vehicle configuration and create DAs
     */
    private static void startVehicleConfigPoller() {
        Thread pollerThread = new Thread(() -> {
            while (running) {
                try {
                    // Poll for vehicle configuration
                    project.Utils.BackendClient.VehicleConfigResponse config = 
                        project.Utils.BackendClient.pollForVehicleConfig();
                    
                    if (config != null && config.vehicles != null && !config.vehicles.isEmpty()) {
                        System.out.println("\n=== Received Vehicle Configuration ===");
                        System.out.println("Creating MRA and " + config.vehicles.size() + " delivery agents...");
                        
                        // Create MRA first (if not already exists)
                        ensureMRAExists(config.depot);
                        
                        // Create DAs from vehicle configuration
                        ensureDAsExist(config.vehicles);
                        
                        System.out.println("✓ All agents created and ready");
                        System.out.println("  MRA is now polling backend API for requests\n");
                    }
                    
                    // Poll every 2 seconds
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    System.out.println("Vehicle config poller interrupted");
                    break;
                } catch (Exception e) {
                    // Silently handle errors
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        });
        pollerThread.setDaemon(true);
        pollerThread.start();
    }
    
    /**
     * Creates MRA when vehicle configuration is received
     * MRA is NOT created at startup - only after vehicle confirmation
     */
    private static void ensureMRAExists(JsonConfigReader.DepotConfig depot) {
        try {
            // Check if MRA already exists
            if (persistentMRA != null) {
                try {
                    // Try to get the agent to verify it's still alive
                    jade.wrapper.AgentController existing = getMainContainer().getAgent(mraName);
                    if (existing != null) {
                        System.out.println("  MRA already exists, keeping it");
                        return;
                    }
                } catch (Exception e) {
                    // MRA doesn't exist, create it
                    persistentMRA = null;
                }
            }
            
            // Create MRA with depot configuration
            CVRPConfig mraConfig = new CVRPConfig();
            mraConfig.depot = depot;
            mraConfig.vehicles = new java.util.ArrayList<>();
            mraConfig.customers = new java.util.ArrayList<>();
            
            Object[] mraArgs = new Object[]{mraConfig, mraName};
            persistentMRA = getMainContainer().createNewAgent(
                mraName,
                "project.Agent.MasterRoutingAgent",
                mraArgs
            );
            persistentMRA.start();
            System.out.println("  ✓ MRA created: " + mraName);
            
            // Wait for MRA to initialize
            Thread.sleep(2000);
        } catch (Exception e) {
            System.err.println("Error creating MRA: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Creates delivery agents for the given vehicles
     * Called by MRA when it receives the first request, or by vehicle config poller
     */
    public static void ensureDAsExist(List<JsonConfigReader.VehicleConfig> vehicles) {
        try {
            System.out.println("=== Ensuring Delivery Agents Exist ===");
            for (JsonConfigReader.VehicleConfig vehicleConfig : vehicles) {
                String daName = vehicleConfig.name; // Use base name (DA1, DA2, etc.)
                
                // Check if DA already exists
                if (createdDAs.contains(daName)) {
                    System.out.println("  DA '" + daName + "' already exists, reusing it");
                    continue;
                }
                
                try {
                    // Try to get existing agent
                    jade.wrapper.AgentController existing = getMainContainer().getAgent(daName);
                    if (existing != null) {
                        System.out.println("  DA '" + daName + "' already exists, keeping it");
                        createdDAs.add(daName);
                        continue;
                    }
                } catch (Exception e) {
                    // Agent doesn't exist, create it
                }
                
                Object[] daArgs = new Object[]{
                    daName,
                    vehicleConfig.capacity,
                    vehicleConfig.maxDistance
                };
                
                System.out.println("Creating DA: name='" + daName + 
                                 "', capacity=" + vehicleConfig.capacity + 
                                 ", maxDistance=" + vehicleConfig.maxDistance);
                
                AgentController daController = getMainContainer().createNewAgent(
                    daName,
                    "project.Agent.DeliveryAgent",
                    daArgs
                );
                daController.start();
                createdDAs.add(daName);
                System.out.println("  ✓ DA '" + daName + "' started");
            }
            System.out.println("================================\n");
        } catch (Exception e) {
            System.err.println("Error ensuring DAs exist: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
