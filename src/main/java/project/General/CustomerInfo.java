package project.General;

public class CustomerInfo {
    public int id;
    public double x;
    public double y;
    public int demand;
    public String name;

    public CustomerInfo(int id, double x, double y, int demand) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.demand = demand;
        this.name = "C" + id;
    }
}
