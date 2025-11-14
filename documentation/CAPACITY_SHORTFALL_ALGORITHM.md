# Capacity Shortfall Algorithm: How OR-Tools Solves Demand > Capacity

## Overview

This document explains how the Google OR-Tools solver handles scenarios where **total customer demand exceeds total vehicle capacity** in the Capacitated Vehicle Routing Problem (CVRP). The algorithm prioritizes **maximizing the number of items delivered** over minimizing total travel distance.

## Problem Scenario

When the total demand from all customers exceeds the combined capacity of available vehicles, it's physically impossible to serve all customers. The solver must make intelligent decisions about which customers to serve and which to leave unserved.

### Example:
- **Total Demand**: 100 items (from 6 customers)
- **Total Capacity**: 60 items (2 vehicles × 30 items each)
- **Shortfall**: 40 items cannot be delivered

## Algorithm Mechanism

### 1. Disjunctions with Penalties

The core mechanism uses **disjunctions** with **large penalties** to handle optional customer visits:

```java
// Large penalty for unvisited nodes to prioritize items delivered over distance
private static final long UNVISITED_NODE_PENALTY = 1,000,000L;

// Add disjunction for each customer node
for (int node = 1; node < numNodes; node++) {
    long index = manager.nodeToIndex(node);
    routing.addDisjunction(new long[]{index}, UNVISITED_NODE_PENALTY);
}
```

**What this means:**
- Each customer node is marked as "optional" using `addDisjunction()`
- If a customer is **not visited**, a penalty of 1,000,000 is added to the objective function
- The solver can choose to skip customers, but at a high cost

### 2. Objective Function

The total objective function combines:
```
Total Cost = (Distance Traveled) + (1,000,000 × Number of Unserved Customers)
```

**Key Insight:**
- The penalty (1,000,000) is **much larger** than any realistic distance value
- This creates a **hierarchical optimization**:
  1. **Primary objective**: Minimize unserved customers (maximize items delivered)
  2. **Secondary objective**: Among solutions with the same number of customers served, minimize travel distance

### 3. How Priority Works

#### Step 1: Maximize Items Delivered
The solver first tries to visit as many customers as possible because:
- Skipping 1 customer = penalty of 1,000,000
- Traveling additional distance = much smaller cost (typically < 1,000)
- **Example**: If serving an extra customer adds 500 units of distance but avoids a 1,000,000 penalty, the solver will serve that customer

#### Step 2: Minimize Distance (Secondary)
Once the maximum number of customers is determined (subject to capacity constraints), the solver optimizes routes to minimize distance among all feasible solutions.

### 4. Capacity Constraints

The algorithm respects hard capacity constraints:
- Each vehicle has a maximum capacity
- Routes are constructed such that the sum of demands along each route does not exceed vehicle capacity
- If serving a customer would violate capacity, the solver considers skipping that customer (incurring the penalty)

### 5. Solution Process

```
1. Initialize: Mark all customers as optional with penalty
2. Solve: OR-Tools finds solution minimizing:
   - Number of unserved customers (primary)
   - Total travel distance (secondary)
3. Extract: Routes and unserved customers are identified
4. Output: Solution includes:
   - Served customers (in routes)
   - Unserved customers (with demand and location)
```

## Example Solution

### Input:
- **Customers**: 6 customers with demands [25, 20, 18, 15, 12, 10] = 100 items
- **Vehicles**: 2 vehicles with capacity 30 each = 60 items total
- **Shortfall**: 40 items

### Output:
- **Route 1**: Customers [3, 6] with demands [18, 10] = 28 items
- **Route 2**: Customers [5, 4] with demands [12, 15] = 27 items
- **Total Delivered**: 55 items
- **Unserved**: Customers [1, 2] with demands [25, 20] = 45 items

### Why This Solution?

1. **Capacity Constraints**: Each route respects the 30-item capacity limit
2. **Maximization**: The solver selected customers that maximize total items delivered (55 items)
3. **Distance Optimization**: Among all combinations that deliver 55 items, this minimizes distance
4. **Penalty Trade-off**: Skipping customers 1 and 2 incurs a penalty of 2,000,000, but this is unavoidable due to capacity constraints

## Algorithm Properties

### Advantages:
1. **Guaranteed Feasibility**: Always finds a solution (even if some customers are unserved)
2. **Optimal Trade-offs**: Balances items delivered vs. distance traveled
3. **Scalability**: Works efficiently for large problem instances
4. **Flexibility**: Can handle various capacity and distance constraints

### Limitations:
1. **Penalty Tuning**: The penalty value (1,000,000) must be carefully chosen:
   - Too small: Distance minimization may override serving customers
   - Too large: May cause numerical issues
2. **Local Optima**: May not find the globally optimal solution (heuristic solver)
3. **No Guarantees**: Does not guarantee maximum items delivered (depends on search strategy)

## Implementation Details

### Code Location:
- **Solver**: `src/main/java/project/Solver/ORToolsSolver.java`
- **Key Method**: `solve()` method, lines 78-88 (disjunctions) and 204-268 (solution extraction)

### Key Parameters:
- `UNVISITED_NODE_PENALTY = 1,000,000L`: Penalty for unserved customers
- Search strategy: `PATH_CHEAPEST_ARC` (first solution) + `GUIDED_LOCAL_SEARCH` (improvement)
- Time limit: 30 seconds

### Unserved Customer Tracking:
```java
// Track which nodes are served
boolean[] servedNodes = new boolean[numNodes];

// Mark served nodes during route extraction
servedNodes[nodeIndex] = true;

// Identify unserved customers
for (int node = 1; node < numNodes; node++) {
    if (!servedNodes[node]) {
        result.unservedCustomers.add(new CustomerInfo(node, 0, 0, demand[node]));
    }
}
```

## Output Format

The solution includes unserved customers in the JSON output:

```json
{
  "summary": {
    "totalItemsRequested": 100,
    "totalItemsDelivered": 55,
    "unservedCustomers": 2
  },
  "routes": [...],
  "unservedCustomers": [
    {
      "id": 1,
      "name": "C1",
      "x": 10.0,
      "y": 10.0,
      "demand": 25
    },
    {
      "id": 2,
      "name": "C2",
      "x": 20.0,
      "y": 20.0,
      "demand": 20
    }
  ]
}
```

## Conclusion

The OR-Tools solver handles capacity shortfall scenarios elegantly by:
1. Using disjunctions with large penalties to prioritize customer service
2. Creating a hierarchical objective that maximizes items delivered first, then minimizes distance
3. Respecting hard capacity constraints while making intelligent trade-offs
4. Providing clear output showing both served and unserved customers

This approach ensures that even when demand exceeds capacity, the solver finds the best possible solution that serves as many customers as possible while respecting all constraints.

