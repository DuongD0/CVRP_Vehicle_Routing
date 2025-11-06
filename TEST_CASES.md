# Test Cases for VRP Multi-Agent System

## Overview

This document provides detailed test cases to verify the functionality of the VRP Multi-Agent System. Each test case includes objectives, steps, expected results, and verification methods.

---

## Test Case 1: Basic Inventory Check ✅

**Priority:** High  
**Objective:** Verify depot correctly checks inventory and responds to customers

### Prerequisites
- System started with Main.java
- At least one customer agent running
- Depot agent initialized with inventory

### Test Steps
1. Start the system: `mvn exec:java -Dexec.mainClass="project.Main"`
2. Wait for customer agent to generate a request (5-15 seconds)
3. Observe console logs for depot processing
4. Observe customer agent logs for response

### Expected Results
- **Depot logs:**
  ```
  Depot: Processing Request
  Customer: Customer1 (customer-1)
  Item: ItemA, Quantity: 10
  Location: (100.0, 150.0)
  Depot: ✓ Item available. Inventory: ItemA = 90
  ```
- **Customer logs:**
  ```
  Customer Customer1: Requested 10 units of ItemA
  Customer Customer1: ✓ Request accepted - Request accepted. Item will be delivered in next batch.
  ```

### Verification Checklist
- [ ] Depot receives FIPA-REQUEST from customer
- [ ] Depot checks inventory dictionary
- [ ] Inventory quantity decreases if item available
- [ ] Customer receives FIPA-REQUEST (INFORM) response
- [ ] Response contains correct availability status

### Test Data
- **Item:** ItemA
- **Initial Inventory:** 100 units
- **Request Quantity:** 10 units
- **Expected Result:** Available (90 units remaining)

---

## Test Case 2: Inventory Unavailable Scenario ✅

**Priority:** High  
**Objective:** Verify depot correctly handles unavailable items

### Prerequisites
- System started
- Item with low/zero inventory (or modify inventory in Depot.java)

### Test Steps
1. Start system
2. Modify inventory to set ItemA = 5 (in Depot.java setup())
3. Wait for customer to request ItemA with quantity > 5
4. Observe depot and customer logs

### Expected Results
- **Depot logs:**
  ```
  Depot: ✗ Item unavailable. Available: 5, Requested: 10
  ```
- **Customer logs:**
  ```
  Customer Customer1: ✗ Request rejected - Insufficient inventory. Available: 5, Requested: 10
  ```

### Verification Checklist
- [ ] Depot correctly identifies insufficient inventory
- [ ] Inventory quantity does NOT decrease
- [ ] Customer receives INFORM with unavailable status
- [ ] Request is NOT added to queue

### Test Data
- **Item:** ItemA
- **Initial Inventory:** 5 units
- **Request Quantity:** 10 units
- **Expected Result:** Unavailable

---

## Test Case 3: Request Batching ✅

**Priority:** High  
**Objective:** Verify depot batches requests when threshold reached

### Prerequisites
- System started
- Multiple customers active
- BATCH_THRESHOLD = 5 (default)

### Test Steps
1. Start system with 4 customers
2. Wait for customers to send requests
3. Count requests until batch threshold reached
4. Observe depot batch processing logs

### Expected Results
- **Depot logs:**
  ```
  Depot: Request added to queue. Queue size: 1
  Depot: Request added to queue. Queue size: 2
  ...
  Depot: Request added to queue. Queue size: 5
  === Depot: Processing Batch ===
  Batch size: 5
  ```

### Verification Checklist
- [ ] Requests accumulate in queue
- [ ] Batch processing triggers when threshold reached
- [ ] All queued requests processed together
- [ ] Queue cleared after batch processing

### Test Data
- **Batch Threshold:** 5 requests
- **Number of Customers:** 4
- **Expected Time:** ~10-20 seconds (depends on request frequency)

---

## Test Case 4: DF Registration and Discovery ✅

**Priority:** High  
**Objective:** Verify agents register with DF and discover each other

### Prerequisites
- System started
- JADE GUI enabled

### Test Steps
1. Start system with JADE GUI
2. Open JADE GUI → Tools → DF GUI (or right-click DF agent → Show GUI)
3. Verify registered services
4. Check console logs for DF registration messages

### Expected Results
- **DF GUI shows:**
  - Service Type: `depot-service` → 1 agent
  - Service Type: `vehicle-service` → 3 agents
  - Service Type: `customer-service` → 4 agents

- **Console logs:**
  ```
  Depot: Registered with DF as 'depot-service'
  Vehicle Vehicle1: Registered with DF as 'vehicle-service'
  Customer Customer1: Registered with DF as 'customer-service'
  ...
  Depot: Found 3 vehicles via DF
  Customer Customer1: Found depot via DF
  ```

### Verification Checklist
- [ ] All agents register with DF at startup
- [ ] Depot can find vehicles via DF search
- [ ] Customers can find depot via DF search
- [ ] DF GUI displays all services correctly

### Test Data
- **Expected Services:**
  - 1 depot-service
  - 3 vehicle-service
  - 4 customer-service

---

## Test Case 5: Vehicle State Query ✅

**Priority:** High  
**Objective:** Verify depot queries vehicle states and vehicles respond

### Prerequisites
- System started
- Vehicles registered with DF

### Test Steps
1. Start system
2. Wait for batch processing to trigger
3. Observe depot querying vehicles
4. Observe vehicle responses

### Expected Results
- **Depot logs:**
  ```
  Depot: Found 3 vehicles via DF
  Depot: Querying vehicle states...
  Depot: Updated vehicle Vehicle1 state: free
  Depot: Found 3 available vehicles
  ```

- **Vehicle logs:**
  ```
  Vehicle Vehicle1 reported state: free at (10.5, 20.3)
  ```

### Verification Checklist
- [ ] Depot finds vehicles via DF
- [ ] Depot sends FIPA-QUERY to vehicles
- [ ] Vehicles respond with FIPA-QUERY (INFORM)
- [ ] Response includes state, capacity, name, position
- [ ] Depot updates vehicle state in internal map

### Test Data
- **Vehicle States:** free, absent, busy
- **Response Format:** STATE:free|CAPACITY:50|NAME:Vehicle1|X:10.5|Y:20.3

---

## Test Case 6: VRP Solving ✅

**Priority:** High  
**Objective:** Verify depot solves VRP and generates routes

### Prerequisites
- Batch processing triggered
- Vehicles available

### Test Steps
1. Start system
2. Wait for batch processing
3. Observe solver execution
4. Verify solution contains routes

### Expected Results
- **Depot logs:**
  ```
  === OR-Tools VRP Solver ===
  Nodes: 6 (including depot)
  Vehicles: 3
  Capacity: 50
  Solving...
  Solution found in 245 ms!
  Objective: 283.0
  Vehicle 1: [1, 3] | Load: 18/50 | Distance: 187.00
  Vehicle 2: [2] | Load: 15/50 | Distance: 96.00
  Depot: Solution found with 2 routes
  ```

### Verification Checklist
- [ ] Solver interface is called
- [ ] OR-Tools solver executes
- [ ] Solution contains routes
- [ ] Each route has customers assigned
- [ ] Total distance calculated
- [ ] Routes respect capacity constraints

### Test Data
- **Solver:** OR-Tools
- **Time Limit:** 30 seconds
- **Solution Strategy:** PATH_CHEAPEST_ARC
- **Metaheuristic:** GUIDED_LOCAL_SEARCH

---

## Test Case 7: Contract-Net Route Bidding ✅

**Priority:** High  
**Objective:** Verify vehicles bid for routes via Contract-Net protocol

### Prerequisites
- Batch processed
- Routes generated
- Vehicles available

### Test Steps
1. Start system
2. Wait for batch processing and route generation
3. Observe Contract-Net messages
4. Verify bidding process

### Expected Results
- **Depot logs:**
  ```
  === Depot: Contract-Net Route Assignment ===
  Depot: Received proposal from vehicle-Vehicle1@... for conversation cn-...
  Depot: Assigned route 1 to vehicle Vehicle1
  Depot: All routes assigned via Contract-Net
  ```

- **Vehicle logs:**
  ```
  === Vehicle Vehicle1: Received CFP ===
  CFP: ROUTE:1|CUSTOMERS:1,2|DEMAND:25|DISTANCE:150.0
  Vehicle Vehicle1: Bidding with cost: 165.50
  Vehicle Vehicle1: Route accepted
  Vehicle Vehicle1: Assigned route R1
  ```

### Verification Checklist
- [ ] Depot sends CFP (Call for Proposal) to vehicles
- [ ] Vehicles receive CFP
- [ ] Vehicles calculate bid cost
- [ ] Vehicles send PROPOSE messages
- [ ] Depot accepts proposals
- [ ] Vehicles receive ACCEPT_PROPOSAL
- [ ] Vehicles confirm with INFORM
- [ ] Routes assigned to vehicles

### Test Data
- **Bid Calculation:** Distance from position + route distance + return distance
- **Capacity Check:** Route demand <= vehicle capacity

---

## Test Case 8: Vehicle Position Tracking ✅

**Priority:** Medium  
**Objective:** Verify vehicles track position and update after route completion

### Prerequisites
- System started
- Vehicle assigned a route

### Test Steps
1. Start system
2. Wait for route assignment
3. Observe vehicle position logs
4. Wait for route completion (30 seconds)
5. Verify position update

### Expected Results
- **Initial:**
  ```
  Vehicle Vehicle1: Initial position: (10.5, 20.3)
  Vehicle Vehicle1: Depot: (0.0, 0.0)
  ```

- **After Assignment:**
  ```
  Vehicle Vehicle1: Assigned route R1
  Vehicle Vehicle1: New position: (5.2, 8.1)
  ```

- **After Completion:**
  ```
  Vehicle Vehicle1: Route completed. Returning to free state.
  Vehicle Vehicle1: Position: (2.5, 3.1)
  ```

### Verification Checklist
- [ ] Vehicle tracks initial position
- [ ] Position updates after route assignment
- [ ] Position updates after route completion
- [ ] Position moves closer to depot after completion

### Test Data
- **Depot Location:** (0.0, 0.0)
- **Initial Position:** Random within 100 units of depot
- **Route Completion Time:** 30 seconds

---

## Test Case 9: Vehicle Capacity Constraints ✅

**Priority:** Medium  
**Objective:** Verify vehicles refuse routes exceeding capacity

### Prerequisites
- System started
- Modify customer requests to have high demand

### Test Steps
1. Modify Customer.java to generate requests with high quantity (e.g., 60 units)
2. Start system
3. Wait for batch processing
4. Observe vehicle responses to oversized routes

### Expected Results
- **Vehicle logs:**
  ```
  Vehicle Vehicle1: Insufficient capacity (60 > 50)
  ```
- **Vehicle sends:** REFUSE message
- **Depot logs:**
  ```
  Depot: Vehicle Vehicle1 refused route assignment
  ```

### Verification Checklist
- [ ] Vehicle checks capacity before bidding
- [ ] Vehicle sends REFUSE if capacity exceeded
- [ ] Depot handles refusals correctly
- [ ] Route assigned to vehicle with sufficient capacity

### Test Data
- **Vehicle Capacity:** 50 units
- **Route Demand:** 60 units
- **Expected Result:** REFUSE

---

## Test Case 10: Multiple Concurrent Requests ✅

**Priority:** Medium  
**Objective:** Verify system handles multiple customers simultaneously

### Prerequisites
- System started with 4 customers

### Test Steps
1. Start system
2. Monitor all customer request logs
3. Verify all requests processed
4. Verify batching works correctly

### Expected Results
- **All customers send requests:**
  ```
  Customer Customer1: Requested 10 units of ItemA
  Customer Customer2: Requested 15 units of ItemB
  Customer Customer3: Requested 8 units of ItemC
  Customer Customer4: Requested 12 units of ItemD
  ```

- **Depot processes all:**
  ```
  Depot: Request added to queue. Queue size: 1
  Depot: Request added to queue. Queue size: 2
  Depot: Request added to queue. Queue size: 3
  Depot: Request added to queue. Queue size: 4
  ```

### Verification Checklist
- [ ] All customers generate requests
- [ ] All requests received by depot
- [ ] All requests added to queue
- [ ] All customers receive responses
- [ ] Batch includes requests from all customers

### Test Data
- **Number of Customers:** 4
- **Request Frequency:** 5-15 seconds per customer
- **Expected Batch Time:** ~10-20 seconds

---

## Test Case 11: Solver Abstraction ✅

**Priority:** Low  
**Objective:** Verify solver interface allows swapping implementations

### Prerequisites
- Understanding of solver interface

### Test Steps
1. Review `VRPSolver` interface
2. Review `ORToolsSolver` implementation
3. Create new solver class implementing `VRPSolver`
4. Modify Depot to use new solver
5. Verify system works

### Expected Results
- New solver implements `VRPSolver` interface
- Depot can instantiate new solver
- System compiles without errors
- Routes generated correctly

### Verification Checklist
- [ ] Solver interface defined correctly
- [ ] ORToolsSolver implements interface
- [ ] New solver can be created
- [ ] Depot can swap solvers
- [ ] System functionality preserved

### Test Data
- **Interface:** `VRPSolver.solve()`
- **Parameters:** numNodes, numCustomers, numVehicles, capacity, demand, distance
- **Return:** `SolutionResult`

---

## Test Case 12: Agent Independence ✅

**Priority:** High  
**Objective:** Verify agents have no hardcoded dependencies

### Prerequisites
- System started
- Agents registered with DF

### Test Steps
1. Start system
2. Stop one agent (e.g., vehicle)
3. Verify other agents handle gracefully
4. Restart agent
5. Verify agent re-registers with DF

### Expected Results
- **When vehicle stops:**
  ```
  Vehicle Vehicle1: Deregistered from DF
  Vehicle Vehicle1: Terminating
  ```

- **Depot handles:**
  ```
  Depot: Found 2 vehicles via DF (instead of 3)
  ```

- **When vehicle restarts:**
  ```
  Vehicle Vehicle1: Registered with DF as 'vehicle-service'
  Depot: Found 3 vehicles via DF
  ```

### Verification Checklist
- [ ] Agents can start in any order
- [ ] Agents discover each other via DF
- [ ] No hardcoded agent names in communication
- [ ] Agents handle missing services gracefully
- [ ] Agents re-register after restart

### Test Data
- **Agent Discovery:** Via DF service search
- **No Hardcoded Names:** All AIDs resolved via DF

---

## Test Execution Summary

### Quick Test Run
Execute all critical test cases in sequence:

1. **TC1:** Basic Inventory Check
2. **TC2:** Inventory Unavailable
3. **TC3:** Request Batching
4. **TC4:** DF Registration
5. **TC5:** Vehicle State Query
6. **TC6:** VRP Solving
7. **TC7:** Contract-Net Bidding

### Test Coverage
- ✅ Inventory Management
- ✅ Request Processing
- ✅ Agent Discovery
- ✅ Route Solving
- ✅ Route Assignment
- ✅ Vehicle Management
- ✅ FIPA Protocol Compliance

---

## Notes

- All test cases assume default configuration
- Test execution time: ~5-10 minutes for full suite
- Monitor console logs for detailed execution traces
- Use JADE GUI for visual verification of agent interactions
- DF GUI provides service registration verification

