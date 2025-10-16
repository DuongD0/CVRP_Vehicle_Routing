package project.Agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.variables.IntVar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jade.core.AID;

// Support classes
import project.General.*;

public class MRA extends Agent {
    private int numVehicles;
    private int vehicleCapacity;
    private int numNodes;
    private double[] x;
    private double[] y;
    private int[] demand;
    private int[][] distance;
    private String requestId;

    @Override
    protected void setup() {
        System.out.println("MRA Agent " + getAID().getName() + " is ready.");
        System.out.println("MRA: Waiting for problem data to solve...");
        
        // Continuous GET-like request to prevent agent from termination
        addBehaviour(new ContinuousProblemSolverBehaviour());
    }

    private class ContinuousProblemSolverBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                System.out.println("\n=== MRA: Received Problem Data ===");
                if (msg.getPerformative() == ACLMessage.INFORM) {
                    System.out.println("MRA: Processing problem data from " + msg.getSender().getName());

                    // need more explanation about these types of data source
                    if (msg.getContent().startsWith("API_PROBLEM_DATA")) {
                        parseAPIProblemData(msg.getContent());
                    } else {
                        parseProblemData(msg.getContent());
                    }

                    solveVRPProblem(msg.getSender());
                    System.out.println("MRA: Problem solved and solution sent. Waiting for next problem...");
                }
                System.out.println("=== End of Problem Processing ===\n");
            } else {
                block();
            }
        }
    }

    private void parseProblemData(String content) {
        try {
            String[] lines = content.split("\n");
            
            for (String line : lines) {
                if (line.startsWith("NUM_VEHICLES:")) {
                    numVehicles = Integer.parseInt(line.substring("NUM_VEHICLES:".length()));
                } else if (line.startsWith("VEHICLE_CAPACITY:")) {
                    vehicleCapacity = Integer.parseInt(line.substring("VEHICLE_CAPACITY:".length()));
                } else if (line.startsWith("NUM_NODES:")) {
                    numNodes = Integer.parseInt(line.substring("NUM_NODES:".length()));
                } else if (line.startsWith("X_COORDINATES:")) {
                    String[] coords = line.substring("X_COORDINATES:".length()).split(",");
                    x = new double[coords.length];
                    for (int i = 0; i < coords.length; i++) {
                        x[i] = Double.parseDouble(coords[i]);
                    }
                } else if (line.startsWith("Y_COORDINATES:")) {
                    String[] coords = line.substring("Y_COORDINATES:".length()).split(",");
                    y = new double[coords.length];
                    for (int i = 0; i < coords.length; i++) {
                        y[i] = Double.parseDouble(coords[i]);
                    }
                } else if (line.startsWith("DEMANDS:")) {
                    String[] demands = line.substring("DEMANDS:".length()).split(",");
                    demand = new int[demands.length];
                    for (int i = 0; i < demands.length; i++) {
                        demand[i] = Integer.parseInt(demands[i]);
                    }
                } else if (line.startsWith("DISTANCE_MATRIX:")) {
                    String[] rows = line.substring("DISTANCE_MATRIX:".length()).split(";");
                    distance = new int[rows.length][];
                    for (int i = 0; i < rows.length; i++) {
                        String[] cols = rows[i].split(",");
                        distance[i] = new int[cols.length];
                        for (int j = 0; j < cols.length; j++) {
                            distance[i][j] = Integer.parseInt(cols[j]);
                        }
                    }
                }
            }
            
            System.out.println("MRA: Problem data parsed successfully");
            System.out.println("MRA: " + numVehicles + " vehicles, capacity " + vehicleCapacity + ", " + (numNodes-1) + " customers");
            
        } catch (Exception e) {
            System.err.println("MRA: Error parsing problem data: " + e.getMessage());
        }
    }

    private void parseAPIProblemData(String content) {
        try {
            System.out.println("MRA: Parsing problem data...");
            String[] lines = content.split("\n");
            
            // Initialize default values
            numVehicles = 0;
            vehicleCapacity = 0;
            numNodes = 0;
            x = null;
            y = null;
            demand = null;
            distance = null;
            
            for (String line : lines) {
                if (line.startsWith("REQUEST_ID:")) {
                    requestId = line.substring("REQUEST_ID:".length());
                } else if (line.startsWith("NUM_VEHICLES:")) {
                    numVehicles = Integer.parseInt(line.substring("NUM_VEHICLES:".length()));
                } else if (line.startsWith("VEHICLE_CAPACITY:")) {
                    vehicleCapacity = Integer.parseInt(line.substring("VEHICLE_CAPACITY:".length()));
                } else if (line.startsWith("NUM_CUSTOMERS:")) {
                    int numCustomers = Integer.parseInt(line.substring("NUM_CUSTOMERS:".length()));
                    numNodes = numCustomers + 1; // +1 for depot
                } else if (line.startsWith("DEPOT_X:")) {
                    // We'll handle depot coordinates later
                } else if (line.startsWith("DEPOT_Y:")) {
                    // We'll handle depot coordinates later
                } else if (line.startsWith("CUSTOMER_COORDINATES:")) {
                    // Parse customer coordinates and demands
                    String coordsAndDemands = line.substring("CUSTOMER_COORDINATES:".length());
                    String[] parts = coordsAndDemands.split("DEMANDS:");
                    if (parts.length != 2) {
                        System.err.println("MRA: Error parsing coordinates and demands");
                        return;
                    }
                    
                    String coordsStr = parts[0];
                    String demandsStr = parts[1];
                    
                    // Parse coordinates
                    String[] coordPairs = coordsStr.split(",");
                    x = new double[numNodes];
                    y = new double[numNodes];
                    
                    // Depot at index 0 (will be set from DEPOT_X, DEPOT_Y)
                    x[0] = 400; // Default depot
                    y[0] = 300;
                    
                    // Customers starting from index 1
                    for (int i = 0; i < coordPairs.length && i/2 + 1 < numNodes; i += 2) {
                        x[i/2 + 1] = Double.parseDouble(coordPairs[i]);
                        y[i/2 + 1] = Double.parseDouble(coordPairs[i + 1]);
                    }
                    
                    // Parse demands
                    String[] demandStrs = demandsStr.split(",");
                    demand = new int[numNodes];
                    demand[0] = 0; // Depot has no demand
                    for (int i = 0; i < demandStrs.length && i + 1 < numNodes; i++) {
                        demand[i + 1] = Integer.parseInt(demandStrs[i]);
                    }
                }
            }
            
            // Quick diagnostics for parsed sizes
            System.out.println("MRA: Parsed sizes -> numVehicles=" + numVehicles
                    + ", numNodes=" + numNodes
                    + ", xLen=" + (x != null ? x.length : -1)
                    + ", yLen=" + (y != null ? y.length : -1)
                    + ", demandLen=" + (demand != null ? demand.length : -1));

            // Validate that we have all required data
            if (numVehicles <= 0 || vehicleCapacity <= 0 || numNodes <= 1 || x == null || y == null || demand == null) {
                System.err.println("MRA: Invalid problem data - missing required fields");
                System.err.println("MRA: numVehicles=" + numVehicles + ", vehicleCapacity=" + vehicleCapacity + ", numNodes=" + numNodes);
                return;
            }
            
            // Calculate distance matrix
            distance = new int[numNodes][numNodes];
            for (int i = 0; i < numNodes; i++) {
                for (int j = 0; j < numNodes; j++) {
                    double dx = x[i] - x[j];
                    double dy = y[i] - y[j];
                    distance[i][j] = (int) Math.round(Math.hypot(dx, dy));
                }
            }
            
            System.out.println("MRA: API problem data parsed successfully");
            System.out.println("MRA: " + numVehicles + " vehicles, capacity " + vehicleCapacity + ", " + (numNodes-1) + " customers");
            
        } catch (Exception e) {
            System.err.println("MRA: Error parsing API problem data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void solveVRPProblem(AID sender) {
        System.out.println("\n=== MRA Agent - Solving VRP Problem ===");
        
        // Validate data before solving
        if (numVehicles <= 0 || vehicleCapacity <= 0 || numNodes <= 1 || x == null || y == null || demand == null || distance == null) {
            System.err.println("MRA: Cannot solve - invalid or missing problem data");
            SolutionResult errorResult = new SolutionResult();
            errorResult.totalDistance = 0.0;
            sendSolutionBack(sender, errorResult);
            return;
        }
        
        SolutionResult result = solveVRPWithChoco(numNodes, numNodes - 1, numVehicles, vehicleCapacity, demand, distance);
        sendSolutionBack(sender, result);
    }

    private void sendSolutionBack(AID sender, SolutionResult result) {
        try {
            ACLMessage response = new ACLMessage(ACLMessage.INFORM);
            response.addReceiver(sender);
            
            // Format solution as JSON-like string
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
            solutionJson.append("  \"total_distance\": ").append(result.totalDistance).append(",\n");
            solutionJson.append("  \"unserved_customers\": []\n");
            solutionJson.append("}");
            
            response.setContent(solutionJson.toString());
            send(response);
            
            System.out.println("MRA: Solution sent back to Depot agent");
            
        } catch (Exception e) {
            System.err.println("MRA: Error sending solution back: " + e.getMessage());
        }
    }

    private SolutionResult solveVRPWithChoco(int numNodes, int numCustomers, int numVehicles,
                                          int capacity, int[] demand, int[][] distance) {
        Model model = new Model("CVRP-Choco-Solver");
        final int depot = 0;

        // Decision variables
        // successor[i] = next node after node i in the tour
        IntVar[] successor = model.intVarArray("successor", numNodes, 0, numNodes - 1);

        // vehicle[i] = which vehicle visits customer i (0 means depot, 1..numVehicles for customers)
        IntVar[] vehicle = model.intVarArray("vehicle", numNodes, 0, numVehicles);

        // Load of each vehicle
        IntVar[] vehicleLoad = model.intVarArray("vehicleLoad", numVehicles, 0, capacity);

        // Total distance (objective to minimize)
        IntVar totalDistance = model.intVar("totalDistance", 0, 999999);

        // ==============================
        // CONSTRAINTS
        // ==============================

        // 1. Depot always assigned to vehicle 0 (special marker)
        model.arithm(vehicle[depot], "=", 0).post();

        // 2. Depot successor must be a customer (start of a route)
        model.member(successor[depot], 1, numNodes - 1).post();

        // 3. Each customer is assigned to exactly one vehicle (1..numVehicles)
        for (int i = 1; i < numNodes; i++) {
            model.member(vehicle[i], 1, numVehicles).post();
        }

        // 4. Circuit constraint: all nodes form a giant tour (will be partitioned by vehicles)
        model.circuit(successor).post();

        // 5. If two consecutive nodes are served by different vehicles,
        //    the successor must return to depot
        for (int i = 1; i < numNodes; i++) {
            IntVar nextNode = successor[i];
            IntVar nextVehicle = model.intVar("nextVeh_" + i, 0, numVehicles);
            model.element(nextVehicle, vehicle, nextNode, 0).post();
        }

        // 6. Capacity constraints: sum of demands per vehicle <= capacity
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

            // Use int array for demands (coefficients)
            int[] demandCoeffs = new int[numCustomers];
            for (int i = 0; i < numCustomers; i++) {
                demandCoeffs[i] = demand[i + 1];
            }

            IntVar totalDemand = model.intVar("totalDemand_v" + v, 0, capacity);
            model.scalar(customerOfVehicle, demandCoeffs, "=", totalDemand).post();
            model.arithm(totalDemand, "<=", capacity).post();
            model.arithm(vehicleLoad[v - 1], "=", totalDemand).post();
        }

        // 7. Distance calculation: sum of distances between successive nodes
        IntVar[] edgeDistances = new IntVar[numNodes];
        for (int i = 0; i < numNodes; i++) {
            edgeDistances[i] = model.intVar("dist_" + i, 0, 99999);
            int[] distRow = distance[i];
            model.element(edgeDistances[i], distRow, successor[i], 0).post();
        }
        model.sum(edgeDistances, "=", totalDistance).post();

        // Objective: minimize total distance
        model.setObjective(Model.MINIMIZE, totalDistance);

        // ==============================
        // SOLVE
        // ==============================

        // Configure solver time limit (in seconds)
        // You can increase this value for larger problems
        int timeLimitSeconds = 10; // Reduced for simpler problem
        model.getSolver().limitTime(timeLimitSeconds * 1000); // Convert to milliseconds

        // Optional: Configure additional solver settings
        // model.getSolver().setSearchStrategy(); // Custom search strategy
        // model.getSolver().limitNode(1000000); // Limit number of nodes explored

        System.out.println("Solving with time limit: " + timeLimitSeconds + " seconds");
        System.out.println();

        Solution solution = model.getSolver().findOptimalSolution(totalDistance, false);
        SolutionResult result = new SolutionResult();

        if (solution != null) {
            System.out.println("Solution found!");
            double totalDist = solution.getIntVal(totalDistance);
            result.totalDistance = totalDist;
            System.out.println("Total distance: " + String.format("%.2f", totalDist));
            System.out.println();

            // Extract routes per vehicle
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

            // Create route info objects
            for (int v = 1; v <= numVehicles; v++) {
                List<Integer> customers = routes.get(v);
                if (!customers.isEmpty()) {
                    RouteInfo routeInfo = new RouteInfo(v);
                    int load = 0;
                    double routeDistance = 0.0;
                    
                    // Add depot to depot distance
                    routeDistance += Math.hypot(x[0] - x[customers.get(0)], y[0] - y[customers.get(0)]);
                    
                    for (int i = 0; i < customers.size(); i++) {
                        int c = customers.get(i);
                        load += demand[c];
                        
                        // Create customer info
                        CustomerInfo customerInfo = new CustomerInfo(c, x[c], y[c], demand[c]);
                        routeInfo.customers.add(customerInfo);
                        
                        // Calculate distance to next customer or depot
                        if (i < customers.size() - 1) {
                            int nextCustomer = customers.get(i + 1);
                            routeDistance += Math.hypot(x[c] - x[nextCustomer], y[c] - y[nextCustomer]);
                        } else {
                            // Distance back to depot
                            routeDistance += Math.hypot(x[c] - x[0], y[c] - y[0]);
                        }
                    }
                    
                    routeInfo.totalDemand = load;
                    routeInfo.totalDistance = routeDistance;
                    result.routes.add(routeInfo);

                    System.out.print("Vehicle " + v + ": 0 -> ");
                    System.out.print(String.join(" -> ", customers.stream()
                            .map(String::valueOf).toArray(String[]::new)));
                    System.out.println(" -> 0 | Load: " + load + "/" + capacity + " | Distance: " + String.format("%.2f", routeDistance));
                }
            }
        } else {
            System.out.println("No solution found within the time limit.");
            System.out.println("Try relaxing constraints or increasing solver time limit.");
            result.totalDistance = 0.0;
        }
        
        return result;
    }

    @Override
    protected void takeDown() {
        System.out.println("MRA Agent " + getAID().getName() + " terminating.");
    }
}
