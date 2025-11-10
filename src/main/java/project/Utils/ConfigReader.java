package project.Utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration reader that reads agent configuration from web URL or local file
 */
public class ConfigReader {
    
    /**
     * Reads configuration from web URL, falls back to local file if web is unavailable
     */
    public static AgentConfig readConfig(String webUrl, String localFile) {
        AgentConfig config = new AgentConfig();
        
        // Try to read from web first
        if (webUrl != null && !webUrl.isEmpty()) {
            try {
                System.out.println("Attempting to read configuration from web: " + webUrl);
                String webContent = readFromWeb(webUrl);
                if (webContent != null && !webContent.isEmpty()) {
                    System.out.println("✓ Successfully read configuration from web");
                    parseConfig(webContent, config);
                    return config;
                }
            } catch (Exception e) {
                System.out.println("✗ Failed to read from web: " + e.getMessage());
                System.out.println("Falling back to local configuration file: " + localFile);
            }
        }
        
        // Fall back to local file
        try {
            System.out.println("Reading configuration from local file: " + localFile);
            String fileContent = readFromFile(localFile);
            if (fileContent != null && !fileContent.isEmpty()) {
                System.out.println("✓ Successfully read configuration from local file");
                parseConfig(fileContent, config);
                return config;
            }
        } catch (Exception e) {
            System.err.println("✗ Failed to read from local file: " + e.getMessage());
            e.printStackTrace();
        }
        
        return config;
    }
    
    /**
     * Reads content from web URL
     */
    private static String readFromWeb(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000); // 5 second timeout
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }
        
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        return content.toString();
    }
    
    /**
     * Reads content from local file
     */
    private static String readFromFile(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
    
    /**
     * Parses configuration content
     */
    private static void parseConfig(String content, AgentConfig config) {
        String[] lines = content.split("\n");
        String currentSection = null;
        
        for (String line : lines) {
            line = line.trim();
            
            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            // Check for section headers
            if (line.equals("DEPOT:")) {
                currentSection = "DEPOT";
                continue;
            } else if (line.equals("VEHICLES:")) {
                currentSection = "VEHICLES";
                continue;
            } else if (line.equals("CUSTOMERS:")) {
                currentSection = "CUSTOMERS";
                continue;
            }
            
            // Parse section content
            if ("DEPOT".equals(currentSection)) {
                config.depotName = line;
            } else if ("VEHICLES".equals(currentSection)) {
                // Format: vehicleName,capacity,maxDistance
                String[] parts = line.split(",");
                if (parts.length == 3) {
                    String vehicleName = parts[0].trim();
                    int capacity = Integer.parseInt(parts[1].trim());
                    double maxDistance = Double.parseDouble(parts[2].trim());
                    config.vehicles.add(new VehicleConfig(vehicleName, capacity, maxDistance));
                }
            } else if ("CUSTOMERS".equals(currentSection)) {
                // Format: customerId,customerName,x,y
                String[] parts = line.split(",");
                if (parts.length == 4) {
                    String customerId = parts[0].trim();
                    String customerName = parts[1].trim();
                    double x = Double.parseDouble(parts[2].trim());
                    double y = Double.parseDouble(parts[3].trim());
                    config.customers.add(new CustomerConfig(customerId, customerName, x, y));
                }
            }
        }
    }
    
    /**
     * Configuration data structure
     */
    public static class AgentConfig {
        public String depotName = "depot-agent";
        public List<VehicleConfig> vehicles = new ArrayList<>();
        public List<CustomerConfig> customers = new ArrayList<>();
    }
    
    public static class VehicleConfig {
        public String name;
        public int capacity;
        public double maxDistance;
        
        public VehicleConfig(String name, int capacity, double maxDistance) {
            this.name = name;
            this.capacity = capacity;
            this.maxDistance = maxDistance;
        }
    }
    
    public static class CustomerConfig {
        public String id;
        public String name;
        public double x;
        public double y;
        
        public CustomerConfig(String id, String name, double x, double y) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
        }
    }
}

