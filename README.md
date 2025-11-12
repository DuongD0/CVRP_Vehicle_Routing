# CVRP Multi-Agent System

A multi-agent system implementation for solving the **Capacitated Vehicle Routing Problem (CVRP)** using JADE (Java Agent Development Framework) and Google OR-Tools solver.

## Overview

This system implements a CVRP solution where:
- **MRA (Master Routing Agent)** reads the problem from configuration, queries Delivery Agents for vehicle information, solves routes using Google OR-Tools, and assigns routes to Delivery Agents.
- **DA (Delivery Agent)** responds to MRA queries with capacity and maximum travel distance, and executes assigned routes.
- Agents discover each other through JADE's Yellow Pages (DF) using service types (no hardcoded names).
- All communication uses FIPA protocols (FIPA-Request).
- Results are logged as JSON files.

## Table of Contents

1. [Agent Architecture](#agent-architecture)
2. [Agent Requirements](#agent-requirements)
3. [Problem Specification](#problem-specification)
4. [Configuration](#configuration)
5. [Usage](#usage)
6. [Test Cases](#test-cases)
7. [Output](#output)
8. [Technical Details](#technical-details)

---

## Agent Architecture

### Master Routing Agent (MRA)

**Responsibilities:**
- Has its own location (acts as the depot where all DAs start from and travel back to)
- Reads the problem passed by main (about the "customers" it needs to deliver packages to)
- Asks for vehicle information from DAs, combines with customer information to solve routes
- Assigns routes back to the DAs to deliver

**Service Registration:**
- Registers with DF as `mra-service`
- Discovers DAs via `da-service` type

**Communication:**
- Uses FIPA-Request protocol to query DAs for vehicle information
- Uses FIPA-Request protocol to assign routes to DAs

### Delivery Agent (DA)

**Responsibilities:**
- Has its own capacity & maximum travel distance
- Responds to MRA queries with vehicle information
- Accepts route assignments from MRA
- Executes routes and returns to depot

**Service Registration:**
- Registers with DF as `da-service`
- Discovers MRA via `mra-service` type

**Communication:**
- Uses FIPA-Request protocol to respond to MRA queries
- Uses FIPA-Request protocol to receive route assignments

---

## Agent Requirements

### Service Discovery

- **MRA and DA must register their services to JADE's Yellow Pages (DF)**
- **Must find each other through the type of service (no hardcoded names)**
  - MRA searches for `da-service` type
  - DA searches for `mra-service` type
- **Initiated using a configuration file** (the main reads that file and starts the agents)

### Communication Protocol

- **Proper FIPA communication protocol must be used**
- **Protocol choice:** FIPA-Request for queries/answers and route assignments
- **All messages include:**
  - Sender, receiver, performative, content, timestamp
  - Conversation IDs for tracking

### Logging

- **Proper logs must be extracted to show the detailed communication process of the agents**
- **Logging level:** Save both message-level logs (sender, receiver, performative, content, timestamp) and result logs (routes assigned, route distance, items delivered)
- **Output format:** Put final results in JSON and also produce a human-readable summary in the console

---

## Problem Specification

### CVRP Problem

The system solves a Capacitated Vehicle Routing Problem where:

- **Customer list contains:**
  - Customer ID (node ID)
  - Demand (number of items)
  - Coordinates (x, y)

- **Solution must work when total demand is larger than total vehicle capacity**
  - Optimal solution is to deliver the most packages with the least distance
  - **Prefer number of packages delivered over minimizing distance**

- **Solver:** Uses Google OR-Tools as the main solver

### Optimization Objective

1. **Primary:** Maximize number of packages delivered
2. **Secondary:** Minimize total travel distance

The solver prioritizes visiting as many customers as possible (delivering more packages) over minimizing distance. This is achieved by using large penalties for unvisited nodes in OR-Tools.

---

## Configuration

The system uses JSON configuration files. Each configuration file contains:

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
      "capacity": 50,
      "maxDistance": 1000.0
    }
  ],
  "customers": [
    {
      "id": "1",
      "demand": 10,
      "x": 10.0,
      "y": 10.0
    }
  ]
}
```

### Configuration Fields

- **depot:** Depot location and name
  - `name`: Agent name for MRA
  - `x`, `y`: Depot coordinates
- **vehicles:** List of Delivery Agents
  - `name`: Agent name for DA
  - `capacity`: Maximum number of items vehicle can carry
  - `maxDistance`: Maximum distance vehicle can travel
- **customers:** List of customers to deliver to
  - `id`: Customer node ID
  - `demand`: Number of items to deliver
  - `x`, `y`: Customer coordinates

---

## Usage

### Prerequisites

- Java JDK 8 or higher
- Maven (for dependency management)
- JADE framework (included in dependencies)
- Google OR-Tools native libraries (for solver)
- Gson library (for JSON parsing)

### Building the Project

```bash
# Compile the project
mvn clean compile

# Run the system with default config
mvn exec:java -Dexec.mainClass="project.Main"

# Run with specific config file
mvn exec:java -Dexec.mainClass="project.Main" -Dexec.args="config/case_small.json"
```

### Running with JADE GUI

The system automatically starts with JADE GUI enabled. You can:

1. **View agent status** - See all agents in the agent tree
2. **Monitor messages** - View FIPA messages in the message queue
3. **Check DF** - Open DF GUI to see registered services:
   - `mra-service` - Master Routing Agent
   - `da-service` - Delivery Agents

---

## Test Cases

The system includes 5 test case configuration files:

### 1. case_small.json
- **Purpose:** Sanity test - few customers, capacity >= demand
- **Description:** Small problem with 5 customers and 2 vehicles with sufficient capacity

### 2. case_capacity_shortfall.json
- **Purpose:** Edge case - total demand > total capacity
- **Description:** Tests system behavior when total customer demand exceeds total vehicle capacity
- **Expected:** System should deliver as many packages as possible, prioritizing packages delivered over distance

### 3. case_tight_distance.json
- **Purpose:** Edge case - vehicles have tight max_distance making some customers unreachable
- **Description:** Tests system with vehicles that have very limited maximum travel distances
- **Expected:** System should handle unreachable customers gracefully

### 4. case_many_customers.json
- **Purpose:** Stress test (performance)
- **Description:** Large problem with 20 customers and 5 vehicles
- **Expected:** System should handle larger problems efficiently

### 5. case_random_seeded.json
- **Purpose:** For reproducibility
- **Description:** Fixed random seed for consistent testing
- **Expected:** Same input should produce same results

---

## Output

### JSON Results

Solution results are saved as JSON files in the `results/` directory with format:
```
results/result_{configName}_{timestamp}.json
```

**JSON Structure:**
```json
{
  "timestamp": "...",
  "configName": "...",
  "solveTimeMs": 1234,
  "summary": {
    "totalItemsRequested": 100,
    "totalItemsDelivered": 95,
    "totalDistance": 1234.56,
    "numberOfRoutes": 3,
    "deliveryRate": 0.95
  },
  "routes": [
    {
      "routeId": 1,
      "vehicleName": "DA1",
      "totalDemand": 30,
      "totalDistance": 456.78,
      "customers": [
        {
          "id": 1,
          "name": "1",
          "x": 10.0,
          "y": 10.0,
          "demand": 10
        }
      ]
    }
  ]
}
```

### Console Output

The system also prints a human-readable summary to the console showing:
- Total items requested vs delivered
- Delivery rate
- Total distance
- Number of routes
- Solve time
- Detailed route information

### Log Files

Detailed communication logs are saved in the `logs/` directory:
- `MRA_conversations.log` - MRA message logs
- `DA-{name}_conversations.log` - Individual DA message logs

Each log file contains:
- Message-level logs (sender, receiver, performative, content, timestamp)
- Conversation tracking (conversation IDs, start/end)
- Event logs (state changes, route assignments, etc.)

---

## Technical Details

### Solver Implementation

The system uses Google OR-Tools for CVRP solving:

- **Primary Objective:** Maximize packages delivered
  - Uses disjunction with large penalty (1,000,000) for unvisited nodes
  - Ensures visiting nodes takes precedence over distance minimization

- **Secondary Objective:** Minimize total distance
  - Arc cost evaluator minimizes travel distance
  - Only considered after maximizing packages delivered

- **Constraints:**
  - Capacity constraint per vehicle
  - Maximum distance constraint per vehicle

### Agent Communication Flow

1. **MRA Startup:**
   - MRA reads problem from config
   - MRA registers with DF as `mra-service`
   - MRA waits for DAs to register

2. **DA Startup:**
   - DAs register with DF as `da-service`
   - DAs wait for MRA queries

3. **Vehicle Information Query:**
   - MRA searches DF for `da-service` type
   - MRA sends FIPA-Request to each DA: `QUERY_VEHICLE_INFO`
   - DAs respond with: `STATE|CAPACITY|MAX_DISTANCE|NAME|X|Y`

4. **Route Solving:**
   - MRA assembles problem with customer and vehicle data
   - MRA calls OR-Tools solver
   - Solver returns routes optimized for packages delivered

5. **Route Assignment:**
   - MRA sends FIPA-Request to each DA with route assignment
   - Route message contains: route ID, vehicle ID, customers, coordinates, demand, distance
   - DAs validate and accept routes if feasible

6. **Route Execution:**
   - DAs execute routes (move to customers)
   - DAs return to depot after completing routes
   - DAs update state to "free" when ready for next assignment

### Project Structure

```
src/main/java/project/
├── Agent/
│   ├── MasterRoutingAgent.java    # MRA - solves CVRP and assigns routes
│   ├── DeliveryAgent.java         # DA - executes routes
│   └── DepotProblemAssembler.java # Helper for problem assembly
├── General/
│   ├── CustomerInfo.java          # Customer data structure
│   ├── CustomerRequest.java       # Request data structure
│   ├── RouteInfo.java             # Route data structure
│   ├── SolutionResult.java        # Solver result structure
│   └── VehicleInfo.java           # Vehicle data structure
├── Solver/
│   ├── VRPSolver.java             # Solver interface
│   └── ORToolsSolver.java         # OR-Tools CVRP implementation
├── Utils/
│   ├── AgentLogger.java           # Message logging utility
│   ├── JsonConfigReader.java      # JSON configuration reader
│   └── JsonResultLogger.java      # JSON result logger
└── Main.java                      # Entry point

config/
├── case_small.json                # Small test case
├── case_capacity_shortfall.json   # Capacity shortfall test case
├── case_tight_distance.json       # Tight distance test case
├── case_many_customers.json       # Many customers test case
└── case_random_seeded.json        # Random seeded test case

results/                            # JSON result files
logs/                               # Agent conversation logs
```

---

## Dependencies

- **JADE 4.6.0** - Multi-agent framework
- **Google OR-Tools 9.12.4544** - CVRP solver
- **Gson 2.10.1** - JSON parsing

---

## License

This project is for educational purposes.
