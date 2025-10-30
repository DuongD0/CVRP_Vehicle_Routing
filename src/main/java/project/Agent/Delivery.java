package project.Agent;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.FIPANames;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import project.General.*;

import java.util.*;

public class Delivery extends Agent {
    private static final String API_URL = "http://localhost:8000/api/solve-cvrp";
    
    // Track vehicle agents
    private Map<String, AID> vehicleAgents;  // name -> AID
    private Map<String, VehicleInfo> vehicleInfo;  // name -> info
    private CloseableHttpClient httpClient;
    // removed unused currentRequestId
    
    @Override
    protected void setup() {
        System.out.println("Delivery Agent " + getAID().getName() + " is ready.");
        
        vehicleAgents = new HashMap<>();
        vehicleInfo = new HashMap<>();
        httpClient = HttpClients.createDefault();
        
        // Add behavior to handle messages from Depot and Vehicle agents
        addBehaviour(new MessageHandlerBehaviour());
    }
    
    private class MessageHandlerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            // Prefer messages matching FIPA-REQUEST for Depot -> Delivery
            MessageTemplate template = MessageTemplate.or(
                MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST)
                ),
                MessageTemplate.MatchAll()
            );
            ACLMessage msg = receive(template);
            if (msg != null) {
                String content = msg.getContent();
                
                if (msg.getPerformative() == ACLMessage.REQUEST && FIPANames.InteractionProtocol.FIPA_REQUEST.equals(msg.getProtocol())) {
                    // FIPA-REQUEST from Depot with vehicle data (minimal payload)
                    handleDepotRequest(msg);
                } else if (content.startsWith("ROUTES:")) {
                    // Routes from Depot after solving
                    handleRoutesFromDepot(msg);
                } else if (content.startsWith("STATE:")) {
                    // State response from vehicle agent
                    handleVehicleStateResponse(msg);
                } else if (content.startsWith("ROUTE_ACCEPTED:")) {
                    // Route acceptance confirmation from vehicle
                    handleRouteAcceptance(msg);
                }
            } else {
                block();
            }
        }
    }
    
    private void handleDepotRequest(ACLMessage msg) {
        try {
            String content = msg.getContent();
            System.out.println("\n=== Delivery Agent: Processing Request ===");
            
            // Minimal format supported: VEHICLES:name1:cap1,name2:cap2
            String vehicleData = null;
            if (content.startsWith("VEHICLES:")) {
                vehicleData = content.substring("VEHICLES:".length());
            } else {
                // Backward-compat: split by '|' and find VEHICLES:
                String[] parts = content.split("\\|");
                for (String part : parts) {
                    if (part.startsWith("VEHICLES:")) {
                        vehicleData = part.substring("VEHICLES:".length());
                        break;
                    }
                }
            }
            
            if (vehicleData != null) {
                Map<String, Integer> requestedVehicles = parseVehicleData(vehicleData);
                manageVehicleAgents(requestedVehicles);
            }
            
            // Query all vehicle states
            System.out.println("Delivery Agent: Querying vehicle states...");
            queryAllVehicleStates();
            
            // Wait a bit for all responses
            Thread.sleep(1000);
            
            // Count free vehicles and collect their names
            int freeCount = 0;
            List<String> freeVehicleNames = new ArrayList<>();
            for (VehicleInfo vInfo : vehicleInfo.values()) {
                if ("free".equals(vInfo.state)) {
                    freeCount++;
                    freeVehicleNames.add(vInfo.name);
                }
            }
            
            System.out.println("Delivery Agent: Found " + freeCount + " free vehicles out of " + vehicleInfo.size() + " total");
            System.out.println("Delivery Agent: Free vehicles: " + freeVehicleNames);
            
            // Send response back to Depot with minimal payload (names only). Preserve FIPA metadata.
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            // Keep protocol and conversation for FIPA compliance
            reply.setProtocol(msg.getProtocol());
            reply.setConversationId(msg.getConversationId());
            
            // Format: NAMES:name1,name2,name3
            StringBuilder namesOnly = new StringBuilder();
            namesOnly.append("NAMES:");
            for (int i = 0; i < freeVehicleNames.size(); i++) {
                if (i > 0) namesOnly.append(",");
                namesOnly.append(freeVehicleNames.get(i));
            }
            reply.setContent(namesOnly.toString());
            send(reply);
            
        } catch (Exception e) {
            System.err.println("Delivery Agent: Error handling depot request: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private Map<String, Integer> parseVehicleData(String vehicleData) {
        // Format: name1:capacity1,name2:capacity2,...
        Map<String, Integer> vehicles = new HashMap<>();
        String[] vehicleEntries = vehicleData.split(",");
        for (String entry : vehicleEntries) {
            String[] nameCapacity = entry.split(":");
            if (nameCapacity.length == 2) {
                vehicles.put(nameCapacity[0].trim(), Integer.parseInt(nameCapacity[1].trim()));
            }
        }
        return vehicles;
    }
    
    private void manageVehicleAgents(Map<String, Integer> requestedVehicles) {
        try {
            // Terminate vehicles not in the requested list
            List<String> toRemove = new ArrayList<>();
            for (String existingName : vehicleAgents.keySet()) {
                if (!requestedVehicles.containsKey(existingName)) {
                    System.out.println("Delivery Agent: Terminating vehicle agent: " + existingName);
                    // TODO: Properly kill agent
                    toRemove.add(existingName);
                }
            }
            for (String name : toRemove) {
                vehicleAgents.remove(name);
                vehicleInfo.remove(name);
            }
            
            // Create new vehicle agents for those in the list but don't exist
            for (Map.Entry<String, Integer> entry : requestedVehicles.entrySet()) {
                String name = entry.getKey();
                int capacity = entry.getValue();
                
                if (!vehicleAgents.containsKey(name)) {
                    System.out.println("Delivery Agent: Creating vehicle agent: " + name);
                    createVehicleAgent(name, capacity);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Delivery Agent: Error managing vehicle agents: " + e.getMessage());
        }
    }
    
    private void createVehicleAgent(String name, int capacity) {
        try {
            Object[] args = new Object[]{name, capacity};
            AgentController controller = getContainerController().createNewAgent(
                "vehicle-" + name,
                "project.Agent.VehicleAgent",
                args
            );
            controller.start();
            
            AID vehicleAID = new AID("vehicle-" + name, AID.ISLOCALNAME);
            vehicleAgents.put(name, vehicleAID);
            vehicleInfo.put(name, new VehicleInfo(name, capacity));
            
            System.out.println("Delivery Agent: Vehicle agent created: " + name);
            
        } catch (StaleProxyException e) {
            System.err.println("Delivery Agent: Error creating vehicle agent: " + e.getMessage());
        }
    }
    
    private void queryAllVehicleStates() {
        for (Map.Entry<String, AID> entry : vehicleAgents.entrySet()) {
            ACLMessage query = new ACLMessage(ACLMessage.QUERY_REF);
            query.addReceiver(entry.getValue());
            query.setContent("QUERY_STATE");
            send(query);
        }
    }
    
    private void handleVehicleStateResponse(ACLMessage msg) {
        try {
            // Format: STATE:free|CAPACITY:20|NAME:Thanh
            String content = msg.getContent();
            String[] parts = content.split("\\|");
            
            String state = null;
            String name = null;
            int capacity = 0;
            
            for (String part : parts) {
                if (part.startsWith("STATE:")) {
                    state = part.substring("STATE:".length());
                } else if (part.startsWith("NAME:")) {
                    name = part.substring("NAME:".length());
                } else if (part.startsWith("CAPACITY:")) {
                    capacity = Integer.parseInt(part.substring("CAPACITY:".length()));
                }
            }
            
            if (name != null && vehicleInfo.containsKey(name)) {
                VehicleInfo info = vehicleInfo.get(name);
                info.state = state;
                if (capacity > 0) {
                    info.capacity = capacity;
                }
                System.out.println("Delivery Agent: Vehicle " + name + " state: " + state);
            }
            
        } catch (Exception e) {
            System.err.println("Delivery Agent: Error handling vehicle state: " + e.getMessage());
        }
    }
    
    private void handleRoutesFromDepot(ACLMessage msg) {
        try {
            System.out.println("\n=== Delivery Agent: Assigning Routes ===");
            String content = msg.getContent();
            
            // Parse routes and assign to free vehicles
            // Format: ROUTES:request_id|ROUTE_DATA:...
            String[] parts = content.split("\\|", 2);
            String requestId = parts[0].substring("ROUTES:".length());
            
            // Remove the "ROUTE_DATA:" prefix from the route data
            String routeData = parts[1];
            if (routeData.startsWith("ROUTE_DATA:")) {
                routeData = routeData.substring("ROUTE_DATA:".length());
            }
            
            System.out.println("DEBUG - Request ID: " + requestId);
            System.out.println("DEBUG - Route data (first 100 chars): " + 
                (routeData.length() > 100 ? routeData.substring(0, 100) + "..." : routeData));
            
            // For now, store the route data and prepare to send to API
            // In a full implementation, we would parse routes and assign to specific vehicles
            
            // Wait for vehicle confirmations (simplified - wait 1 second)
            Thread.sleep(1000);
            
            // Format the final response and send to API
            String finalResponse = formatFinalResponse(requestId, routeData);
            sendSolutionToAPI(finalResponse);
            
            System.out.println("Delivery Agent: Solution sent to API");
            
        } catch (Exception e) {
            System.err.println("Delivery Agent: Error handling routes: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleRouteAcceptance(ACLMessage msg) {
        String content = msg.getContent();
        System.out.println("Delivery Agent: " + content);
    }
    
    private String formatFinalResponse(String requestId, String routeData) {
        StringBuilder response = new StringBuilder();
        response.append("{\n");
        response.append("  \"request_id\": \"").append(requestId).append("\",\n");
        
        // Add vehicle states
        response.append("  \"vehicle_state\": {\n");
        int i = 0;
        for (VehicleInfo vInfo : vehicleInfo.values()) {
            if (i > 0) response.append(",\n");
            response.append("    \"").append(vInfo.name).append("\": \"").append(vInfo.state).append("\"");
            i++;
        }
        response.append("\n  },\n");
        
        // Count available vehicles
        int freeCount = 0;
        for (VehicleInfo vInfo : vehicleInfo.values()) {
            if ("free".equals(vInfo.state)) freeCount++;
        }
        response.append("  \"available_vehicle_count\": ").append(freeCount).append(",\n");
        
        // Add the route data (from Depot)
        response.append(routeData);
        
        response.append("}");
        return response.toString();
    }
    
    private void sendSolutionToAPI(String solution) {
        try {
            System.out.println("\n=== Delivery Agent: Sending Solution to API ===");
            System.out.println("DEBUG - Solution JSON (first 500 chars):");
            System.out.println(solution.length() > 500 ? solution.substring(0, 500) + "..." : solution);
            System.out.println("DEBUG - Solution JSON length: " + solution.length() + " characters");
            
            HttpPost request = new HttpPost(API_URL + "?action=response");
            request.setHeader("Content-Type", "application/json");
            
            StringEntity entity = new StringEntity(solution, ContentType.APPLICATION_JSON);
            request.setEntity(entity);
            
            org.apache.hc.core5.http.io.HttpClientResponseHandler<Void> handler = (resp) -> {
                int statusCode = resp.getCode();
                if (statusCode == 200) {
                    System.out.println("✓ Delivery Agent: Solution sent to API successfully");
                } else {
                    System.err.println("✗ Delivery Agent: Failed to send solution to API. Status: " + statusCode);
                    if (resp.getEntity() != null) {
                        java.io.InputStream is = resp.getEntity().getContent();
                        try (java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A")) {
                            String errorBody = s.hasNext() ? s.next() : "";
                            System.err.println("Error response: " + errorBody);
                        }
                    }
                }
                return null;
            };
            httpClient.execute(request, handler);
        } catch (Exception e) {
            System.err.println("Delivery Agent: Error sending solution to API: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    protected void takeDown() {
        System.out.println("Delivery Agent " + getAID().getName() + " terminating.");
        
        // Close HTTP client
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception e) {
                System.err.println("Delivery Agent: Error closing HTTP client: " + e.getMessage());
            }
        }
    }
}
