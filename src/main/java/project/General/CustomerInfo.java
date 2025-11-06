package project.General;

/**
 * Customer information with time window constraints
 */
public class CustomerInfo {
    public int id;
    public double x;
    public double y;
    public int demand;
    public String name;
    
    // Time window constraints (in minutes from start)
    public int timeWindowStart;  // Earliest arrival time
    public int timeWindowEnd;    // Latest arrival time
    public int serviceTime;     // Service time at customer (minutes)

    public CustomerInfo(int id, double x, double y, int demand) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.demand = demand;
        this.name = "C" + id;
        this.timeWindowStart = 0;
        this.timeWindowEnd = Integer.MAX_VALUE;
        this.serviceTime = 0;
    }
    
    public CustomerInfo(int id, double x, double y, int demand, int timeWindowStart, int timeWindowEnd, int serviceTime) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.demand = demand;
        this.name = "C" + id;
        this.timeWindowStart = timeWindowStart;
        this.timeWindowEnd = timeWindowEnd;
        this.serviceTime = serviceTime;
    }
}
