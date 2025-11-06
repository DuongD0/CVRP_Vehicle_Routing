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
 * OR-Tools implementation of VRPSolver with Time Window support
 * Implements CVRP-TW (Capacitated Vehicle Routing Problem with Time Windows)
 */
public class ORToolsSolver implements VRPSolver {
    
    @Override
    public SolutionResult solve(int numNodes, int numCustomers, int numVehicles,
                               int vehicleCapacity, int[] demand, int[][] distance,
                               int[][] timeWindows, int[] serviceTime, int vehicleSpeed) {
        long startTime = System.currentTimeMillis();
        
        // Load OR-Tools native library
        Loader.loadNativeLibraries();
        
        System.out.println("=== OR-Tools CVRP-TW Solver ===");
        System.out.println("Nodes: " + numNodes + " (including depot)");
        System.out.println("Vehicles: " + numVehicles);
        System.out.println("Capacity: " + vehicleCapacity);
        System.out.println("Vehicle Speed: " + vehicleSpeed + " units/minute");
        
        SolutionResult result = new SolutionResult();
        
        try {
            // Create Routing Index Manager
            RoutingIndexManager manager = new RoutingIndexManager(numNodes, numVehicles, 0);
            
            // Create Routing Model
            RoutingModel routing = new RoutingModel(manager);
            
            // Create transit callback (distance in time: distance / speed)
            final int transitCallbackIndex = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
                int fromNode = manager.indexToNode(fromIndex);
                int toNode = manager.indexToNode(toIndex);
                // Convert distance to time (distance / speed)
                return (long)(distance[fromNode][toNode] / vehicleSpeed);
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
                vehicleCapacities[i] = vehicleCapacity;
            }
            
            routing.addDimensionWithVehicleCapacity(
                demandCallbackIndex,
                0,  // null capacity slack
                vehicleCapacities,  // vehicle capacities
                true,  // start cumul to zero
                "Capacity"
            );
            
            // Add time window constraint
            final int timeCallbackIndex = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
                int fromNode = manager.indexToNode(fromIndex);
                int toNode = manager.indexToNode(toIndex);
                // Travel time + service time at origin
                long travelTime = (long)(distance[fromNode][toNode] / vehicleSpeed);
                long service = (fromNode == 0) ? 0 : serviceTime[fromNode];
                return travelTime + service;
            });
            
            routing.addDimension(
                timeCallbackIndex,
                Integer.MAX_VALUE,  // Slack max value
                Integer.MAX_VALUE,  // Vehicle capacity for time
                false,  // Don't force start cumul to zero
                "Time"
            );
            
            // Set time windows for each node
            com.google.ortools.constraintsolver.RoutingDimension timeDimension = routing.getDimensionOrDie("Time");
            for (int i = 0; i < numNodes; i++) {
                long index = manager.nodeToIndex(i);
                if (i == 0) {
                    // Depot: allow all vehicles to start at time 0
                    for (int vehicle = 0; vehicle < numVehicles; vehicle++) {
                        timeDimension.cumulVar(routing.start(vehicle)).setRange(0, Integer.MAX_VALUE);
                    }
                } else {
                    // Customers: set time window
                    int start = timeWindows[i][0];
                    int end = timeWindows[i][1];
                    timeDimension.cumulVar(index).setRange(start, end);
                }
            }
            
            // Set search parameters
            RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(com.google.protobuf.Duration.newBuilder().setSeconds(30).build())
                .build();
            
            System.out.println("Solving CVRP with Time Windows...");
            
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
                        routeDistance += routing.getArcCostForVehicle(previousIndex, index, vehicleId) * vehicleSpeed;
                    }
                    
                    // Add customers to route (without coordinates - will be added by Depot)
                    if (!route.isEmpty()) {
                        for (int nodeIndex : route) {
                            routeLoad += demand[nodeIndex];
                            // CustomerInfo will be created by Depot with proper coordinates and time windows
                            routeInfo.customers.add(new CustomerInfo(nodeIndex, 0, 0, demand[nodeIndex], 
                                timeWindows[nodeIndex][0], timeWindows[nodeIndex][1], serviceTime[nodeIndex]));
                        }
                        routeInfo.totalDemand = routeLoad;
                        routeInfo.totalDistance = routeDistance;
                        result.routes.add(routeInfo);
                        
                        // Get arrival times
                        StringBuilder timeInfo = new StringBuilder();
                        index = routing.start(vehicleId);
                        while (!routing.isEnd(index)) {
                            int nodeIndex = manager.indexToNode(index);
                            if (nodeIndex != 0) {
                                long arrivalTime = solution.value(timeDimension.cumulVar(index));
                                timeInfo.append(" C").append(nodeIndex).append("@").append(arrivalTime).append("min");
                            }
                            index = solution.value(routing.nextVar(index));
                        }
                        
                        System.out.println("Vehicle " + (vehicleId + 1) + ": " + route + 
                            " | Load: " + routeLoad + "/" + vehicleCapacity + 
                            " | Distance: " + String.format("%.2f", routeDistance) +
                            timeInfo.toString());
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
}
