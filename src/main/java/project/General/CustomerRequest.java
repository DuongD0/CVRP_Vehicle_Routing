package project.General;

/**
 * Represents a customer request for items with time window constraints
 */
public class CustomerRequest {
    public String customerId;
    public String customerName;
    public double x;
    public double y;
    public String itemName;
    public int quantity;
    public String requestId;  // Unique ID for this request
    
    // Time window constraints (in minutes from start)
    public int timeWindowStart;  // Earliest delivery time
    public int timeWindowEnd;    // Latest delivery time
    public int serviceTime;      // Service time at customer (minutes)
    
    public CustomerRequest(String customerId, String customerName, double x, double y, 
                          String itemName, int quantity, int timeWindowStart, int timeWindowEnd, int serviceTime) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.x = x;
        this.y = y;
        this.itemName = itemName;
        this.quantity = quantity;
        this.timeWindowStart = timeWindowStart;
        this.timeWindowEnd = timeWindowEnd;
        this.serviceTime = serviceTime;
        this.requestId = customerId + "_" + System.currentTimeMillis();
    }
}

