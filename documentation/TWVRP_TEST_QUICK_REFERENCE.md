# TWVRP Test Cases - Quick Reference

## Test Cases at a Glance

| File | Edge Case | Demand | Capacity | Expected Unserved | Key Test |
|------|-----------|--------|----------|-------------------|----------|
| `case_twvrp_basic.json` | Basic | 83 | 100 | 0 | Basic functionality |
| `case_twvrp_mixed.json` | Mixed constraints | 83 | 100 | 0 | Some with/without time windows |
| `case_twvrp_tight_windows.json` | Tight windows | 83 | 100 | Some | Very narrow time windows |
| `case_twvrp_impossible.json` | Impossible | 83 | 100 | Some | Impossible time windows |
| `case_twvrp_capacity_shortfall.json` | Capacity + TW | 100 | 60 | Some | Capacity shortfall + time windows |
| `case_twvrp_early_late.json` | Time gaps | 83 | 100 | 0 | Early vs late time windows |
| `case_twvrp_overlapping.json` | Overlapping | 83 | 100 | 0 | Overlapping time windows |
| `case_twvrp_wide_windows.json` | Wide windows | 83 | 100 | 0 | Very wide windows (no constraint) |
| `case_twvrp_conflicting.json` | Conflicts | 83 | 100 | Some | Distance + time conflicts |
| `case_twvrp_sequential.json` | Sequential | 83 | 100 | 0 | Sequential time windows |

## Quick Test Commands

```bash
# Basic test
java -cp ... project.Main config/case_twvrp_basic.json

# Mixed constraints
java -cp ... project.Main config/case_twvrp_mixed.json

# Tight windows (edge case)
java -cp ... project.Main config/case_twvrp_tight_windows.json

# Impossible windows (edge case)
java -cp ... project.Main config/case_twvrp_impossible.json

# Capacity shortfall + time windows (edge case)
java -cp ... project.Main config/case_twvrp_capacity_shortfall.json

# Early vs late windows
java -cp ... project.Main config/case_twvrp_early_late.json

# Overlapping windows
java -cp ... project.Main config/case_twvrp_overlapping.json

# Wide windows
java -cp ... project.Main config/case_twvrp_wide_windows.json

# Conflicting constraints
java -cp ... project.Main config/case_twvrp_conflicting.json

# Sequential windows
java -cp ... project.Main config/case_twvrp_sequential.json
```

## Key Edge Cases

### 1. Tight Time Windows
**File**: `case_twvrp_tight_windows.json`  
**Test**: Very narrow time windows (10 units each)  
**Expected**: Some customers may be unserved

### 2. Impossible Time Windows
**File**: `case_twvrp_impossible.json`  
**Test**: Time windows that conflict with travel time  
**Expected**: Some customers will be unserved

### 3. Capacity Shortfall + Time Windows
**File**: `case_twvrp_capacity_shortfall.json`  
**Test**: Demand > Capacity + time windows  
**Expected**: Some customers unserved (capacity + time constraints)

### 4. Conflicting Constraints
**File**: `case_twvrp_conflicting.json`  
**Test**: Distance + time window conflicts  
**Expected**: Some customers unserved due to conflicts

### 5. Mixed Constraints
**File**: `case_twvrp_mixed.json`  
**Test**: Some customers with time windows, some without  
**Expected**: All served, but routes respect time windows

## What to Check

1. **Unserved Customers**: Are customers correctly identified as unserved?
2. **Time Window Compliance**: Do routes respect time window constraints?
3. **Route Optimization**: Are routes optimized for distance and time?
4. **Multiple Constraints**: How do capacity, distance, and time windows interact?

## Expected Behavior

- **Time Windows**: Hard constraints (must be satisfied or customer is unserved)
- **Waiting**: Vehicles can wait at customer locations (up to 30 time units)
- **Transit Time**: 1 unit distance = 1 unit time
- **Service Time**: 0 (no unloading time)
- **Unserved Tracking**: All unserved customers are tracked and reported

