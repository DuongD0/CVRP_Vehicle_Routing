# Agents Code Documentation

This document provides a comprehensive, detailed description of all agent classes and their functions in the Delivery Vehicle Routing Problem system.

---

## Table of Contents

1. [MasterRoutingAgent](#masterroutingagent)
2. [DepotProblemAssembler](#depotproblemassembler)
3. [DeliveryAgent](#deliveryagent)

---

## MasterRoutingAgent

**Package:** `project.Agent`  
**Extends:** `jade.core.Agent`

The Master Routing Agent (MRA) is the central coordinator for the CVRP system. It reads problem configurations, queries delivery agents for vehicle information, solves routing problems using Google OR-Tools, and assigns routes to delivery agents.

### Class Fields

- `depotX` (double): X coordinate of the depot location
- `depotY` (double): Y coordinate of the depot location
- `customers` (List<CustomerInfo>): List of customer information from the problem configuration
- `registeredVehicles` (Map<String, VehicleInfo>): Map of vehicle names to their information
- `expectedVehicleCount` (int): Number of vehicles expected to respond to queries
- `receivedVehicleCount` (int): Number of vehicles that have responded
- `allVehiclesReceived` (boolean): Flag indicating if all vehicles have responded
- `solver` (VRPSolver): Interface to the VRP solver (OR-Tools implementation)
- `problemAssembler` (DepotProblemAssembler): Helper class for assembling problem data
- `logger` (AgentLogger): Logger for conversation and event tracking
- `config` (JsonConfigReader.CVRPConfig): Problem configuration data
- `configName` (String): Name/ID of the configuration
- `solutionLatch` (CountDownLatch): Synchronization mechanism for backend mode
- `solutionHolder` (Object): Holder for solution result in backend mode

### Methods

#### `setup()`
**Access:** protected  
**Override:** Yes (from Agent)

Initializes the Master Routing Agent when it starts.

**Parameters:** None (uses `getArguments()`)

**Process:**
1. Retrieves arguments: config, configName, and optionally solutionLatch and solutionHolder for backend mode
2. Validates arguments - requires at least config and configName
3. Initializes logger with agent name "MRA"
4. Prints detailed debug information about the request (depot, vehicles, customers)
5. Extracts depot coordinates from config
6. Converts customer configurations to CustomerInfo objects
7. Initializes collections (registeredVehicles, counters)
8. Initializes solver (ORToolsSolver) and problem assembler
9. Registers with DF (Directory Facilitator) as "mra-service"
10. Adds behaviors:
    - WakerBehaviour: Waits 3 seconds then queries vehicles and solves
    - VehicleInfoResponseHandler: Handles vehicle information responses
    - RouteAssignmentResponseHandler: Handles route assignment responses

**Error Handling:** If arguments are invalid, the agent terminates itself.

---

#### `queryVehiclesAndSolve()`
**Access:** private

Queries all Delivery Agents for their vehicle information, then proceeds to solve the routing problem.

**Process:**
1. Finds all Delivery Agents via DF (Directory Facilitator) using service type "da-service"
2. If no DAs found, logs error and returns
3. Sets expected vehicle count to the number of DAs found
4. Resets counters (receivedVehicleCount = 0, allVehiclesReceived = false)
5. For each DA found:
   - Creates a FIPA-Request message with content "QUERY_VEHICLE_INFO"
   - Sets conversation ID with timestamp
   - Logs conversation start
   - Sends the query message
6. Adds WaitForVehiclesBehaviour to wait for all vehicle responses (checks every 500ms, timeout 10 seconds)

**Error Handling:** Catches exceptions when querying individual DAs and logs errors.

---

#### `VehicleInfoResponseHandler` (Inner Class)
**Extends:** `CyclicBehaviour`

Handles vehicle information responses from Delivery Agents.

**Methods:**

##### `action()`
Processes incoming vehicle information messages.

**Process:**
1. Uses message template to match INFORM messages with FIPA_REQUEST protocol
2. Filters out route assignment responses by checking conversation ID and content
3. For each vehicle info response:
   - Parses content string (pipe-delimited format: NAME, CAPACITY, MAX_DISTANCE)
   - Extracts vehicle name, capacity, and max distance
   - Uses sender name as fallback if NAME not in content
   - Validates name matches sender name
   - Logs conversation end
   - Registers or updates vehicle in registeredVehicles map
   - Increments receivedVehicleCount only for new vehicles
   - Sets allVehiclesReceived flag when all vehicles have responded
4. Blocks if no messages available

**Message Format:** `CAPACITY:50|MAX_DISTANCE:1000.0|NAME:DA1|X:0.0|Y:0.0`

**Error Handling:** Handles null content, parsing errors, and name mismatches with appropriate logging.

---

#### `WaitForVehiclesBehaviour` (Inner Class)
**Extends:** `TickerBehaviour`

Periodically checks if all vehicles have responded before proceeding to solve.

**Fields:**
- `startTime` (long): Timestamp when behavior started
- `timeoutMs` (long): Maximum wait time in milliseconds
- `minWaitMs` (long): Minimum wait time (3 seconds) even if all vehicles respond quickly
- `shouldStop` (boolean): Flag to stop the behavior

**Methods:**

##### `WaitForVehiclesBehaviour(Agent a, long period, long timeoutMs)`
Constructor that initializes the ticker behavior with check period and timeout.

##### `onTick()`
Called periodically to check vehicle response status.

**Process:**
1. If shouldStop is true, returns immediately
2. Calculates elapsed time since start
3. If allVehiclesReceived is true:
   - Checks if minimum wait time has passed
   - If yes, removes behavior and calls solveAndAssignRoutes()
   - If no, continues waiting
4. If timeout reached:
   - Logs timeout message
   - Removes behavior and calls solveAndAssignRoutes() with available vehicles
5. Otherwise, logs waiting status (every 2 ticks to avoid spam)

---

#### `RouteAssignmentResponseHandler` (Inner Class)
**Extends:** `CyclicBehaviour`

Handles route assignment responses (accept/reject) from Delivery Agents.

**Methods:**

##### `action()`
Processes route assignment responses from DAs.

**Process:**
1. Creates message templates for INFORM (accept) and REFUSE (reject) messages
2. Receives messages matching the template
3. For each response:
   - Checks if content starts with "ROUTE_ACCEPTED:" or "ROUTE_REJECTED:"
   - Parses response content (pipe-delimited):
     - ROUTE_ACCEPTED/ROUTE_REJECTED: route ID
     - VEHICLE: vehicle name
     - STATUS: acceptance status
     - REASON: rejection reason (if rejected)
     - DETAILS: additional details
     - DEMAND: route demand
     - DISTANCE: route distance
   - Logs detailed response information
   - Logs conversation end
   - Prints formatted response summary
4. Blocks if no messages available

**Message Format:** 
- Accept: `ROUTE_ACCEPTED:1|VEHICLE:DA1|STATUS:ACCEPTED|DEMAND:30|DISTANCE:150.50|CUSTOMERS:3`
- Reject: `ROUTE_REJECTED:1|VEHICLE:DA1|STATUS:REJECTED|REASON:CAPACITY_EXCEEDED|DETAILS:Demand exceeds capacity`

---

#### `solveAndAssignRoutes()`
**Access:** private

Solves the CVRP problem using the solver and assigns routes to Delivery Agents.

**Process:**
1. Collects all registered vehicles into a list
2. Validates that vehicles are available
3. Converts customers to CustomerRequest format for problem assembler
   - Includes time windows if available in config
4. Calls problemAssembler.assembleAndSolve() with:
   - Depot coordinates
   - Customer requests
   - Available vehicles
5. Updates unserved customers with proper coordinates and names
6. Prints solution summary:
   - Number of routes
   - Items delivered vs total
   - Total distance
   - Unserved customers count
7. Sets vehicle names for all routes (uses original names from config)
8. Logs result as JSON using JsonResultLogger
9. If in backend mode:
   - Sets solution in solutionHolder using reflection
   - Submits solution to backend via BackendClient
   - Assigns routes to vehicles
   - Waits 8 seconds for route assignment responses (non-blocking)
   - Signals completion via solutionLatch
10. If in file mode:
    - Assigns routes to DAs if routes exist

**Error Handling:** Handles null solver results, missing vehicles, and backend submission errors.

---

#### `assignRoutes(SolutionResult result, List<VehicleInfo> availableVehicles)`
**Access:** private

Assigns computed routes to Delivery Agents.

**Parameters:**
- `result` (SolutionResult): The solution containing routes
- `availableVehicles` (List<VehicleInfo>): List of available vehicles

**Process:**
1. For each route in the solution:
   - Creates route ID (1-based index)
   - Gets vehicle index from route.vehicleId
   - Validates vehicle index is in range
   - Gets target vehicle from availableVehicles list
   - Updates customer details in route (coordinates, names)
   - Builds route content string with:
     - ROUTE: route ID
     - VEHICLE_ID: vehicle ID
     - VEHICLE_NAME: vehicle name
     - CUSTOMERS: comma-separated customer IDs
     - CUSTOMER_IDS: comma-separated customer agent IDs
     - COORDS: semicolon-separated coordinate pairs
     - DEMAND: total demand
     - DISTANCE: total distance
     - DEPOT_X, DEPOT_Y: depot coordinates
   - Finds DA by vehicle name using findDAByName()
   - Creates FIPA-Request message with:
     - Performative: REQUEST
     - Protocol: FIPA_REQUEST
     - Ontology: "route-assignment"
     - Conversation ID: "route-assignment-{routeId}-{vehicleName}-{timestamp}"
     - Content: "ROUTE_ASSIGNMENT:{routeContent}"
   - Logs detailed route assignment information
   - Logs conversation start
   - Sends route assignment message
2. Logs completion message

**Error Handling:** Handles invalid vehicle indices, missing DAs, and logs all errors.

---

#### `findDAByName(String vehicleName)`
**Access:** private  
**Returns:** AID of the Delivery Agent, or null if not found

Finds a Delivery Agent by vehicle name.

**Parameters:**
- `vehicleName` (String): Name of the vehicle to find

**Process:**
1. Gets all DAs via findDeliveryAgentsViaDF()
2. For each DA:
   - Checks exact name match
   - Checks if vehicle name is a prefix of DA name (handles request ID suffixes)
3. Returns matching DA AID, or null if not found

**Error Handling:** Catches exceptions during DF search and logs errors.

---

#### `registerWithDF()`
**Access:** private

Registers the MRA with the Directory Facilitator (DF) for service discovery.

**Process:**
1. Creates DFAgentDescription with agent AID
2. Creates ServiceDescription:
   - Type: "mra-service"
   - Name: "CVRP-Master-Routing-Agent"
   - Ownership: "CVRP-System"
3. Registers with DFService
4. Logs registration success

**Error Handling:** Catches FIPAException and logs error message.

---

#### `findDeliveryAgentsViaDF()`
**Access:** private  
**Returns:** List<AID> of found Delivery Agents

Finds all Delivery Agents registered with DF.

**Process:**
1. Creates DFAgentDescription with service type "da-service"
2. Searches DF for matching agents
3. Collects AIDs of all found agents
4. Returns list of AIDs

**Error Handling:** Catches FIPAException and logs error, returns empty list.

---

#### `takeDown()`
**Access:** protected  
**Override:** Yes (from Agent)

Cleanup method called when agent terminates.

**Process:**
1. Logs termination event
2. Deregisters from DF
3. Closes logger
4. Prints termination message

**Error Handling:** Catches FIPAException during deregistration and logs error.

---

## DepotProblemAssembler

**Package:** `project.Agent`

Helper class responsible for assembling VRP problem data and invoking the solver. This class separates problem preparation from the MRA, making it easier to swap solvers in the future.

### Class Fields

- `solver` (VRPSolver): Interface to the VRP solver (final)
- `logger` (AgentLogger): Logger for event tracking (final)

### Methods

#### `DepotProblemAssembler(VRPSolver solver, AgentLogger logger)`
**Access:** public

Constructor that initializes the problem assembler with a solver and logger.

**Parameters:**
- `solver` (VRPSolver): The VRP solver to use
- `logger` (AgentLogger): Logger for tracking events

---

#### `assembleAndSolve(double depotX, double depotY, List<CustomerRequest> requests, List<VehicleInfo> vehicles)`
**Access:** public  
**Returns:** SolutionResult (may contain zero routes if solver fails)

Builds the VRP problem from provided requests and vehicles, then calls the solver.

**Parameters:**
- `depotX` (double): Depot X coordinate
- `depotY` (double): Depot Y coordinate
- `requests` (List<CustomerRequest>): Current batch of customer requests
- `vehicles` (List<VehicleInfo>): Available vehicles from the fleet

**Process:**
1. **Problem Setup:**
   - Calculates number of nodes (customers + 1 depot)
   - Creates arrays for coordinates (x, y) and demands
   - Sets depot at index 0 (demand = 0)
   - Populates customer data starting at index 1

2. **Distance Matrix Calculation:**
   - Creates 2D distance matrix (numNodes × numNodes)
   - Calculates Euclidean distance between all node pairs
   - Rounds distances to integers

3. **Vehicle Data Extraction:**
   - Creates arrays for vehicle capacities and max distances
   - Extracts data from VehicleInfo objects
   - Logs vehicle information

4. **Time Window Processing:**
   - Checks if any customer has time windows
   - If time windows exist:
     - Creates timeWindows array (numNodes × 2)
     - Sets depot time window to [0, Long.MAX_VALUE/2]
     - Sets customer time windows from requests
     - Sets default wide window for customers without time windows
   - Logs if time windows are detected

5. **Solver Invocation:**
   - Calls solver.solve() with:
     - Number of nodes
     - Number of customers
     - Number of vehicles
     - Vehicle capacities array
     - Vehicle max distances array
     - Demand array
     - Distance matrix
     - Time windows array (null if no time windows)
   - Returns solver result

**Error Handling:** Returns null if solver fails (handled by caller).

**Logging:** Logs vehicle details, time window detection, and solver invocation.

---

## DeliveryAgent

**Package:** `project.Agent`  
**Extends:** `jade.core.Agent`

The Delivery Agent (DA) represents a delivery vehicle in the system. It responds to MRA queries with vehicle information, accepts route assignments, and executes delivery routes.

### Class Fields

- `vehicleName` (String): Name of the vehicle
- `capacity` (int): Maximum capacity (number of items)
- `maxDistance` (double): Maximum distance the vehicle can travel
- `currentX` (double): Current X coordinate
- `currentY` (double): Current Y coordinate
- `depotX` (double): Depot X coordinate
- `depotY` (double): Depot Y coordinate
- `assignedRouteId` (String): ID of currently assigned route
- `currentRoute` (List<CustomerInfo>): List of customers in current route
- `currentCustomerIndex` (int): Index of customer currently moving to (-1 = idle, -2 = returning to depot)
- `targetX` (double): Target X coordinate for movement
- `targetY` (double): Target Y coordinate for movement
- `isMoving` (boolean): Whether vehicle is currently moving
- `currentMovementBehaviour` (MovementBehaviour): Reference to current movement behavior instance
- `MOVEMENT_SPEED` (double): Movement speed in units per second (10.0)
- `ARRIVAL_THRESHOLD` (double): Distance threshold to consider arrived (1.0)
- `logger` (AgentLogger): Logger for conversation and event tracking

### Methods

#### `setup()`
**Access:** protected  
**Override:** Yes (from Agent)

Initializes the Delivery Agent when it starts.

**Process:**
1. Retrieves arguments: vehicleName, capacity, maxDistance
2. Uses defaults if arguments not provided:
   - vehicleName: local name
   - capacity: 50
   - maxDistance: 1000.0
3. Initializes position at depot (0, 0)
4. Initializes state variables (assignedRouteId = null, currentRoute = null, etc.)
5. Initializes logger with name "DA-{vehicleName}"
6. Registers with DF as "da-service"
7. Adds behaviors:
   - VehicleInfoQueryHandler: Handles vehicle info queries
   - RouteAssignmentHandler: Handles route assignments
   - ReturnToDepotBehaviour: Returns to depot when free (checks every 5 seconds)

**Error Handling:** Uses default values if arguments are missing.

---

#### `VehicleInfoQueryHandler` (Inner Class)
**Extends:** `CyclicBehaviour`

Handles vehicle information queries from MRA using FIPA-Request protocol.

**Methods:**

##### `action()`
Processes vehicle information queries.

**Process:**
1. Creates message template matching REQUEST performative with FIPA_REQUEST protocol
2. Receives matching messages
3. If message content is "QUERY_VEHICLE_INFO":
   - Logs received message
   - Logs conversation start
   - Creates INFORM reply message
   - Sets reply content with vehicle info:
     - CAPACITY: capacity
     - MAX_DISTANCE: maxDistance
     - NAME: vehicleName
     - X: currentX
     - Y: currentY
   - Logs conversation end
   - Sends reply
4. Blocks if no matching messages

**Message Format:** `CAPACITY:50|MAX_DISTANCE:1000.0|NAME:DA1|X:0.0|Y:0.0`

---

#### `RouteAssignmentHandler` (Inner Class)
**Extends:** `CyclicBehaviour`

Handles route assignments from MRA.

**Methods:**

##### `action()`
Processes route assignment messages.

**Process:**
1. Creates message template matching REQUEST performative with "route-assignment" ontology
2. Receives matching messages
3. If route assignment received:
   - Logs detailed message information
   - Logs conversation start
   - Calls handleRouteAssignment() to process the assignment
4. Blocks if no messages

---

##### `handleRouteAssignment(ACLMessage routeAssignment)`
**Access:** private (within RouteAssignmentHandler)

Handles route assignment from MRA. Validates the assignment and starts execution if feasible.

**Parameters:**
- `routeAssignment` (ACLMessage): The route assignment message

**Process:**
1. **Message Parsing:**
   - Extracts route data from content (after "ROUTE_ASSIGNMENT:")
   - Parses pipe-delimited fields:
     - ROUTE: route ID
     - VEHICLE_ID: assigned vehicle ID
     - VEHICLE_NAME: assigned vehicle name
     - DEMAND: route demand
     - DISTANCE: route distance
     - CUSTOMERS: comma-separated customer IDs
     - DEPOT_X, DEPOT_Y: depot coordinates
   - Updates depot coordinates and current position

2. **Validation Checks:**
   - **Missing Route ID:** Returns if route ID is null
   - **Wrong Vehicle:** If assigned vehicle name doesn't match this vehicle:
     - Sends reject response with reason "WRONG_VEHICLE"
     - Returns
   - **Already Assigned:** If vehicle already has a route:
     - Sends reject response with reason "ALREADY_ASSIGNED"
     - Returns
   - **Capacity Check:** If route demand exceeds capacity:
     - Sends reject response with reason "CAPACITY_EXCEEDED"
     - Returns
   - **Distance Check:** If route distance exceeds maxDistance:
     - Sends reject response with reason "DISTANCE_EXCEEDED"
     - Returns

3. **Route Acceptance:**
   - Logs acceptance
   - Creates acceptance response message:
     - Performative: INFORM
     - Content: ROUTE_ACCEPTED with route details
   - Sends response to MRA
   - Logs conversation end
   - Calls parseRouteAndStartMovement() to begin delivery

**Error Handling:** Catches parsing errors and logs them, returns early on validation failures.

---

##### `sendRejectResponse(ACLMessage routeAssignment, String routeId, String reason, String details)`
**Access:** private (within RouteAssignmentHandler)

Sends a rejection response to MRA for a route assignment.

**Parameters:**
- `routeAssignment` (ACLMessage): Original route assignment message
- `routeId` (String): ID of the route being rejected
- `reason` (String): Rejection reason code
- `details` (String): Detailed rejection message

**Process:**
1. Creates rejection response content:
   - ROUTE_REJECTED: routeId
   - VEHICLE: vehicleName
   - STATUS: REJECTED
   - REASON: reason
   - DETAILS: details
2. Creates REFUSE reply message
3. Sets FIPA_REQUEST protocol and conversation ID
4. Logs rejection event
5. Sends response
6. Logs conversation end

**Message Format:** `ROUTE_REJECTED:1|VEHICLE:DA1|STATUS:REJECTED|REASON:CAPACITY_EXCEEDED|DETAILS:Demand exceeds capacity`

---

#### `parseRouteAndStartMovement(String routeId, String routeData)`
**Access:** private

Parses route data and starts movement behavior to execute the delivery.

**Parameters:**
- `routeId` (String): ID of the route
- `routeData` (String): Pipe-delimited route data string

**Process:**
1. **Route Parsing:**
   - Parses route data to extract:
     - CUSTOMERS: numeric customer IDs
     - CUSTOMER_IDS: customer agent IDs
     - COORDS: coordinate pairs (semicolon-separated, comma-separated pairs)
   - Creates CustomerInfo objects with:
     - Numeric ID
     - Coordinates
     - Agent name
   - Validates customers list is not empty

2. **State Initialization:**
   - Sets assignedRouteId
   - Stores route in currentRoute
   - Sets currentCustomerIndex to 0
   - Sets isMoving to true
   - Sets target to first customer coordinates

3. **Movement Behavior:**
   - Stops any existing MovementBehaviour
   - Creates new MovementBehaviour (updates every 1 second)
   - Adds behavior to agent
   - Logs movement start

**Error Handling:** Handles parsing errors, validates customer data, resets assignment on error.

---

#### `MovementBehaviour` (Inner Class)
**Extends:** `TickerBehaviour`

Movement behavior that updates vehicle position periodically. Moves vehicle towards customers and returns to depot.

**Methods:**

##### `MovementBehaviour(Agent a, long period)`
Constructor that initializes ticker behavior with update period.

**Parameters:**
- `a` (Agent): The agent this behavior belongs to
- `period` (long): Update period in milliseconds

---

##### `onTick()`
Called periodically to update vehicle position.

**Process:**
1. Checks if vehicle is moving
2. Validates currentRoute is not null
3. Routes to appropriate movement function:
   - If moving to customer (currentCustomerIndex >= 0 and < route size): calls moveTowardsCustomer()
   - If returning to depot (currentCustomerIndex == -2): calls returnToDepot()
   - Edge case: if route empty and index -1, returns to depot

---

##### `moveTowardsCustomer()`
**Access:** private (within MovementBehaviour)

Moves vehicle towards the current target customer.

**Process:**
1. Validates currentRoute and currentCustomerIndex
2. Gets current customer from route
3. Sets target coordinates to customer location
4. Calculates distance to target
5. **If arrived (distance <= ARRIVAL_THRESHOLD):**
   - Sets position to customer location
   - Logs arrival at customer
   - Increments currentCustomerIndex
   - **If all customers visited:**
     - Sets currentCustomerIndex to -2 (returning to depot)
     - Sets target to depot coordinates
     - Logs all customers visited
   - **Else:**
     - Sets target to next customer coordinates
6. **Else (not arrived):**
   - Calculates movement distance (min of MOVEMENT_SPEED and distance)
   - Updates position proportionally towards target

**Logging:** Logs arrival events and customer information.

---

##### `returnToDepot()`
**Access:** private (within MovementBehaviour)

Moves vehicle back to depot after completing all deliveries.

**Process:**
1. Sets target to depot coordinates
2. Calculates distance to depot
3. **If arrived (distance <= ARRIVAL_THRESHOLD):**
   - Sets position to depot
   - Sets isMoving to false
   - Clears assignment (assignedRouteId = null, currentRoute = null)
   - Resets currentCustomerIndex to -1
   - Logs return to depot and route completion
   - Stops behavior and removes it
4. **Else (not arrived):**
   - Calculates movement distance
   - Updates position towards depot

**Logging:** Logs return to depot and route completion events.

---

#### `ReturnToDepotBehaviour` (Inner Class)
**Extends:** `CyclicBehaviour`

Behavior that returns vehicle to depot when free and not at depot.

**Fields:**
- `checkInterval` (long): Interval between checks in milliseconds

**Methods:**

##### `ReturnToDepotBehaviour(Agent a, long checkInterval)`
Constructor that initializes the behavior with check interval.

**Parameters:**
- `a` (Agent): The agent this behavior belongs to
- `checkInterval` (long): Interval between checks in milliseconds

---

##### `action()`
Checks if vehicle should return to depot.

**Process:**
1. If vehicle has no assigned route and is not moving:
   - Calculates distance to depot
   - If distance > ARRIVAL_THRESHOLD:
     - Teleports vehicle to depot (sets currentX, currentY to depot coordinates)
2. Blocks for checkInterval milliseconds

**Note:** This behavior ensures vehicles return to depot when idle, but doesn't handle movement - that's done by MovementBehaviour.

---

#### `registerWithDF()`
**Access:** private

Registers the Delivery Agent with the Directory Facilitator (DF) for service discovery.

**Process:**
1. Creates DFAgentDescription with agent AID
2. Creates ServiceDescription:
   - Type: "da-service"
   - Name: "CVRP-Delivery-Agent-{vehicleName}"
   - Ownership: "CVRP-System"
3. Registers with DFService
4. Logs registration success

**Error Handling:** Catches FIPAException and logs error message.

---

#### `takeDown()`
**Access:** protected  
**Override:** Yes (from Agent)

Cleanup method called when agent terminates.

**Process:**
1. Logs termination event
2. Deregisters from DF
3. Closes logger
4. Prints termination message

**Error Handling:** Catches FIPAException during deregistration and logs error.

---

## Summary

### Agent Communication Flow

1. **Initialization:**
   - MRA and DAs register with DF (Directory Facilitator)
   - MRA waits 3 seconds for DAs to register

2. **Vehicle Information Query:**
   - MRA queries all DAs via DF for vehicle information
   - DAs respond with capacity, max distance, name, and position
   - MRA waits for all responses (with timeout)

3. **Problem Solving:**
   - MRA assembles problem using DepotProblemAssembler
   - Solver (OR-Tools) computes optimal routes
   - Solution includes routes, unserved customers, and statistics

4. **Route Assignment:**
   - MRA assigns routes to DAs via FIPA-Request messages
   - DAs validate routes (capacity, distance, vehicle match)
   - DAs send accept/reject responses

5. **Route Execution:**
   - DAs execute routes using MovementBehaviour
   - Vehicles move towards customers at MOVEMENT_SPEED
   - After all deliveries, vehicles return to depot
   - Vehicles become available for next assignment

### Key Design Patterns

- **FIPA Protocols:** All communication uses FIPA-Request protocol
- **Service Discovery:** Agents discover each other via DF (Yellow Pages)
- **Separation of Concerns:** DepotProblemAssembler separates problem preparation from agent logic
- **Behavior-Based Architecture:** JADE behaviors handle different agent activities
- **Logging:** Comprehensive logging of all conversations and events

### Constants

- **MOVEMENT_SPEED:** 10.0 units per second (DeliveryAgent)
- **ARRIVAL_THRESHOLD:** 1.0 units (DeliveryAgent)
- **Vehicle Query Wait:** 3 seconds (MasterRoutingAgent)
- **Vehicle Response Timeout:** 10 seconds (MasterRoutingAgent)
- **Route Assignment Wait:** 8 seconds (MasterRoutingAgent)
- **Movement Update Period:** 1 second (DeliveryAgent)
- **Depot Return Check:** 5 seconds (DeliveryAgent)

---

**Document Version:** 1.0  
**Last Updated:** Generated from codebase analysis  
**Total Functions Documented:** 30+ methods across 3 agent classes

