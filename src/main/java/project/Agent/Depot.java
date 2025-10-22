package project.Agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.core.AID;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.variables.IntVar;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import project.General.*;

public class Depot extends Agent {
    // API URL for the backend
    private static final String API_URL = "http://localhost:8000/api/solve-cvrp";
    private CloseableHttpClient httpClient;

    // Fields merged from MRA for solving
    private int numVehicles;
    private int vehicleCapacity;
    private int numNodes;
    private double[] x;
    private double[] y;
    private int[] demand;
    private int[][] distance;
    private String requestId;
    private double depotX;
    private double depotY;

    @Override
    protected void setup() {
        System.out.println("Depot Agent " + getAID().getName() + " is ready.");
        System.out.println("Depot: Connecting to API at " + API_URL);
        
        /* ########### API endpoint connection ########### */
        // Initialize HTTP client
        httpClient = HttpClients.createDefault();
        
        // Add behavior to continuously poll API and handle responses
        addBehaviour(new APIPollerBehaviour());
        addBehaviour(new ContinuousAPIHandlerBehaviour());
    }

    private class APIPollerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            try {
                // Poll the API for new requests continuously
                String apiData = pollAPI();
                if (apiData != null && !apiData.isEmpty()) {
                    System.out.println("Depot: Received data from API");
                    handleAPIRequest(apiData);
                }
                
                // Wait 2 seconds before polling again
                Thread.sleep(2000);
                
            } catch (InterruptedException e) {
                // Ignore interruption
            } catch (Exception e) {
                System.err.println("Depot: Error polling API: " + e.getMessage());
                try {
                    Thread.sleep(5000); // Wait longer on error
                } catch (InterruptedException ie) {
                    // Ignore
                }
            }
        }
    }

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
            // Build internal problem, solve, and send solution back to API directly
            buildProblemFromParsedData(parsedData, requestId);
            SolutionResult result = solveVRPWithChoco(numNodes, numNodes - 1, numVehicles, vehicleCapacity, demand, distance);
            String solutionJson = formatSolutionJson(result);
            sendSolutionToAPI(solutionJson);

            /*            
            // Send to MRA for solving with request_id
            if (parsedData != null) {
                sendProblemDataToMRA(parsedData, requestId);
            } else {
                throw new Exception("Parsed data is null");
            }
            */
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

    // Build internal arrays and fields from parsed API map
    private void buildProblemFromParsedData(Map<String, Object> apiData, String reqId) {
        try {
            this.requestId = reqId != null ? reqId : "unknown";
            @SuppressWarnings("unchecked")
            Map<String, Object> vehicles = (Map<String, Object>) apiData.get("vehicles");
            @SuppressWarnings("unchecked")
            Map<String, Object> customers = (Map<String, Object>) apiData.get("customers");

            this.numVehicles = vehicles.size();
            // Assume uniform capacity across vehicles
            this.vehicleCapacity = (Integer) vehicles.values().iterator().next();

            int numCustomers = customers.size();
            this.numNodes = numCustomers + 1; // +1 for depot

            this.depotX = 400;
            this.depotY = 300;

            this.x = new double[numNodes];
            this.y = new double[numNodes];
            this.demand = new int[numNodes];

            // Depot at index 0
            x[0] = depotX;
            y[0] = depotY;
            demand[0] = 0;

            // Sort customers numerically by id string key
            List<Map.Entry<String, Object>> sortedCustomers = new ArrayList<>(customers.entrySet());
            sortedCustomers.sort((a, b) -> Integer.compare(
                    Integer.parseInt(a.getKey()),
                    Integer.parseInt(b.getKey())
            ));

            int idx = 1;
            for (Map.Entry<String, Object> entry : sortedCustomers) {
                Object[] customerData = (Object[]) entry.getValue();
                double[] coords = (double[]) customerData[0];
                int d = (Integer) customerData[1];
                x[idx] = coords[0];
                y[idx] = coords[1];
                demand[idx] = d;
                idx++;
            }

            // Build distance matrix
            this.distance = new int[numNodes][numNodes];
            for (int i = 0; i < numNodes; i++) {
                for (int j = 0; j < numNodes; j++) {
                    double dx = x[i] - x[j];
                    double dy = y[i] - y[j];
                    distance[i][j] = (int) Math.round(Math.hypot(dx, dy));
                }
            }

            System.out.println("Depot: Problem built for solving: vehicles=" + numVehicles + ", customers=" + (numNodes - 1));
        } catch (Exception e) {
            System.err.println("Depot: Error building problem from parsed data: " + e.getMessage());
        }
    }

    // Solver logic from MRA
    private SolutionResult solveVRPWithChoco(int numNodes, int numCustomers, int numVehicles,
                                          int capacity, int[] demand, int[][] distance) {
        Model model = new Model("CVRP-Choco-Solver");
        final int depot = 0;

        IntVar[] successor = model.intVarArray("successor", numNodes, 0, numNodes - 1);
        IntVar[] vehicle = model.intVarArray("vehicle", numNodes, 0, numVehicles);
        IntVar[] vehicleLoad = model.intVarArray("vehicleLoad", numVehicles, 0, capacity);
        IntVar totalDistance = model.intVar("totalDistance", 0, 999999);

        model.arithm(vehicle[depot], "=", 0).post();
        model.member(successor[depot], 1, numNodes - 1).post();
        for (int i = 1; i < numNodes; i++) {
            model.member(vehicle[i], 1, numVehicles).post();
        }
        model.circuit(successor).post();
        for (int i = 1; i < numNodes; i++) {
            IntVar nextNode = successor[i];
            IntVar nextVehicle = model.intVar("nextVeh_" + i, 0, numVehicles);
            model.element(nextVehicle, vehicle, nextNode, 0).post();
        }
        for (int v = 1; v <= numVehicles; v++) {
            IntVar[] customerOfVehicle = new IntVar[numCustomers];
            for (int i = 1; i < numNodes; i++) {
                customerOfVehicle[i - 1] = model.intVar("isV" + v + "_C" + i, 0, 1);
                model.ifThenElse(
                        model.arithm(vehicle[i], "=", v),
                        model.arithm(customerOfVehicle[i - 1], "=", 1),
                        model.arithm(customerOfVehicle[i - 1], "=", 0)
                );
            }
            int[] demandCoeffs = new int[numCustomers];
            for (int i = 0; i < numCustomers; i++) {
                demandCoeffs[i] = demand[i + 1];
            }
            IntVar totalDemand = model.intVar("totalDemand_v" + v, 0, capacity);
            model.scalar(customerOfVehicle, demandCoeffs, "=", totalDemand).post();
            model.arithm(totalDemand, "<=", capacity).post();
            model.arithm(vehicleLoad[v - 1], "=", totalDemand).post();
        }
        IntVar[] edgeDistances = new IntVar[numNodes];
        for (int i = 0; i < numNodes; i++) {
            edgeDistances[i] = model.intVar("dist_" + i, 0, 99999);
            int[] distRow = distance[i];
            model.element(edgeDistances[i], distRow, successor[i], 0).post();
        }
        model.sum(edgeDistances, "=", totalDistance).post();
        model.setObjective(Model.MINIMIZE, totalDistance);

        int timeLimitSeconds = 10;
        model.getSolver().limitTime(timeLimitSeconds * 1000);

        System.out.println("Solving with time limit: " + timeLimitSeconds + " seconds");
        System.out.println();

        Solution solution = model.getSolver().findOptimalSolution(totalDistance, false);
        SolutionResult result = new SolutionResult();
        if (solution != null) {
            System.out.println("Solution found!");
            double totalDist = solution.getIntVal(totalDistance);
            result.totalDistance = totalDist;

            Map<Integer, List<Integer>> routes = new HashMap<>();
            for (int v = 1; v <= numVehicles; v++) {
                routes.put(v, new ArrayList<>());
            }
            for (int i = 1; i < numNodes; i++) {
                int v = solution.getIntVal(vehicle[i]);
                if (v >= 1 && v <= numVehicles) {
                    routes.get(v).add(i);
                }
            }
            for (int v = 1; v <= numVehicles; v++) {
                List<Integer> customers = routes.get(v);
                if (!customers.isEmpty()) {
                    RouteInfo routeInfo = new RouteInfo(v);
                    int load = 0;
                    double routeDistance = 0.0;
                    routeDistance += Math.hypot(x[0] - x[customers.get(0)], y[0] - y[customers.get(0)]);
                    for (int i = 0; i < customers.size(); i++) {
                        int c = customers.get(i);
                        load += demand[c];
                        CustomerInfo customerInfo = new CustomerInfo(c, x[c], y[c], demand[c]);
                        routeInfo.customers.add(customerInfo);
                        if (i < customers.size() - 1) {
                            int nextCustomer = customers.get(i + 1);
                            routeDistance += Math.hypot(x[c] - x[nextCustomer], y[c] - y[nextCustomer]);
                        } else {
                            routeDistance += Math.hypot(x[c] - x[0], y[c] - y[0]);
                        }
                    }
                    routeInfo.totalDemand = load;
                    routeInfo.totalDistance = routeDistance;
                    result.routes.add(routeInfo);
                }
            }
        } else {
            System.out.println("No solution found within the time limit.");
            result.totalDistance = 0.0;
        }
        return result;
    }

    private String formatSolutionJson(SolutionResult result) {
        StringBuilder solutionJson = new StringBuilder();
        solutionJson.append("SOLUTION:{\n");
        solutionJson.append("  \"request_id\": \"").append(requestId != null ? requestId : "unknown").append("\",\n");
        solutionJson.append("  \"routes\": [\n");
        for (int i = 0; i < result.routes.size(); i++) {
            RouteInfo route = result.routes.get(i);
            if (i > 0) solutionJson.append(",\n");
            solutionJson.append("    {\n");
            solutionJson.append("      \"vehicle_id\": ").append(route.vehicleId).append(",\n");
            solutionJson.append("      \"customers\": [\n");
            for (int j = 0; j < route.customers.size(); j++) {
                CustomerInfo customer = route.customers.get(j);
                if (j > 0) solutionJson.append(",\n");
                solutionJson.append("        {\n");
                solutionJson.append("          \"id\": ").append(customer.id).append(",\n");
                solutionJson.append("          \"x\": ").append(customer.x).append(",\n");
                solutionJson.append("          \"y\": ").append(customer.y).append(",\n");
                solutionJson.append("          \"demand\": ").append(customer.demand).append(",\n");
                solutionJson.append("          \"name\": \"").append(customer.name).append("\"\n");
                solutionJson.append("        }");
            }
            solutionJson.append("\n      ],\n");
            solutionJson.append("      \"total_demand\": ").append(route.totalDemand).append(",\n");
            solutionJson.append("      \"total_distance\": ").append(route.totalDistance).append("\n");
            solutionJson.append("    }");
        }
        solutionJson.append("\n  ],\n");
        solutionJson.append("  \"total_distance\": ").append(result.totalDistance).append("\n");
        solutionJson.append("}");
        return solutionJson.toString();
    }

    // Method to process external API requests (for integration with HTTP server)
    public void processAPIRequest(String jsonData) {
        System.out.println("Depot: Received external API request");
        handleAPIRequest(jsonData);
    }

    @Override
    protected void takeDown() {
        System.out.println("Depot Agent " + getAID().getName() + " terminating.");
        
        // Close HTTP client
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception e) {
                System.err.println("Depot: Error closing HTTP client: " + e.getMessage());
            }
        }
    }
}
