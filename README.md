# CVRP Multi-Agent System - Basic Requirements 1 & 2

A multi-agent system implementation for solving the **Capacitated Vehicle Routing Problem (CVRP)** with capacity and maximum distance constraints using JADE (Java Agent Development Framework) and OR-Tools solver. The system implements Basic Requirements 1 and 2:

- **Basic Requirement 1:** Prioritizes number of items delivered over total travel distance
- **Basic Requirement 2:** Enforces maximum distance constraint per vehicle (dv)

## Table of Contents

1. [Project Structure](#project-structure)
2. [Agent Architecture](#agent-architecture)
3. [Basic Requirements](#basic-requirements)
4. [Agent Roles](#agent-roles)
5. [Workflow](#workflow)
6. [Usage](#usage)
7. [Test Cases](#test-cases)
8. [Technical Details](#technical-details)

---

## Project Structure

```
src/main/java/project/
├── Agent/
│   ├── Customer.java          # Customer agent that sends item requests
│   ├── Depot.java             # Depot agent that manages inventory and solves CVRP
│   └── VehicleAgent.java      # Vehicle agent that bids for routes with constraints
├── General/
│   ├── CustomerInfo.java      # Customer data structure
│   ├── CustomerRequest.java   # Request data structure
│   ├── RouteInfo.java         # Route data structure
│   ├── SolutionResult.java    # Solver result structure (includes itemsDelivered)
│   └── VehicleInfo.java       # Vehicle data structure (includes maxDistance)
├── Solver/
│   ├── VRPSolver.java         # Solver interface
│   └── ORToolsSolver.java     # OR-Tools CVRP implementation with constraints
└── Main.java                  # Entry point - creates and starts agents
```

---

## Agent Architecture

The system implements a **decentralized multi-agent architecture** where:

- **Agents are independent** - No direct dependencies between agents
- **Service discovery via DF only** - Agents discover each other exclusively through JADE's Directory Facilitator (Yellow Pages)
- **No hardcoded agent names** - All communication uses DF service discovery
- **FIPA protocol compliance** - All communications follow FIPA standards
- **Pluggable solver** - Solver implementation can be swapped via interface

### Agent Discovery Flow

1. All agents register their services with DF at startup
2. Agents discover each other by searching DF for service types:
   - **Depot** searches for `vehicle-service` (no knowledge of vehicle names)
   - **Customers** search for `depot-service` (no knowledge of depot name)
   - **Vehicles** register as `vehicle-service` and are discovered dynamically

**Critical:** Agents **never** use hardcoded agent names. All discovery is done through DF service type matching.

---

## Basic Requirements

### Basic Requirement 1: Prioritize Items Delivered Over Distance

**Objective:** Maximize number of items delivered (primary), minimize total travel distance (secondary)

**Implementation:**
- OR-Tools solver uses disjunction with large penalty (1,000,000) for unvisited nodes
- This ensures that visiting nodes (delivering items) takes precedence over distance minimization
- Solver will try to visit as many nodes as possible first, then minimize distance as secondary objective

**Example:**
- Solution 1: Delivers 21 items with total distance 500 → **Better**
- Solution 2: Delivers 20 items with total distance 400 → Worse

### Basic Requirement 2: Maximum Distance Constraint Per Vehicle

**Constraint:** Each vehicle v can only travel a maximum distance dv

**Implementation:**
- Each vehicle has a `maxDistance` field (dv)
- OR-Tools solver adds distance dimension with vehicle-specific maximum distances
- Vehicles check maximum distance constraint during bidding phase
- Solver enforces distance constraint during optimization

**Distance Calculation:**
- Total route distance = distance from current position to first customer + route distance + distance from last customer to depot
- Route is refused if total distance > vehicle's maxDistance

---

## Agent Roles

### 1. Depot Agent (`Depot.java`)

**Responsibilities:**
- Manages inventory (simple dictionary: `Map<String, Integer>`)
- Receives and processes customer requests
- Checks item availability
- Batches requests for routing
- Solves **CVRP** with capacity and maximum distance constraints
- Assigns routes to vehicles via Contract-Net protocol

**Services Registered:**
- Service Type: `depot-service`
- Service Name: `VRP-Depot`

**Key Behaviors:**
- `CustomerRequestHandler` - Handles FIPA-REQUEST messages from customers
- `BatchProcessor` - Processes request batches when threshold reached (default: 5 requests)
- `VehicleStateResponseHandler` - Updates vehicle state from responses
- `ContractNetProposalHandler` - Handles route assignment via Contract-Net

**Inventory System:**
- Simple in-memory dictionary: `Map<String, Integer>`
- Items: ItemA, ItemB, ItemC, ItemD (each initialized to 100 units)
- Located at coordinates: **(0, 0)**

**Communication:**
- Receives: `FIPA-REQUEST` from customers
- Sends: `FIPA-REQUEST` (INFORM/REFUSE) to customers
- Discovers vehicles: `DFService.search()` for `vehicle-service`
- Sends: `FIPA-QUERY` to discovered vehicles
- Sends: `FIPA Contract-Net` (CFP) to discovered vehicles for route bidding

---

### 2. Vehicle Agent (`VehicleAgent.java`)

**Responsibilities:**
- Maintains current position (not always at depot)
- Tracks state: `free` or `absent`
- Participates in Contract-Net bidding for routes
- **Checks capacity constraint** before bidding
- **Checks maximum distance constraint** before bidding
- Calculates bid cost based on total distance
- Completes assigned routes and returns to free state

**Services Registered:**
- Service Type: `vehicle-service`
- Service Name: `VRP-Vehicle-{localName}`

**Key Behaviors:**
- `QueryHandlerBehaviour` - Responds to state queries from depot
- `VehicleBiddingCoordinator` - Handles vehicle-to-vehicle bidding for routes
- `MovementBehaviour` - Real movement simulation (10 units/second toward customers)
- `ReturnToDepotBehaviour` - Safety mechanism to return vehicle to depot when free

**Constraint Checks:**
- **Capacity Check:** Route demand ≤ vehicle capacity
- **Distance Check:** Total route distance ≤ vehicle maxDistance
- Total distance = current position → first customer + route distance + last customer → depot

**Bid Calculation:**
- Considers distance from current position to first customer
- Includes route distance
- Includes return distance to depot
- Checks capacity constraints
- Checks maximum distance constraints

**Communication:**
- Receives: `FIPA-QUERY` from depot (state queries)
- Receives: `FIPA Contract-Net` (CFP) from depot
- Sends: `FIPA-QUERY` (INFORM) responses
- Sends: `FIPA Contract-Net` (PROPOSE) bids or REFUSE if constraints violated
- Sends: `FIPA Contract-Net` (INFORM) after route acceptance

---

### 3. Customer Agent (`Customer.java`)

**Responsibilities:**
- Generates random item requests
- Finds depot via DF (no hardcoded depot name)
- Sends item requests to depot
- Receives responses about request status
- Receives delivery completion notifications

**Services Registered:**
- Service Type: `customer-service`
- Service Name: `VRP-Customer-{id}`

**Key Behaviors:**
- `RequestGeneratorBehaviour` - Generates requests every 30-60 seconds
- `ResponseHandlerBehaviour` - Handles responses from depot

**Request Generation:**
- Items: Randomly selects from ItemA, ItemB, ItemC, ItemD
- Quantity: 5-15 units per request
- Includes: customer ID, name, coordinates, item name, quantity

**Communication:**
- Discovers depot: `DFService.search()` for `depot-service`
- Sends: `FIPA-REQUEST` to discovered depot
- Receives: `FIPA-REQUEST` (INFORM/REFUSE) from depot
- Receives: `FIPA-REQUEST` (INFORM) from vehicles (delivery completion)

---

## Workflow

### Complete System Workflow

```
1. System Startup
   ├─ Main.java creates and starts all agents independently
   ├─ Each agent registers with DF (Yellow Pages) using service types
   └─ Agents discover each other via DF service type matching (no hardcoded names)

2. Customer Request Flow
   ├─ Customer generates random request (item + quantity)
   ├─ Customer finds depot via DF search (service type: "depot-service")
   ├─ Customer sends FIPA-REQUEST to discovered depot
   ├─ Depot checks inventory
   │  ├─ If available: Reserve items, queue request, send INFORM
   │  └─ If unavailable: Send INFORM with availability status
   └─ Customer logs response

3. Batch Processing Flow (when threshold reached)
   ├─ Depot batches requests (default: 5+ requests)
   ├─ Depot finds vehicles via DF search (service type: "vehicle-service")
   ├─ Depot queries vehicle states (FIPA-QUERY) using discovered AIDs
   ├─ Depot filters free vehicles
   ├─ Depot builds CVRP problem from requests
   ├─ Depot calls solver (OR-Tools CVRP with constraints)
   ├─ Depot receives solution with routes respecting constraints
   └─ Depot assigns routes via Contract-Net

4. Route Assignment Flow (Contract-Net with Constraints)
   ├─ Depot sends CFP (Call for Proposal) to all discovered free vehicles
   │  └─ CFP includes: route info, customers, demand, distance
   ├─ Each vehicle evaluates route:
   │  ├─ Checks capacity constraints
   │  ├─ Checks maximum distance constraints
   │  ├─ Calculates bid cost (total distance)
   │  └─ Sends PROPOSE if feasible, REFUSE if constraints violated
   ├─ Depot receives proposals
   ├─ Depot accepts proposals
   ├─ Vehicles receive ACCEPT_PROPOSAL
   ├─ Vehicles confirm with INFORM
   └─ Vehicles mark as busy and execute route

5. Route Execution with Real Movement
   ├─ Vehicle parses route data and extracts customer information
   ├─ MovementBehaviour starts (updates position every 1 second)
   ├─ Vehicle moves toward customers (10 units/second):
   │  ├─ Position updated every second using linear interpolation
   │  ├─ Distance calculated and movement interpolated
   │  └─ Logs position every 5 seconds
   ├─ When vehicle arrives at customer (within 1 unit threshold):
   │  ├─ Vehicle notifies customer (DELIVERY_COMPLETE)
   │  ├─ Vehicle moves to next customer in route
   │  └─ Repeat for all customers
   ├─ After all customers visited:
   │  ├─ Vehicle returns to depot (10 units/second)
   │  └─ Position updated every second during return
   └─ When vehicle arrives at depot:
      ├─ Vehicle state changes to "free"
      ├─ Vehicle position set to depot (0, 0) exactly
      └─ MovementBehaviour stops
```

---

## Usage

### Prerequisites

- Java JDK 8 or higher
- Maven (for dependency management)
- JADE framework (included in dependencies)
- OR-Tools native libraries (for solver)

### Building the Project

```bash
# Compile the project
mvn clean compile

# Run the system
mvn exec:java -Dexec.mainClass="project.Main"
```

### Running with JADE GUI

The system automatically starts with JADE GUI enabled. You can:

1. **View agent status** - See all agents in the agent tree
2. **Monitor messages** - View FIPA messages in the message queue
3. **Check DF** - Open DF GUI to see registered services:
   - Tools → DF GUI
   - Verify all services are registered by type (not by name)
4. **View agent logs** - Console output shows all agent activities

### Configuration

**Depot Configuration:**
- Location: `(0, 0)` (hardcoded)
- Inventory: Edit `Depot.java` setup() method
- Batch threshold: `BATCH_THRESHOLD = 5` (requests)

**Vehicle Configuration:**
- Vehicle names, capacities, and maxDistances: Edit `Main.java`
- Default: 3 vehicles with capacities [50, 40, 30] and maxDistances [1000.0, 800.0, 600.0]

**Customer Configuration:**
- Customer positions: Edit `Main.java`
- Request frequency: 30-60 seconds (random)

---

## Test Cases

Test cases are provided in separate files:

1. **test_case_basic_requirement_1.txt** - Tests prioritization of items delivered over distance
2. **test_case_basic_requirement_2.txt** - Tests maximum distance constraint per vehicle
3. **test_case_insufficient_capacity.txt** - Tests handling of insufficient total capacity
4. **test_case_vehicle_bidding.txt** - Tests vehicle bidding with constraints
5. **test_case_end_to_end.txt** - Tests complete system workflow
6. **test_case_distance_calculation.txt** - Tests distance calculation and constraints

See individual test case files for detailed test steps and expected results.

---

## Technical Details

### CVRP Problem

**Capacitated Vehicle Routing Problem:**
- Vehicles have limited capacity (number of items)
- Vehicles have maximum distance constraint (dv)
- Customers have demand (number of items)
- Objective: Maximize items delivered (primary), minimize total distance (secondary)

### FIPA Protocols Used

1. **FIPA-REQUEST** - Customer → Depot (item requests), Vehicle → Customer (delivery notifications)
2. **FIPA-QUERY** - Depot → Vehicle (state queries)
3. **FIPA Contract-Net** - Depot → Vehicle (route bidding with constraints) - DEPRECATED
4. **Vehicle-to-Vehicle Bidding** - Vehicles bid among themselves for routes (custom protocol)

### Message Formats

**Customer Request:**
```
REQUEST:customerId|customerName|x|y|itemName|quantity
```

**Depot Response:**
```
ITEM_AVAILABLE:Request accepted...
or
ITEM_UNAVAILABLE:Insufficient inventory...
```

**Vehicle State Query:**
```
QUERY_STATE
```

**Vehicle State Response:**
```
STATE:free|CAPACITY:50|MAX_DISTANCE:1000.0|NAME:Vehicle1|X:10.5|Y:20.3
```

**Route Announcement (Vehicle-to-Vehicle Bidding):**
```
ROUTE_ANNOUNCEMENT:ROUTE:1|CUSTOMERS:1,2,3|CUSTOMER_IDS:customer-1,customer-2,customer-3|COORDS:100.00,150.00;200.00,100.00|DEMAND:25|DISTANCE:350.0
```

**Route Winner Notification:**
```
ROUTE_WON:ROUTE:1|VEHICLE:Vehicle1|ROUTE:1|CUSTOMERS:1,2,3|CUSTOMER_IDS:customer-1,customer-2,customer-3|COORDS:...|...
```

**Delivery Notification:**
```
DELIVERY_COMPLETE:Your order has been delivered by vehicle Vehicle1. Package arrived at (100.00, 150.00)
```

### Solver Interface

```java
public interface VRPSolver {
    SolutionResult solve(int numNodes, int numCustomers, int numVehicles, 
                       int[] vehicleCapacities, double[] vehicleMaxDistances,
                       int[] demand, int[][] distance);
}
```

**Constraints:**
- `vehicleCapacities[i]` = capacity of vehicle i (number of items)
- `vehicleMaxDistances[i]` = maximum distance for vehicle i
- `demand[i]` = demand at node i (index 0 is depot, demand=0)
- `distance[i][j]` = straight-line distance between nodes i and j

**Solution Result:**
- `routes` - List of routes for each vehicle
- `itemsDelivered` - Number of items delivered (Basic Requirement 1)
- `itemsTotal` - Total number of items requested
- `totalDistance` - Total distance traveled by all vehicles

### OR-Tools Implementation

**Basic Requirement 1 - Prioritize Items Delivered:**
- Uses `addDisjunction()` with large penalty (1,000,000) for unvisited nodes
- Ensures visiting nodes (delivering items) takes precedence over distance minimization
- Primary objective: Maximize items delivered
- Secondary objective: Minimize total distance

**Basic Requirement 2 - Maximum Distance Constraint:**
- Adds distance dimension with `addDimensionWithVehicleCapacity()`
- Sets vehicle-specific maximum distances
- Enforces cumulative distance ≤ maxDistance for each vehicle
- Vehicles also check distance constraint during bidding phase

### Distance Calculation

**Straight-Line Distance:**
- Uses Euclidean distance: `sqrt((x1-x2)^2 + (y1-y2)^2)`
- Distance matrix calculated for all node pairs
- Total route distance for vehicle includes:
  1. Distance from vehicle's current position to first customer
  2. Route distance (sum of distances between customers on route)
  3. Distance from last customer back to depot

### Movement Simulation

**Real Movement Implementation:**
- Vehicles move at **10 units per second** toward customers
- Position updated every **1 second** (1000ms tick interval)
- Movement calculated using **linear interpolation** toward target
- **Arrival threshold:** 1.0 unit distance (vehicle considered arrived)

**Movement Behavior:**
- `MovementBehaviour` (TickerBehaviour) updates vehicle position every second
- When moving to customer:
  - Calculates distance to target customer
  - Moves 10 units toward target (or remaining distance if less)
  - Updates `currentX` and `currentY` using linear interpolation
  - Logs position every 5 seconds
- When arriving at customer (distance ≤ 1.0 unit):
  - Sets vehicle position to customer location (exact)
  - Notifies customer (DELIVERY_COMPLETE message)
  - Moves to next customer in route
- When all customers visited:
  - Vehicle returns to depot (10 units/second)
  - Position updated every second during return
- When arriving at depot:
  - Sets vehicle position to depot (0, 0) exactly
  - Sets state to "free"
  - Stops MovementBehaviour

**Starting Position:**
- All vehicles start at depot coordinates (0, 0) when system starts
- Vehicle position is tracked continuously during route execution
- Position is used for distance calculations in bidding phase

### Agent Discovery (DF Only)

**No Hardcoded Names:**
- All agents use `DFService.search()` to find each other
- Search by service type, not by agent name
- Depot finds vehicles by searching for `vehicle-service`
- Customers find depot by searching for `depot-service`
- Vehicles are discovered dynamically - no need to know their names

**Example Discovery:**
```java
// Depot finding vehicles (NO hardcoded names)
DFAgentDescription dfd = new DFAgentDescription();
ServiceDescription sd = new ServiceDescription();
sd.setType("vehicle-service");  // Search by TYPE only
dfd.addServices(sd);
DFAgentDescription[] results = DFService.search(this, dfd);
// Results contain all vehicles registered with "vehicle-service"
```

---

## Troubleshooting

### Agents Not Discovering Each Other

- **Check DF is running** - JADE automatically starts DF
- **Verify registration** - Check console logs for DF registration messages
- **Wait for registration** - Agents need time to register (1-2 seconds)
- **Use DF GUI** - Verify services are registered by type (not by name)

### No Routes Generated

- **Check vehicle availability** - Verify vehicles are in "free" state
- **Check batch threshold** - Ensure enough requests are queued
- **Check constraints** - Verify capacity and distance constraints are feasible
- **Check solver** - Verify OR-Tools libraries are loaded

### Distance Constraint Violations

- **Check vehicle maxDistance** - Verify maxDistance values are reasonable
- **Check customer locations** - Verify distances are calculable
- **Check route distance calculation** - Verify total distance includes all segments

---

## Future Enhancements

- Custom optimization algorithm implementation (GA, ACO, PSO, CSP)
- GUI for user input, parameter settings, and visualization
- Configuration file for default parameters
- File input for loading delivery items
- Database integration for inventory
- Advanced bidding strategies
- Load balancing between vehicles

---

## License

This project is for educational purposes.

---

## Contact

For questions or issues, please refer to the project documentation or contact the development team.
