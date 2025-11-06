package project.Solver;

import project.General.SolutionResult;

/**
 * Abstract interface for VRP solvers.
 * Allows swapping between different solver implementations (OR-Tools, CPLEX, custom heuristics, etc.)
 */
public interface VRPSolver {
    /**
     * Solves a CVRP with Time Windows problem
     * 
     * @param numNodes Number of nodes (customers + depot)
     * @param numCustomers Number of customers (excluding depot)
     * @param numVehicles Number of available vehicles
     * @param vehicleCapacity Capacity of each vehicle
     * @param demand Array of demands for each node (index 0 is depot, demand=0)
     * @param distance Distance matrix between nodes
     * @param timeWindows Array of [start, end] time windows for each node (index 0 is depot)
     * @param serviceTime Array of service times for each node (index 0 is depot, serviceTime=0)
     * @param vehicleSpeed Speed of vehicles (distance units per minute)
     * @return SolutionResult containing routes and total distance
     */
    SolutionResult solve(int numNodes, int numCustomers, int numVehicles, 
                       int vehicleCapacity, int[] demand, int[][] distance,
                       int[][] timeWindows, int[] serviceTime, int vehicleSpeed);
}

