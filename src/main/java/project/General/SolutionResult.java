package project.General;
import java.util.ArrayList;
import java.util.List;

public class SolutionResult {
    public List<RouteInfo> routes;
    public double totalDistance;
    public long solveTimeMs;  // Time taken to solve in milliseconds

    public SolutionResult() {
        routes = new ArrayList<>();
        solveTimeMs = 0;
    }
}
