# TWVRP Test Cases - Edge Cases Documentation

This document describes the test cases created to test the Time-Windowed Vehicle Routing Problem (TWVRP) implementation, focusing on edge cases and various scenarios.

## Test Cases Overview

### 1. `case_twvrp_basic.json`
**Purpose**: Basic time windows test case  
**Description**: All customers have reasonable, non-overlapping time windows. This is the baseline test to verify basic TWVRP functionality.

**Characteristics**:
- All 6 customers have time windows
- Time windows are spaced appropriately
- Total demand (83 items) < Total capacity (100 items)
- Expected: All customers should be served

**Time Windows**:
- C1: [0, 50]
- C2: [20, 70]
- C3: [40, 90]
- C4: [30, 80]
- C5: [50, 100]
- C6: [60, 110]

---

### 2. `case_twvrp_mixed.json`
**Purpose**: Mixed time windows test case  
**Description**: Some customers have time windows, some don't. Tests backward compatibility and mixed constraint scenarios.

**Characteristics**:
- 3 customers have time windows (C1, C3, C5)
- 3 customers have no time windows (C2, C4, C6)
- Total demand (83 items) < Total capacity (100 items)
- Expected: All customers should be served, but routes should respect time windows for those that have them

**Time Windows**:
- C1: [0, 50]
- C2: No time window
- C3: [40, 90]
- C4: No time window
- C5: [50, 100]
- C6: No time window

---

### 3. `case_twvrp_tight_windows.json`
**Purpose**: Tight time windows test case  
**Description**: Very narrow time windows that may cause some customers to be unserved due to time constraints.

**Characteristics**:
- All customers have tight time windows (10-unit windows)
- Time windows are sequential but very narrow
- Total demand (83 items) < Total capacity (100 items)
- Expected: Some customers may be unserved due to tight time windows

**Time Windows**:
- C1: [0, 20]
- C2: [15, 25]
- C3: [25, 35]
- C4: [30, 40]
- C5: [35, 45]
- C6: [40, 50]

**Edge Case**: Tests if the solver can handle very tight time windows and may need to skip some customers.

---

### 4. `case_twvrp_impossible.json`
**Purpose**: Impossible time windows test case  
**Description**: Time windows that are too tight and may be impossible to satisfy, especially with distance constraints.

**Characteristics**:
- All customers have very tight, overlapping time windows
- Some customers are far from depot (C2 at (50, 50))
- Time windows conflict with travel time requirements
- Total demand (83 items) < Total capacity (100 items)
- Expected: Some customers will likely be unserved due to impossible time windows

**Time Windows**:
- C1: [0, 10]
- C2: [5, 15] (at distance ~70.71 from depot)
- C3: [8, 18]
- C4: [12, 22]
- C5: [15, 25]
- C6: [18, 28]

**Edge Case**: Tests if the solver correctly identifies and handles impossible time window constraints.

---

### 5. `case_twvrp_capacity_shortfall.json`
**Purpose**: Capacity shortfall + time windows test case  
**Description**: Combines capacity shortfall (demand > capacity) with time windows. Tests how the algorithm handles multiple constraints.

**Characteristics**:
- Total demand (100 items) > Total capacity (60 items)
- All customers have time windows
- Tests capacity shortfall algorithm with time window constraints
- Expected: Some customers will be unserved due to capacity constraints, time windows may cause additional unserved customers

**Time Windows**:
- C1: [0, 50]
- C2: [20, 70]
- C3: [40, 90]
- C4: [30, 80]
- C5: [50, 100]
- C6: [60, 110]

**Edge Case**: Tests the interaction between capacity constraints and time window constraints.

---

### 6. `case_twvrp_early_late.json`
**Purpose**: Early and late time windows test case  
**Description**: Mix of early time windows (near depot start) and late time windows (far from depot start). Tests route optimization with time gaps.

**Characteristics**:
- 2 customers have early time windows (C1, C2: 0-35)
- 4 customers have late time windows (C3-C6: 100-180)
- Large time gap between early and late customers
- Total demand (83 items) < Total capacity (100 items)
- Expected: Solver should create routes that serve early customers first, then late customers

**Time Windows**:
- C1: [0, 30]
- C2: [5, 35]
- C3: [100, 150]
- C4: [110, 160]
- C5: [120, 170]
- C6: [130, 180]

**Edge Case**: Tests if the solver can handle large time gaps and optimize routes accordingly.

---

### 7. `case_twvrp_overlapping.json`
**Purpose**: Overlapping time windows test case  
**Description**: All customers have overlapping time windows. Tests route optimization when multiple customers can be served in the same time window.

**Characteristics**:
- All customers have overlapping time windows (20-90 range)
- Time windows overlap significantly
- Total demand (83 items) < Total capacity (100 items)
- Expected: Solver should optimize routes to serve customers in optimal order despite overlapping windows

**Time Windows**:
- C1: [20, 60]
- C2: [30, 70]
- C3: [40, 80]
- C4: [50, 90]
- C5: [35, 75]
- C6: [45, 85]

**Edge Case**: Tests route optimization with overlapping time windows and multiple valid visit orders.

---

### 8. `case_twvrp_wide_windows.json`
**Purpose**: Wide time windows test case  
**Description**: Very wide time windows that are effectively no constraint. Tests if wide time windows behave like no time windows.

**Characteristics**:
- All customers have very wide time windows (0-1000)
- Time windows are effectively no constraint
- Total demand (83 items) < Total capacity (100 items)
- Expected: Should behave similarly to CVRP without time windows (all customers served)

**Time Windows**:
- All customers: [0, 1000]

**Edge Case**: Tests if very wide time windows are handled correctly and don't unnecessarily constrain the solution.

---

### 9. `case_twvrp_conflicting.json`
**Purpose**: Conflicting time windows and distance test case  
**Description**: Customers at distant locations with conflicting time windows. Tests if the solver can handle spatial and temporal conflicts.

**Characteristics**:
- Customers are grouped in two locations: (10, 10) and (100, 100)
- Time windows are sequential but locations are far apart
- Max distance constraint (200.0) may be limiting
- Total demand (83 items) < Total capacity (100 items)
- Expected: Some customers may be unserved due to distance + time window conflicts

**Time Windows**:
- C1: [0, 20] at (10, 10)
- C2: [10, 30] at (100, 100)
- C3: [25, 45] at (10, 10)
- C4: [35, 55] at (100, 100)
- C5: [50, 70] at (10, 10)
- C6: [60, 80] at (100, 100)

**Edge Case**: Tests if the solver can handle conflicts between spatial constraints (distance) and temporal constraints (time windows).

---

### 10. `case_twvrp_sequential.json`
**Purpose**: Sequential time windows test case  
**Description**: Customers with strictly sequential, non-overlapping time windows. Tests if the solver can create routes that respect sequential ordering.

**Characteristics**:
- All customers have sequential, non-overlapping time windows
- Time windows are spaced 20-25 units apart
- Total demand (83 items) < Total capacity (100 items)
- Expected: Solver should create routes that visit customers in time window order

**Time Windows**:
- C1: [0, 20]
- C2: [25, 45]
- C3: [50, 70]
- C4: [75, 95]
- C5: [100, 120]
- C6: [125, 145]

**Edge Case**: Tests if the solver respects sequential time windows and creates optimal routes.

---

## Running the Test Cases

To run a specific test case, use:

```bash
java -cp ... project.Main config/case_twvrp_basic.json
```

Replace `case_twvrp_basic.json` with the desired test case file.

## Expected Results Summary

| Test Case | Expected Outcome | Key Test Point |
|-----------|------------------|----------------|
| `case_twvrp_basic.json` | All customers served | Basic functionality |
| `case_twvrp_mixed.json` | All customers served | Mixed constraints |
| `case_twvrp_tight_windows.json` | Some customers unserved | Tight time windows |
| `case_twvrp_impossible.json` | Some customers unserved | Impossible constraints |
| `case_twvrp_capacity_shortfall.json` | Some customers unserved | Capacity + time windows |
| `case_twvrp_early_late.json` | All customers served | Time gap handling |
| `case_twvrp_overlapping.json` | All customers served | Overlapping windows |
| `case_twvrp_wide_windows.json` | All customers served | Wide windows (no constraint) |
| `case_twvrp_conflicting.json` | Some customers unserved | Distance + time conflicts |
| `case_twvrp_sequential.json` | All customers served | Sequential windows |

## Analysis Points

When analyzing results, check for:

1. **Unserved Customers**: Are customers correctly identified as unserved?
2. **Time Window Compliance**: Do routes respect time window constraints?
3. **Route Optimization**: Are routes optimized for both distance and time?
4. **Capacity Constraints**: Are capacity constraints respected?
5. **Distance Constraints**: Are maximum distance constraints respected?
6. **Multiple Constraints**: How do multiple constraints interact?

## Notes

- All time windows are in **minutes from depot start** (time 0)
- Transit time equals distance (1 unit distance = 1 unit time)
- Service time at customer locations is assumed to be 0
- Vehicles can wait at customer locations if they arrive early (up to 30 time units)
- Time windows are **hard constraints** (must be satisfied or customer is unserved)

