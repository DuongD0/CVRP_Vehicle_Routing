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

/**
 * Client for communicating with Python Flask backend server
 * Handles polling for requests and submitting solutions
 */
public class BackendClient {
    public static final String BACKEND_URL = "http://localhost:8000";
    private static final int POLL_INTERVAL_MS = 2000; // Poll every 2 seconds
    private static final Gson gson = new Gson();
    
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
        
        // Read depot
        JsonObject depotObj = backendRequest.getAsJsonObject("depot");
        config.depot = new DepotConfig();
        config.depot.name = depotObj.get("name").getAsString();
        config.depot.x = depotObj.get("x").getAsDouble();
        config.depot.y = depotObj.get("y").getAsDouble();
        
        // Read vehicles
        config.vehicles = new ArrayList<>();
        JsonArray vehiclesArray = backendRequest.getAsJsonArray("vehicles");
        if (vehiclesArray == null) {
            throw new IllegalArgumentException("Backend request missing 'vehicles' array");
        }
        
        System.out.println("BackendClient: Reading " + vehiclesArray.size() + " vehicles from API");
        for (int i = 0; i < vehiclesArray.size(); i++) {
            JsonObject vehicleObj = vehiclesArray.get(i).getAsJsonObject();
            VehicleConfig vehicle = new VehicleConfig();
            
            // Read vehicle name (required field)
            if (!vehicleObj.has("name")) {
                throw new IllegalArgumentException("Vehicle " + (i + 1) + " missing 'name' field");
            }
            vehicle.name = vehicleObj.get("name").getAsString();
            
            // Read capacity (required field)
            if (!vehicleObj.has("capacity")) {
                throw new IllegalArgumentException("Vehicle " + (i + 1) + " ('" + vehicle.name + "') missing 'capacity' field");
            }
            vehicle.capacity = vehicleObj.get("capacity").getAsInt();
            
            // Read maxDistance (required field)
            if (!vehicleObj.has("maxDistance")) {
                throw new IllegalArgumentException("Vehicle " + (i + 1) + " ('" + vehicle.name + "') missing 'maxDistance' field");
            }
            vehicle.maxDistance = vehicleObj.get("maxDistance").getAsDouble();
            
            System.out.println("BackendClient: Vehicle " + (i + 1) + " - name='" + vehicle.name + 
                             "', capacity=" + vehicle.capacity + ", maxDistance=" + vehicle.maxDistance);
            config.vehicles.add(vehicle);
        }
        
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
            
            // Read optional time window
            if (customerObj.has("timeWindow")) {
                JsonArray timeWindowArray = customerObj.getAsJsonArray("timeWindow");
                if (timeWindowArray.size() >= 2) {
                    customer.timeWindow = new long[2];
                    customer.timeWindow[0] = timeWindowArray.get(0).getAsLong();
                    customer.timeWindow[1] = timeWindowArray.get(1).getAsLong();
                }
            }
            
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
}

