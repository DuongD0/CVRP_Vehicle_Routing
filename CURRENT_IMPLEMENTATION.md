# Current System Implementation Description

## Overview

This is a **Capacitated Vehicle Routing Problem (CVRP)** multi-agent system that uses a three-tier architecture:
1. **Frontend** (React/TypeScript) - User interface for managing vehicles and submitting customer requests
2. **Backend** (Python Flask) - API server that queues requests and stores solutions
3. **Agent System** (Java/JADE) - Multi-agent system that solves routing problems using Google OR-Tools

---

## Architecture

### System Flow

```
Frontend → Backend API → Java Agent System → OR-Tools Solver → Backend API → Frontend
```

1. **Frontend** submits CVRP requests to backend
2. **Backend** queues requests and assigns request IDs
3. **Java Main** polls backend for pending requests
4. **JADE Agents** process requests, solve routes, and submit solutions
5. **Backend** stores solutions
6. **Frontend** polls backend for solutions

---

## Component Details

### 1. Frontend (React/TypeScript)

**Location**: `frontend/src/`

#### Key Components:

**App.tsx** - Main application router
- Manages two pages: Vehicle Management and Request Workspace
- Maintains vehicle state and version tracking

**VehicleManagementPage.tsx** - Vehicle fleet management
- Allows users to add/edit/delete vehicles
- Tracks unsaved changes
- Forces save before navigation
- Sends vehicle data via `saveVehiclesOnly()` API call
- Features:
  - Save button with validation
  - Unsaved changes modal
  - Vehicle version tracking

**RequestWorkspace.tsx** - Customer request submission
- Allows users to add customers on a map
- Submits customer-only requests (without vehicles)
- Supports test cases (which include vehicles)
- Polls for solutions
- Displays route visualization
- Features:
  - Log viewer button (opens LogViewerSheet)
  - Test case loading
  - Solution status tracking

**LogViewerSheet.tsx** - Agent log viewer
- Displays logs from different agents (MRA, DA1, DA2, DA3)
- Filters by severity and search query
- Fetches logs via `fetchAgentLogs()` API

#### API Functions (`utils/api.ts`):
- `saveVehiclesOnly()` - Sends vehicle-only data
- `submitCustomerRequest()` - Sends customer-only data
- `submitVehicleCustomerRequest()` - Sends both (for test cases)
- `checkSolutionStatus()` - Polls for solution completion
- `fetchAgentLogs()` - Retrieves agent logs

#### Current Limitations:
- Frontend expects separate APIs for different data types, but backend only has one endpoint
- Log viewer calls `/api/logs` which doesn't exist in backend
- Vehicle state updates not implemented

---

### 2. Backend (Python Flask)

**Location**: `backend/backend_server.py`

#### Current Endpoints:

**POST `/api/solve-cvrp`** - Submit CVRP request
- Accepts requests with `depot`, `vehicles`, and `customers`
- Validates all required fields
- Stores request with status "pending"
- Returns `request_id` immediately (202 Accepted)
- **Limitation**: Doesn't distinguish between `vehicle_only`, `customer_only`, or `vehicle_customer` data types

**GET `/api/solve-cvrp?action=poll`** - Poll for pending requests
- Returns oldest pending request
- Marks request as "processing"
- Returns 204 No Content if no pending requests
- Used by Java Main to poll for work

**POST `/api/solve-cvrp?action=response`** - Submit solution
- Receives solution from MRA agent
- Stores solution linked to request_id
- Marks request as "completed"

**GET `/api/solution/<request_id>`** - Check solution status
- Returns solution if completed
- Returns status (pending/processing/completed) if not ready
- Used by frontend to poll for solutions

#### Current Limitations:
- No separate endpoints for vehicle-only or customer-only requests
- No `/api/logs` endpoint (frontend expects this)
- No `/api/agent-state` endpoint for DA state updates
- Doesn't process `data_type` field from frontend requests

---

### 3. Agent System (Java/JADE)

**Location**: `src/main/java/project/`

#### Main.java - Entry Point

**Function**: Polls backend and orchestrates agents

**Process**:
1. Initializes JADE runtime (no GUI)
2. Creates main container
3. Continuously polls backend every 2 seconds
4. When request received:
   - Resets log folder (timestamped)
   - Converts backend JSON to CVRPConfig
   - Creates MRA (Master Routing Agent)
   - Creates DAs (Delivery Agents) for each vehicle
   - Waits for solution (60 second timeout)
   - Agents remain running after solution

**Current Behavior**:
- Creates new MRA and DAs for **every request**
- DAs are named: `{vehicleName}-{requestId}`
- Agents persist until program shutdown
- No DA lifecycle management (terminate/keep/start logic)

#### MasterRoutingAgent (MRA)

**Function**: Solves CVRP and assigns routes

**Process**:
1. **Setup**:
   - Receives config (depot, vehicles, customers)
   - Registers with DF (Directory Facilitator) as "mra-service"
   - Initializes OR-Tools solver

2. **Query Vehicles** (after 3 second delay):
   - Searches DF for "da-service" agents
   - Sends FIPA-Request to each DA: `QUERY_VEHICLE_INFO`
   - Waits for responses (up to 10 seconds)
   - Collects vehicle info (capacity, maxDistance, name, position)

3. **Solve Problem**:
   - Assembles problem with customers and vehicles
   - Calls OR-Tools solver
   - Gets optimized routes

4. **Submit Solution**:
   - Converts solution to JSON format
   - Submits to backend via `BackendClient.submitSolution()`
   - Logs solution to file

5. **Assign Routes**:
   - Sends route assignments to DAs via FIPA-Request
   - Route message contains: route ID, customers, coordinates, demand, distance
   - Waits 8 seconds for DA responses
   - Signals completion to Main

**Current Limitations**:
- Always queries all DAs (doesn't check if busy)
- No handler for DA arrival notifications
- Doesn't track DA states (busy/free)

#### DeliveryAgent (DA)

**Function**: Executes delivery routes

**Process**:
1. **Setup**:
   - Receives vehicle name, capacity, maxDistance
   - Initializes position at depot (0, 0)
   - Registers with DF as "da-service"
   - Sets up handlers for queries and route assignments

2. **Vehicle Info Query Handler**:
   - Responds to MRA queries with: `CAPACITY|MAX_DISTANCE|NAME|X|Y`
   - **Current**: Always responds, never says "busy"
   - **Missing**: State checking (busy/free)

3. **Route Assignment Handler**:
   - Receives route assignment from MRA
   - Validates route (capacity, distance constraints)
   - Accepts or rejects route
   - If accepted, starts movement behavior

4. **Movement Behavior**:
   - Moves vehicle towards customers (10 units/second)
   - Updates position every 1 second
   - When arrives at customer, moves to next
   - When all customers visited, returns to depot
   - When arrives at depot, sets `isMoving = false`

5. **Return to Depot Behavior**:
   - Periodically checks if free and not at depot
   - Moves vehicle to depot if needed

**Current Limitations**:
- No state tracking (busy/free)
- Doesn't notify depot when returning
- Always responds to queries (doesn't check if delivering)
- No state included in vehicle info response

---

## Data Flow Examples

### Example 1: Regular Customer Request

1. **Frontend**: User adds customers, clicks "Submit Request"
2. **Frontend**: Calls `submitCustomerRequest()` → `POST /api/solve-cvrp` (customer-only)
3. **Backend**: Stores request, returns `request_id`
4. **Java Main**: Polls backend, receives request
5. **Java Main**: Creates MRA and DAs (from existing vehicles? **Currently creates new ones**)
6. **MRA**: Queries DAs for vehicle info
7. **DAs**: Respond with capacity, maxDistance, position
8. **MRA**: Solves problem with OR-Tools
9. **MRA**: Submits solution to backend
10. **MRA**: Assigns routes to DAs
11. **DAs**: Execute routes (move to customers, return to depot)
12. **Frontend**: Polls `/api/solution/<request_id>` for solution
13. **Frontend**: Displays routes on map

### Example 2: Vehicle Management

1. **Frontend**: User edits vehicles, clicks "Save"
2. **Frontend**: Calls `saveVehiclesOnly()` → `POST /api/solve-cvrp` (vehicle-only)
3. **Backend**: **Currently treats as regular request** (doesn't handle vehicle-only)
4. **Java Main**: **Would create new DAs** (but should terminate/keep/start based on vehicle list)

---

## Current State Management

### Vehicle States
- **Not Implemented**: DAs don't track or report busy/free states
- **Not Implemented**: Backend doesn't receive state updates
- **Not Implemented**: Frontend doesn't display real-time states

### Agent Lifecycle
- **Current**: New MRA and DAs created for every request
- **Expected**: DAs should persist, only terminate/start based on vehicle list changes

### Request Types
- **Frontend**: Sends `data_type` field (`vehicle_only`, `customer_only`, `vehicle_customer`)
- **Backend**: **Ignores** `data_type` field, treats all requests the same
- **Agent**: **Doesn't distinguish** between request types

---

## Logging System

### Agent Logs
- **Location**: `logs/{timestamp}/`
- **Format**: Separate files for each agent (MRA, DA-DA1, DA-DA2, etc.)
- **Content**: FIPA messages, conversations, events
- **Frontend**: LogViewerSheet tries to fetch via `/api/logs` (doesn't exist)

### Solution Logs
- **Location**: `results/`
- **Format**: JSON files with solution data
- **Naming**: `result_{requestId}_{timestamp}.json`

---

## Key Technologies

- **Frontend**: React, TypeScript, Vite, Tailwind CSS
- **Backend**: Python Flask, CORS enabled
- **Agent Framework**: JADE (Java Agent Development Framework)
- **Solver**: Google OR-Tools
- **Communication**: FIPA-Request protocol, DF (Directory Facilitator) for service discovery
- **Data Format**: JSON

---

## Current Limitations Summary

1. **Backend**:
   - No separate endpoints for different data types
   - No `/api/logs` endpoint
   - No `/api/agent-state` endpoint
   - Doesn't process `data_type` field

2. **Agent (Main)**:
   - Creates new agents for every request (should manage lifecycle)
   - No logic to terminate/keep/start DAs based on vehicle list

3. **Agent (DA)**:
   - No state tracking (busy/free)
   - Doesn't notify depot when returning
   - Always responds to queries (doesn't check if busy)

4. **Agent (MRA)**:
   - No handler for DA arrival notifications
   - Doesn't track DA states

5. **Frontend**:
   - Ready for features, but backend/agent don't support them yet

---

## What Works Currently

✅ Frontend can submit customer requests with vehicles
✅ Backend queues requests and stores solutions
✅ Java system polls backend and processes requests
✅ MRA solves CVRP using OR-Tools
✅ Routes are assigned to DAs
✅ DAs execute routes (move to customers, return to depot)
✅ Solutions are returned to frontend
✅ Logging system works (file-based)
✅ Test cases can be loaded
✅ Vehicle management page with save functionality

---

## What Doesn't Work Yet

❌ Vehicle-only requests (backend doesn't handle them)
❌ Customer-only requests (backend doesn't distinguish)
❌ DA state management (busy/free)
❌ DA notifies depot on arrival
❌ DA lifecycle management (terminate/keep/start)
❌ DA responds "busy" when delivering
❌ Log viewer API endpoint
❌ Real-time state updates to frontend

