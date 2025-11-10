package project.General;
import java.util.ArrayList;
import java.util.List;

public class SolutionResult {
    public List<RouteInfo> routes;
    public double totalDistance;
    public int itemsDelivered;  // Number of items delivered (Basic Requirement 1)
    public int itemsTotal;      // Total number of items requested
    public long solveTimeMs;    // Time taken to solve in milliseconds

    public SolutionResult() {
        routes = new ArrayList<>();
        totalDistance = 0.0;
        itemsDelivered = 0;
        itemsTotal = 0;
        solveTimeMs = 0;
    }
}
