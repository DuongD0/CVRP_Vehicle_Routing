package project.General;

/**
 * Customer information for delivery
 */
public class CustomerInfo {
    public int id;
    public double x;
    public double y;
    public int demand;  // Number of items requested
    public String name;

    public CustomerInfo(int id, double x, double y, int demand) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.demand = demand;
        this.name = "C" + id;
    }
    
    public CustomerInfo(int id, double x, double y, int demand, String name) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.demand = demand;
        this.name = name;
    }
}
