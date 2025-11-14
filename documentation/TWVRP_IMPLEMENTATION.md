# Time-Windowed Vehicle Routing Problem (TWVRP) Implementation

## Overview

This document describes the implementation of Time-Windowed Vehicle Routing Problem (TWVRP) support in the CVRP Multi-Agent System. TWVRP extends the basic CVRP by adding time window constraints that specify when each customer can be served.

## Features

1. **Optional Time Windows**: Time windows are optional in the configuration
2. **Automatic Detection**: The system automatically detects if time windows are present
3. **Backward Compatible**: Works with existing CVRP configurations (without time windows)
4. **Unserved Customer Tracking**: Tracks customers that cannot be served due to time window violations

## Configuration Format

### JSON Configuration

Time windows can be added to customer configurations in the JSON file:

```json
{
  "depot": {
    "name": "mra",
    "x": 0.0,
    "y": 0.0
  },
  "vehicles": [
    {
      "name": "DA1",
      "capacity": 30,
      "maxDistance": 1000.0
    }
  ],
  "customers": [
    {
      "id": "1",
      "demand": 25,
      "x": 10.0,
      "y": 10.0,
      "timeWindow": [0, 50]  // Optional: [earliest, latest] in minutes from depot start
    },
    {
      "id": "2",
      "demand": 20,
      "x": 20.0,
      "y": 20.0
      // No time window - customer can be served anytime
    }
  ]
}
```

### Time Window Format

- **Format**: `[earliest, latest]` (array of two integers)
- **Units**: Minutes from depot start time (time 0)
- **Required**: Optional (if not specified, customer has no time window constraint)
- **Depot**: Depot time window is automatically set to `[0, very_large_value]`

### Example Time Windows

```json
"timeWindow": [0, 50]      // Customer can be served between time 0 and 50
"timeWindow": [30, 90]     // Customer can be served between time 30 and 90
"timeWindow": [10, 20]     // Narrow time window: customer can only be served between time 10 and 20
```

## Implementation Details

### 1. Configuration Reading

Time windows are read from the JSON configuration file in `JsonConfigReader.java`:

```java
// Read optional time window
if (customerJson.has("timeWindow")) {
    JsonArray timeWindowArray = customerJson.getAsJsonArray("timeWindow");
    if (timeWindowArray.size() >= 2) {
        customer.timeWindow = new long[2];
        customer.timeWindow[0] = timeWindowArray.get(0).getAsLong();
        customer.timeWindow[1] = timeWindowArray.get(1).getAsLong();
    }
}
```

### 2. Problem Assembly

Time windows are extracted and passed to the solver in `DepotProblemAssembler.java`:

```java
// Extract time windows if available
long[][] timeWindows = null;
boolean hasTimeWindows = false;
for (CustomerRequest req : requests) {
    if (req.timeWindow != null && req.timeWindow.length >= 2) {
        hasTimeWindows = true;
        break;
    }
}
```

### 3. Solver Implementation

Time window constraints are added to the OR-Tools solver in `ORToolsSolver.java`:

```java
// Add time dimension
routing.addDimension(
    timeCallbackIndex,
    (long) 30,  // Allow 30 units of waiting time (slack max)
    maxTime,    // Maximum time per vehicle
    false,      // Don't force start cumul to zero
    "Time"
);

// Set time windows for each node
for (int node = 0; node < numNodes; node++) {
    if (timeWindows[node] != null && timeWindows[node].length >= 2) {
        long earliest = timeWindows[node][0];
        long latest = timeWindows[node][1];
        long index = manager.nodeToIndex(node);
        timeDimension.cumulVar(index).setRange(earliest, latest);
    }
}
```

### 4. Time Calculation

- **Transit Time**: Equals the distance between nodes (1 unit distance = 1 unit time)
- **Service Time**: Assumed to be 0 (no unloading time)
- **Waiting Time**: Vehicles can wait up to 30 time units at a customer location
- **Time Window Enforcement**: Vehicles must arrive at a customer within the time window

## Algorithm Behavior

### Without Time Windows (CVRP)

- Standard CVRP behavior
- Vehicles can visit customers in any order
- Only capacity and distance constraints apply

### With Time Windows (TWVRP)

- **Time Window Constraints**: Vehicles must arrive at customers within their time windows
- **Waiting**: Vehicles can wait at a customer location if they arrive early
- **Penalties**: Customers with violated time windows may be left unserved (penalty applied)
- **Route Optimization**: Routes are optimized considering both distance and time window feasibility

### Unserved Customers

Customers may be left unserved if:
1. **Capacity Constraints**: Total demand exceeds vehicle capacity
2. **Time Window Violations**: Customer cannot be reached within their time window
3. **Distance Constraints**: Customer is too far from the depot or other customers
4. **Combination**: Multiple constraints make serving the customer infeasible

## Output Format

### JSON Output

The solution includes unserved customers in the JSON output:

```json
{
  "summary": {
    "totalItemsRequested": 100,
    "totalItemsDelivered": 55,
    "totalDistance": 157.0,
    "numberOfRoutes": 2,
    "deliveryRate": 0.55,
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

### Console Output

The console output includes:
- Number of unserved customers
- List of unserved customers with their locations and demands
- Reason for unserved status (if available)

## Example Usage

### Example 1: Basic TWVRP

```json
{
  "customers": [
    {
      "id": "1",
      "demand": 10,
      "x": 10.0,
      "y": 10.0,
      "timeWindow": [0, 30]
    },
    {
      "id": "2",
      "demand": 15,
      "x": 20.0,
      "y": 20.0,
      "timeWindow": [20, 50]
    }
  ]
}
```

### Example 2: Mixed (Some customers have time windows)

```json
{
  "customers": [
    {
      "id": "1",
      "demand": 10,
      "x": 10.0,
      "y": 10.0,
      "timeWindow": [0, 30]
    },
    {
      "id": "2",
      "demand": 15,
      "x": 20.0,
      "y": 20.0
      // No time window - can be served anytime
    }
  ]
}
```

## Limitations

1. **Service Time**: Service time at customer locations is assumed to be 0
2. **Transit Time**: Transit time equals distance (1:1 ratio)
3. **Vehicle Speed**: All vehicles travel at the same speed
4. **Time Units**: Time windows are in minutes from depot start (time 0)

## Future Enhancements

1. **Variable Service Times**: Support for different service times at different customers
2. **Variable Vehicle Speeds**: Support for different vehicle speeds
3. **Time Unit Configuration**: Configurable time units (minutes, hours, etc.)
4. **Soft Time Windows**: Support for soft time windows with penalties
5. **Time Window Violation Reasons**: Detailed reasons for time window violations

## References

- [OR-Tools Vehicle Routing with Time Windows](https://developers.google.com/optimization/routing/vrptw)
- [OR-Tools Java API Documentation](https://google.github.io/or-tools/java/)

