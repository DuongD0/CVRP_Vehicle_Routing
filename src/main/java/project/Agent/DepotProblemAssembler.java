package project.Agent;

import java.util.List;

import project.General.CustomerRequest;
import project.General.VehicleInfo;
import project.General.SolutionResult;
import project.Solver.VRPSolver;
import project.Utils.AgentLogger;

/**
 * Helper class responsible for assembling VRP problem data and invoking the solver.
 * The MRA delegates problem preparation to this class to keep responsibilities
 * separated and to simplify swapping the underlying solver in the future.
 */
public class DepotProblemAssembler {

    private final VRPSolver solver;
    private final AgentLogger logger;

    public DepotProblemAssembler(VRPSolver solver, AgentLogger logger) {
        this.solver = solver;
        this.logger = logger;
    }

    /**
     * Builds the VRP problem from the provided requests and vehicles, then calls the solver.
     *
     * @param depotX depot X coordinate
     * @param depotY depot Y coordinate
     * @param requests current batch of customer requests
     * @param vehicles available vehicles gathered from the fleet
     * @return solver result (may contain zero routes if solver fails)
     */
    public SolutionResult assembleAndSolve(double depotX, double depotY,
                                           List<CustomerRequest> requests,
                                           List<VehicleInfo> vehicles) {
        int numCustomers = requests.size();
        int numNodes = numCustomers + 1; // +1 for the depot node

        double[] x = new double[numNodes];
        double[] y = new double[numNodes];
        int[] demand = new int[numNodes];

        // Depot at index 0
        x[0] = depotX;
        y[0] = depotY;
        demand[0] = 0;

        // Customers
        for (int i = 0; i < numCustomers; i++) {
            CustomerRequest req = requests.get(i);
            int idx = i + 1;
            x[idx] = req.x;
            y[idx] = req.y;
            demand[idx] = req.quantity;
        }

        // Distance matrix (Euclidean distance, rounded)
        int[][] distance = new int[numNodes][numNodes];
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                double dx = x[i] - x[j];
                double dy = y[i] - y[j];
                distance[i][j] = (int) Math.round(Math.hypot(dx, dy));
            }
        }

        int numVehicles = vehicles.size();
        int[] vehicleCapacities = new int[numVehicles];
        double[] vehicleMaxDistances = new double[numVehicles];
        for (int i = 0; i < numVehicles; i++) {
            VehicleInfo vehicle = vehicles.get(i);
            vehicleCapacities[i] = vehicle.capacity;
            vehicleMaxDistances[i] = vehicle.maxDistance;
            if (logger != null) {
                logger.logEvent("Vehicle " + (i + 1) + " (" + vehicle.name + "): Capacity=" +
                        vehicle.capacity + ", MaxDistance=" + vehicle.maxDistance);
            }
        }

        // Extract time windows if available
        long[][] timeWindows = null;
        boolean hasTimeWindows = false;
        for (CustomerRequest req : requests) {
            if (req.timeWindow != null && req.timeWindow.length >= 2) {
                hasTimeWindows = true;
                break;
            }
        }
        
        if (hasTimeWindows) {
            timeWindows = new long[numNodes][];
            // Depot time window: [0, large_value] - vehicles can start anytime
            timeWindows[0] = new long[]{0, Long.MAX_VALUE / 2}; // Use large but safe value
            
            // Customer time windows
            for (int i = 0; i < numCustomers; i++) {
                CustomerRequest req = requests.get(i);
                int idx = i + 1;
                if (req.timeWindow != null && req.timeWindow.length >= 2) {
                    timeWindows[idx] = new long[]{req.timeWindow[0], req.timeWindow[1]};
                } else {
                    // No time window for this customer - use very wide window
                    timeWindows[idx] = new long[]{0, Long.MAX_VALUE / 2};
                }
            }
            
            if (logger != null) {
                logger.logEvent("Time windows detected: TWVRP mode enabled");
            }
        }

        if (logger != null) {
            logger.logEvent("Calling VRP solver: " + numVehicles + " vehicles, " + numCustomers + " customers" +
                           (hasTimeWindows ? " (with time windows)" : ""));
        }

        return solver.solve(
            numNodes,
            numCustomers,
            numVehicles,
            vehicleCapacities,
            vehicleMaxDistances,
            demand,
            distance,
            timeWindows
        );
    }
}


