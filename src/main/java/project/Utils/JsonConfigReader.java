package project.Utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON Configuration Reader for CVRP problem
 * Reads configuration from JSON files with depot, customers, and vehicles
 */
public class JsonConfigReader {
    
    public static class CVRPConfig {
        public DepotConfig depot;
        public List<CustomerConfig> customers;
        public List<VehicleConfig> vehicles;
    }
    
    public static class DepotConfig {
        public String name;
        public double x;
        public double y;
    }
    
    public static class CustomerConfig {
        public String id;  // Node ID
        public int demand;  // Number of items
        public double x;
        public double y;
    }
    
    public static class VehicleConfig {
        public String name;
        public int capacity;
        public double maxDistance;
    }
    
    /**
     * Reads CVRP configuration from JSON file
     */
    public static CVRPConfig readConfig(String filePath) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        
        try (FileReader reader = new FileReader(filePath)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            
            CVRPConfig config = new CVRPConfig();
            
            // Read depot
            if (json.has("depot")) {
                JsonObject depotJson = json.getAsJsonObject("depot");
                config.depot = new DepotConfig();
                config.depot.name = depotJson.get("name").getAsString();
                config.depot.x = depotJson.get("x").getAsDouble();
                config.depot.y = depotJson.get("y").getAsDouble();
            }
            
            // Read customers
            config.customers = new ArrayList<>();
            if (json.has("customers")) {
                JsonArray customersArray = json.getAsJsonArray("customers");
                for (JsonElement elem : customersArray) {
                    JsonObject customerJson = elem.getAsJsonObject();
                    CustomerConfig customer = new CustomerConfig();
                    customer.id = customerJson.get("id").getAsString();
                    customer.demand = customerJson.get("demand").getAsInt();
                    customer.x = customerJson.get("x").getAsDouble();
                    customer.y = customerJson.get("y").getAsDouble();
                    config.customers.add(customer);
                }
            }
            
            // Read vehicles
            config.vehicles = new ArrayList<>();
            if (json.has("vehicles")) {
                JsonArray vehiclesArray = json.getAsJsonArray("vehicles");
                for (JsonElement elem : vehiclesArray) {
                    JsonObject vehicleJson = elem.getAsJsonObject();
                    VehicleConfig vehicle = new VehicleConfig();
                    vehicle.name = vehicleJson.get("name").getAsString();
                    vehicle.capacity = vehicleJson.get("capacity").getAsInt();
                    vehicle.maxDistance = vehicleJson.get("maxDistance").getAsDouble();
                    config.vehicles.add(vehicle);
                }
            }
            
            return config;
        }
    }
}

