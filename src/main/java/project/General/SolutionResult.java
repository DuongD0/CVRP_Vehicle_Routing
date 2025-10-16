package project.General;

import project.Agent.MRA;

import java.util.ArrayList;
import java.util.List;

public class SolutionResult {
    public List<RouteInfo> routes;
    public double totalDistance;
    public List<Integer> unservedCustomers;

    public SolutionResult() {
        routes = new ArrayList<>();
        unservedCustomers = new ArrayList<>();
    }
}
