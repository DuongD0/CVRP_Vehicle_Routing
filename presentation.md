# Delivery Vehicle Routing Problem (CVRP)
## Multi-Agent System Presentation

---

## Slide 1: Title Slide

# Delivery Vehicle Routing Problem (CVRP)
## Multi-Agent System

**Solving Capacitated Vehicle Routing Problems using**
- JADE Multi-Agent Framework
- Google OR-Tools Solver
- FIPA Communication Protocols

---

## Slide 2: Problem Introduction

# The Problem: Capacitated Vehicle Routing Problem (CVRP)

### What is CVRP?
- **Depot**: Starting point where all vehicles begin and return
- **Customers**: Locations requiring delivery with specific demands
- **Vehicles**: Limited capacity and maximum travel distance
- **Objective**: Deliver packages to customers while minimizing distance

### Key Constraints
- ✅ **Capacity Constraint**: Each vehicle can carry limited items
- ✅ **Distance Constraint**: Each vehicle has maximum travel distance
- ✅ **Demand Constraint**: Total demand may exceed total capacity

### Real-World Applications
- Package delivery services
- Waste collection
- School bus routing
- Supply chain logistics

---

## Slide 3: Problem Example

# Problem Example

### Scenario
- **Depot**: Location (0, 0)
- **5 Customers**: 
  - Customer 1: 10 items at (10, 10)
  - Customer 2: 15 items at (20, 20)
  - Customer 3: 20 items at (30, 10)
  - Customer 4: 12 items at (15, 25)
  - Customer 5: 18 items at (25, 15)
- **4 Vehicles**: Each with capacity 50, max distance 1000

### Challenge
- **Total Demand**: 75 items
- **Total Capacity**: 200 items (4 vehicles × 50)
- **Goal**: Find optimal routes serving all customers

---

## Slide 4: System Architecture

# System Architecture

## Multi-Agent System Components

### 1. Master Routing Agent (MRA)
- **Role**: Central coordinator
- **Responsibilities**:
  - Receives customer requests
  - Queries vehicle information
  - Solves CVRP using OR-Tools
  - Assigns routes to delivery agents
- **Location**: Depot coordinates

### 2. Delivery Agents (DAs)
- **Role**: Vehicle representatives
- **Responsibilities**:
  - Respond to MRA queries
  - Receive route assignments
  - Execute delivery routes
  - Return to depot and notify MRA
- **Properties**: Capacity, max distance, current position

### 3. Directory Facilitator (DF)
- **Role**: Service discovery (JADE Yellow Pages)
- **Function**: Enables agents to find each other dynamically

---

## Slide 5: Algorithm Overview

# Algorithm: Google OR-Tools Solver

## Optimization Strategy

### Primary Objective: **Maximize Items Delivered**
- Uses **disjunctions with large penalties** (1,000,000 per unserved customer)
- Ensures visiting customers takes precedence over distance

### Secondary Objective: **Minimize Total Distance**
- Arc cost evaluator minimizes travel distance
- Only considered after maximizing items delivered

### Constraints
- **Capacity Dimension**: Each route respects vehicle capacity
- **Distance Dimension**: Each route respects maximum travel distance

---

## Slide 6: Algorithm Details

# Algorithm: How It Works

## Disjunction Mechanism

```java
// Large penalty for unvisited nodes
UNVISITED_NODE_PENALTY = 1,000,000

// Mark each customer as optional
for (each customer node) {
    routing.addDisjunction(node, UNVISITED_NODE_PENALTY);
}
```

## Objective Function

```
Total Cost = (Distance Traveled) + (1,000,000 × Unserved Customers)
```

### Why This Works
- Penalty (1,000,000) >> Typical distance (< 1,000)
- Creates hierarchical optimization:
  1. **First**: Minimize unserved customers (maximize items)
  2. **Then**: Minimize distance among feasible solutions

---

## Slide 7: Algorithm: Capacity Shortfall Handling

# Algorithm: Handling Demand > Capacity

## When Total Demand Exceeds Total Capacity

### Example
- **Total Demand**: 140 items (6 customers)
- **Total Capacity**: 120 items (4 vehicles × 30)
- **Shortfall**: 20 items cannot be delivered

### Solution Process
1. Solver tries to serve as many customers as possible
2. Uses disjunctions to allow skipping customers
3. Prioritizes customers that maximize total items delivered
4. Returns:
   - **Served customers**: In optimized routes
   - **Unserved customers**: Tracked for later processing

### Result
- Maximum items delivered: ~100-115 items
- Unserved customers: 1-2 customers (20-25 items)
- Processed in second round when vehicles return

---

## Slide 8: Communication Protocol

# Communication Protocol: FIPA-Request

## Protocol Overview

All agent communications use **FIPA-Request Protocol**:
- Standard FIPA (Foundation for Intelligent Physical Agents) protocol
- Ensures reliable, structured communication
- Supports request-response patterns

## Message Structure

### Components
- **Performative**: REQUEST, INFORM, REFUSE
- **Protocol**: FIPA_REQUEST
- **Conversation ID**: Unique identifier for tracking
- **Content**: Structured data (pipe-separated format)
- **Ontology**: Optional (e.g., "route-assignment")

---

## Slide 9: Communication Protocol: Message Types

# Communication Protocol: Message Types

## 1. Vehicle Information Query

**MRA → DA (REQUEST)**
```
Content: QUERY_VEHICLE_INFO
```

**DA → MRA (INFORM)**
```
Content: CAPACITY:50|MAX_DISTANCE:1000.0|NAME:DA1|X:0.0|Y:0.0
```

## 2. Route Assignment

**MRA → DA (REQUEST)**
```
Content: ROUTE_ASSIGNMENT:ROUTE:1|VEHICLE_NAME:DA1|CUSTOMERS:1,3|
         COORDS:10.0,10.0;30.0,10.0|DEMAND:30|DISTANCE:45.25|...
```

**DA → MRA (INFORM)**
```
Content: ROUTE_ACCEPTED:1|VEHICLE:DA1|STATUS:ACCEPTED|...
```

## 3. Arrival Notification

**DA → MRA (INFORM)**
```
Content: DA_ARRIVED_AT_DEPOT
```

---

## Slide 10: Communication Protocol: Service Discovery

# Communication Protocol: Service Discovery

## Directory Facilitator (DF) - JADE Yellow Pages

### Registration
- **MRA** registers as: `mra-service`
- **DAs** register as: `da-service`

### Discovery
- **MRA** searches for: `da-service` type
- **DAs** search for: `mra-service` type

### Benefits
- ✅ **Dynamic Discovery**: No hardcoded agent names
- ✅ **Scalability**: Easy to add/remove agents
- ✅ **Flexibility**: Agents can join/leave system

### Process
1. Agents register with DF on startup
2. Agents search DF to find other agents
3. Communication established via discovered AIDs

---

## Slide 11: Complete Workflow - Overview

# Complete Workflow: Overview

## High-Level Process

```
1. System Initialization
   ↓
2. Request Reception
   ↓
3. Vehicle Information Query
   ↓
4. Problem Solving
   ↓
5. Route Assignment
   ↓
6. Route Execution
   ↓
7. Return to Depot
   ↓
8. Next Request Processing
```

---

## Slide 12: Complete Workflow - Step 1-2

# Complete Workflow: Steps 1-2

## Step 1: System Initialization

1. **MRA Startup**:
   - Reads depot location from config
   - Registers with DF as `mra-service`
   - Starts polling backend API for requests

2. **DA Startup**:
   - Each DA initialized with capacity and max distance
   - Registers with DF as `da-service`
   - Waits at depot for route assignments

## Step 2: Request Reception

1. **Backend API** receives customer request
2. **MRA** polls backend API (every 2 seconds)
3. **MRA** adds request to processing queue
4. **Request contains**: Customer list (id, demand, coordinates)

---

## Slide 13: Complete Workflow - Step 3

# Complete Workflow: Step 3

## Step 3: Vehicle Information Query

### Process
1. **MRA** searches DF for all `da-service` agents
2. **MRA** sends REQUEST to each DA:
   ```
   Content: QUERY_VEHICLE_INFO
   ```
3. **Each DA** responds with INFORM:
   ```
   Content: CAPACITY:50|MAX_DISTANCE:1000.0|NAME:DA1|X:0.0|Y:0.0
   ```
4. **MRA** collects all vehicle information
5. **MRA** waits for all responses (with timeout)

### Result
- MRA has complete vehicle information
- Ready to assemble CVRP problem

---

## Slide 14: Complete Workflow - Step 4

# Complete Workflow: Step 4

## Step 4: Problem Solving

### Problem Assembly
1. **MRA** combines:
   - Customer data (from request)
   - Vehicle data (from queries)
   - Depot location
   - Unserved customers (if any)

### OR-Tools Solving
1. **Create routing model** with:
   - Distance matrix
   - Capacity constraints
   - Distance constraints
   - Disjunctions (for unserved handling)

2. **Solve** using:
   - First solution: PATH_CHEAPEST_ARC
   - Improvement: GUIDED_LOCAL_SEARCH
   - Time limit: 30 seconds

### Solution Extraction
- Extract routes (vehicle → customer sequence)
- Identify unserved customers
- Calculate total distance and items delivered

---

## Slide 15: Complete Workflow - Step 5

# Complete Workflow: Step 5

## Step 5: Route Assignment

### Process
1. **MRA** creates route assignment message for each route:
   ```
   ROUTE_ASSIGNMENT:ROUTE:1|VEHICLE_NAME:DA1|CUSTOMERS:1,3|
   COORDS:10.0,10.0;30.0,10.0|DEMAND:30|DISTANCE:45.25|...
   ```

2. **MRA** sends REQUEST to assigned DA

3. **DA** validates route:
   - ✅ Capacity check: demand ≤ capacity
   - ✅ Distance check: route distance ≤ maxDistance
   - ✅ Vehicle match: route assigned to correct vehicle

4. **DA** responds:
   - **INFORM**: Route accepted → Added to queue
   - **REFUSE**: Route rejected → Reason provided

---

## Slide 16: Complete Workflow - Step 6

# Complete Workflow: Step 6

# Step 6: Route Execution

## DA Route Execution Process

1. **Route Queuing**:
   - DA adds accepted route to `routeQueue`
   - If DA is idle, starts executing immediately
   - If DA is busy, queues for later execution

2. **Movement Behavior**:
   - DA moves towards first customer (speed: 1 unit/second)
   - Updates position every second
   - Reports position to backend for visualization

3. **Customer Visits**:
   - Arrives at customer when distance < 1.0 unit
   - Delivers packages (demand satisfied)
   - Moves to next customer in route

4. **Return to Depot**:
   - After all customers visited
   - Returns to depot location
   - Updates position continuously

---

## Slide 17: Complete Workflow - Step 7-8

# Complete Workflow: Steps 7-8

## Step 7: Return to Depot

1. **DA** arrives at depot (distance < 1.0 unit)
2. **DA** sends arrival notification to MRA:
   ```
   Content: DA_ARRIVED_AT_DEPOT
   ```
3. **DA** processes next route in queue (if any)
4. **DA** state: Ready for next assignment

## Step 8: Next Request Processing

1. **MRA** checks request queue:
   - If queue not empty → Process next request
   - If queue empty → Wait for new requests

2. **Unserved Customer Handling**:
   - Unserved customers added to `accumulatedUnroutedNodes`
   - When count ≥ 6 OR queue empty → Process unserved
   - Create synthetic request with unserved customers

3. **Cycle repeats** from Step 3

---

## Slide 18: Complete Workflow Diagram

# Complete Workflow: Visual Diagram

```
┌─────────────┐
│ Backend API │
└──────┬──────┘
       │ Request
       ▼
┌─────────────┐     1. Query Vehicle Info      ┌──────┐
│     MRA    │───────────────────────────────>│ DA1  │
│            │<─────────────────────────────── │ DA2  │
│            │     2. Vehicle Info Response    │ DA3  │
│            │                                 │ DA4  │
│            │     3. Solve CVRP (OR-Tools)   └──────┘
│            │
│            │     4. Route Assignment ──────>┌──────┐
│            │<─────────────────────────────────│ DA1  │
│            │     5. Route Acceptance         │ DA2  │
│            │                                 └──────┘
│            │
│            │<─────────────────────────────────┌──────┐
│            │     6. Execute Routes           │ DA1  │
│            │     7. Arrival Notification      │ DA2  │
│            │                                 └──────┘
└─────────────┘
```

---

## Slide 19: Scenario: Test Case 1

# Scenario: Test Case 1 - Basic CVRP

## Configuration

- **Depot**: (0.0, 0.0)
- **Vehicles**: 4 vehicles (DA1-DA4)
  - Capacity: 50 items each
  - Max Distance: 1000.0 each
- **Customers**: 5 customers
  - C1: 10 items at (10, 10)
  - C2: 15 items at (20, 20)
  - C3: 20 items at (30, 10)
  - C4: 12 items at (15, 25)
  - C5: 18 items at (25, 15)
- **Total Demand**: 75 items
- **Total Capacity**: 200 items

---

## Slide 20: Scenario: Execution Timeline

# Scenario: Execution Timeline

## Timeline of Events

### T=0s: Request Received
- MRA receives request with 5 customers
- Request added to queue

### T=1s: Vehicle Query
- MRA queries all 4 DAs for vehicle info
- All DAs respond with capacity and max distance

### T=2s: Problem Solving
- MRA assembles CVRP problem
- OR-Tools solves in ~500ms
- Solution: 2 routes generated

### T=3s: Route Assignment
- MRA assigns Route 1 to DA1: [C1, C3]
- MRA assigns Route 2 to DA2: [C2, C4, C5]
- Both DAs accept routes

### T=4s-30s: Route Execution
- DA1: Depot → C1 → C3 → Depot
- DA2: Depot → C2 → C4 → C5 → Depot
- Both DAs report position updates

### T=31s: Completion
- Both DAs arrive at depot
- DAs notify MRA
- Solution logged to JSON

---

## Slide 21: Scenario: Solution Result

# Scenario: Solution Result

## Generated Routes

### Route 1 (DA1)
- **Customers**: C1, C3
- **Demand**: 30 items (10 + 20)
- **Distance**: ~45.25 units
- **Status**: ✅ Accepted and executed

### Route 2 (DA2)
- **Customers**: C2, C4, C5
- **Demand**: 45 items (15 + 12 + 18)
- **Distance**: ~55.30 units
- **Status**: ✅ Accepted and executed

## Summary
- ✅ **All 5 customers served**
- ✅ **Total items delivered**: 75/75 (100%)
- ✅ **Total distance**: ~100.55 units
- ✅ **Vehicles used**: 2/4
- ✅ **Unserved customers**: 0

---

## Slide 22: Scenario: Message Exchange

# Scenario: Message Exchange

## Key Messages

### 1. Vehicle Query (MRA → DA1)
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Content: QUERY_VEHICLE_INFO
```

### 2. Vehicle Response (DA1 → MRA)
```
Performative: INFORM
Protocol: FIPA_REQUEST
Content: CAPACITY:50|MAX_DISTANCE:1000.0|NAME:DA1|X:0.0|Y:0.0
```

### 3. Route Assignment (MRA → DA1)
```
Performative: REQUEST
Protocol: FIPA_REQUEST
Ontology: route-assignment
Content: ROUTE_ASSIGNMENT:ROUTE:1|VEHICLE_NAME:DA1|CUSTOMERS:1,3|
         COORDS:10.0,10.0;30.0,10.0|DEMAND:30|DISTANCE:45.25|...
```

### 4. Route Acceptance (DA1 → MRA)
```
Performative: INFORM
Protocol: FIPA_REQUEST
Content: ROUTE_ACCEPTED:1|VEHICLE:DA1|STATUS:ACCEPTED|...
```

### 5. Arrival Notification (DA1 → MRA)
```
Performative: INFORM
Content: DA_ARRIVED_AT_DEPOT
```

---

## Slide 23: Advanced Features

# Advanced Features

## 1. Unserved Customer Handling
- When demand > capacity, some customers unserved
- Unserved customers tracked and processed in second round
- Solver prioritizes items delivered over distance

## 2. Route Queuing
- DAs can queue multiple route assignments
- Routes executed sequentially
- Enables handling multiple requests concurrently

## 3. Accumulated Unrouted Nodes
- Unserved customers accumulated in buffer
- Processed when count ≥ 6 OR no more requests
- Ensures efficient batch processing

## 4. Dynamic Service Discovery
- Agents discover each other via DF
- No hardcoded agent names
- System scales dynamically

---

## Slide 24: System Benefits

# System Benefits

## Advantages

### 1. Scalability
- ✅ Easy to add/remove vehicles
- ✅ Handles varying number of customers
- ✅ Dynamic agent discovery

### 2. Robustness
- ✅ Handles capacity shortfalls gracefully
- ✅ Respects distance constraints
- ✅ Queues routes for concurrent requests

### 3. Optimization
- ✅ Maximizes items delivered (primary)
- ✅ Minimizes distance (secondary)
- ✅ Uses proven OR-Tools solver

### 4. Communication
- ✅ Standard FIPA protocols
- ✅ Comprehensive logging
- ✅ Conversation tracking

---

## Slide 25: Technical Stack

# Technical Stack

## Technologies Used

### Multi-Agent Framework
- **JADE 4.6.0**: Java Agent Development Framework
  - Agent lifecycle management
  - Message passing
  - Directory Facilitator (DF)

### Solver
- **Google OR-Tools 9.12.4544**: Constraint programming
  - CVRP solver
  - Capacity and distance constraints
  - Disjunction support

### Data Format
- **Gson 2.10.1**: JSON parsing
  - Configuration files
  - Result logging
  - API communication

### Backend/Frontend
- **Python Flask**: REST API server
- **JavaScript/HTML**: Web visualization

---

## Slide 26: Results and Logging

# Results and Logging

## Output Formats

### 1. JSON Results
- Saved to `results/` directory
- Contains:
  - Routes with customer sequences
  - Unserved customers
  - Total distance and items delivered
  - Solve time

### 2. Conversation Logs
- Saved to `logs/` directory
- One log file per agent:
  - `MRA_conversations.log`
  - `DA-DA1_conversations.log`
  - etc.
- Includes all messages with timestamps

### 3. Console Output
- Human-readable summary
- Route details
- Performance metrics

---

## Slide 27: Key Takeaways

# Key Takeaways

## Problem Solving Approach
1. **Multi-Agent System**: Distributed coordination
2. **OR-Tools Solver**: Proven optimization algorithm
3. **FIPA Protocols**: Standard communication

## Algorithm Highlights
1. **Hierarchical Optimization**: Items first, distance second
2. **Disjunction Mechanism**: Handles capacity shortfalls
3. **Constraint Handling**: Capacity and distance limits

## Communication Protocol
1. **FIPA-Request**: Standard protocol
2. **Service Discovery**: Dynamic agent finding
3. **Message Logging**: Complete conversation tracking

## System Capabilities
1. **Scalable**: Add/remove agents dynamically
2. **Robust**: Handles edge cases gracefully
3. **Optimized**: Maximizes delivery efficiency

---

## Slide 28: Questions & Discussion

# Questions & Discussion

## Thank You!

### Contact Information
- Project Repository: [Your Repository URL]
- Documentation: `documentation/` folder
- Test Cases: `test_cases/` folder

### Key Resources
- README.md: System overview
- API_DOCUMENTATION.md: API details
- TEST_CASES_DOCUMENTATION.md: Test scenarios
- CAPACITY_SHORTFALL_ALGORITHM.md: Algorithm details

---

## Slide 29: Appendix: Message Flow Diagram

# Appendix: Complete Message Flow

```
┌──────┐                    ┌──────┐
│ MRA  │                    │ DA1  │
└──┬───┘                    └───┬──┘
   │                            │
   │ 1. REQUEST                 │
   │ QUERY_VEHICLE_INFO         │
   ├───────────────────────────>│
   │                            │
   │ 2. INFORM                  │
   │ CAPACITY:50|MAX_DIST:1000  │
   │<───────────────────────────┤
   │                            │
   │ 3. REQUEST                  │
   │ ROUTE_ASSIGNMENT:...       │
   ├───────────────────────────>│
   │                            │
   │ 4. INFORM                   │
   │ ROUTE_ACCEPTED:...          │
   │<───────────────────────────┤
   │                            │
   │                            │ [Execute Route]
   │                            │
   │ 5. INFORM                   │
   │ DA_ARRIVED_AT_DEPOT        │
   │<───────────────────────────┤
   │                            │
```

---

## Slide 30: Appendix: Algorithm Pseudo-code

# Appendix: Algorithm Pseudo-code

## OR-Tools CVRP Solving

```java
// 1. Create routing model
RoutingModel routing = new RoutingModel(manager);

// 2. Add distance callback
routing.setArcCostEvaluatorOfAllVehicles(distanceCallback);

// 3. Add disjunctions (prioritize items delivered)
for (each customer node) {
    routing.addDisjunction(node, 1,000,000);
}

// 4. Add capacity constraint
routing.addDimensionWithVehicleCapacity(
    demandCallback, vehicleCapacities, "Capacity"
);

// 5. Add distance constraint
routing.addDimensionWithVehicleCapacity(
    distanceCallback, vehicleMaxDistances, "Distance"
);

// 6. Solve
Assignment solution = routing.solveWithParameters(params);

// 7. Extract routes and unserved customers
extractRoutes(solution);
identifyUnservedCustomers(solution);
```

