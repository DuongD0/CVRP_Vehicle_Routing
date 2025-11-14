package project;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import project.Utils.JsonConfigReader;
import project.Utils.JsonConfigReader.CVRPConfig;
import project.Utils.BackendClient;
import project.Utils.BackendClient.BackendRequest;
import project.Utils.AgentLogger;
import project.General.SolutionResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Main entry point for the CVRP Multi-Agent System
 * Continuously polls the Python Flask backend for CVRP requests,
 * processes them using JADE agents, and submits solutions back to the backend.
 * 
 * This system only operates in backend API mode - no local file processing.
 */
public class Main {
    private static volatile boolean running = true;
    private static AgentContainer mainContainer;
    
    public static void main(String[] args) {
        runBackendMode();
    }
    
    /**
     * Runs in backend polling mode - continuously polls backend for requests
     */
    private static void runBackendMode() {
        System.out.println("===============================================");
        System.out.println("  CVRP MULTI-AGENT SYSTEM - BACKEND MODE");
        System.out.println("===============================================\n");
        System.out.println("Connecting to backend at: " + BackendClient.BACKEND_URL);
        System.out.println("Polling interval: " + BackendClient.getPollInterval() + " ms");
        System.out.println("Press Ctrl+C to stop\n");
        
        // Initialize JADE runtime
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.MAIN_PORT, "1099");
        p.setParameter(Profile.GUI, "false"); // Disable GUI in backend mode
        
        mainContainer = rt.createMainContainer(p);
        
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
        
        // Main polling loop
        while (running) {
            try {
                // Poll backend for requests
                BackendRequest request = BackendClient.pollForRequest();
                
                if (request != null) {
                    System.out.println("\n=== New CVRP Request Received ===");
                    System.out.println("Request ID: " + request.requestId);
                    
                    // Process the request
                    processBackendRequest(request);
                } else {
                    // No pending requests, wait before next poll
                    Thread.sleep(BackendClient.getPollInterval());
                }
            } catch (InterruptedException e) {
                System.out.println("Polling interrupted");
                break;
            } catch (Exception e) {
                System.err.println("Error in polling loop: " + e.getMessage());
                e.printStackTrace();
                try {
                    Thread.sleep(BackendClient.getPollInterval());
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        
        System.out.println("Backend main loop stopped");
    }
    
    /**
     * Processes a CVRP request from the backend
     */
    private static void processBackendRequest(BackendRequest request) {
        try {
            // Reset log folder for this new request/conversation
            // This ensures each request gets its own timestamped folder
            AgentLogger.resetLogFolder();
            
            // DEBUG: Print raw backend request
            System.out.println("\n=== DEBUG: Raw Backend Request ===");
            System.out.println("Request ID: " + request.requestId);
            System.out.println("Request Data (JSON):");
            // Pretty print the JSON data
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            System.out.println(gson.toJson(request.data));
            System.out.println("=====================================\n");
            
            // Convert backend request to CVRPConfig
            CVRPConfig config = BackendClient.convertBackendRequestToConfig(request.data);
            
            System.out.println("Problem: " + config.customers.size() + " customers, " + 
                             config.vehicles.size() + " vehicles");
            
            // Debug: Print vehicle names from API
            System.out.println("\n=== Vehicles from API ===");
            for (int i = 0; i < config.vehicles.size(); i++) {
                JsonConfigReader.VehicleConfig v = config.vehicles.get(i);
                System.out.println("  Vehicle " + (i + 1) + ": name='" + v.name + 
                                 "', capacity=" + v.capacity + ", maxDistance=" + v.maxDistance);
            }
            System.out.println("==========================\n");
            
            // Create a latch to wait for solution
            CountDownLatch solutionLatch = new CountDownLatch(1);
            SolutionHolder solutionHolder = new SolutionHolder();
            
            // Create MRA with callback for solution
            Object[] mraArgs = new Object[]{config, request.requestId, solutionLatch, solutionHolder};
            AgentController mraController = mainContainer.createNewAgent(
                "mra-" + request.requestId,
                "project.Agent.MasterRoutingAgent",
                mraArgs
            );
            mraController.start();
            System.out.println("✓ Master Routing Agent started");
            
            // Wait for MRA to initialize
            Thread.sleep(1000);
            
            // Create Delivery Agents
            System.out.println("=== Creating Delivery Agents ===");
            for (JsonConfigReader.VehicleConfig vehicleConfig : config.vehicles) {
                String daName = vehicleConfig.name + "-" + request.requestId;
                Object[] daArgs = new Object[]{
                    daName,
                    vehicleConfig.capacity,
                    vehicleConfig.maxDistance
                };
                
                System.out.println("Creating DA: name='" + daName + 
                                 "' (from API: '" + vehicleConfig.name + 
                                 "', requestId: '" + request.requestId + "')" +
                                 ", capacity=" + vehicleConfig.capacity + 
                                 ", maxDistance=" + vehicleConfig.maxDistance);
                
                AgentController daController = mainContainer.createNewAgent(
                    daName,
                    "project.Agent.DeliveryAgent",
                    daArgs
                );
                daController.start();
                System.out.println("  ✓ DA '" + daName + "' started");
            }
            System.out.println("================================\n");
            
            System.out.println("✓ All agents started, waiting for solution...");
            
            // Wait for solution (with timeout)
            boolean completed = solutionLatch.await(60, TimeUnit.SECONDS);
            
            if (completed && solutionHolder.solution != null) {
                // Solution is already submitted to backend by MRA
                // Routes have also been assigned to vehicles by MRA (before signaling completion)
                System.out.println("\n=== Solution Ready ===");
                System.out.println("✓ Solution received from MRA");
                System.out.println("  Routes: " + solutionHolder.solution.routes.size());
                System.out.println("  Items Delivered: " + solutionHolder.solution.itemsDelivered + "/" + solutionHolder.solution.itemsTotal);
                System.out.println("  Total Distance: " + String.format("%.2f", solutionHolder.solution.totalDistance));
                System.out.println("  Unserved Customers: " + solutionHolder.solution.unservedCustomers.size());
                System.out.println("✓ Solution submitted to backend");
                System.out.println("✓ Routes assigned to vehicles");
                System.out.println("  (Route assignment communication logged in timestamped log folder)");
            } else {
                System.err.println("✗ Solution timeout or error for request " + request.requestId);
                // Solution should have been submitted by MRA even on error, but log it
                if (solutionHolder.solution == null) {
                    // Submit empty solution to indicate failure
                    SolutionResult errorResult = new SolutionResult();
                    errorResult.itemsTotal = config.customers.stream().mapToInt(c -> c.demand).sum();
                    BackendClient.submitSolution(request.requestId, errorResult, request.requestId);
                }
            }
            
            // Wait a bit before cleanup to allow route assignment responses to be fully logged
            // Route assignments have already been sent by MRA before signaling completion
            // This ensures all route assignment messages and responses are logged
            System.out.println("\n=== Waiting for Route Assignment Responses to be Logged ===");
            System.out.println("Waiting 3 seconds for route assignment responses to complete logging...");
            try {
                Thread.sleep(3000); // Wait 3 seconds for final route assignment responses to be logged
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("✓ Route assignment communication logging completed");
            
            // Clean up agents after route assignment has completed
            System.out.println("\n=== Cleaning Up Agents ===");
            try {
                System.out.println("Terminating MRA...");
                mraController.kill();
                System.out.println("✓ MRA terminated");
                
                for (JsonConfigReader.VehicleConfig vehicleConfig : config.vehicles) {
                    try {
                        String daName = vehicleConfig.name + "-" + request.requestId;
                        System.out.println("Terminating DA: " + daName);
                        mainContainer.getAgent(daName).kill();
                        System.out.println("  ✓ DA " + daName + " terminated");
                    } catch (Exception e) {
                        System.err.println("  ✗ Failed to terminate DA " + vehicleConfig.name + "-" + request.requestId + ": " + e.getMessage());
                        // Ignore cleanup errors
                    }
                }
                System.out.println("✓ All agents terminated");
            } catch (Exception e) {
                System.err.println("Error terminating agents: " + e.getMessage());
                e.printStackTrace();
            }
            
            System.out.println("Request processing complete\n");
            
        } catch (Exception e) {
            System.err.println("Error processing request: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Holder class for solution result
     */
    private static class SolutionHolder {
        SolutionResult solution = null;
    }
}
