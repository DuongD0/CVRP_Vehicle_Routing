package project.General;

import project.Agent.MRA;

import java.util.ArrayList;
import java.util.List;

public class RouteInfo {
    public int vehicleId;
    public List<CustomerInfo> customers;
    public int totalDemand;
    public double totalDistance;

    public RouteInfo(int vehicleId) {
        this.vehicleId = vehicleId;
        customers = new ArrayList<>();
    }
}
