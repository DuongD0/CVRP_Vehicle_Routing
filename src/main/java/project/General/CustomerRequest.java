package project.General;

/**
 * Represents a customer request for items
 * Optionally includes time windows for TWVRP
 */
public class CustomerRequest {
    public String customerId;
    public String customerName;
    public double x;
    public double y;
    public String itemName;
    public int quantity;
    public String requestId;  // Unique ID for this request
    public long[] timeWindow;  // Time window [earliest, latest] in minutes from depot start, null if no time window
    
    public CustomerRequest(String customerId, String customerName, double x, double y, 
                          String itemName, int quantity) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.x = x;
        this.y = y;
        this.itemName = itemName;
        this.quantity = quantity;
        this.requestId = customerId + "_" + System.currentTimeMillis();
        this.timeWindow = null;
    }
    
    public CustomerRequest(String customerId, String customerName, double x, double y, 
                          String itemName, int quantity, long[] timeWindow) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.x = x;
        this.y = y;
        this.itemName = itemName;
        this.quantity = quantity;
        this.requestId = customerId + "_" + System.currentTimeMillis();
        this.timeWindow = timeWindow;
    }
}

