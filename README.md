# CVRP-TW Multi-Agent System

A multi-agent system implementation for solving the **Capacitated Vehicle Routing Problem with Time Windows (CVRP-TW)** using JADE (Java Agent Development Framework) and OR-Tools solver. The system follows FIPA (Foundation for Intelligent Physical Agents) protocols for agent communication and uses JADE's Directory Facilitator (DF) for automatic agent discovery.

## Table of Contents

1. [Project Structure](#project-structure)
2. [Agent Architecture](#agent-architecture)
3. [Agent Roles](#agent-roles)
4. [Workflow](#workflow)
5. [Usage](#usage)
6. [Technical Details](#technical-details)

---

## Project Structure

```
src/main/java/project/
├── Agent/
│   ├── Customer.java          # Customer agent that sends item requests with time windows
│   ├── Depot.java             # Depot agent that manages inventory and solves CVRP-TW
│   └── VehicleAgent.java      # Vehicle agent that bids for routes considering time windows
├── General/
│   ├── CustomerInfo.java      # Customer data structure with time window fields
│   ├── CustomerRequest.java   # Request data structure with time window constraints
│   ├── RouteInfo.java         # Route data structure
│   ├── SolutionResult.java    # Solver result structure
│   └── VehicleInfo.java       # Vehicle data structure
├── Solver/
│   ├── VRPSolver.java         # Solver interface (abstraction)
│   └── ORToolsSolver.java    # OR-Tools CVRP-TW implementation
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

## Agent Roles

### 1. Depot Agent (`Depot.java`)

**Responsibilities:**
- Manages inventory (simple dictionary: `Map<String, Integer>`)
- Receives and processes customer requests with time windows
- Checks item availability
- Batches requests for routing
- Solves **CVRP-TW** (Capacitated Vehicle Routing Problem with Time Windows) using OR-Tools
- Assigns routes to vehicles via Contract-Net protocol

**Services Registered:**
- Service Type: `depot-service`
- Service Name: `VRP-Depot`

**Key Behaviors:**
- `CustomerRequestHandler` - Handles FIPA-REQUEST messages from customers (includes time windows)
- `BatchProcessor` - Processes request batches when threshold reached (default: 5 requests)
- `VehicleStateResponseHandler` - Updates vehicle state from responses
- `ContractNetProposalHandler` - Handles route assignment via Contract-Net

**Inventory System:**
- Simple in-memory dictionary: `Map<String, Integer>`
- Items: ItemA, ItemB, ItemC, ItemD (each initialized to 100 units)
- Located at coordinates: **(0, 0)**

**Time Window Support:**
- Accepts time window constraints from customer requests
- Passes time windows to solver
- Solver ensures routes respect time window constraints

**Communication:**
- Receives: `FIPA-REQUEST` from customers (with time windows)
- Sends: `FIPA-REQUEST` (INFORM/REFUSE) to customers
- Discovers vehicles: `DFService.search()` for `vehicle-service` (no hardcoded names)
- Sends: `FIPA-QUERY` to discovered vehicles
- Sends: `FIPA Contract-Net` (CFP) to discovered vehicles for route bidding

---

### 2. Vehicle Agent (`VehicleAgent.java`)

**Responsibilities:**
- Maintains current position (not always at depot)
- Tracks state: `free`, `absent`, or `busy`
- Participates in Contract-Net bidding for routes
- **Evaluates time window feasibility** before bidding
- Calculates bid cost based on distance and time window constraints
- Completes assigned routes and returns to free state

**Services Registered:**
- Service Type: `vehicle-service`
- Service Name: `VRP-Vehicle-{localName}`

**Key Behaviors:**
- `StateChangeBehaviour` - Randomly changes state (simulating real-world factors)
- `QueryHandlerBehaviour` - Responds to state queries from depot
- `RouteContractNetResponder` - Handles Contract-Net bidding with time window checks
- `RouteCompletionBehaviour` - Simulates route completion

**Time Window Evaluation:**
- Parses time windows from CFP messages
- Calculates travel time from current position to first customer
- Checks if arrival time is within time window
- Refuses route if time window cannot be met
- Adds penalty for early arrival (waiting time)

**Bid Calculation:**
- Considers distance from current position to first customer
- Includes route distance
- Includes return distance to depot
- Adds time window penalty for early arrival
- Checks capacity constraints
- Checks time window feasibility

**Communication:**
- Discovers depot: Via DF (not used directly, receives CFP from depot)
- Receives: `FIPA-QUERY` from depot (state queries)
- Receives: `FIPA Contract-Net` (CFP) from depot (with time windows)
- Sends: `FIPA-QUERY` (INFORM) responses
- Sends: `FIPA Contract-Net` (PROPOSE) bids or REFUSE if time window infeasible
- Sends: `FIPA Contract-Net` (INFORM) after route acceptance

---

### 3. Customer Agent (`Customer.java`)

**Responsibilities:**
- Generates random item requests with **time window constraints**
- Finds depot via DF (no hardcoded depot name)
- Sends item requests to depot with time windows
- Receives responses about request status

**Services Registered:**
- Service Type: `customer-service`
- Service Name: `VRP-Customer-{id}`

**Key Behaviors:**
- `RequestGeneratorBehaviour` - Generates requests every 5-15 seconds with random time windows
- `ResponseHandlerBehaviour` - Handles responses from depot

**Time Window Generation:**
- Start time: Random between 0-480 minutes (0-8 hours)
- Duration: Random between 60-240 minutes (1-4 hours)
- Service time: Random between 5-15 minutes

**Request Format:**
- Items: Randomly selects from ItemA, ItemB, ItemC, ItemD
- Quantity: 5-20 units per request
- Includes: customer ID, name, coordinates, item name, quantity, **time window start, time window end, service time**

**Communication:**
- Discovers depot: `DFService.search()` for `depot-service` (no hardcoded name)
- Sends: `FIPA-REQUEST` to discovered depot (with time windows)
- Receives: `FIPA-REQUEST` (INFORM/REFUSE) from depot

---

## Workflow

### Complete System Workflow

```
1. System Startup
   ├─ Main.java creates and starts all agents independently
   ├─ Each agent registers with DF (Yellow Pages) using service types
   └─ Agents discover each other via DF service type matching (no hardcoded names)

2. Customer Request Flow
   ├─ Customer generates random request (item + quantity + time window)
   ├─ Customer finds depot via DF search (service type: "depot-service")
   ├─ Customer sends FIPA-REQUEST to discovered depot (includes time windows)
   ├─ Depot checks inventory
   │  ├─ If available: Reserve items, queue request with time windows, send INFORM
   │  └─ If unavailable: Send INFORM with availability status
   └─ Customer logs response

3. Batch Processing Flow (when threshold reached)
   ├─ Depot batches requests (default: 5+ requests with time windows)
   ├─ Depot finds vehicles via DF search (service type: "vehicle-service")
   ├─ Depot queries vehicle states (FIPA-QUERY) using discovered AIDs
   ├─ Depot filters free vehicles
   ├─ Depot builds CVRP-TW problem from requests (includes time windows)
   ├─ Depot calls solver (OR-Tools CVRP-TW)
   ├─ Depot receives solution with routes respecting time windows
   └─ Depot assigns routes via Contract-Net

4. Route Assignment Flow (Contract-Net with Time Windows)
   ├─ Depot sends CFP (Call for Proposal) to all discovered free vehicles
   │  └─ CFP includes: route info, customers, demand, distance, TIME WINDOWS
   ├─ Each vehicle evaluates route:
   │  ├─ Checks capacity constraints
   │  ├─ Checks TIME WINDOW FEASIBILITY (can it reach first customer in time?)
   │  ├─ Calculates bid cost (distance + time window penalties)
   │  └─ Sends PROPOSE if feasible, REFUSE if time window infeasible
   ├─ Depot receives proposals
   ├─ Depot accepts proposals
   ├─ Vehicles receive ACCEPT_PROPOSAL
   ├─ Vehicles confirm with INFORM
   └─ Vehicles mark as busy and execute route

5. Route Completion
   ├─ Vehicle simulates route completion (30 seconds)
   ├─ Vehicle returns to free state
   ├─ Vehicle position updated (moves closer to depot)
   └─ Vehicle ready for next route
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
4. **View agent logs** - Console output shows all agent activities including time window constraints

### Configuration

**Depot Configuration:**
- Location: `(0, 0)` (hardcoded)
- Inventory: Edit `Depot.java` setup() method
- Batch threshold: `BATCH_THRESHOLD = 5` (requests)
- Vehicle speed: `VEHICLE_SPEED = 1` (distance units per minute)

**Vehicle Configuration:**
- Vehicle names and capacities: Edit `Main.java`
- Default capacity: 50, 40, 30 (for 3 vehicles)
- Vehicle speed: 1 unit per minute (used for time calculations)

**Customer Configuration:**
- Customer positions: Edit `Main.java`
- Request frequency: 5-15 seconds (random)
- Time window generation: Random between 0-480 minutes start, 60-240 minutes duration

---

## Technical Details

### CVRP-TW Problem

**Capacitated Vehicle Routing Problem with Time Windows:**
- Vehicles have limited capacity
- Customers have time window constraints (earliest and latest arrival times)
- Vehicles must arrive at customers within their time windows
- Objective: Minimize total distance while respecting capacity and time windows

### FIPA Protocols Used

1. **FIPA-REQUEST** - Customer → Depot (item requests with time windows)
2. **FIPA-QUERY** - Depot → Vehicle (state queries)
3. **FIPA Contract-Net** - Depot → Vehicle (route bidding with time window constraints)

### Message Formats

**Customer Request (with Time Windows):**
```
REQUEST:customerId|customerName|x|y|itemName|quantity|twStart|twEnd|serviceTime
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
STATE:free|CAPACITY:50|NAME:Vehicle1|X:10.5|Y:20.3
```

**Contract-Net CFP (with Time Windows):**
```
ROUTE:1|CUSTOMERS:1,2,3|DEMAND:25|DISTANCE:150.0|TW:60-180,120-240,200-320
```

**Contract-Net Proposal:**
```
COST:165.50|CAPACITY:50|AVAILABLE:25
```

### Solver Interface

```java
public interface VRPSolver {
    SolutionResult solve(int numNodes, int numCustomers, int numVehicles,
                        int vehicleCapacity, int[] demand, int[][] distance,
                        int[][] timeWindows, int[] serviceTime, int vehicleSpeed);
}
```

**Time Window Constraints:**
- `timeWindows[i]` = `[start, end]` for node i (in minutes from start)
- Depot (index 0): `[0, Integer.MAX_VALUE]` (no constraint)
- Service time: Time spent at each customer (in minutes)

**OR-Tools Implementation:**
- Uses time dimension to enforce time window constraints
- Converts distance to time using vehicle speed
- Ensures vehicles arrive within customer time windows
- Penalizes or rejects routes that violate time windows

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

### Time Window Handling

**In Customer Requests:**
- Each request includes: `timeWindowStart`, `timeWindowEnd`, `serviceTime`
- Time windows are in minutes from system start (0 = immediate)

**In Solver:**
- OR-Tools time dimension enforces time windows
- Vehicles must arrive at customers within `[start, end]` window
- Service time added after arrival
- Travel time calculated from distance / vehicle speed

**In Vehicle Bidding:**
- Vehicles check time window feasibility before bidding
- Calculate travel time from current position to first customer
- Refuse if arrival time > time window end
- Add penalty if arrival time < time window start (early arrival)

### Inventory System

Simple in-memory dictionary:
```java
Map<String, Integer> inventory;
// Key: item name (e.g., "ItemA")
// Value: quantity available
```

No database required - sufficient for demonstration and testing.

---

## Troubleshooting

### Agents Not Discovering Each Other

- **Check DF is running** - JADE automatically starts DF
- **Verify registration** - Check console logs for DF registration messages
- **Wait for registration** - Agents need time to register (1-2 seconds)
- **Use DF GUI** - Verify services are registered by type (not by name)

### Time Window Violations

- **Check vehicle speed** - Ensure `VEHICLE_SPEED` is appropriate for distance units
- **Check time window values** - Verify time windows are reasonable (in minutes)
- **Check solver logs** - OR-Tools will indicate if time windows cannot be satisfied

### No Routes Generated

- **Check vehicle availability** - Verify vehicles are in "free" state
- **Check batch threshold** - Ensure enough requests are queued
- **Check time windows** - Verify time windows are feasible
- **Check solver** - Verify OR-Tools libraries are loaded

---

## Future Enhancements

- Multiple depots
- Pickup and delivery requests
- Dynamic vehicle addition/removal
- Real-time position tracking
- Database integration for inventory
- Advanced bidding strategies
- Load balancing between vehicles
- Time window relaxation (soft constraints)

---

## License

This project is for educational purposes.

---

## Contact

For questions or issues, please refer to the project documentation or contact the development team.
