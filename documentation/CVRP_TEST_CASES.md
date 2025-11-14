# CVRP Test Cases - Standard Test Cases Documentation

This document describes the standard Capacitated Vehicle Routing Problem (CVRP) test cases that do not include time window constraints. These test cases focus on capacity, distance, and basic routing optimization.

## Test Cases Overview

### 1. `case_small.json`
**Purpose**: Basic small-scale CVRP test case  
**Description**: A simple test case with a small number of customers and vehicles. This is the baseline test to verify basic CVRP functionality without time constraints.

**Characteristics**:
- 5 customers
- 2 vehicles (DA1, DA2)
- Each vehicle has capacity of 50 items
- Each vehicle has max distance of 1000.0 units
- Total demand (50 items) = Total capacity (100 items)
- No time windows
- Expected: All customers should be served

**Customer Details**:
- C1: Demand 10, Location (10.0, 10.0)
- C2: Demand 15, Location (20.0, 20.0)
- C3: Demand 12, Location (30.0, 10.0)
- C4: Demand 8, Location (15.0, 30.0)
- C5: Demand 5, Location (25.0, 25.0)

**Depot**: Location (0.0, 0.0)

**Test Point**: Verifies basic routing optimization with capacity constraints only.

---

### 2. `case_capacity_shortfall.json`
**Purpose**: Capacity shortfall test case  
**Description**: Tests the algorithm's behavior when total customer demand exceeds total vehicle capacity. This tests the capacity shortfall handling mechanism.

**Characteristics**:
- 6 customers
- 2 vehicles (DA1, DA2)
- Each vehicle has capacity of 30 items
- Each vehicle has max distance of 1000.0 units
- Total demand (100 items) > Total capacity (60 items)
- No time windows
- Expected: Some customers will be unserved due to capacity constraints. The algorithm should prioritize maximizing the number of items delivered over minimizing distance.

**Customer Details**:
- C1: Demand 25, Location (10.0, 10.0)
- C2: Demand 20, Location (20.0, 20.0)
- C3: Demand 18, Location (30.0, 10.0)
- C4: Demand 15, Location (15.0, 30.0)
- C5: Demand 12, Location (25.0, 25.0)
- C6: Demand 10, Location (35.0, 15.0)

**Depot**: Location (0.0, 0.0)

**Test Point**: Tests the capacity shortfall algorithm that uses disjunctions with penalties to prioritize item delivery over distance minimization. See `docs/CAPACITY_SHORTFALL_ALGORITHM.md` for details.

**Edge Case**: This is a critical test case that verifies the system correctly handles situations where not all customers can be served.

---

### 3. `case_many_customers.json`
**Purpose**: Large-scale CVRP test case  
**Description**: Tests the solver's ability to handle a larger number of customers with multiple vehicles. This tests scalability and route optimization for more complex scenarios.

**Characteristics**:
- 20 customers
- 5 vehicles (DA1-DA5)
- Each vehicle has capacity of 50 items
- Each vehicle has max distance of 2000.0 units
- Total demand (200 items) = Total capacity (250 items)
- No time windows
- Expected: All customers should be served, with routes optimized across 5 vehicles

**Customer Distribution**:
- Customers are spread across a wider area (x: 10-170, y: 10-40)
- Demands range from 7 to 14 items per customer
- Locations are distributed to test route clustering and optimization

**Depot**: Location (0.0, 0.0)

**Test Point**: Tests scalability, multi-vehicle coordination, and route optimization for larger problem instances.

**Edge Case**: Verifies that the solver can efficiently handle larger problem sizes and distribute customers across multiple vehicles optimally.

---

### 4. `case_random_seeded.json`
**Purpose**: Random distribution test case  
**Description**: Tests the solver with randomly distributed customer locations and varying demands. This simulates real-world scenarios where customers are not in a regular pattern.

**Characteristics**:
- 10 customers
- 3 vehicles (DA1-DA3)
- Each vehicle has capacity of 40 items
- Each vehicle has max distance of 1500.0 units
- Total demand (105 items) < Total capacity (120 items)
- No time windows
- Customer locations use decimal coordinates (not on grid)
- Expected: All customers should be served with routes optimized for the random distribution

**Customer Details**:
- Locations use decimal coordinates (e.g., 15.5, 22.3) to test non-grid scenarios
- Demands range from 6 to 15 items
- Customers are distributed across a wider area (x: 15-72, y: 11-61)

**Depot**: Location (0.0, 0.0)

**Test Point**: Tests route optimization with non-grid customer locations and varying demand patterns, simulating real-world delivery scenarios.

**Edge Case**: Verifies that the solver handles decimal coordinates correctly and optimizes routes for irregular customer distributions.

---

### 5. `case_tight_distance.json`
**Purpose**: Maximum distance constraint test case  
**Description**: Tests the solver's ability to handle tight maximum distance constraints that may limit vehicle routes. Some customers may be unserved if they are too far from the depot or other customers.

**Characteristics**:
- 6 customers
- 3 vehicles (DA1-DA3)
- Each vehicle has capacity of 100 items
- Vehicles have different max distance constraints:
  - DA1: 50.0 units
  - DA2: 60.0 units
  - DA3: 40.0 units
- Total demand (80 items) < Total capacity (300 items)
- No time windows
- Some customers are far from depot (C5 at (50, 50), C6 at (60, 60))
- Expected: Some customers may be unserved due to distance constraints, especially C5 and C6 which are far from the depot

**Customer Details**:
- C1: Demand 10, Location (10.0, 10.0) - Distance from depot: ~14.14
- C2: Demand 15, Location (20.0, 20.0) - Distance from depot: ~28.28
- C3: Demand 12, Location (30.0, 10.0) - Distance from depot: ~31.62
- C4: Demand 8, Location (15.0, 30.0) - Distance from depot: ~33.54
- C5: Demand 20, Location (50.0, 50.0) - Distance from depot: ~70.71
- C6: Demand 15, Location (60.0, 60.0) - Distance from depot: ~84.85

**Depot**: Location (0.0, 0.0)

**Test Point**: Tests maximum distance constraints and verifies that the solver correctly identifies and handles customers that cannot be reached within distance limits.

**Edge Case**: This is a critical test case that verifies distance constraints are properly enforced, especially when combined with capacity constraints. Customers C5 and C6 are likely to be unserved due to their distance from the depot exceeding some vehicle max distance constraints.

---

## Running the Test Cases

These test cases are located in `frontend/src/config/` and can be used through the frontend interface or by submitting them to the backend API.

To use via the frontend:
1. Open the frontend application
2. Select the desired test case from the configuration dropdown
3. Submit the problem to the backend

To use via API:
```bash
POST /api/submit_problem
Content-Type: application/json

{
  "depot": { ... },
  "vehicles": [ ... ],
  "customers": [ ... ]
}
```

## Expected Results Summary

| Test Case | Expected Outcome | Key Test Point |
|-----------|------------------|----------------|
| `case_small.json` | All customers served | Basic functionality |
| `case_capacity_shortfall.json` | Some customers unserved | Capacity shortfall handling |
| `case_many_customers.json` | All customers served | Scalability and multi-vehicle optimization |
| `case_random_seeded.json` | All customers served | Random distribution handling |
| `case_tight_distance.json` | Some customers unserved | Maximum distance constraints |

## Analysis Points

When analyzing results, check for:

1. **Unserved Customers**: Are customers correctly identified as unserved? Are the reasons (capacity, distance) appropriate?
2. **Capacity Constraints**: Are capacity constraints respected? Do routes not exceed vehicle capacity?
3. **Distance Constraints**: Are maximum distance constraints respected? Do routes not exceed vehicle max distance?
4. **Route Optimization**: Are routes optimized for distance? Do vehicles serve customers in efficient order?
5. **Multi-Vehicle Coordination**: In multi-vehicle cases, are customers distributed efficiently across vehicles?
6. **Capacity Shortfall Handling**: In capacity shortfall cases, does the algorithm prioritize number of items delivered over distance minimization?

## Algorithm Behavior

### Capacity Shortfall Algorithm

When total demand exceeds total capacity, the OR-Tools solver uses **disjunctions with penalties** to handle unserved customers:

1. **Penalty Assignment**: Each customer node is assigned a large penalty (default: 100,000) for being skipped
2. **Optimization Objective**: The solver balances:
   - Minimizing total distance traveled
   - Maximizing number of items delivered (by avoiding penalties)
3. **Prioritization**: The large penalty ensures that delivering items is prioritized over minimizing distance
4. **Result**: The solver will serve as many customers as possible within capacity constraints, even if it means longer routes

For detailed information, see `docs/CAPACITY_SHORTFALL_ALGORITHM.md`.

### Distance Constraints

Maximum distance constraints are enforced per vehicle:
- Each vehicle has a `maxDistance` limit
- The total distance traveled by a vehicle (including return to depot) must not exceed this limit
- If a customer cannot be reached within the distance limit, it will be marked as unserved

### Route Optimization

The solver optimizes routes to:
- Minimize total distance traveled
- Respect capacity constraints
- Respect maximum distance constraints
- Serve as many customers as possible (when capacity is limited)

## Notes

- All distances are calculated using Euclidean distance
- Vehicle speed is assumed to be 10 units per unit time (for time calculations if needed)
- Service time at customer locations is assumed to be 0
- Vehicles start and end at the depot
- The depot location is always (0.0, 0.0) in these test cases
- No time windows are applied in these standard CVRP test cases

## Comparison with TWVRP Test Cases

These standard CVRP test cases differ from TWVRP test cases in that they:
- **Do not include time window constraints**
- Focus on capacity and distance constraints only
- Test basic routing optimization without temporal constraints
- Are simpler and faster to solve

For time-windowed test cases, see `documentation/TWVRP_TEST_CASES.md`.

