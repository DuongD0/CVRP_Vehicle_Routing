package project.Agent;

import java.util.ArrayList;
import jade.core.Agent;
import jade.core.AID;
import org.chocosolver.solver.Model;

public class Depot extends Agent {
    private ArrayList<AID> deliveries;
    private ArrayList<AID> customers;

    @Override
    protected void setup() {
        Model model = new Model("Depot");


    private String pollAPI() {
        try {
            HttpGet request = new HttpGet(API_URL + "?action=poll");
            request.setHeader("Accept", "application/json");
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();
                if (statusCode == 200) {
                    // API has data for us
                    try (java.io.InputStream inputStream = response.getEntity().getContent()) {
                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                        int nRead;  
                        byte[] data = new byte[1024];
                        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }
                        return buffer.toString("UTF-8");
                    }
                } else if (statusCode == 204) {
                    // No data available
                    return null;
                }
            }
        } catch (Exception e) {
            System.err.println("Depot: Error polling API: " + e.getMessage());
        }
        return null;
    }

    private void sendSolutionToAPI(String solution) {
        try {
            HttpPost request = new HttpPost(API_URL + "?action=response");
            request.setHeader("Content-Type", "application/json");
            
            // Extract the JSON part from the solution (remove "SOLUTION:" prefix)
            String jsonSolution = solution;
            if (solution.startsWith("SOLUTION:")) {
                jsonSolution = solution.substring("SOLUTION:".length());
            }
            
            StringEntity entity = new StringEntity(jsonSolution, ContentType.APPLICATION_JSON);
            request.setEntity(entity);
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();
                if (statusCode == 200) {
                    System.out.println("Depot: Solution sent to API successfully");
                } else {
                    System.err.println("Depot: Failed to send solution to API. Status: " + statusCode);
                }
            }
        } catch (Exception e) {
            System.err.println("Depot: Error sending solution to API: " + e.getMessage());
        }
    }

    private class ContinuousAPIHandlerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                System.out.println("\n=== Depot: Received Message ===");
                if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("SOLUTION:")) {
                    // Received solution from MRA
                    System.out.println("Depot: Processing solution from MRA");
                    handleSolutionResponse(msg.getContent());
                    System.out.println("Depot: Solution processed. Waiting for next request...");
                } else if (msg.getPerformative() == ACLMessage.REQUEST || 
                          msg.getContent().startsWith("{")) {
                    // Received API request data
                    System.out.println("Depot: Processing new API request");
                    handleAPIRequest(msg.getContent());
                    System.out.println("Depot: API request processed. Waiting for solution...");
                }
                System.out.println("=== End of Message Processing ===\n");
            } else {
                block();
            }
        }
    }

    private void handleAPIRequest(String apiData) {
        try {
            System.out.println("Depot: Received API request data");
            
            // Extract request_id if present
            String requestId = extractRequestId(apiData);
            System.out.println("Depot: Request ID: " + requestId);
            
            // Parse API JSON-like format
            System.out.println("Depot: API data: \n" + apiData);
            Map<String, Object> parsedData = parseAPIData(apiData);
            System.out.println("Depot: Parsed data: \n" + parsedData.toString());
            // Send to MRA for solving with request_id
            if (parsedData != null) {
                sendProblemDataToMRA(parsedData, requestId);
            } else {
                throw new Exception("Parsed data is null");
            }

        } catch (Exception e) {
            System.err.println("Depot: Error handling API request: " + e.getMessage());
        }
    }
    
    private String extractRequestId(String apiData) {
        // Look for request_id in the JSON data
        Pattern requestIdPattern = Pattern.compile("\"request_id\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = requestIdPattern.matcher(apiData);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "unknown"; // Default if not found
    }

    private void handleSolutionResponse(String solutionData) {
        System.out.println("Depot: Received solution from MRA");
        System.out.println("Solution: " + solutionData);
        
        // Send solution back to API
        sendSolutionToAPI(solutionData);
    }

    private Map<String, Object> parseAPIData(String apiData) {
        Map<String, Object> result = new HashMap<>();
        
        System.out.println("Depot: Parsing API data...");
        System.out.println("Depot: API data content: " + apiData);
        
        // Extract vehicles - handle the format: "vehicle_1": 100 (just capacity, no coordinates)
        Pattern vehiclePattern = Pattern.compile("\"vehicle_(\\d+)\":\\s*(\\d+)");
        Matcher vehicleMatcher = vehiclePattern.matcher(apiData);
        
        Map<String, Object> vehicles = new HashMap<>();
        while (vehicleMatcher.find()) {
            String vehicleId = vehicleMatcher.group(1);
            int capacity = Integer.parseInt(vehicleMatcher.group(2));
            
            vehicles.put(vehicleId, capacity);
            System.out.println("Depot: Found vehicle_" + vehicleId + " with capacity " + capacity);
        }
        result.put("vehicles", vehicles);
        
        // Extract customers - handle the format: "customer_1": [[x, y], demand] (nested array with newlines)
        Pattern customerPattern = Pattern.compile("\"customer_(\\d+)\":\\s*\\[\\s*\\[\\s*(\\d+(?:\\.\\d+)?),\\s*(\\d+(?:\\.\\d+)?)\\s*\\]\\s*,\\s*(\\d+)\\s*\\]", Pattern.DOTALL);
        Matcher customerMatcher = customerPattern.matcher(apiData);
        
        System.out.println("Depot: Customer regex pattern: " + customerPattern.pattern());
        System.out.println("Depot: Looking for customer matches...");
        
        // Test if pattern matches at all
        if (!customerMatcher.find()) {
            System.out.println("Depot: No customer matches found with current pattern");
            // Reset matcher for actual processing
            customerMatcher = customerPattern.matcher(apiData);
        } else {
            System.out.println("Depot: Found at least one customer match");
            // Reset matcher for actual processing
            customerMatcher = customerPattern.matcher(apiData);
        }
        
        Map<String, Object> customers = new HashMap<>();
        while (customerMatcher.find()) {
            String customerId = customerMatcher.group(1);
            double x = Double.parseDouble(customerMatcher.group(2));
            double y = Double.parseDouble(customerMatcher.group(3));
            int demand = Integer.parseInt(customerMatcher.group(4));
            
            customers.put(customerId, new Object[]{new double[]{x, y}, demand});
            System.out.println("Depot: Found customer_" + customerId + " at (" + x + ", " + y + ") with demand " + demand);
        }
        result.put("customers", customers);
        
        System.out.println("Depot: Parsed " + vehicles.size() + " vehicles and " + customers.size() + " customers");
        
        return result;
    }

    private void sendProblemDataToMRA(Map<String, Object> apiData, String requestId) {
        try {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            AID mraAgent = new AID("mra-agent", AID.ISLOCALNAME);
            msg.addReceiver(mraAgent);
            
            // Convert API data to our internal format
            StringBuilder problemData = new StringBuilder();
            problemData.append("API_PROBLEM_DATA\n");
            problemData.append("REQUEST_ID:").append(requestId).append("\n");
            
            // Process vehicles
            @SuppressWarnings("unchecked")
            Map<String, Object> vehicles = (Map<String, Object>) apiData.get("vehicles");
            problemData.append("NUM_VEHICLES:").append(vehicles.size()).append("\n");
            
            // Get vehicle capacity (assuming all same capacity)
            Integer vehicleCapacity = (Integer) vehicles.values().iterator().next();
            problemData.append("VEHICLE_CAPACITY:").append(vehicleCapacity).append("\n");
            
            // Set depot coordinates (default or from API if needed)
            problemData.append("DEPOT_X:400\n");  // Default depot X
            problemData.append("DEPOT_Y:300\n");  // Default depot Y
            
            // Process customers
            @SuppressWarnings("unchecked")
            Map<String, Object> customers = (Map<String, Object>) apiData.get("customers");
            int numCustomers = customers.size();
            problemData.append("NUM_CUSTOMERS:").append(numCustomers).append("\n");
            
            // Build coordinate and demand arrays in numeric customer order and correct format
            // Sort customers by numeric id to keep coordinates aligned with demands
            List<Map.Entry<String, Object>> sortedCustomers = new ArrayList<>(customers.entrySet());
            sortedCustomers.sort((a, b) -> Integer.compare(
                    Integer.parseInt(a.getKey()),
                    Integer.parseInt(b.getKey())
            ));

            StringBuilder coordsSb = new StringBuilder();
            StringBuilder demandsSb = new StringBuilder();

            for (int i = 0; i < sortedCustomers.size(); i++) {
                Map.Entry<String, Object> entry = sortedCustomers.get(i);
                Object[] customerData = (Object[]) entry.getValue();
                double[] coords = (double[]) customerData[0];
                int demandVal = (Integer) customerData[1];

                if (i > 0) {
                    coordsSb.append(",");
                    demandsSb.append(",");
                }
                coordsSb.append(coords[0]).append(",").append(coords[1]);
                demandsSb.append(demandVal);
            }

            // MRA expects both sections on the SAME line: "CUSTOMER_COORDINATES:...DEMANDS:..."
            problemData.append("CUSTOMER_COORDINATES:")
                       .append(coordsSb)
                       .append("DEMANDS:")
                       .append(demandsSb)
                       .append("\n");
            
            msg.setContent(problemData.toString());
            send(msg);
            
            System.out.println("Depot: API data sent to MRA agent for solving");
            
        } catch (Exception e) {
            System.err.println("Depot: Error sending API data to MRA: " + e.getMessage());
        }
    }

    // Method to process external API requests (for integration with HTTP server)
    public void processAPIRequest(String jsonData) {
        System.out.println("Depot: Received external API request");
        handleAPIRequest(jsonData);
    }

    @Override
    protected void takeDown() {

    }
}
