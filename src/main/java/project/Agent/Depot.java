package project.Agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.domain.FIPANames;
import jade.core.AID;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.LocalSearchMetaheuristic;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.google.ortools.constraintsolver.main;
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
    private List<String> availableVehicleNames;  // List of free vehicle names
    private Map<String, Object> cachedVehicles;  // Cached vehicle list for customer-only requests

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
            
            org.apache.hc.core5.http.io.HttpClientResponseHandler<String> handler = (resp) -> {
                int statusCode = resp.getCode();
                if (statusCode == 200) {
                    if (resp.getEntity() == null) return null;
                    try (java.io.InputStream inputStream = resp.getEntity().getContent()) {
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
                return null;
            };
            return httpClient.execute(request, handler);
        } catch (Exception e) {
            System.err.println("Depot: Error polling API: " + e.getMessage());
        }
        return null;
    }

    private class ContinuousAPIHandlerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                System.out.println("\n=== Depot: Received Message ===");
                String content = msg.getContent();
                
                if (content.startsWith("AVAILABLE_VEHICLES:") || content.startsWith("NAMES:")) {
                    // Response from Delivery Agent with available vehicle info
                    handleDeliveryAgentResponse(msg);
                } else if (msg.getPerformative() == ACLMessage.REQUEST || content.startsWith("{")) {
                    // Received API request data
                    System.out.println("Depot: Processing new API request");
                    handleAPIRequest(content);
                    System.out.println("Depot: Request forwarded to Delivery Agent");
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
            
            // Parsed data is already logged in parseAPIData() method with proper formatting
            
            // Forward request to Delivery Agent and wait for available vehicle count
            sendRequestToDeliveryAgent(parsedData, requestId);
            
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

    private void sendRequestToDeliveryAgent(Map<String, Object> parsedData, String requestId) {
        try {
            // Store parsed data and request ID for later use
            this.requestId = requestId;
            buildProblemFromParsedData(parsedData, requestId);
            
            // Format vehicle data for Delivery Agent
            @SuppressWarnings("unchecked")
            Map<String, Object> vehicles = (Map<String, Object>) parsedData.get("vehicles");
            StringBuilder vehicleData = new StringBuilder();
            int i = 0;
            for (Map.Entry<String, Object> entry : vehicles.entrySet()) {
                if (i > 0) vehicleData.append(",");
                vehicleData.append(entry.getKey()).append(":").append(entry.getValue());
                i++;
            }
            
            // Send request to Delivery Agent (FIPA-REQUEST)
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            AID deliveryAgent = new AID("delivery-agent", AID.ISLOCALNAME);
            msg.addReceiver(deliveryAgent);
            // Minimal payload: only vehicles list; conversationId carries correlation
            msg.setContent("VEHICLES:" + vehicleData.toString());
            // FIPA metadata
            msg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            String convId = "req-" + requestId;
            msg.setConversationId(convId);
            msg.setReplyWith("rw-" + System.currentTimeMillis());
            send(msg);
            
            System.out.println("Depot: Request sent to Delivery Agent");
            
        } catch (Exception e) {
            System.err.println("Depot: Error sending request to Delivery Agent: " + e.getMessage());
        }
    }
    
    private void handleDeliveryAgentResponse(ACLMessage msg) {
        try {
            // Ensure response matches the current conversation (if set)
            if (this.requestId != null) {
                String expectedConv = "req-" + this.requestId;
                String gotConv = msg.getConversationId();
                if (gotConv != null && !expectedConv.equals(gotConv)) {
                    System.out.println("Depot: Ignoring INFORM with mismatched conversationId: " + gotConv + " (expected " + expectedConv + ")");
                    return;
                }
            }
            String content = msg.getContent();
            this.availableVehicleNames = new ArrayList<>();
            int availableVehicles = 0;
            if (content.startsWith("AVAILABLE_VEHICLES:")) {
                // Backward-compat format: AVAILABLE_VEHICLES:count|NAMES:a,b
                String[] parts = content.split("\\|");
                availableVehicles = Integer.parseInt(parts[0].substring("AVAILABLE_VEHICLES:".length()));
                if (parts.length > 1 && parts[1].startsWith("NAMES:")) {
                    String namesStr = parts[1].substring("NAMES:".length());
                    String[] names = namesStr.split(",");
                    for (String name : names) {
                        if (!name.trim().isEmpty()) availableVehicleNames.add(name.trim());
                    }
                }
            } else if (content.startsWith("NAMES:")) {
                // New minimal format: NAMES:a,b
                String namesStr = content.substring("NAMES:".length());
                String[] names = namesStr.split(",");
                for (String name : names) {
                    if (!name.trim().isEmpty()) availableVehicleNames.add(name.trim());
                }
                availableVehicles = availableVehicleNames.size();
            }
            
            System.out.println("Depot: Received available vehicle count: " + availableVehicles);
            System.out.println("Depot: Available vehicle names: " + availableVehicleNames);
            
            // Update numVehicles to match available vehicles
            this.numVehicles = availableVehicles;
            
            // Solve VRP with available vehicles
            if (availableVehicles > 0) {
                System.out.println("Depot: Solving VRP with " + availableVehicles + " vehicles using Google OR-Tools...");
                SolutionResult result = solveVRPWithORTools(numNodes, numNodes - 1, availableVehicles, vehicleCapacity, demand, distance);
                
                // Map vehicle IDs to names in the result
                mapVehicleNamesToRoutes(result);
                
                // Send routes to Delivery Agent
                sendRoutesToDeliveryAgent(result);
            } else {
                System.out.println("Depot: No vehicles available, cannot solve VRP");
            }
            
        } catch (Exception e) {
            System.err.println("Depot: Error handling Delivery Agent response: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void mapVehicleNamesToRoutes(SolutionResult result) {
        // Map numeric vehicle IDs (1, 2, 3, ...) to actual vehicle names
        for (RouteInfo route : result.routes) {
            if (route.vehicleId > 0 && route.vehicleId <= availableVehicleNames.size()) {
                route.vehicleName = availableVehicleNames.get(route.vehicleId - 1);
                System.out.println("Depot: Mapped vehicle ID " + route.vehicleId + " to name '" + route.vehicleName + "'");
            }
        }
    }
    
    private void sendRoutesToDeliveryAgent(SolutionResult result) {
        try {
            // Format routes as JSON string
            String routeData = formatRoutesForDelivery(result);
            
            // Send to Delivery Agent
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            AID deliveryAgent = new AID("delivery-agent", AID.ISLOCALNAME);
            msg.addReceiver(deliveryAgent);
            msg.setContent("ROUTES:" + requestId + "|ROUTE_DATA:" + routeData);
            send(msg);
            
            System.out.println("Depot: Routes sent to Delivery Agent");
            
        } catch (Exception e) {
            System.err.println("Depot: Error sending routes to Delivery Agent: " + e.getMessage());
        }
    }
    
    private String formatRoutesForDelivery(SolutionResult result) {
        StringBuilder routeData = new StringBuilder();
        routeData.append("\"routes\": [\n");
        for (int i = 0; i < result.routes.size(); i++) {
            RouteInfo route = result.routes.get(i);
            if (i > 0) routeData.append(",\n");
            routeData.append("    {\n");
            routeData.append("      \"route_id\": \"R").append(i + 1).append("\",\n");
            
            // Use vehicle name instead of numeric ID
            if (route.vehicleName != null && !route.vehicleName.isEmpty()) {
                routeData.append("      \"vehicle_agent\": \"").append(route.vehicleName).append("\",\n");
            } else {
                // Fallback to numeric ID if name is not available
                routeData.append("      \"vehicle_id\": ").append(route.vehicleId).append(",\n");
            }
            
            routeData.append("      \"customers\": [\n");
            for (int j = 0; j < route.customers.size(); j++) {
                CustomerInfo customer = route.customers.get(j);
                if (j > 0) routeData.append(",\n");
                routeData.append("        {\n");
                routeData.append("          \"id\": ").append(customer.id).append(",\n");
                routeData.append("          \"x\": ").append(customer.x).append(",\n");
                routeData.append("          \"y\": ").append(customer.y).append(",\n");
                routeData.append("          \"demand\": ").append(customer.demand).append(",\n");
                routeData.append("          \"name\": \"").append(customer.name).append("\"\n");
                routeData.append("        }");
            }
            routeData.append("\n      ],\n");
            routeData.append("      \"total_demand\": ").append(route.totalDemand).append(",\n");
            routeData.append("      \"total_distance\": ").append(route.totalDistance).append("\n");
            routeData.append("    }");
        }
        routeData.append("\n  ],\n");
        routeData.append("  \"total_distance\": ").append(result.totalDistance).append(",\n");
        routeData.append("  \"meta\": {\n");
        routeData.append("    \"solver\": \"or-tools\",\n");
        routeData.append("    \"solve_time_ms\": ").append(result.solveTimeMs).append("\n");
        routeData.append("  }");
        return routeData.toString();
    }

    private Map<String, Object> parseAPIData(String apiData) {
        Map<String, Object> result = new HashMap<>();
        
        System.out.println("Depot: Parsing API data...");
        System.out.println("Depot: API data content: " + apiData);
        
        // Extract vehicles - handle both formats: "Thanh": 100 OR "vehicle_1": 100
        // Pattern matches: "any_name": number
        Pattern vehiclePattern = Pattern.compile("\"([^\"]+)\":\\s*(\\d+)(?=\\s*[,}])");
        
        Map<String, Object> vehicles = new HashMap<>();
        
        // Find the vehicles section
        int vehiclesStart = apiData.indexOf("\"vehicles\"");
        int vehiclesEnd = -1;
        if (vehiclesStart != -1) {
            int braceCount = 0;
            boolean inVehicles = false;
            for (int i = vehiclesStart; i < apiData.length(); i++) {
                char c = apiData.charAt(i);
                if (c == '{') {
                    braceCount++;
                    inVehicles = true;
                } else if (c == '}') {
                    braceCount--;
                    if (inVehicles && braceCount == 0) {
                        vehiclesEnd = i + 1;
                        break;
                    }
                }
            }
        }
        
        if (vehiclesStart != -1 && vehiclesEnd != -1) {
            String vehiclesSection = apiData.substring(vehiclesStart, vehiclesEnd);
            Matcher vm = vehiclePattern.matcher(vehiclesSection);
            while (vm.find()) {
                String vehicleName = vm.group(1);
                if (!vehicleName.equals("vehicles")) {  // Skip the "vehicles" key itself
                    int capacity = Integer.parseInt(vm.group(2));
                    vehicles.put(vehicleName, capacity);
                    System.out.println("Depot: Found vehicle '" + vehicleName + "' with capacity " + capacity);
                }
            }
            // Cache the vehicles for future customer-only requests
            this.cachedVehicles = new HashMap<>(vehicles);
        } else {
            // No vehicles in request - use cached vehicles from previous request
            if (this.cachedVehicles != null && !this.cachedVehicles.isEmpty()) {
                vehicles = new HashMap<>(this.cachedVehicles);
                System.out.println("Depot: No vehicles in request - using cached vehicle list");
                for (Map.Entry<String, Object> entry : vehicles.entrySet()) {
                    System.out.println("Depot: Using cached vehicle '" + entry.getKey() + "' with capacity " + entry.getValue());
                }
            } else {
                System.err.println("Depot: WARNING - No vehicles in request and no cached vehicles available!");
            }
        }
        result.put("vehicles", vehicles);
        
        // Extract customers - handle the format: "customer_1": [[x, y], demand]
        // Updated pattern to handle floating point numbers (including scientific notation)
        Pattern customerPattern = Pattern.compile(
            "\"(customer_\\d+)\":\\s*\\[\\s*\\[\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?),\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*\\]\\s*,\\s*(\\d+)\\s*\\]", 
            Pattern.DOTALL
        );
        Matcher customerMatcher = customerPattern.matcher(apiData);
        
        System.out.println("Depot: Looking for customer matches...");
        
        Map<String, Object> customers = new HashMap<>();
        while (customerMatcher.find()) {
            String customerId = customerMatcher.group(1);  // This is "customer_1", "customer_2", etc.
            double x = Double.parseDouble(customerMatcher.group(2));
            double y = Double.parseDouble(customerMatcher.group(3));
            int demand = Integer.parseInt(customerMatcher.group(4));
            
            customers.put(customerId, new Object[]{new double[]{x, y}, demand});
            System.out.println("Depot: Found " + customerId + " at (" + x + ", " + y + ") with demand " + demand);
        }
        
        if (customers.isEmpty()) {
            System.out.println("Depot: WARNING - No customers found in API data!");
        }
        
        result.put("customers", customers);
        
        System.out.println("Depot: Parsed " + vehicles.size() + " vehicles and " + customers.size() + " customers");
        
        return result;
    }

    // Build internal arrays and fields from parsed API map
    private void buildProblemFromParsedData(Map<String, Object> apiData, String reqId) {
        try {
            System.out.println("\n=== Depot: Building Problem from Parsed Data ===");
            
            this.requestId = reqId != null ? reqId : "unknown";
            System.out.println("DEBUG - Request ID: " + this.requestId);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> vehicles = (Map<String, Object>) apiData.get("vehicles");
            @SuppressWarnings("unchecked")
            Map<String, Object> customers = (Map<String, Object>) apiData.get("customers");

            System.out.println("DEBUG - Raw vehicles map: " + vehicles.keySet());
            System.out.println("DEBUG - Raw customers map: " + customers.keySet());

            this.numVehicles = vehicles.size();
            System.out.println("DEBUG - Number of vehicles: " + this.numVehicles);
            
            // Assume uniform capacity across vehicles
            this.vehicleCapacity = (Integer) vehicles.values().iterator().next();
            System.out.println("DEBUG - Vehicle capacity: " + this.vehicleCapacity);

            int numCustomers = customers.size();
            this.numNodes = numCustomers + 1; // +1 for depot
            System.out.println("DEBUG - Number of customers: " + numCustomers);
            System.out.println("DEBUG - Total nodes (including depot): " + this.numNodes);

            // hardcoded depot coordinates
            this.depotX = 800;
            this.depotY = 600;
            System.out.println("DEBUG - Depot coordinates: (" + this.depotX + ", " + this.depotY + ")");

            this.x = new double[numNodes];
            this.y = new double[numNodes];
            this.demand = new int[numNodes];

            // Depot at index 0
            x[0] = depotX;
            y[0] = depotY;
            demand[0] = 0;
            System.out.println("DEBUG - Node[0] (Depot): x=" + x[0] + ", y=" + y[0] + ", demand=" + demand[0]);

            // Sort customers numerically by id string key
            List<Map.Entry<String, Object>> sortedCustomers = new ArrayList<>(customers.entrySet());
            sortedCustomers.sort((a, b) -> {
                // Extract numeric part from "customer_1" -> 1
                String keyA = a.getKey().replace("customer_", "");
                String keyB = b.getKey().replace("customer_", "");
                return Integer.compare(Integer.parseInt(keyA), Integer.parseInt(keyB));
            });
            
            System.out.println("DEBUG - Sorted customer order: " + 
                sortedCustomers.stream()
                    .map(e -> e.getKey())
                    .collect(java.util.stream.Collectors.toList())
            );

            int idx = 1;
            for (Map.Entry<String, Object> entry : sortedCustomers) {
                String customerKey = entry.getKey();
                Object[] customerData = (Object[]) entry.getValue();
                double[] coords = (double[]) customerData[0];
                int d = (Integer) customerData[1];
                
                x[idx] = coords[0];
                y[idx] = coords[1];
                demand[idx] = d;
                
                System.out.println("DEBUG - Node[" + idx + "] (" + customerKey + "): " +
                    "x=" + x[idx] + ", y=" + y[idx] + ", demand=" + demand[idx]);
                
                idx++;
            }

            // Build distance matrix
            System.out.println("DEBUG - Building distance matrix...");
            this.distance = new int[numNodes][numNodes];
            for (int i = 0; i < numNodes; i++) {
                for (int j = 0; j < numNodes; j++) {
                    double dx = x[i] - x[j];
                    double dy = y[i] - y[j];
                    distance[i][j] = (int) Math.round(Math.hypot(dx, dy));
                }
            }
            
            // Print sample distances
            System.out.println("DEBUG - Sample distances:");
            System.out.println("  Depot to Node[1]: " + distance[0][1]);
            if (numNodes > 2) {
                System.out.println("  Depot to Node[2]: " + distance[0][2]);
                System.out.println("  Node[1] to Node[2]: " + distance[1][2]);
            }

            System.out.println("DEBUG - Problem built successfully!");
            System.out.println("=== Summary: vehicles=" + numVehicles + 
                ", customers=" + (numNodes - 1) + 
                ", capacity=" + vehicleCapacity + " ===\n");
                
        } catch (Exception e) {
            System.err.println("Depot: Error building problem from parsed data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Solver logic using Google OR-Tools
    private SolutionResult solveVRPWithORTools(int numNodes, int numCustomers, int numVehicles,
                                               int capacity, int[] demand, int[][] distance) {
        long startTime = System.currentTimeMillis();
        
        // Load OR-Tools native library
        Loader.loadNativeLibraries();
        
        System.out.println("=== Google OR-Tools VRP Solver ===");
        System.out.println("Nodes: " + numNodes + " (including depot)");
        System.out.println("Vehicles: " + numVehicles);
        System.out.println("Capacity: " + capacity);
        
        SolutionResult result = new SolutionResult();
        
        try {
            // Create Routing Index Manager
            RoutingIndexManager manager = new RoutingIndexManager(numNodes, numVehicles, 0);
            
            // Create Routing Model
            RoutingModel routing = new RoutingModel(manager);
            
            // Create distance callback
            final int transitCallbackIndex = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
                int fromNode = manager.indexToNode(fromIndex);
                int toNode = manager.indexToNode(toIndex);
                return distance[fromNode][toNode];
            });
            
            // Set cost of travel
            routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);
            
            // Add capacity constraint
            final int demandCallbackIndex = routing.registerUnaryTransitCallback((long fromIndex) -> {
                int fromNode = manager.indexToNode(fromIndex);
                return demand[fromNode];
            });
            
            // Create vehicle capacity array
            long[] vehicleCapacities = new long[numVehicles];
            for (int i = 0; i < numVehicles; i++) {
                vehicleCapacities[i] = capacity;
            }
            
            routing.addDimensionWithVehicleCapacity(
                demandCallbackIndex,
                0,  // null capacity slack
                vehicleCapacities,  // vehicle capacities
                true,  // start cumul to zero
                "Capacity"
            );
            
            // Set search parameters
            RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(com.google.protobuf.Duration.newBuilder().setSeconds(30).build())
                .build();
            
            System.out.println("Solving...");
            
            // Solve
            Assignment solution = routing.solveWithParameters(searchParameters);
            
            if (solution != null) {
                long endTime = System.currentTimeMillis();
                result.solveTimeMs = endTime - startTime;
                
                System.out.println("Solution found in " + result.solveTimeMs + " ms!");
                System.out.println("Objective: " + solution.objectiveValue());
                
                result.totalDistance = solution.objectiveValue();
                
                // Extract routes
                for (int vehicleId = 0; vehicleId < numVehicles; vehicleId++) {
                    long index = routing.start(vehicleId);
                    RouteInfo routeInfo = new RouteInfo(vehicleId + 1);
                    int routeLoad = 0;
                    double routeDistance = 0.0;
                    List<Integer> route = new ArrayList<>();
                    
                    while (!routing.isEnd(index)) {
                        int nodeIndex = manager.indexToNode(index);
                        if (nodeIndex != 0) {  // Skip depot
                            route.add(nodeIndex);
                        }
                        long previousIndex = index;
                        index = solution.value(routing.nextVar(index));
                        routeDistance += routing.getArcCostForVehicle(previousIndex, index, vehicleId);
                    }
                    
                    // Add customers to route
                    if (!route.isEmpty()) {
                        for (int nodeIndex : route) {
                            routeLoad += demand[nodeIndex];
                            CustomerInfo customerInfo = new CustomerInfo(nodeIndex, x[nodeIndex], y[nodeIndex], demand[nodeIndex]);
                            routeInfo.customers.add(customerInfo);
                        }
                        routeInfo.totalDemand = routeLoad;
                        routeInfo.totalDistance = routeDistance;
                        result.routes.add(routeInfo);
                        
                        System.out.println("Vehicle " + (vehicleId + 1) + ": " + route + 
                            " | Load: " + routeLoad + "/" + capacity + 
                            " | Distance: " + String.format("%.2f", routeDistance));
                    }
                }
                
            } else {
                long endTime = System.currentTimeMillis();
                result.solveTimeMs = endTime - startTime;
                System.out.println("No solution found within time limit.");
                result.totalDistance = 0.0;
            }
            
        } catch (Exception e) {
            System.err.println("Error during OR-Tools solving: " + e.getMessage());
            e.printStackTrace();
            result.totalDistance = 0.0;
        }
        
        System.out.println("=== Solving Complete ===\n");
        return result;
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
