package project;

import java.util.*;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.variables.IntVar;

public class Main {
    public static void main(String[] args) {
        // ==============================
        // Basic CVRP sample data (small instance)
        // ==============================
        int depot = 0;
        int numVehicles = 2;
        int vehicleCapacity = 8;

        // Coordinates (node 0 is depot)
        double[] x = {0.0, 2.0, 2.0, 5.0, 6.0, 8.0};
        double[] y = {0.0, 2.0, 5.0, 2.0, 6.0, 3.0};

        // Demands (how much each customer needs). Index 0 = depot with 0 demand.
        int[] demand = {0, 2, 4, 2, 6, 3};

        int numNodes = x.length;           // includes depot
        int numCustomers = numNodes - 1;   // excludes depot

        // Build a Euclidean distance matrix between all nodes (including depot)
        double[][] distance = new double[numNodes][numNodes];
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                double dx = x[i] - x[j];
                double dy = y[i] - y[j];
                distance[i][j] = Math.hypot(dx, dy);
            }
        }

        // Toggle which parts to run; you can also control via CLI args: "choco-only" or "cw-only"
        boolean runChocoDemo = true;
        boolean runClarkeWright = true;
        if (args != null) {
            for (String a : args) {
                String al = a == null ? "" : a.toLowerCase(Locale.ROOT);
                if (al.equals("choco-only")) { runChocoDemo = true; runClarkeWright = false; }
                if (al.equals("cw-only")) { runChocoDemo = false; runClarkeWright = true; }
            }
        }

        if (runChocoDemo) {
            // ========================================================
            // Implementation 1: A tiny Choco-Solver model (illustrative)
            // Goal: enforce "each customer is visited exactly once" by a permutation
            // NOTE: This demonstrates modeling only (not a full CVRP optimizer)
            // ========================================================
            // We model a simple permutation of customers 1..numCustomers.
            // The constraint allDifferent(order) ensures every customer appears once.
            Model chocoModel = new Model("CVRP-visit-once-demo");
            IntVar[] order = chocoModel.intVarArray("order", numCustomers, 1, numCustomers, false);
            chocoModel.allDifferent(order).post();
            Solution chocoSol = chocoModel.getSolver().findSolution();
            if (chocoSol != null) {
                StringBuilder oneTour = new StringBuilder();
                oneTour.append("Choco visit-once tour (single sequence, no capacity): 0");
                for (int p = 0; p < numCustomers; p++) {
                    oneTour.append(" -> ").append(chocoSol.getIntVal(order[p]));
                }
                oneTour.append(" -> 0");
                System.out.println(oneTour.toString());
            } else {
                System.out.println("Choco could not find a permutation (unexpected for allDifferent).");
            }
        }


        if (runClarkeWright) {
            // Clarke–Wright savings heuristic (capacity-aware), then 2-opt per route
            List<Route> routes = clarkeWright(distance, demand, vehicleCapacity);
            for (Route r : routes) {
                optimize2Opt(r, distance);
            }

            double total = 0.0;
            int vid = 1;
            for (Route r : routes) {
                double cost = routeCost(r, distance);
                total += cost;
                System.out.println("Vehicle " + vid + ": " + routeToString(r) + " | load=" + r.load + " | dist=" + String.format(java.util.Locale.US, "%.2f", cost));
                vid++;
            }
            System.out.println("Total distance: " + String.format(Locale.US, "%.2f", total));
        }
    }

	private static class Route {
		List<Integer> nodes = new ArrayList<>();
		int load;
	}

	private static class Saving {
		final int i; // customer i
		final int j; // customer j
		final double value;
		Saving(int i, int j, double value) { this.i = i; this.j = j; this.value = value; }
	}

	private static java.util.List<Route> clarkeWright(double[][] distance, int[] demand, int capacity) {
		int n = demand.length - 1; // customers 1..n
		List<Route> routes = new ArrayList<>();
		Map<Integer, Route> map = new HashMap<>();
		for (int i = 1; i <= n; i++) {
			Route r = new Route();
			r.nodes.add(0);
			r.nodes.add(i);
			r.nodes.add(0);
			r.load = demand[i];
			routes.add(r);
			map.put(i, r);
		}

		List<Saving> savings = new ArrayList<>();
		for (int i = 1; i <= n; i++) {
			for (int j = i + 1; j <= n; j++) {
				double s = distance[0][i] + distance[0][j] - distance[i][j];
				savings.add(new Saving(i, j, s));
			}
		}
		savings.sort((a, b) -> Double.compare(b.value, a.value));

		for (Saving s : savings) {
			Route ri = map.get(s.i);
			Route rj = map.get(s.j);
			if (ri == null || rj == null || ri == rj) continue;

			boolean iAtStart = ri.nodes.get(1) == s.i;
			boolean iAtEnd = ri.nodes.get(ri.nodes.size() - 2) == s.i;
			boolean jAtStart = rj.nodes.get(1) == s.j;
			boolean jAtEnd = rj.nodes.get(rj.nodes.size() - 2) == s.j;
			if (!(iAtStart || iAtEnd) || !(jAtStart || jAtEnd)) continue;
			if (ri.load + rj.load > capacity) continue;

			List<Integer> merged = new ArrayList<>();
			if (iAtStart && jAtEnd) {
				merged.addAll(rj.nodes.subList(0, rj.nodes.size() - 1));
				merged.addAll(ri.nodes.subList(1, ri.nodes.size()));
			} else if (iAtEnd && jAtStart) {
				merged.addAll(ri.nodes.subList(0, ri.nodes.size() - 1));
				merged.addAll(rj.nodes.subList(1, rj.nodes.size()));
			} else if (iAtStart && jAtStart) {
				List<Integer> mid = new ArrayList<>(rj.nodes.subList(1, rj.nodes.size() - 1));
				java.util.Collections.reverse(mid);
				merged.add(0);
				merged.addAll(mid);
				merged.addAll(ri.nodes.subList(1, ri.nodes.size()));
			} else if (iAtEnd && jAtEnd) {
				List<Integer> mid = new ArrayList<>(ri.nodes.subList(1, ri.nodes.size() - 1));
				java.util.Collections.reverse(mid);
				merged.add(0);
				merged.addAll(mid);
				merged.addAll(rj.nodes.subList(1, rj.nodes.size()));
			}
			if (merged.isEmpty()) continue;
			if (merged.get(0) != 0) merged.add(0, 0);
			if (merged.get(merged.size() - 1) != 0) merged.add(0);

			Route newRoute = new Route();
			newRoute.nodes.addAll(merged);
			newRoute.load = ri.load + rj.load;
			for (int k = 1; k < ri.nodes.size() - 1; k++) map.put(ri.nodes.get(k), newRoute);
			for (int k = 1; k < rj.nodes.size() - 1; k++) map.put(rj.nodes.get(k), newRoute);
			routes.remove(ri);
			routes.remove(rj);
			routes.add(newRoute);
		}

		return routes;
	}

	private static double routeCost(Route r, double[][] distance) {
		double cost = 0.0;
		for (int i = 0; i < r.nodes.size() - 1; i++) cost += distance[r.nodes.get(i)][r.nodes.get(i + 1)];
		return cost;
	}

	private static void optimize2Opt(Route r, double[][] distance) {
		boolean improved = true;
		while (improved) {
			improved = false;
			for (int i = 1; i < r.nodes.size() - 2; i++) {
				for (int k = i + 1; k < r.nodes.size() - 1; k++) {
					double delta = twoOptGain(r.nodes, i, k, distance);
					if (delta < -1e-9) {
						reverseSegment(r.nodes, i, k);
						improved = true;
					}
				}
			}
		}
	}

	private static double twoOptGain(List<Integer> nodes, int i, int k, double[][] distance) {
		int a = nodes.get(i - 1);
		int b = nodes.get(i);
		int c = nodes.get(k);
		int d = nodes.get(k + 1);
		double before = distance[a][b] + distance[c][d];
		double after = distance[a][c] + distance[b][d];
		return after - before; // negative means improvement
	}

	private static void reverseSegment(List<Integer> nodes, int i, int k) {
		while (i < k) {
			int tmp = nodes.get(i);
			nodes.set(i, nodes.get(k));
			nodes.set(k, tmp);
			i++; k--;
		}
	}

	private static String routeToString(Route r) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < r.nodes.size(); i++) {
			sb.append(r.nodes.get(i));
			if (i < r.nodes.size() - 1) sb.append(" -> ");
		}
		return sb.toString();
	}
}

