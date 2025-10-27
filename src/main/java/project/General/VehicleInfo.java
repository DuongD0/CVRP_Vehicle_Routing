package project.General;

public class VehicleInfo {
    public String name;           // Vehicle agent name (e.g., "Thanh", "Chang")
    public int capacity;          // Vehicle capacity
    public String state;          // Current state: "free" or "absent"
    
    public VehicleInfo(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
        this.state = "free";      // Default state
    }
    
    public VehicleInfo(String name, int capacity, String state) {
        this.name = name;
        this.capacity = capacity;
        this.state = state;
    }
}

