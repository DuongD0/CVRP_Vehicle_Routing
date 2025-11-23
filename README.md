# CVRP Multi-Agent System

A comprehensive multi-agent system implementation for solving the **Capacitated Vehicle Routing Problem (CVRP)** using JADE (Java Agent Development Framework), Google OR-Tools solver, and a modern web interface.

---

## 📋 Table of Contents

1. [Problem Introduction](#problem-introduction)
2. [Project Overview](#project-overview)
3. [System Architecture](#system-architecture)
4. [Prerequisites](#prerequisites)
5. [Installation](#installation)
6. [How to Run](#how-to-run)
7. [Usage Guide](#usage-guide)
8. [Test Cases](#test-cases)
9. [Project Structure](#project-structure)
10. [Technical Details](#technical-details)

---

## 🎯 Problem Introduction

### What is CVRP?

The **Capacitated Vehicle Routing Problem (CVRP)** is a classic optimization problem in logistics and transportation. It involves:

- **A depot** where vehicles start and return
- **Multiple customers** that need deliveries, each with a specific demand
- **A fleet of vehicles** with limited capacity and maximum travel distance
- **Goal**: Find optimal routes that:
  - Serve as many customers as possible
  - Minimize total travel distance
  - Respect vehicle capacity constraints
  - Respect maximum distance constraints

### Real-World Applications

CVRP is used in:

- Package delivery services (Amazon, FedEx, UPS)
- Food delivery (DoorDash, Uber Eats)
- Waste collection routes
- Field service scheduling
- Supply chain logistics

### Problem Complexity

CVRP is an **NP-hard problem**, meaning:

- Finding the optimal solution becomes exponentially harder as the number of customers increases
- Exact solutions are only feasible for small problems
- Heuristic and metaheuristic approaches are needed for real-world scale

---

## 🏗️ Project Overview

This project implements a **distributed multi-agent system** to solve CVRP problems using:

### Key Technologies

- **JADE Framework**: Multi-agent platform for agent communication and coordination
- **Google OR-Tools**: State-of-the-art optimization solver for CVRP
- **Python Flask**: Backend API server and web frontend
- **Java**: Agent implementation and problem solving
- **FIPA Protocols**: Standard agent communication protocols

### System Features

✅ **Multi-Agent Architecture**: Master Routing Agent coordinates Delivery Agents
✅ **Real-Time Visualization**: Interactive web interface with route visualization
✅ **Constraint Handling**: Capacity and distance constraints
✅ **Unserved Customer Management**: Automatic retry for unserved customers
✅ **Request Queue Processing**: Handles multiple concurrent requests
✅ **Agent Communication Logging**: Detailed FIPA message logs
✅ **Adaptive Polling**: Efficient backend API polling
✅ **Route Queuing**: Vehicles can queue multiple route assignments

---

## 🏛️ System Architecture

The system consists of three main components:

```
┌─────────────────────────────────────────────────────────────┐
│                    WEB FRONTEND (Flask)                      │
│  - Vehicle Management                                        │
│  - Customer Definition                                       │
│  - Route Visualization                                       │
│  - Agent Status & Logs                                       │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP API
┌──────────────────────▼──────────────────────────────────────┐
│              BACKEND SERVER (Python Flask)                   │
│  - Request Queue Management                                  │
│  - Solution Storage                                          │
│  - Agent Status Tracking                                    │
│  - Vehicle Position Tracking                                 │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP API
┌──────────────────────▼──────────────────────────────────────┐
│            JAVA BACKEND (JADE Agents)                       │
│  ┌──────────────────┐         ┌──────────────────┐        │
│  │   MRA (Master)    │◄───────►│  DA1, DA2, ...   │        │
│  │  - Solves CVRP    │  FIPA   │  - Execute Routes│        │
│  │  - Assigns Routes │         │  - Return to Depot│        │
│  └──────────────────┘         └──────────────────┘        │
│         │                            │                       │
│         └────────── OR-Tools ────────┘                       │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

#### 1. **Frontend (Port 5000)**

- User interface for vehicle and customer management
- Real-time route visualization on interactive map
- Agent status monitoring
- Log viewing interface

#### 2. **Backend Server (Port 8000)**

- Receives customer requests from frontend
- Stores solutions from agents
- Tracks agent status and vehicle positions
- Provides API endpoints for frontend

#### 3. **Java Backend (JADE Platform)**

- **Master Routing Agent (MRA)**:
  - Polls backend for requests
  - Queries delivery agents for vehicle information
  - Solves CVRP using OR-Tools
  - Assigns routes to delivery agents
- **Delivery Agents (DAs)**:
  - Respond to MRA queries
  - Accept/reject route assignments
  - Execute routes and return to depot
  - Queue multiple route assignments

---

## 📦 Prerequisites

Before running the system, ensure you have:

### Required Software

1. **Java JDK 8 or higher**

   ```bash
   java -version  # Should show version 8
   ```
2. **Maven 3.6+** (for Java dependency management)

   ```bash
   mvn -version  # Should show version 3.6 or higher
   ```
3. **Python 3.7+** (for backend and frontend)

   ```bash
   python --version  # Should show version 3.7 or higher
   ```
4. **Google OR-Tools Native Libraries**

   - Automatically downloaded by Maven
   - Platform-specific binaries (Windows/Linux/Mac)

### Required Ports

Ensure these ports are available:

- **Port 5000**: Frontend web server
- **Port 8000**: Backend API server
- **Port 1099**: JADE platform (default)

---

## 🚀 Installation

### Step 1: Clone the Repository

```bash
git clone <repository-url>
cd Delivery_Vehicle_Routing_Problem
```

### Step 2: Install Java Dependencies

```bash
# Maven will download all dependencies automatically
mvn clean install
```

This will download:

- JADE 4.6.0
- Google OR-Tools
- Gson (JSON library)
- Other required dependencies

### Step 3: Install Python Dependencies

```bash
# Install backend dependencies
cd backend
pip install -r requirements.txt  # If requirements.txt exists, or install manually:
pip install flask flask-cors requests

# Install frontend dependencies
cd ../frontend
pip install -r requirements.txt  # If requirements.txt exists, or install manually:
pip install flask flask-cors requests
```

---

## 🎮 How to Run

The system requires **three components** to be running simultaneously. Follow these steps in order:

### Step 1: Start Backend Server

Open **Terminal 1**:

```bash
cd backend
python backend_server.py
```

**Expected Output:**

```
Starting CVRP Backend Server on localhost:8000
Available endpoints:
  GET  /api/solve-cvrp?action=poll     - Poll for pending requests
  POST /api/solve-cvrp?action=response - Submit solution
  ...
 * Running on http://localhost:8000
```

✅ **Keep this terminal open** - the backend server must stay running.

---

### Step 2: Start Java Backend (JADE Agents)

Open **Terminal 2**:

```bash
# From project root
mvn exec:java -Dexec.mainClass="project.Main"
```

**Expected Output:**

```
===============================================
  CVRP MULTI-AGENT SYSTEM - BACKEND MODE
===============================================

Backend URL: http://localhost:8000
Initializing agents...

✓ System initialized
  Waiting for vehicle confirmation from frontend...
  MRA and DAs will be created after vehicles are confirmed
```

✅ **Keep this terminal open** - the Java backend must stay running.

**Note**: Agents are created only after vehicles are confirmed from the frontend.

---

### Step 3: Start Frontend Server

Open **Terminal 3**:

```bash
cd frontend
python app.py
```

**Expected Output:**

```
 * Running on http://localhost:5000
 * Debug mode: on
```

✅ **Keep this terminal open** - the frontend server must stay running.

---

### Step 4: Open Web Interface

Open your web browser and navigate to:

```
http://localhost:5000
```

You should see the CVRP System dashboard.

---

## 📖 Usage Guide

### Initial Setup (First Time)

#### 1. Configure Vehicles

1. On the main page, click **"Manage Vehicles"** (top right)

   - Or navigate to `http://localhost:5000/vehicles`
2. Add vehicles:

   - **Name**: `DA1`
   - **Capacity**: `50` (items)
   - **Max Distance**: `1000.0` (units)
   - Click **"Add Vehicle"**
3. Repeat for additional vehicles (e.g., DA2, DA3, DA4)
4. Click **"Save & Continue"**

**What happens:**

- Vehicles are saved in the frontend session
- Vehicle configuration is sent to backend
- Backend triggers agent creation in Java backend
- MRA and DAs are created automatically

#### 2. Verify Agents Created

1. Go back to main page (`http://localhost:5000`)
2. Click **"Agents"** tab
3. You should see:
   - **MRA**: Status "Active"
   - **DA1, DA2, DA3, DA4**: Status "Active"

---

### Running a Test Case

#### Option 1: Load Pre-configured Test Case

1. On main page, go to **"Customers"** tab
2. Scroll to **"Scenarios & Data"** section
3. Select a test case from dropdown:
   - **Test Case 1 – Basic CVRP** (15 customers, all served)
   - **Test Case 2 – Demand Exceeds Capacity** (15 customers, some unserved)
   - **Test Case 3 – Max Distance Constraint** (15 customers, some too far)
   - **Note**: Test Case 4 requires manual setup (see Test Cases section below)
4. Click **"Load"** button
5. Customers will be automatically added
6. Click **"Submit Request"**

#### Option 2: Add Customers Manually

1. On main page, go to **"Customers"** tab
2. Fill in customer details:
   - **Customer ID**: `1`
   - **Demand**: `10`
   - **X Coordinate**: `10.0`
   - **Y Coordinate**: `10.0`
3. Click **"Add Customer"**
4. Repeat for more customers
5. Click **"Submit Request"**

---

### Viewing Results

#### 1. View Routes on Map

- The map automatically updates when solution is ready (5-15 seconds)
- **Blue marker**: Depot (center)
- **Green markers**: Customers
- **Colored lines**: Routes (different color per vehicle)
- **Orange markers**: Unserved customers (if any)
- **Triangles**: Vehicle positions (if movement tracking active)

#### 2. View Agent Status

- Click **"Agents"** tab
- See MRA and DA status
- View agent details (capacity, max distance, etc.)

#### 3. View Logs

- Click **"Logs"** tab
- Select an agent from dropdown (e.g., "MRA" or "DA1")
- View detailed communication logs
- See FIPA messages, route assignments, etc.

---

## 🧪 Test Cases

The system includes four test cases designed to demonstrate different scenarios:

### Test Case 1: Basic Communication

- **File**: `test_cases/case_1_basic_communication.json`
- **Customers**: 15 customers arranged in clusters
- **Vehicles**: 4 vehicles, capacity 50 each, max distance 1000
- **Purpose**: All customers can be served, demonstrates basic agent communication
- **Expected**: All 15 customers served, rounded routes created

### Test Case 2: Demand Exceeds Capacity

- **File**: `test_cases/case_2_demand_exceeds_capacity.json`
- **Customers**: 15 customers, total demand 300 items
- **Vehicles**: 4 vehicles, capacity 30 each (total 120)
- **Purpose**: Tests handling when demand > capacity
- **Expected**: Some customers served in first round, unserved customers processed in second round

### Test Case 3: Maximum Distance Constraint

- **File**: `test_cases/case_3_maximum_distance.json`
- **Customers**: 15 customers, some very far from depot
- **Vehicles**: 4 vehicles, max distance 50 (tight constraint)
- **Purpose**: Tests distance constraint handling
- **Expected**: Nearby customers served, far customers remain unserved

### Test Case 4: Multiple Requests & Unserved Customer Logic

- **Type**: Manual testing scenario (no pre-configured JSON file)
- **Purpose**: Tests unserved customer accumulation across multiple requests and automatic assignment to new vehicles
- **Key Features**:
  - Unserved customers accumulate across multiple requests
  - Unserved customers are processed when threshold (6) is reached OR when request queue is empty
  - New vehicles with larger constraints can automatically serve previously unserved customers
  - Demonstrates adaptive unserved customer handling

**Manual Testing Steps:**

#### Step 1: Initial Vehicle Setup

1. Go to **"Manage Vehicles"** page
2. Add **one vehicle** with restrictive constraints:
   - **Name**: `DA1`
   - **Capacity**: `50` items
   - **Max Distance**: `50.0` units (small distance limit)
3. Click **"Save & Continue"**
4. Verify DA1 is created and active

#### Step 2: Submit First Request (Far Customer)

1. Go to **"Customers"** tab
2. Add a customer that is **far from depot** (will exceed max distance):
   - **Customer ID**: `1`
   - **Demand**: `10`
   - **X Coordinate**: `100.0` (far from depot at 0,0)
   - **Y Coordinate**: `100.0`
3. Click **"Add Customer"**
4. Click **"Submit Request"**
5. **Expected Result**:
   - Customer 1 cannot be served (distance > 50.0)
   - Customer 1 is added to unserved customers list
   - Solution shows Customer 1 as unserved

#### Step 3: Submit Second Request (Another Far Customer)

1. While waiting for the first solution, add **another far customer (remember to remove the old customer from the list)**:
   - **Customer ID**: `2`
   - **Demand**: `15`
   - **X Coordinate**: `120.0`
   - **Y Coordinate**: `120.0`
2. Click **"Add Customer"**
3. Click **"Submit Request"**
4. **Expected Result**:
   - Customer 2 also cannot be served
   - Customer 2 is added to accumulated unserved customers
   - Both Customer 1 and Customer 2 are now in the unserved buffer

#### Step 4: Add More Far Customers (Reach Threshold)

1. Add **4 more far customers** to reach the threshold of 6:
   - Customer 3: demand=12, x=110.0, y=110.0
   - Customer 4: demand=14, x=130.0, y=130.0
   - Customer 5: demand=11, x=105.0, y=105.0
   - Customer 6: demand=13, x=125.0, y=125.0
2. Submit each request separately
3. **Expected Result**:
   - As unserved customers accumulate, they are stored in the buffer
   - When buffer reaches 6 customers, MRA processes them automatically
   - However, they still cannot be served by DA1 (distance constraint)

#### Step 5: Add New Vehicle with Larger Max Distance

1. Go to **"Manage Vehicles"** page
2. Add a **new vehicle** with larger max distance:
   - **Name**: `DA2`
   - **Capacity**: `50` items
   - **Max Distance**: `200.0` units (large enough to reach far customers)
3. Click **"Add Vehicle"**
4. Click **"Save & Continue"**
5. **Expected Result**:
   - DA2 is created and registered with MRA
   - MRA queries DA2 for vehicle information
   - System recognizes DA2 can serve the far customers

#### Step 6: Observe Automatic Unserved Customer Processing

1. Wait for the system to process unserved customers
2. The MRA will:
   - Detect accumulated unserved customers (6 customers)
   - Query all vehicles including DA2
   - Solve CVRP problem with DA2's larger max distance
   - Assign routes to DA2 to serve the previously unserved customers
3. **Expected Result**:
   - DA2 receives route assignments for the far customers
   - Previously unserved customers (1-6) are now served by DA2
   - Solution shows all customers served
   - Map displays routes from DA2 to far customers

#### Step 7: Verify in Logs

1. Go to **"Logs"** tab
2. Select **"MRA"** from dropdown
3. Look for messages showing:
   - Unserved customer accumulation
   - Processing of accumulated unrouted nodes
   - Route assignments to DA2
4. Select **"DA2"** from dropdown
5. Verify DA2 received route assignments and executed them

**What This Test Demonstrates:**

- ✅ Unserved customers accumulate across multiple requests
- ✅ Unserved buffer is processed when threshold (6) is reached
- ✅ New vehicles can automatically serve previously unserved customers
- ✅ System adapts to new vehicle capabilities
- ✅ Multiple request handling with unserved customer management

See `documentation/TEST_CASES_DOCUMENTATION.md` for detailed test case documentation.

---

## 📁 Project Structure

```
Delivery_Vehicle_Routing_Problem/
├── backend/
│   └── backend_server.py          # Python Flask backend API server
├── frontend/
│   ├── app.py                      # Python Flask frontend server
│   ├── templates/
│   │   ├── main.html              # Main dashboard
│   │   └── vehicles.html          # Vehicle setup page
│   └── requirements.txt           # Python dependencies
├── src/main/java/project/
│   ├── Agent/
│   │   ├── MasterRoutingAgent.java    # MRA - coordinates and solves
│   │   ├── DeliveryAgent.java         # DA - executes routes
│   │   └── DepotProblemAssembler.java # Problem assembly helper
│   ├── General/
│   │   ├── CustomerInfo.java          # Customer data structure
│   │   ├── RouteInfo.java             # Route data structure
│   │   ├── SolutionResult.java       # Solution data structure
│   │   └── VehicleInfo.java           # Vehicle data structure
│   ├── Solver/
│   │   ├── VRPSolver.java             # Solver interface
│   │   └── ORToolsSolver.java         # OR-Tools implementation
│   ├── Utils/
│   │   ├── AgentLogger.java           # Message logging
│   │   ├── BackendClient.java         # Backend API client
│   │   └── JsonResultLogger.java      # JSON result logging
│   └── Main.java                      # Entry point
├── test_cases/
│   ├── case_1_basic_communication.json
│   ├── case_2_demand_exceeds_capacity.json
│   └── case_3_maximum_distance.json
├── documentation/
│   ├── TEST_CASES_DOCUMENTATION.md    # Detailed test case docs
│   └── API_DOCUMENTATION.md           # API documentation
├── pom.xml                            # Maven configuration
└── README.md                          # This file
```

---

## 🔧 Technical Details

### Agent Communication

All agent communication uses **FIPA-Request protocol**:

1. **Vehicle Info Query**:

   - MRA → DA: `QUERY_VEHICLE_INFO`
   - DA → MRA: `CAPACITY:50|MAX_DISTANCE:1000.0|NAME:DA1|X:0.0|Y:0.0`
2. **Route Assignment**:

   - MRA → DA: `ROUTE_ASSIGNMENT:ROUTE:1|VEHICLE:DA1|CUSTOMERS:1,2,3|...`
   - DA → MRA: `ROUTE_ACCEPTED:1|VEHICLE:DA1|STATUS:ACCEPTED` or `ROUTE_REJECTED:...`
3. **Arrival Notification**:

   - DA → MRA: `DA_ARRIVED_AT_DEPOT`

### Solver Configuration

The OR-Tools solver is configured to:

- **Primary Objective**: Maximize packages delivered
  - Uses disjunction with penalty (1,000,000) for unserved customers
- **Secondary Objective**: Minimize total distance
- **Constraints**: Capacity and maximum distance per vehicle

### Request Processing Flow

1. Frontend submits customer request → Backend API
2. Backend stores request in queue
3. MRA polls backend every 2 seconds (adaptive: 2s when active, 10s when idle)
4. MRA receives request and queries all DAs
5. MRA solves CVRP problem using OR-Tools
6. MRA assigns routes to DAs
7. DAs execute routes and return to depot
8. MRA submits solution to backend
9. Frontend polls for solution and displays on map

### Unserved Customer Handling

- Unserved customers are accumulated in a buffer
- Processed when:
  - Buffer reaches 6 customers, OR
  - Request queue is empty
- Creates synthetic requests for unserved customers
- Attempts to serve them in subsequent rounds

---

## 🐛 Troubleshooting

### Issue: Agents not appearing

**Solution:**

- Make sure Java backend (Main.java) is running
- Make sure vehicles are configured in frontend
- Check backend server is running on port 8000
- Check Java backend console for errors

### Issue: Solution not appearing

**Solution:**

- Wait 10-15 seconds (solving takes time)
- Check backend server is running
- Check Java backend is running
- Check browser console for errors
- Verify request was submitted successfully

### Issue: Port already in use

**Solution:**

- Frontend (5000): Change port in `frontend/app.py`
- Backend (8000): Change port in `backend/backend_server.py` and `BackendClient.java`
- JADE (1099): Change in `Main.java` Profile parameters

### Issue: Dependencies not found

**Solution:**

```bash
# Java dependencies
mvn clean install

# Python dependencies
pip install flask flask-cors requests
```

---

## 📚 Additional Documentation

- **Test Cases**: See `documentation/TEST_CASES_DOCUMENTATION.md`
- **API Documentation**: See `documentation/API_DOCUMENTATION.md`
- **Frontend Guide**: See `frontend/README.md`

---

## 🎓 Learning Outcomes

This project demonstrates:

- Multi-agent system design and implementation
- FIPA communication protocols
- Service discovery using JADE Directory Facilitator
- Constraint optimization problem solving
- Web-based visualization of agent systems
- Real-time system monitoring and logging

---

## 📝 License

This project is for educational purposes.

---

## 👥 Credits

Built using:

- **JADE** - Java Agent Development Framework
- **Google OR-Tools** - Optimization solver
- **Flask** - Python web framework
- **Plotly** - Interactive visualization

---

**Happy Routing! 🚚📦**
