package project.General;

public class VehicleInfo {
    public String name;           // Vehicle agent name (e.g., "Vehicle1", "Vehicle2")
    public int capacity;          // Vehicle capacity (number of items)
    public double maxDistance;    // Maximum distance vehicle can travel (Basic Requirement 2)
    public String state;          // Current state: "free" or "absent"
    
    public VehicleInfo(String name, int capacity, double maxDistance) {
        this.name = name;
        this.capacity = capacity;
        this.maxDistance = maxDistance;
        this.state = "free";      // Default state
    }
    
    public VehicleInfo(String name, int capacity, double maxDistance, String state) {
        this.name = name;
        this.capacity = capacity;
        this.maxDistance = maxDistance;
        this.state = state;
    }
}

