package project;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import project.Utils.JsonConfigReader;
import project.Utils.JsonConfigReader.CVRPConfig;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private static Map<String, VehicleSpec> activeDAs = new HashMap<>();  // Track created DAs and their specs

    private static class VehicleSpec {
        final int capacity;
        final double maxDistance;

        VehicleSpec(int capacity, double maxDistance) {
            this.capacity = capacity;
            this.maxDistance = maxDistance;
        }

        static VehicleSpec fromConfig(JsonConfigReader.VehicleConfig config) {
            return new VehicleSpec(config.capacity, config.maxDistance);
        }

        boolean matches(JsonConfigReader.VehicleConfig config) {
            return config != null &&
                   this.capacity == config.capacity &&
                   Double.compare(this.maxDistance, config.maxDistance) == 0;
        }
    }
    
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
            boolean mraExists = false;
            if (persistentMRA != null) {
                try {
                    // Try to get the agent to verify it's still alive
                    jade.wrapper.AgentController existing = getMainContainer().getAgent(mraName);
                    if (existing != null) {
                        System.out.println("  MRA already exists, keeping it");
                        mraExists = true;
                    }
                } catch (Exception e) {
                    // MRA doesn't exist
                    persistentMRA = null;
                }
            } else {
                // Check if MRA exists even though we don't have a reference
                try {
                    jade.wrapper.AgentController existing = getMainContainer().getAgent(mraName);
                    if (existing != null) {
                        System.out.println("  MRA already exists, keeping it");
                        persistentMRA = existing;
                        mraExists = true;
                    }
                } catch (Exception e) {
                    // MRA doesn't exist, will create it
                }
            }
            
            if (mraExists) {
                return;
            }
            
            // If MRA doesn't exist but there's a stale reference, try to terminate it first
            if (persistentMRA != null) {
                try {
                    System.out.println("  Terminating stale MRA reference...");
                    persistentMRA.kill();
                    Thread.sleep(500);
                } catch (Exception e) {
                    // Ignore errors - agent might already be gone
                }
                persistentMRA = null;
            }
            
            // Also check if agent exists in container and terminate it
            try {
                jade.wrapper.AgentController existing = getMainContainer().getAgent(mraName);
                if (existing != null) {
                    System.out.println("  Terminating existing MRA before creating new one...");
                    existing.kill();
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                // Agent doesn't exist, which is fine
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
    public static synchronized void ensureDAsExist(List<JsonConfigReader.VehicleConfig> vehicles) {
        try {
            System.out.println("=== Ensuring Delivery Agents Exist ===");

            Map<String, JsonConfigReader.VehicleConfig> desiredVehicles = new HashMap<>();
            for (JsonConfigReader.VehicleConfig config : vehicles) {
                desiredVehicles.put(config.name, config);
            }

            // Stop agents that are no longer present or whose specs have changed
            Set<String> existingAgents = new HashSet<>(activeDAs.keySet());
            for (String existingName : existingAgents) {
                JsonConfigReader.VehicleConfig desiredConfig = desiredVehicles.get(existingName);
                VehicleSpec currentSpec = activeDAs.get(existingName);

                if (desiredConfig == null) {
                    stopDeliveryAgent(existingName, "removed from configuration");
                } else if (currentSpec == null || !currentSpec.matches(desiredConfig)) {
                    stopDeliveryAgent(existingName, "specification changed");
                }
            }

            // Create or reuse agents based on desired configuration
            for (JsonConfigReader.VehicleConfig vehicleConfig : vehicles) {
                VehicleSpec currentSpec = activeDAs.get(vehicleConfig.name);
                if (currentSpec != null && currentSpec.matches(vehicleConfig)) {
                    System.out.println("  DA '" + vehicleConfig.name + "' already running with matching specs");
                    continue;
                }

                startDeliveryAgent(vehicleConfig);
            }

            System.out.println("================================\n");
        } catch (Exception e) {
            System.err.println("Error ensuring DAs exist: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void stopDeliveryAgent(String daName, String reason) {
        try {
            System.out.println("  Stopping DA '" + daName + "' (" + reason + ")");
            AgentController controller = getMainContainer().getAgent(daName);
            if (controller != null) {
                controller.kill();
                // Wait a bit for the agent to fully terminate
                Thread.sleep(500);
                System.out.println("  ✓ DA '" + daName + "' terminated");
            }
        } catch (Exception e) {
            System.out.println("  DA '" + daName + "' already stopped or not found (" + reason + ")");
        } finally {
            activeDAs.remove(daName);
        }
    }

    private static void startDeliveryAgent(JsonConfigReader.VehicleConfig vehicleConfig) throws Exception {
        String daName = vehicleConfig.name;
        
        // First, check if agent already exists and terminate it if necessary
        boolean agentExists = false;
        try {
            AgentController existing = getMainContainer().getAgent(daName);
            if (existing != null) {
                agentExists = true;
                System.out.println("  DA '" + daName + "' already exists, terminating it first...");
                try {
                    existing.kill();
                    // Wait for the agent to fully terminate, with retries
                    for (int i = 0; i < 5; i++) {
                        Thread.sleep(200);
                        try {
                            AgentController check = getMainContainer().getAgent(daName);
                            if (check == null) {
                                break; // Agent is gone
                            }
                        } catch (Exception e) {
                            break; // Agent is gone (exception means not found)
                        }
                    }
                } catch (Exception e) {
                    System.out.println("  Warning: Error terminating existing DA '" + daName + "': " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // Agent doesn't exist, which is fine - we'll create it
        }
        
        // Double-check that agent is gone before creating new one
        if (agentExists) {
            try {
                AgentController verify = getMainContainer().getAgent(daName);
                if (verify != null) {
                    System.err.println("  ERROR: DA '" + daName + "' still exists after termination attempt. Retrying...");
                    verify.kill();
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                // Good - agent is gone
            }
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
        activeDAs.put(daName, VehicleSpec.fromConfig(vehicleConfig));
        System.out.println("  ✓ DA '" + daName + "' started");
    }
}
