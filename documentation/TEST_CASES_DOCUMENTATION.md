# Test Cases Documentation

This document provides complete scenarios, workflow diagrams, communication messages, and expected outcomes for the four test cases in the Delivery Vehicle Routing Problem (CVRP) multi-agent system.

## System Overview

The system consists of:
- **Master Routing Agent (MRA)**: Receives customer requests, queries vehicle information, solves CVRP problems, and assigns routes to delivery agents
- **Delivery Agents (DAs)**: Represent vehicles with capacity and maximum distance constraints. They respond to queries, receive route assignments, execute routes, and notify MRA when returning to depot

---

## Test Case 1: Basic Agent Communication (Basic CVRP)

### Scenario Description

This test case validates the fundamental agent communication workflow for a basic CVRP problem where all customers can be served in a single round. All vehicles have sufficient capacity and maximum distance to serve all customers.

### Test Configuration

- **Vehicles**: 4 vehicles (DA1, DA2, DA3, DA4)
  - Capacity: 50 items each
  - Max Distance: 1000.0 units each
- **Customers**: 5 customers
  - Customer 1: demand=10, location=(10.0, 10.0)
  - Customer 2: demand=15, location=(20.0, 20.0)
  - Customer 3: demand=20, location=(30.0, 10.0)
  - Customer 4: demand=12, location=(15.0, 25.0)
  - Customer 5: demand=18, location=(25.0, 15.0)
- **Total Demand**: 75 items
- **Total Capacity**: 200 items (4 vehicles × 50)
- **Depot**: Location (0.0, 0.0)

### Workflow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    TEST CASE 1: BASIC CVRP                      │
└─────────────────────────────────────────────────────────────────┘

[Backend API] ──Request──> [MRA]
                              │
                              ├─1. Query Vehicle Info──> [DA1, DA2, DA3, DA4]
                              │
                              │<─2. Vehicle Info Response──┤
                              │   (Capacity, MaxDistance, Name, Position)
                              │
                              ├─3. Solve CVRP Problem (OR-Tools)
                              │   - All customers can be served
                              │   - Generate optimal routes
                              │
                              ├─4. Route Assignment──> [DA1]
                              │   ROUTE_ASSIGNMENT: ROUTE:1|VEHICLE:DA1|...
                              │
                              ├─4. Route Assignment──> [DA2]
                              │   ROUTE_ASSIGNMENT: ROUTE:2|VEHICLE:DA2|...
                              │
                              │<─5. Route Acceptance──┤
                              │   ROUTE_ACCEPTED: ROUTE:1|VEHICLE:DA1|STATUS:ACCEPTED
                              │
                              │<─5. Route Acceptance──┤
                              │   ROUTE_ACCEPTED: ROUTE:2|VEHICLE:DA2|STATUS:ACCEPTED
                              │
[DA1] ──6. Execute Route──> [Customer 1] ──> [Customer 3] ──> [Depot]
[DA2] ──6. Execute Route──> [Customer 2] ──> [Customer 4] ──> [Customer 5] ──> [Depot]
                              │
                              │<─7. Arrival Notification──┤
                              │   DA_ARRIVED_AT_DEPOT
                              │
                              │<─7. Arrival Notification──┤
                              │   DA_ARRIVED_AT_DEPOT
```

### Communication Messages

#### Phase 1: Vehicle Information Query

**Message 1: MRA → DA1 (REQUEST)**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Conversation ID: vehicle-info-query-DA1-<timestamp>
Content: QUERY_VEHICLE_INFO
```

**Message 2: DA1 → MRA (INFORM)**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: vehicle-info-query-DA1-<timestamp>
Content: CAPACITY:50|MAX_DISTANCE:1000.0|NAME:DA1|X:0.0|Y:0.0
```

*(Similar messages for DA2, DA3, DA4)*

#### Phase 2: Route Assignment

**Message 3: MRA → DA1 (REQUEST)**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Ontology: route-assignment
Conversation ID: route-assignment-1-DA1-<timestamp>
Content: ROUTE_ASSIGNMENT:ROUTE:1|VEHICLE_ID:1|VEHICLE_NAME:DA1|CUSTOMERS:1,3|CUSTOMER_IDS:1,3|COORDS:10.0,10.0;30.0,10.0|DEMAND:30|DISTANCE:45.25|DEPOT_X:0.0|DEPOT_Y:0.0
```

**Message 4: DA1 → MRA (INFORM)**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: route-assignment-1-DA1-<timestamp>
Content: ROUTE_ACCEPTED:1|VEHICLE:DA1|STATUS:ACCEPTED|DEMAND:30|DISTANCE:45.25|CUSTOMERS:2
```

#### Phase 3: Arrival Notification

**Message 5: DA1 → MRA (INFORM)**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: da-arrival-DA1-<timestamp>
Content: DA_ARRIVED_AT_DEPOT
```

### How It Works

1. **Request Reception**: MRA receives customer request from backend API and adds it to the request queue
2. **Vehicle Query**: MRA queries all registered DAs via DF (Directory Facilitator) for vehicle information (capacity, maxDistance, current position)
3. **Vehicle Response**: Each DA responds with its vehicle information
4. **Problem Solving**: MRA assembles the CVRP problem and solves it using OR-Tools solver:
   - All customers can be served (total demand 75 < total capacity 200)
   - Solver generates optimal routes minimizing distance while serving all customers
5. **Route Assignment**: MRA assigns routes to DAs via FIPA-Request protocol
6. **Route Acceptance**: DAs validate routes (capacity and distance constraints) and accept them
7. **Route Execution**: DAs execute routes by moving to customers sequentially, then returning to depot
8. **Arrival Notification**: When DAs return to depot, they notify MRA (no response required)

### Desired Outcome

- ✅ All 5 customers are served in one round
- ✅ Routes are assigned to vehicles (typically 2-3 vehicles used)
- ✅ All routes respect capacity constraints (demand ≤ 50 per vehicle)
- ✅ All routes respect maximum distance constraints (route distance ≤ 1000.0)
- ✅ Total distance is minimized
- ✅ All DAs return to depot and notify MRA
- ✅ No unserved customers remain
- ✅ Solution is logged to JSON result file

---

## Test Case 2: Demand Exceeds Capacity (Test Favor Item Over Distance)

### Scenario Description

This test case validates the system's ability to handle scenarios where total customer demand exceeds total vehicle capacity. The solver must prioritize maximizing items delivered over minimizing distance, leaving some customers unserved. These unserved customers are then processed in a second round.

### Test Configuration

- **Vehicles**: 4 vehicles (DA1, DA2, DA3, DA4)
  - Capacity: 30 items each
  - Max Distance: 1000.0 units each
- **Customers**: 6 customers
  - Customer 1: demand=20, location=(10.0, 10.0)
  - Customer 2: demand=25, location=(20.0, 20.0)
  - Customer 3: demand=30, location=(30.0, 10.0)
  - Customer 4: demand=22, location=(15.0, 25.0)
  - Customer 5: demand=28, location=(25.0, 15.0)
  - Customer 6: demand=15, location=(35.0, 20.0)
- **Total Demand**: 140 items
- **Total Capacity**: 120 items (4 vehicles × 30)
- **Shortfall**: 20 items cannot be delivered in first round
- **Depot**: Location (0.0, 0.0)

### Workflow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│         TEST CASE 2: DEMAND EXCEEDS CAPACITY                    │
└─────────────────────────────────────────────────────────────────┘

[Backend API] ──Request──> [MRA]
                              │
                              ├─1. Query Vehicle Info──> [DA1, DA2, DA3, DA4]
                              │
                              │<─2. Vehicle Info Response──┤
                              │
                              ├─3. Solve CVRP Problem (OR-Tools)
                              │   - Total demand (140) > Total capacity (120)
                              │   - Solver prioritizes items delivered
                              │   - Some customers unserved
                              │
                              ├─4. Route Assignment──> [DAs]
                              │
                              │<─5. Route Acceptance──┤
                              │
[DA1, DA2, DA3, DA4] ──6. Execute Routes──> [Customers] ──> [Depot]
                              │
                              │<─7. Arrival Notifications──┤
                              │
                              ├─8. Store Unserved Customers
                              │   (Added to unservedCustomers list)
                              │
                              ├─9. Process Unserved Customers
                              │   (Create new request with unserved)
                              │
                              ├─10. Query Vehicle Info Again──> [DAs]
                              │
                              │<─11. Vehicle Info Response──┤
                              │
                              ├─12. Solve CVRP Problem Again
                              │    - Solve with unserved customers
                              │
                              ├─13. Route Assignment──> [DAs]
                              │
                              │<─14. Route Acceptance──┤
                              │
[DAs] ──15. Execute Routes──> [Unserved Customers] ──> [Depot]
                              │
                              │<─16. Arrival Notifications──┤
```

### Communication Messages

#### Round 1: Initial Request

**Message 1: MRA → DA1 (REQUEST)**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Conversation ID: vehicle-info-query-DA1-<timestamp>
Content: QUERY_VEHICLE_INFO
```

**Message 2: DA1 → MRA (INFORM)**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: vehicle-info-query-DA1-<timestamp>
Content: CAPACITY:30|MAX_DISTANCE:1000.0|NAME:DA1|X:0.0|Y:0.0
```

**Message 3: MRA → DA1 (REQUEST) - Route Assignment**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Ontology: route-assignment
Conversation ID: route-assignment-1-DA1-<timestamp>
Content: ROUTE_ASSIGNMENT:ROUTE:1|VEHICLE_ID:1|VEHICLE_NAME:DA1|CUSTOMERS:1,3|...|DEMAND:50|DISTANCE:45.25|...
```

**Message 4: DA1 → MRA (INFORM) - Route Rejection**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: route-assignment-1-DA1-<timestamp>
Content: ROUTE_REJECTED:1|VEHICLE:DA1|STATUS:REJECTED|REASON:CAPACITY_EXCEEDED|DETAILS:Demand 50 exceeds capacity 30
```

*(Note: If route demand exceeds capacity, DA rejects it)*

#### Round 2: Unserved Customers

**Message 5: MRA → DA1 (REQUEST) - Query for Unserved**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Conversation ID: vehicle-info-query-DA1-<timestamp2>
Content: QUERY_VEHICLE_INFO
```

**Message 6: MRA → DA1 (REQUEST) - Route Assignment for Unserved**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Ontology: route-assignment
Conversation ID: route-assignment-1-DA1-<timestamp3>
Content: ROUTE_ASSIGNMENT:ROUTE:1|VEHICLE_ID:1|VEHICLE_NAME:DA1|CUSTOMERS:2|...|DEMAND:25|DISTANCE:28.28|...
```

**Message 7: DA1 → MRA (INFORM) - Route Acceptance**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: route-assignment-1-DA1-<timestamp3>
Content: ROUTE_ACCEPTED:1|VEHICLE:DA1|STATUS:ACCEPTED|DEMAND:25|DISTANCE:28.28|CUSTOMERS:1
```

### How It Works

1. **Initial Request**: MRA receives request with 6 customers (total demand 140 > total capacity 120)
2. **Vehicle Query**: MRA queries all DAs for vehicle information
3. **First Round Solving**: 
   - MRA solves CVRP problem with all 6 customers
   - OR-Tools solver uses disjunctions with penalties (1,000,000 per unserved customer)
   - Solver prioritizes maximizing items delivered over distance
   - Result: Some customers served, some unserved (e.g., 4 customers served, 2 unserved)
4. **Route Assignment (Round 1)**: MRA assigns routes to DAs
   - DAs validate routes (capacity and distance constraints)
   - Routes that exceed capacity are rejected by DAs
5. **Route Execution (Round 1)**: DAs execute routes and return to depot
6. **Unserved Customer Tracking**: 
   - MRA stores unserved customers in `unservedCustomers` list
   - Unserved customers are added to `accumulatedUnroutedNodes` list
7. **Second Round Processing**:
   - MRA creates a new request with unserved customers
   - MRA queries vehicles again (they are now at depot, ready for new routes)
   - MRA solves CVRP problem again with only unserved customers
8. **Route Assignment (Round 2)**: MRA assigns routes for unserved customers
9. **Route Execution (Round 2)**: DAs execute routes for unserved customers
10. **Completion**: All customers that can be served are eventually served

### Desired Outcome

- ✅ First round: Maximum number of customers served (typically 4-5 customers, ~100-115 items)
- ✅ Unserved customers are correctly identified and stored
- ✅ Solver prioritizes items delivered over distance (serves customers with higher demand first if possible)
- ✅ Second round: Unserved customers are processed and assigned to vehicles
- ✅ All routes respect capacity constraints (demand ≤ 30 per vehicle)
- ✅ All routes respect maximum distance constraints
- ✅ DAs queue routes if multiple assignments received
- ✅ All DAs return to depot after each round
- ✅ Solution shows both served and unserved customers in JSON output

---

## Test Case 3: Maximum Distance Constraint

### Scenario Description

This test case validates the system's ability to handle maximum distance constraints. Some customers may be too far from the depot or other customers, causing routes to exceed the maximum distance limit. These customers become unserved and are processed in subsequent rounds.

### Test Configuration

- **Vehicles**: 4 vehicles (DA1, DA2, DA3, DA4)
  - Capacity: 50 items each
  - Max Distance: 50.0 units each (tight constraint)
- **Customers**: 6 customers
  - Customer 1: demand=10, location=(10.0, 10.0) - Distance from depot: ~14.14
  - Customer 2: demand=15, location=(20.0, 20.0) - Distance from depot: ~28.28
  - Customer 3: demand=20, location=(30.0, 10.0) - Distance from depot: ~31.62
  - Customer 4: demand=12, location=(15.0, 25.0) - Distance from depot: ~29.15
  - Customer 5: demand=18, location=(25.0, 15.0) - Distance from depot: ~29.15
  - Customer 6: demand=25, location=(100.0, 100.0) - Distance from depot: ~141.42 (EXCEEDS MAX)
- **Total Demand**: 100 items
- **Total Capacity**: 200 items (4 vehicles × 50)
- **Max Distance**: 50.0 units per vehicle
- **Depot**: Location (0.0, 0.0)

### Workflow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│           TEST CASE 3: MAXIMUM DISTANCE CONSTRAINT              │
└─────────────────────────────────────────────────────────────────┘

[Backend API] ──Request──> [MRA]
                              │
                              ├─1. Query Vehicle Info──> [DA1, DA2, DA3, DA4]
                              │
                              │<─2. Vehicle Info Response──┤
                              │   (MaxDistance: 50.0)
                              │
                              ├─3. Solve CVRP Problem (OR-Tools)
                              │   - Add distance dimension constraint
                              │   - Customer 6 too far (141.42 > 50.0)
                              │   - Customer 6 becomes unserved
                              │
                              ├─4. Route Assignment──> [DAs]
                              │   (Routes for customers 1-5)
                              │
                              │<─5. Route Acceptance──┤
                              │
[DAs] ──6. Execute Routes──> [Customers 1-5] ──> [Depot]
                              │
                              │<─7. Arrival Notifications──┤
                              │
                              ├─8. Store Unserved Customer (Customer 6)
                              │
                              ├─9. Process Unserved Customer
                              │   (Customer 6 still exceeds max distance)
                              │
                              ├─10. Solve Again (Customer 6 still unserved)
                              │
                              └─> Customer 6 remains unserved
                                  (Cannot be served due to distance constraint)
```

### Communication Messages

#### Phase 1: Vehicle Information Query

**Message 1: MRA → DA1 (REQUEST)**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Conversation ID: vehicle-info-query-DA1-<timestamp>
Content: QUERY_VEHICLE_INFO
```

**Message 2: DA1 → MRA (INFORM)**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: vehicle-info-query-DA1-<timestamp>
Content: CAPACITY:50|MAX_DISTANCE:50.0|NAME:DA1|X:0.0|Y:0.0
```

#### Phase 2: Route Assignment

**Message 3: MRA → DA1 (REQUEST)**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Ontology: route-assignment
Conversation ID: route-assignment-1-DA1-<timestamp>
Content: ROUTE_ASSIGNMENT:ROUTE:1|VEHICLE_ID:1|VEHICLE_NAME:DA1|CUSTOMERS:1,3|...|DEMAND:30|DISTANCE:45.25|DEPOT_X:0.0|DEPOT_Y:0.0
```

**Message 4: DA1 → MRA (INFORM)**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: route-assignment-1-DA1-<timestamp>
Content: ROUTE_ACCEPTED:1|VEHICLE:DA1|STATUS:ACCEPTED|DEMAND:30|DISTANCE:45.25|CUSTOMERS:2
```

*(If a route exceeds max distance, DA would reject it)*

**Message 5: MRA → DA1 (REQUEST) - Route Exceeding Distance**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Ontology: route-assignment
Conversation ID: route-assignment-2-DA1-<timestamp>
Content: ROUTE_ASSIGNMENT:ROUTE:2|VEHICLE_ID:1|VEHICLE_NAME:DA1|CUSTOMERS:6|...|DEMAND:25|DISTANCE:141.42|...
```

**Message 6: DA1 → MRA (INFORM) - Route Rejection**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: route-assignment-2-DA1-<timestamp>
Content: ROUTE_REJECTED:2|VEHICLE:DA1|STATUS:REJECTED|REASON:DISTANCE_EXCEEDED|DETAILS:Distance 141.42 exceeds max distance 50.0
```

### How It Works

1. **Request Reception**: MRA receives request with 6 customers
2. **Vehicle Query**: MRA queries all DAs for vehicle information (including maxDistance=50.0)
3. **Problem Solving**: 
   - MRA assembles CVRP problem with distance dimension constraint
   - OR-Tools solver adds distance dimension with vehicle-specific maximum distances
   - Customer 6 at (100.0, 100.0) is too far from depot (distance ~141.42 > 50.0)
   - Solver cannot create a route to Customer 6 without exceeding max distance
   - Customer 6 becomes unserved
4. **Route Assignment**: MRA assigns routes for customers 1-5 (within distance constraint)
5. **Route Validation**: DAs validate routes:
   - If route distance ≤ maxDistance: Accept
   - If route distance > maxDistance: Reject with reason "DISTANCE_EXCEEDED"
6. **Route Execution**: DAs execute accepted routes
7. **Unserved Customer Tracking**: Customer 6 is stored in unservedCustomers list
8. **Second Round**: MRA attempts to process Customer 6 again, but it still exceeds max distance
9. **Final State**: Customer 6 remains unserved (cannot be served due to distance constraint)

### Desired Outcome

- ✅ Customers within distance constraint (1-5) are served
- ✅ Customer 6 (beyond max distance) is correctly identified as unserved
- ✅ All assigned routes respect maximum distance constraint (route distance ≤ 50.0)
- ✅ DAs reject routes that exceed maximum distance
- ✅ Solver correctly applies distance dimension constraint
- ✅ Unserved customer is tracked and attempted in second round
- ✅ Solution shows unserved customer with reason (distance constraint)
- ✅ JSON output includes unserved customers list

---

## Test Case 4: Multiple Requests When DAs Have Not Arrived Back

### Scenario Description

This test case validates the system's ability to handle multiple incoming requests while DAs are still executing routes from previous requests. The MRA accumulates unserved customers in a separate list until reaching a threshold (6 customers) or until no more requests are available, then processes them before handling the next request.

### Test Configuration

- **Vehicles**: 4 vehicles (DA1, DA2, DA3, DA4)
  - Capacity: 40 items each
  - Max Distance: 1000.0 units each
- **Request 1**: 4 customers
  - Customer 1: demand=15, location=(10.0, 10.0)
  - Customer 2: demand=20, location=(20.0, 20.0)
  - Customer 3: demand=25, location=(30.0, 10.0)
  - Customer 4: demand=18, location=(15.0, 25.0)
- **Request 2**: 4 customers (arrives while DAs are delivering)
  - Customer 5: demand=22, location=(25.0, 15.0)
  - Customer 6: demand=28, location=(35.0, 20.0)
  - Customer 7: demand=30, location=(40.0, 10.0)
  - Customer 8: demand=19, location=(18.0, 30.0)
- **Total Demand (Request 1)**: 78 items
- **Total Demand (Request 2)**: 99 items
- **Total Capacity**: 160 items (4 vehicles × 40)
- **Accumulation Threshold**: 6 unserved customers
- **Depot**: Location (0.0, 0.0)

### Workflow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│    TEST CASE 4: MULTIPLE REQUESTS (DAs NOT ARRIVED BACK)       │
└─────────────────────────────────────────────────────────────────┘

[Backend API] ──Request 1──> [MRA]
                              │
                              ├─1. Query Vehicle Info──> [DA1, DA2, DA3, DA4]
                              │
                              │<─2. Vehicle Info Response──┤
                              │
                              ├─3. Solve CVRP Problem (Request 1)
                              │   - Some customers may be unserved
                              │   - Unserved added to accumulatedUnroutedNodes
                              │
                              ├─4. Route Assignment──> [DAs]
                              │
                              │<─5. Route Acceptance──┤
                              │
[DAs] ──6. Execute Routes──> [Customers] ──> [Moving...]
                              │
[Backend API] ──Request 2──> [MRA] (While DAs are delivering)
                              │
                              ├─7. Store Request 2 in Queue
                              │
                              ├─8. Check accumulatedUnroutedNodes
                              │   (Count < 6, continue waiting)
                              │
                              ├─9. Solve CVRP Problem (Request 2)
                              │   - More unserved customers
                              │   - Add to accumulatedUnroutedNodes
                              │   (Now count >= 6)
                              │
                              ├─10. Process accumulatedUnroutedNodes
                              │    (Before processing next request)
                              │
                              ├─11. Query Vehicle Info──> [DAs]
                              │     (DAs may still be delivering)
                              │
                              │<─12. Vehicle Info Response──┤
                              │
                              ├─13. Solve CVRP Problem (Unrouted)
                              │
                              ├─14. Route Assignment──> [DAs]
                              │     (Routes queued in DAs)
                              │
                              │<─15. Route Acceptance──┤
                              │
[DAs] ──16. Complete First Route──> [Depot]
                              │
                              │<─17. Arrival Notification──┤
                              │
                              ├─18. DAs Process Queued Routes
                              │
[DAs] ──19. Execute Queued Routes──> [Unserved Customers] ──> [Depot]
                              │
                              │<─20. Arrival Notifications──┤
                              │
                              ├─21. Process Next Request (Request 2)
                              │
                              └─> Continue processing...
```

### Communication Messages

#### Phase 1: Request 1 Processing

**Message 1: MRA → DA1 (REQUEST) - Vehicle Info Query**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Conversation ID: vehicle-info-query-DA1-<timestamp1>
Content: QUERY_VEHICLE_INFO
```

**Message 2: DA1 → MRA (INFORM) - Vehicle Info Response**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: vehicle-info-query-DA1-<timestamp1>
Content: CAPACITY:40|MAX_DISTANCE:1000.0|NAME:DA1|X:0.0|Y:0.0
```

**Message 3: MRA → DA1 (REQUEST) - Route Assignment (Request 1)**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Ontology: route-assignment
Conversation ID: route-assignment-1-DA1-<timestamp1>
Content: ROUTE_ASSIGNMENT:ROUTE:1|VEHICLE_ID:1|VEHICLE_NAME:DA1|CUSTOMERS:1,3|...|DEMAND:40|DISTANCE:45.25|...
```

**Message 4: DA1 → MRA (INFORM) - Route Acceptance**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: route-assignment-1-DA1-<timestamp1>
Content: ROUTE_ACCEPTED:1|VEHICLE:DA1|STATUS:ACCEPTED|DEMAND:40|DISTANCE:45.25|CUSTOMERS:2
```

#### Phase 2: Request 2 Arrives (DAs Still Delivering)

**Message 5: MRA → DA1 (REQUEST) - Vehicle Info Query (Request 2)**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Conversation ID: vehicle-info-query-DA1-<timestamp2>
Content: QUERY_VEHICLE_INFO
```

**Message 6: DA1 → MRA (INFORM) - Vehicle Info Response (Still Delivering)**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: vehicle-info-query-DA1-<timestamp2>
Content: CAPACITY:40|MAX_DISTANCE:1000.0|NAME:DA1|X:15.5|Y:12.3
```
*(Note: Position shows DA is still moving, not at depot)*

**Message 7: MRA → DA1 (REQUEST) - Route Assignment (Request 2, Queued)**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Ontology: route-assignment
Conversation ID: route-assignment-2-DA1-<timestamp2>
Content: ROUTE_ASSIGNMENT:ROUTE:2|VEHICLE_ID:1|VEHICLE_NAME:DA1|CUSTOMERS:5,7|...|DEMAND:52|...
```

**Message 8: DA1 → MRA (INFORM) - Route Rejection (Capacity Exceeded)**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: route-assignment-2-DA1-<timestamp2>
Content: ROUTE_REJECTED:2|VEHICLE:DA1|STATUS:REJECTED|REASON:CAPACITY_EXCEEDED|DETAILS:Demand 52 exceeds capacity 40
```

#### Phase 3: Processing Accumulated Unrouted Nodes

**Message 9: MRA → DA1 (REQUEST) - Vehicle Info Query (Unrouted)**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Conversation ID: vehicle-info-query-DA1-<timestamp3>
Content: QUERY_VEHICLE_INFO
```

**Message 10: MRA → DA1 (REQUEST) - Route Assignment (Unrouted Customers)**
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Ontology: route-assignment
Conversation ID: route-assignment-3-DA1-<timestamp3>
Content: ROUTE_ASSIGNMENT:ROUTE:1|VEHICLE_ID:1|VEHICLE_NAME:DA1|CUSTOMERS:2,6|...|DEMAND:48|DISTANCE:55.30|...
```

**Message 11: DA1 → MRA (INFORM) - Route Acceptance (Queued)**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: route-assignment-3-DA1-<timestamp3>
Content: ROUTE_ACCEPTED:3|VEHICLE:DA1|STATUS:ACCEPTED|DEMAND:48|DISTANCE:55.30|CUSTOMERS:2
```
*(Note: Route is queued in DA, will execute after current route completes)*

#### Phase 4: DA Returns and Executes Queued Route

**Message 12: DA1 → MRA (INFORM) - Arrival Notification**
```
Performative: INFORM
Protocol: FIPA_REQUEST
Conversation ID: da-arrival-DA1-<timestamp4>
Content: DA_ARRIVED_AT_DEPOT
```

*(DA1 then automatically processes queued route)*

### How It Works

1. **Request 1 Processing**:
   - MRA receives Request 1 with 4 customers
   - MRA queries all DAs for vehicle information
   - MRA solves CVRP problem
   - Some customers may be unserved (added to `accumulatedUnroutedNodes`)
   - MRA assigns routes to DAs
   - DAs start executing routes

2. **Request 2 Arrives** (While DAs are delivering):
   - MRA receives Request 2 with 4 customers
   - Request 2 is added to `requestQueue`
   - MRA continues processing Request 1 (doesn't interrupt)

3. **Request 1 Completion**:
   - DAs complete routes and return to depot
   - DAs notify MRA of arrival
   - Unserved customers from Request 1 are in `accumulatedUnroutedNodes`

4. **Request 2 Processing**:
   - MRA processes Request 2 from queue
   - MRA queries DAs (they may still be delivering or at depot)
   - MRA solves CVRP problem for Request 2
   - More unserved customers are added to `accumulatedUnroutedNodes`
   - If `accumulatedUnroutedNodes.size() >= 6`, trigger processing

5. **Accumulated Unrouted Nodes Processing**:
   - MRA checks if `accumulatedUnroutedNodes.size() >= 6` OR `requestQueue.isEmpty()`
   - If condition met, MRA processes accumulated unrouted nodes BEFORE next request
   - MRA creates a new request with accumulated unrouted customers
   - MRA queries DAs for vehicle information
   - MRA solves CVRP problem with unrouted customers
   - MRA assigns routes to DAs

6. **Route Queuing in DAs**:
   - If DA receives route assignment while still executing a route:
     - DA validates the route (capacity, distance)
     - If valid, DA accepts and adds route to `routeQueue`
     - DA continues executing current route
     - When DA returns to depot, it automatically processes next route in queue

7. **Completion**:
   - All routes are executed
   - All DAs return to depot
   - Unserved customers are tracked and processed when threshold is reached

### Key Features

- **Request Queue**: MRA maintains a queue of incoming requests
- **Accumulated Unrouted Nodes**: Unserved customers are accumulated in a separate list
- **Threshold Processing**: When `accumulatedUnroutedNodes.size() >= 6` OR no more requests, process accumulated nodes
- **Route Queuing**: DAs can queue multiple route assignments
- **Non-Blocking**: DAs can receive new route assignments while delivering
- **Sequential Processing**: MRA processes one request at a time, but accumulates unserved customers

### Desired Outcome

- ✅ Request 1 is processed and routes are assigned
- ✅ Request 2 arrives while DAs are delivering and is queued
- ✅ Request 2 is processed after Request 1 completes
- ✅ Unserved customers from both requests are accumulated
- ✅ When accumulated count >= 6 (or no more requests), unrouted nodes are processed
- ✅ DAs queue route assignments received while delivering
- ✅ DAs execute queued routes after completing current route
- ✅ All DAs return to depot and notify MRA
- ✅ Solution shows served and unserved customers from all requests
- ✅ System handles multiple concurrent requests correctly

---

## Summary of Test Cases

| Test Case | Key Feature | Unserved Customers | Multiple Rounds | Route Queuing |
|-----------|-------------|---------------------|-----------------|---------------|
| **Test Case 1** | Basic CVRP | None | No | No |
| **Test Case 2** | Capacity Exceeded | Yes (demand > capacity) | Yes | No |
| **Test Case 3** | Distance Constraint | Yes (distance > maxDistance) | Yes | No |
| **Test Case 4** | Multiple Requests | Yes (accumulated) | Yes | Yes |

## Common Communication Patterns

### FIPA-Request Protocol

All agent communications use the FIPA-Request protocol:

1. **REQUEST**: Initiator sends request (MRA → DA)
2. **INFORM/REFUSE**: Responder sends response (DA → MRA)
   - **INFORM**: Request accepted (route accepted, vehicle info provided)
   - **REFUSE**: Request rejected (route rejected)

### Message Structure

All messages include:
- **Performative**: REQUEST, INFORM, or REFUSE
- **Protocol**: FIPA_REQUEST
- **Conversation ID**: Unique identifier for conversation tracking
- **Content**: Structured data (pipe-separated format)
- **Ontology**: Optional (e.g., "route-assignment")

### Logging

All conversations are logged to files:
- **MRA**: `logs/<timestamp>/MRA_conversations.log`
- **DA**: `logs/<timestamp>/DA-<name>_conversations.log`

Each log entry includes:
- Timestamp
- Conversation ID
- Message direction (sent/received)
- Message content
- Conversation summary

---

## Conclusion

These four test cases comprehensively validate the multi-agent CVRP system's capabilities:

1. **Basic functionality** (Test Case 1)
2. **Capacity constraint handling** (Test Case 2)
3. **Distance constraint handling** (Test Case 3)
4. **Concurrent request handling** (Test Case 4)

The system demonstrates robust agent communication, constraint handling, route optimization, and concurrent request processing capabilities.

