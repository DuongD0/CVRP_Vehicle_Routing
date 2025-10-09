package project.Agent;

import java.util.ArrayList;
import jade.core.Agent;
import jade.core.AID;
import org.chocosolver.solver.Model;

public class Depot extends Agent {
    private ArrayList<AID> deliveries;
    private ArrayList<AID> customers;

    @Override
    protected void setup() {
        Model model = new Model("Depot");


    }

    @Override
    protected void takeDown() {

    }
}
