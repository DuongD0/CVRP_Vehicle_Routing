package project.Utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import project.General.SolutionResult;
import project.General.RouteInfo;
import project.General.CustomerInfo;
import project.Utils.JsonConfigReader.CVRPConfig;
import project.Utils.JsonConfigReader.DepotConfig;
import project.Utils.JsonConfigReader.CustomerConfig;
import project.Utils.JsonConfigReader.VehicleConfig;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for communicating with Python Flask backend server
 * Handles polling for requests and submitting solutions
 */
public class BackendClient {
    public static final String BACKEND_URL = "http://localhost:8000";
    private static final int POLL_INTERVAL_MS = 2000; // Poll every 2 seconds
    private static final Gson gson = new Gson();
    
    /**
     * Polls the backend for vehicle configuration
     * @return Vehicle configuration data, or null if not available
     */
    public static VehicleConfigResponse pollForVehicleConfig() {
        try {
            URL url = new URL(BACKEND_URL + "/api/vehicles/poll-config");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 204) {
                // No Content - no vehicle config available
                return null;
            } else if (responseCode == 200) {
                // Vehicle config available
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
                    VehicleConfigResponse config = new VehicleConfigResponse();
                    
                    // Parse vehicles
                    JsonArray vehiclesArray = json.getAsJsonArray("vehicles");
                    config.vehicles = new ArrayList<>();
                    for (int i = 0; i < vehiclesArray.size(); i++) {
                        JsonObject v = vehiclesArray.get(i).getAsJsonObject();
                        VehicleConfig vehicle = new VehicleConfig();
                        vehicle.name = v.get("name").getAsString();
                        vehicle.capacity = v.get("capacity").getAsInt();
                        vehicle.maxDistance = v.get("maxDistance").getAsDouble();
                        config.vehicles.add(vehicle);
                    }
                    
                    // Parse depot
                    JsonObject depotJson = json.getAsJsonObject("depot");
                    config.depot = new DepotConfig();
                    config.depot.name = depotJson.get("name").getAsString();
                    config.depot.x = depotJson.get("x").getAsDouble();
                    config.depot.y = depotJson.get("y").getAsDouble();
                    
                    return config;
                }
            } else {
                System.err.println("Backend returned error code: " + responseCode);
                return null;
            }
        } catch (Exception e) {
            // Silently handle errors (backend might not be running or no config available)
            return null;
        }
    }
    
    /**
     * Response class for vehicle configuration
     */
    public static class VehicleConfigResponse {
        public List<VehicleConfig> vehicles;
        public DepotConfig depot;
    }
    
    /**
     * Polls the backend for pending CVRP requests
     * @return Request data with request_id, or null if no pending requests
     */
    public static BackendRequest pollForRequest() {
        try {
            URL url = new URL(BACKEND_URL + "/api/solve-cvrp?action=poll");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 204) {
                // No Content - no pending requests
                return null;
            } else if (responseCode == 200) {
                // Request available
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JsonObject jsonResponse = gson.fromJson(response.toString(), JsonObject.class);
                    String requestId = jsonResponse.get("request_id").getAsString();
                    JsonObject requestData = jsonResponse.getAsJsonObject("data");
                    
                    return new BackendRequest(requestId, requestData);
                }
            } else {
                System.err.println("Backend polling error: HTTP " + responseCode);
                return null;
            }
        } catch (Exception e) {
            // Silently return null on error (backend might not be running)
            return null;
        }
    }
    
    /**
     * Converts backend request format (new config format) to CVRPConfig
     */
    public static CVRPConfig convertBackendRequestToConfig(JsonObject backendRequest) {
        CVRPConfig config = new CVRPConfig();
        
        // Depot is NOT in requests - it's set when vehicles are confirmed and MRA is created
        // Use default depot (0,0) - MRA already has the correct depot from its creation
        config.depot = new DepotConfig();
        config.depot.name = "Depot";
        config.depot.x = 0.0;
        config.depot.y = 0.0;
        
        // Vehicles are NOT in requests - DAs are created separately when vehicles are confirmed
        config.vehicles = new ArrayList<>();
        
        // Read customers
        config.customers = new ArrayList<>();
        JsonArray customersArray = backendRequest.getAsJsonArray("customers");
        for (int i = 0; i < customersArray.size(); i++) {
            JsonObject customerObj = customersArray.get(i).getAsJsonObject();
            CustomerConfig customer = new CustomerConfig();
            customer.id = customerObj.get("id").getAsString();
            customer.x = customerObj.get("x").getAsDouble();
            customer.y = customerObj.get("y").getAsDouble();
            customer.demand = customerObj.get("demand").getAsInt();
            
            config.customers.add(customer);
        }
        
        return config;
    }
    
    /**
     * Submits solution to backend in the new result format
     */
    public static boolean submitSolution(String requestId, SolutionResult solution, String configName) {
        try {
            // Convert SolutionResult to backend format (matches JSON result format)
            JsonObject solutionJson = new JsonObject();
            solutionJson.addProperty("request_id", requestId);
            solutionJson.addProperty("timestamp", new java.util.Date().toString());
            solutionJson.addProperty("configName", configName != null ? configName : "backend_request");
            solutionJson.addProperty("solveTimeMs", solution.solveTimeMs);
            
            // Summary
            JsonObject summary = new JsonObject();
            summary.addProperty("totalItemsRequested", solution.itemsTotal);
            summary.addProperty("totalItemsDelivered", solution.itemsDelivered);
            summary.addProperty("totalDistance", solution.totalDistance);
            summary.addProperty("numberOfRoutes", solution.routes.size());
            summary.addProperty("deliveryRate", solution.itemsTotal > 0 ? 
                (double) solution.itemsDelivered / solution.itemsTotal : 0.0);
            summary.addProperty("unservedCustomers", solution.unservedCustomers.size());
            solutionJson.add("summary", summary);
            
            // Routes
            JsonArray routesArray = new JsonArray();
            for (RouteInfo route : solution.routes) {
                JsonObject routeJson = new JsonObject();
                routeJson.addProperty("routeId", route.vehicleId);
                routeJson.addProperty("vehicleName", route.vehicleName != null ? route.vehicleName : "unknown");
                routeJson.addProperty("totalDemand", route.totalDemand);
                routeJson.addProperty("totalDistance", route.totalDistance);
                
                JsonArray customersArray = new JsonArray();
                for (CustomerInfo customer : route.customers) {
                    JsonObject customerJson = new JsonObject();
                    customerJson.addProperty("id", customer.id);
                    customerJson.addProperty("name", customer.name != null ? customer.name : "C" + customer.id);
                    customerJson.addProperty("x", customer.x);
                    customerJson.addProperty("y", customer.y);
                    customerJson.addProperty("demand", customer.demand);
                    customersArray.add(customerJson);
                }
                routeJson.add("customers", customersArray);
                routesArray.add(routeJson);
            }
            solutionJson.add("routes", routesArray);
            
            // Unserved customers
            JsonArray unservedArray = new JsonArray();
            for (CustomerInfo customer : solution.unservedCustomers) {
                JsonObject customerJson = new JsonObject();
                customerJson.addProperty("id", customer.id);
                customerJson.addProperty("name", customer.name != null ? customer.name : "C" + customer.id);
                customerJson.addProperty("x", customer.x);
                customerJson.addProperty("y", customer.y);
                customerJson.addProperty("demand", customer.demand);
                unservedArray.add(customerJson);
            }
            solutionJson.add("unservedCustomers", unservedArray);
            
            URL url = new URL(BACKEND_URL + "/api/solve-cvrp?action=response");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            // Write request body
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = gson.toJson(solutionJson).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                System.out.println("✓ Solution submitted successfully to backend");
                return true;
            } else {
                System.err.println("Backend submission error: HTTP " + responseCode);
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder error = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line);
                    }
                    System.err.println("Error response: " + error.toString());
                }
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error submitting solution to backend: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Wrapper class for backend request
     */
    public static class BackendRequest {
        public final String requestId;
        public final JsonObject data;
        
        public BackendRequest(String requestId, JsonObject data) {
            this.requestId = requestId;
            this.data = data;
        }
    }
    
    /**
     * Gets the poll interval in milliseconds
     */
    public static int getPollInterval() {
        return POLL_INTERVAL_MS;
    }
    
    /**
     * Updates vehicle position in the backend movement tracking API
     * @param vehicleName Name of the vehicle
     * @param x Current X coordinate
     * @param y Current Y coordinate
     * @param status Vehicle status (idle, moving, at_customer, at_depot)
     * @param routeId Current route ID (if executing a route)
     * @param targetX Target X coordinate (if moving)
     * @param targetY Target Y coordinate (if moving)
     * @return true if update was successful, false otherwise
     */
    public static boolean updateVehiclePosition(String vehicleName, double x, double y, 
                                                String status, String routeId, 
                                                Double targetX, Double targetY) {
        try {
            URL url = new URL(BACKEND_URL + "/api/movement/update");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            
            // Build JSON request
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("vehicle_name", vehicleName);
            requestJson.addProperty("x", x);
            requestJson.addProperty("y", y);
            requestJson.addProperty("status", status);
            if (routeId != null) {
                requestJson.addProperty("route_id", routeId);
            }
            if (targetX != null) {
                requestJson.addProperty("target_x", targetX);
            }
            if (targetY != null) {
                requestJson.addProperty("target_y", targetY);
            }
            
            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = gson.toJson(requestJson).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                return true;
            } else {
                // Silently fail - backend might not be running or endpoint might not be available
                return false;
            }
        } catch (Exception e) {
            // Silently fail - backend might not be running
            return false;
        }
    }
    
    /**
     * Reports agent status to the backend API
     * @param agentName Name of the agent
     * @param agentType Type of agent ('mra' or 'da')
     * @param status Status ('active', 'inactive', 'terminated')
     * @param info Additional agent information (depot coordinates for MRA, capacity/speed for DA)
     * @return true if update was successful, false otherwise
     */
    public static boolean reportAgentStatus(String agentName, String agentType, String status, JsonObject info) {
        try {
            URL url = new URL(BACKEND_URL + "/api/agents/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            
            // Build JSON request
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("agent_name", agentName);
            requestJson.addProperty("agent_type", agentType);
            requestJson.addProperty("status", status);
            if (info != null) {
                requestJson.add("info", info);
            }
            
            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = gson.toJson(requestJson).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                return true;
            } else {
                // Silently fail - backend might not be running
                return false;
            }
        } catch (Exception e) {
            // Silently fail - backend might not be running
            return false;
        }
    }
    
    /**
     * Convenience method for reporting agent status without additional info
     */
    public static boolean reportAgentStatus(String agentName, String agentType, String status) {
        return reportAgentStatus(agentName, agentType, status, null);
    }
}

