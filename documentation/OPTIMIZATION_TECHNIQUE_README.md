# Optimization Technique Documentation

## Overview

This document explains the optimization technique used to solve the Capacitated Vehicle Routing Problem (CVRP) with capacity and maximum distance constraints, implementing Basic Requirements 1 and 2.

## Current Implementation: OR-Tools with Custom Configuration

### Approach

The current implementation uses **Google OR-Tools** constraint programming solver with custom configuration to meet the requirements. While OR-Tools is an existing solver, it is configured in a specific way to prioritize items delivered over distance and enforce maximum distance constraints.

### Why OR-Tools?

OR-Tools provides a robust constraint programming framework that allows:
- Adding custom constraints (capacity, distance)
- Configuring objective functions
- Using disjunctions to handle optional nodes
- Efficiently solving VRP problems

However, **this is a baseline implementation**. A custom optimization algorithm (GA, ACO, PSO, CSP) should be implemented as the primary solver to meet the assignment requirements.

---

## Basic Requirement 1: Prioritize Items Delivered Over Distance

### Problem Statement

The solution must prioritize the number of items delivered over total travel distance. If Solution 1 can deliver 21 items with total distance 500 and Solution 2 can only deliver 20 items with total distance 400, then Solution 1 is better.

### Implementation Strategy

**Multi-Objective Optimization with Weighted Priorities:**

1. **Primary Objective: Maximize Items Delivered**
   - Use disjunction with large penalty for unvisited nodes
   - Penalty value: `UNVISITED_NODE_PENALTY = 1,000,000`
   - This ensures that visiting a node (delivering items) is always preferred over distance minimization

2. **Secondary Objective: Minimize Total Distance**
   - Set arc cost evaluator to distance
   - This minimizes distance as a secondary objective when items delivered is equal

### OR-Tools Implementation

```java
// Add penalty for unvisited nodes (Basic Requirement 1)
for (int node = 1; node < numNodes; node++) {
    long index = manager.nodeToIndex(node);
    // Large penalty ensures maximizing items delivered takes precedence
    routing.addDisjunction(new long[]{index}, UNVISITED_NODE_PENALTY);
}

// Set arc cost (distance) - secondary objective
routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);
```

### How It Works

1. **Disjunction with Penalty:**
   - Each customer node is added as a disjunction (optional node)
   - If a node is not visited, a penalty of 1,000,000 is added to the objective
   - This ensures that visiting nodes (delivering items) is heavily prioritized

2. **Objective Function:**
   - Objective = (Number of unvisited nodes × 1,000,000) + Total distance
   - Since the penalty is much larger than typical distances, the solver will first try to minimize unvisited nodes (maximize items delivered)
   - Once items delivered is maximized, the solver minimizes distance as a secondary objective

3. **Example:**
   - Solution 1: 21 items delivered, distance 500 → Objective = 79 × 1,000,000 + 500 = 79,000,500
   - Solution 2: 20 items delivered, distance 400 → Objective = 80 × 1,000,000 + 400 = 80,000,400
   - Solution 1 is better (lower objective value)

### Handling Insufficient Capacity

When total vehicle capacity < total items requested:
- Solver will naturally skip some nodes due to capacity constraints
- Disjunction allows nodes to be skipped (with penalty)
- Solver will maximize items delivered within capacity constraints
- Some items will remain undelivered (queued for next batch or rejected)

---

## Basic Requirement 2: Maximum Distance Constraint Per Vehicle

### Problem Statement

Each vehicle v can only travel a maximum distance dv. Routes must respect this constraint.

### Implementation Strategy

**Distance Dimension with Vehicle-Specific Limits:**

1. **Add Distance Dimension:**
   - Create distance callback that returns distance between nodes
   - Add dimension with vehicle capacity (maximum distance per vehicle)
   - Enforce cumulative distance ≤ maxDistance for each vehicle

2. **Vehicle-Level Enforcement:**
   - Each vehicle has its own maximum distance constraint
   - Solver ensures that cumulative distance for each vehicle route ≤ vehicle's maxDistance
   - Vehicles also check distance constraint during bidding phase

### OR-Tools Implementation

```java
// Create distance callback
final int distanceCallbackIndex = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
    int fromNode = manager.indexToNode(fromIndex);
    int toNode = manager.indexToNode(toIndex);
    return distance[fromNode][toNode];
});

// Convert vehicle max distances to long array
long[] vehicleMaxDistancesLong = new long[numVehicles];
for (int i = 0; i < numVehicles; i++) {
    vehicleMaxDistancesLong[i] = Math.round(vehicleMaxDistances[i]);
}

// Add distance dimension with vehicle capacity (Basic Requirement 2)
routing.addDimensionWithVehicleCapacity(
    distanceCallbackIndex,
    0,  // null distance slack
    vehicleMaxDistancesLong,  // vehicle maximum distances
    true,  // start cumul to zero
    "Distance"
);
```

### How It Works

1. **Distance Dimension:**
   - Tracks cumulative distance traveled by each vehicle
   - Starts at 0 at the depot
   - Increases as the vehicle visits customers
   - Cannot exceed vehicle's maxDistance

2. **Constraint Enforcement:**
   - Solver ensures that for each vehicle: cumulative distance ≤ maxDistance
   - If a route would exceed maxDistance, the solver will not include it in the solution
   - Vehicles also check this constraint during bidding phase

3. **Distance Calculation:**
   - Route distance includes: depot → customers → depot
   - Vehicle bidding calculates: current position → first customer + route distance + last customer → depot
   - Total distance must be ≤ vehicle's maxDistance

### Vehicle Bidding Check

Vehicles check maximum distance constraint before bidding:

```java
// Calculate total distance: current position → first customer + route distance + last customer → depot
double totalRouteDistance = routeDistance;
if (firstCustomerPos != null) {
    totalRouteDistance += Math.hypot(currentX - firstCustomerPos[0], currentY - firstCustomerPos[1]);
}
if (lastCustomerPos != null) {
    totalRouteDistance += Math.hypot(lastCustomerPos[0] - depotX, lastCustomerPos[1] - depotY);
}

if (totalRouteDistance > maxDistance) {
    // Refuse route
    return refuse;
}
```

---

## Custom Optimization Algorithm (Future Implementation)

### Recommended Approaches

While the current implementation uses OR-Tools, a custom optimization algorithm should be implemented. Recommended approaches:

#### 1. Genetic Algorithm (GA)

**Overview:**
- Population-based evolutionary algorithm
- Represents solutions as chromosomes (route sequences)
- Uses crossover, mutation, and selection operations

**Implementation Steps:**
1. **Chromosome Encoding:** Represent routes as sequences of customer IDs
2. **Initial Population:** Generate random valid routes
3. **Fitness Function:** 
   - Primary: Maximize items delivered (penalty for undelivered items)
   - Secondary: Minimize total distance
4. **Selection:** Tournament selection or roulette wheel
5. **Crossover:** Order crossover or partially mapped crossover
6. **Mutation:** Swap customers, insert customers, remove customers
7. **Constraint Handling:** Repair invalid solutions (capacity, distance)

**Advantages:**
- Can handle complex constraints
- Good exploration of solution space
- Can be parallelized

#### 2. Ant Colony Optimization (ACO)

**Overview:**
- Inspired by ant foraging behavior
- Uses pheromone trails to guide search
- Ants construct solutions probabilistically

**Implementation Steps:**
1. **Pheromone Matrix:** Track pheromone levels between nodes
2. **Ant Construction:** Ants build routes using pheromone and heuristic information
3. **Heuristic Information:** Distance-based or capacity-based
4. **Pheromone Update:** Update pheromone based on solution quality
5. **Constraint Handling:** Check capacity and distance during construction

**Advantages:**
- Good for routing problems
- Can handle dynamic constraints
- Good balance between exploration and exploitation

#### 3. Particle Swarm Optimization (PSO)

**Overview:**
- Population-based algorithm inspired by bird flocking
- Particles move through solution space
- Uses velocity and position updates

**Implementation Steps:**
1. **Particle Encoding:** Represent solution as particle position
2. **Velocity Update:** Update velocity based on personal best and global best
3. **Position Update:** Update position based on velocity
4. **Fitness Evaluation:** Evaluate solution quality
5. **Constraint Handling:** Repair or penalize invalid solutions

**Advantages:**
- Simple to implement
- Fast convergence
- Good for continuous optimization

#### 4. Constraint Satisfaction Problem (CSP)

**Overview:**
- Formulate problem as constraint satisfaction problem
- Use constraint propagation and backtracking
- Systematic search through solution space

**Implementation Steps:**
1. **Variables:** Customer assignments to vehicles
2. **Domains:** Possible vehicle assignments for each customer
3. **Constraints:** Capacity, distance, and other constraints
4. **Search:** Backtracking with constraint propagation
5. **Optimization:** Minimize objective function while satisfying constraints

**Advantages:**
- Guarantees constraint satisfaction
- Systematic search
- Good for problems with many constraints

### Implementation Priority

For this project, **Genetic Algorithm (GA)** is recommended because:
1. Well-suited for VRP problems
2. Can handle multiple objectives (items delivered, distance)
3. Can handle complex constraints (capacity, distance)
4. Relatively straightforward to implement
5. Good balance between solution quality and computation time

---

## Comparison: OR-Tools vs Custom Algorithm

### OR-Tools (Current Implementation)

**Advantages:**
- Robust and well-tested
- Efficient constraint handling
- Good solution quality
- Fast solving time

**Disadvantages:**
- Not a custom algorithm (assignment requirement)
- Limited customization
- Dependency on external library

### Custom Algorithm (Recommended)

**Advantages:**
- Meets assignment requirement (custom implementation)
- Full control over algorithm
- Can be tailored to specific problem
- Educational value

**Disadvantages:**
- Requires more implementation effort
- May have lower solution quality initially
- May require more tuning

---

## Future Work

1. **Implement Custom Genetic Algorithm:**
   - Chromosome encoding for routes
   - Fitness function prioritizing items delivered
   - Crossover and mutation operations
   - Constraint handling (capacity, distance)

2. **Implement Custom ACO:**
   - Pheromone matrix
   - Ant construction with constraints
   - Pheromone update rules

3. **Hybrid Approach:**
   - Combine multiple algorithms
   - Use OR-Tools for initial solution
   - Use GA/ACO for improvement

4. **Performance Optimization:**
   - Parallelize algorithm execution
   - Optimize constraint checking
   - Improve solution quality

---

## References

- Google OR-Tools Documentation: https://developers.google.com/optimization
- Vehicle Routing Problem: https://en.wikipedia.org/wiki/Vehicle_routing_problem
- Genetic Algorithms: https://en.wikipedia.org/wiki/Genetic_algorithm
- Ant Colony Optimization: https://en.wikipedia.org/wiki/Ant_colony_optimization_algorithms
- Particle Swarm Optimization: https://en.wikipedia.org/wiki/Particle_swarm_optimization
- Constraint Satisfaction: https://en.wikipedia.org/wiki/Constraint_satisfaction_problem

---

## Conclusion

The current implementation uses OR-Tools with custom configuration to meet Basic Requirements 1 and 2. While this provides a working solution, a custom optimization algorithm (GA, ACO, PSO, or CSP) should be implemented to fully meet the assignment requirements. The OR-Tools implementation serves as a baseline for comparison and testing.

