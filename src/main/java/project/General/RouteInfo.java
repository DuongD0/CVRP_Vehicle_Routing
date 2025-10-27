package project.General;
import java.util.ArrayList;
import java.util.List;

public class RouteInfo {
    public int vehicleId;           // Numeric ID (for internal use)
    public String vehicleName;      // Vehicle agent name (e.g., "Thanh", "Chang")
    public List<CustomerInfo> customers;
    public int totalDemand;
    public double totalDistance;

    public RouteInfo(int vehicleId) {
        this.vehicleId = vehicleId;
        this.vehicleName = null;
        customers = new ArrayList<>();
    }
    
    public RouteInfo(int vehicleId, String vehicleName) {
        this.vehicleId = vehicleId;
        this.vehicleName = vehicleName;
        customers = new ArrayList<>();
    }
}
