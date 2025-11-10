package project.Solver;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.LocalSearchMetaheuristic;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.google.ortools.constraintsolver.main;
import project.General.CustomerInfo;
import project.General.RouteInfo;
import project.General.SolutionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * OR-Tools implementation of VRPSolver with capacity and maximum distance constraints.
 * 
 * Basic Requirement 1: Prioritizes number of items delivered over total travel distance.
 *   - Uses penalty for unvisited nodes to maximize items delivered
 *   - Minimizes distance as secondary objective
 * 
 * Basic Requirement 2: Enforces maximum distance constraint per vehicle.
 *   - Adds distance dimension with vehicle-specific maximum distances
 */
public class ORToolsSolver implements VRPSolver {
    
    // Large penalty for unvisited nodes to prioritize items delivered over distance
    // This ensures maximizing items delivered is the primary objective
    private static final long UNVISITED_NODE_PENALTY = 1000000L;
    
    @Override
    public SolutionResult solve(int numNodes, int numCustomers, int numVehicles,
                               int[] vehicleCapacities, double[] vehicleMaxDistances,
                               int[] demand, int[][] distance) {
        long startTime = System.currentTimeMillis();
        
        // Load OR-Tools native library
        Loader.loadNativeLibraries();
        
        System.out.println("=== OR-Tools CVRP Solver (Basic Requirements 1 & 2) ===");
        System.out.println("Nodes: " + numNodes + " (including depot)");
        System.out.println("Customers: " + numCustomers);
        System.out.println("Vehicles: " + numVehicles);
        
        // Calculate total items requested
        int totalItems = 0;
        for (int i = 1; i < numNodes; i++) {
            totalItems += demand[i];
        }
        
        SolutionResult result = new SolutionResult();
        result.itemsTotal = totalItems;
        
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
            
            // Set arc cost (distance) - this is the secondary objective
            routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);
            
            // BASIC REQUIREMENT 1: Prioritize items delivered over distance
            // Add penalty for unvisited nodes (disallow or heavily penalize)
            // By setting a large penalty, we prioritize visiting as many nodes as possible
            // This ensures that maximizing items delivered takes precedence over minimizing distance
            for (int node = 1; node < numNodes; node++) {
                long index = manager.nodeToIndex(node);
                // Add disjunction with large penalty - solver will try to visit all nodes first
                // before considering distance minimization
                routing.addDisjunction(new long[]{index}, UNVISITED_NODE_PENALTY);
            }
            
            // Add capacity constraint
            final int demandCallbackIndex = routing.registerUnaryTransitCallback((long fromIndex) -> {
                int fromNode = manager.indexToNode(fromIndex);
                return demand[fromNode];
            });
            
            // Convert vehicle capacities to long array
            long[] vehicleCapacitiesLong = new long[numVehicles];
            for (int i = 0; i < numVehicles; i++) {
                vehicleCapacitiesLong[i] = vehicleCapacities[i];
            }
            
            routing.addDimensionWithVehicleCapacity(
                demandCallbackIndex,
                0,  // null capacity slack
                vehicleCapacitiesLong,  // vehicle capacities
                true,  // start cumul to zero
                "Capacity"
            );
            
            // BASIC REQUIREMENT 2: Add maximum distance constraint per vehicle
            final int distanceCallbackIndex = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
                int fromNode = manager.indexToNode(fromIndex);
                int toNode = manager.indexToNode(toIndex);
                return distance[fromNode][toNode];
            });
            
            // Convert vehicle max distances to long array (round to nearest integer)
            long[] vehicleMaxDistancesLong = new long[numVehicles];
            for (int i = 0; i < numVehicles; i++) {
                vehicleMaxDistancesLong[i] = Math.round(vehicleMaxDistances[i]);
            }
            
            routing.addDimensionWithVehicleCapacity(
                distanceCallbackIndex,
                0,  // null distance slack
                vehicleMaxDistancesLong,  // vehicle maximum distances
                true,  // start cumul to zero
                "Distance"
            );
            
            // Set search parameters
            RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(com.google.protobuf.Duration.newBuilder().setSeconds(30).build())
                .build();
            
            System.out.println("Solving CVRP with capacity and maximum distance constraints...");
            System.out.println("Objective: Maximize items delivered (primary), minimize distance (secondary)");
            
            // Solve
            Assignment solution = routing.solveWithParameters(searchParameters);
            
            if (solution != null) {
                long endTime = System.currentTimeMillis();
                result.solveTimeMs = endTime - startTime;
                
                System.out.println("Solution found in " + result.solveTimeMs + " ms!");
                System.out.println("Objective value: " + solution.objectiveValue());
                
                // Extract routes
                int totalItemsDelivered = 0;
                double totalDist = 0.0;
                
                // Get distance dimension to extract route distances
                com.google.ortools.constraintsolver.RoutingDimension distanceDimension = 
                    routing.getDimensionOrDie("Distance");
                
                for (int vehicleId = 0; vehicleId < numVehicles; vehicleId++) {
                    long index = routing.start(vehicleId);
                    RouteInfo routeInfo = new RouteInfo(vehicleId + 1);
                    int routeLoad = 0;
                    List<Integer> route = new ArrayList<>();
                    List<Integer> nodeSequence = new ArrayList<>();
                    
                    // Extract route sequence
                    while (!routing.isEnd(index)) {
                        int nodeIndex = manager.indexToNode(index);
                        nodeSequence.add(nodeIndex);
                        if (nodeIndex != 0) {  // Skip depot
                            route.add(nodeIndex);
                            routeLoad += demand[nodeIndex];
                        }
                        index = solution.value(routing.nextVar(index));
                    }
                    
                    // Calculate route distance from distance dimension
                    // The distance dimension tracks cumulative distance at each node
                    long routeDistanceLong = solution.value(
                        distanceDimension.cumulVar(routing.end(vehicleId))
                    );
                    double routeDistance = (double) routeDistanceLong;
                    
                    // Add customers to route
                    if (!route.isEmpty()) {
                        for (int nodeIndex : route) {
                            // CustomerInfo will be created by Depot with proper coordinates
                            routeInfo.customers.add(new CustomerInfo(nodeIndex, 0, 0, demand[nodeIndex]));
                        }
                        routeInfo.totalDemand = routeLoad;
                        routeInfo.totalDistance = routeDistance;
                        result.routes.add(routeInfo);
                        
                        totalItemsDelivered += routeLoad;
                        totalDist += routeDistance;
                        
                        System.out.println("Vehicle " + (vehicleId + 1) + ": " + route + 
                            " | Items: " + routeLoad + "/" + vehicleCapacities[vehicleId] + 
                            " | Distance: " + String.format("%.2f", routeDistance) + 
                            "/" + String.format("%.2f", vehicleMaxDistances[vehicleId]));
                    }
                }
                
                result.totalDistance = totalDist;
                result.itemsDelivered = totalItemsDelivered;
                
                System.out.println("\n=== Solution Summary ===");
                System.out.println("Total items delivered: " + result.itemsDelivered + "/" + result.itemsTotal);
                System.out.println("Total distance: " + String.format("%.2f", result.totalDistance));
                System.out.println("Number of routes: " + result.routes.size());
                
            } else {
                long endTime = System.currentTimeMillis();
                result.solveTimeMs = endTime - startTime;
                System.out.println("No solution found within time limit.");
                result.totalDistance = 0.0;
                result.itemsDelivered = 0;
            }
            
        } catch (Exception e) {
            System.err.println("Error during OR-Tools solving: " + e.getMessage());
            e.printStackTrace();
            result.totalDistance = 0.0;
            result.itemsDelivered = 0;
        }
        
        System.out.println("=== Solving Complete ===\n");
        return result;
    }
}
