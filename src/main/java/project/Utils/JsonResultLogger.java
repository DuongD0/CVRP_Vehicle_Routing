package project.Utils;

import project.General.SolutionResult;
import project.General.RouteInfo;
import project.General.CustomerInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * JSON Result Logger for CVRP solutions
 * Outputs solution results in JSON format to text files
 */
public class JsonResultLogger {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    private static final String OUTPUT_DIR = "results";
    
    /**
     * Logs solution result to JSON file
     */
    public static void logResult(SolutionResult result, String configName) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Create results directory if it doesn't exist
        File dir = new File(OUTPUT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // Create filename with timestamp
        String timestamp = dateFormat.format(new Date());
        String fileName = OUTPUT_DIR + "/result_" + configName + "_" + timestamp + ".json";
        
        try (FileWriter writer = new FileWriter(fileName)) {
            JsonObject jsonResult = new JsonObject();
            
            // Metadata
            jsonResult.addProperty("timestamp", new Date().toString());
            jsonResult.addProperty("configName", configName);
            jsonResult.addProperty("solveTimeMs", result.solveTimeMs);
            
            // Summary
            JsonObject summary = new JsonObject();
            summary.addProperty("totalItemsRequested", result.itemsTotal);
            summary.addProperty("totalItemsDelivered", result.itemsDelivered);
            summary.addProperty("totalDistance", result.totalDistance);
            summary.addProperty("numberOfRoutes", result.routes.size());
            summary.addProperty("deliveryRate", result.itemsTotal > 0 ? 
                (double) result.itemsDelivered / result.itemsTotal : 0.0);
            jsonResult.add("summary", summary);
            
            // Routes
            JsonArray routesArray = new JsonArray();
            for (RouteInfo route : result.routes) {
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
            jsonResult.add("routes", routesArray);
            
            // Write JSON
            gson.toJson(jsonResult, writer);
            System.out.println("✓ Solution result saved to: " + fileName);
            
            // Also print human-readable summary
            printSummary(result, configName);
            
        } catch (IOException e) {
            System.err.println("ERROR: Failed to write result JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Prints human-readable summary to console
     */
    private static void printSummary(SolutionResult result, String configName) {
        System.out.println("\n===============================================");
        System.out.println("  CVRP SOLUTION SUMMARY - " + configName.toUpperCase());
        System.out.println("===============================================");
        System.out.println("Total Items Requested: " + result.itemsTotal);
        System.out.println("Total Items Delivered: " + result.itemsDelivered);
        System.out.println("Delivery Rate: " + String.format("%.2f%%", 
            result.itemsTotal > 0 ? (100.0 * result.itemsDelivered / result.itemsTotal) : 0.0));
        System.out.println("Total Distance: " + String.format("%.2f", result.totalDistance));
        System.out.println("Number of Routes: " + result.routes.size());
        System.out.println("Solve Time: " + result.solveTimeMs + " ms");
        System.out.println("\nRoutes:");
        for (int i = 0; i < result.routes.size(); i++) {
            RouteInfo route = result.routes.get(i);
            System.out.println("  Route " + (i + 1) + " (Vehicle: " + 
                (route.vehicleName != null ? route.vehicleName : "unknown") + "):");
            System.out.println("    Customers: " + route.customers.size());
            System.out.println("    Demand: " + route.totalDemand + " items");
            System.out.println("    Distance: " + String.format("%.2f", route.totalDistance));
            System.out.print("    Sequence: Depot");
            for (CustomerInfo customer : route.customers) {
                System.out.print(" -> C" + customer.id);
            }
            System.out.println(" -> Depot");
        }
        System.out.println("===============================================\n");
    }
}

