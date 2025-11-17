package project.Solver;

import project.General.SolutionResult;

/**
 * Abstract interface for VRP solvers.
 * Solves Capacitated Vehicle Routing Problem with maximum distance constraints.
 * Prioritizes number of items delivered over total travel distance (Basic Requirement 1).
 * Enforces maximum distance constraint per vehicle (Basic Requirement 2).
 */
public interface VRPSolver {
    /**
     * Solves a CVRP problem with capacity and maximum distance constraints.
     * 
     * Objective: Maximize number of items delivered (primary), minimize total distance (secondary)
     * Constraints: Vehicle capacity, maximum distance per vehicle
     * 
     * @param numNodes Number of nodes (customers + depot)
     * @param numCustomers Number of customers (excluding depot)
     * @param numVehicles Number of available vehicles
     * @param vehicleCapacities Array of capacities for each vehicle (number of items)
     * @param vehicleMaxDistances Array of maximum distances for each vehicle
     * @param demand Array of demands for each node (index 0 is depot, demand=0)
     * @param distance Distance matrix between nodes (straight-line distance)
     * @return SolutionResult containing routes, total distance, number of items delivered, and unserved customers
     */
    SolutionResult solve(int numNodes, int numCustomers, int numVehicles, 
                       int[] vehicleCapacities, double[] vehicleMaxDistances,
                       int[] demand, int[][] distance);
}

