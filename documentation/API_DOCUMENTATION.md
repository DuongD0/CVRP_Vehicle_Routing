# API Documentation

This document describes all available APIs in the CVRP Multi-Agent System.

## Overview

The system consists of two main components:
- **Backend Server** (Port 8000): Handles CVRP requests, agent management, and log access
- **Frontend Server** (Port 5000): Provides web interface and proxies requests to backend

---

## Backend API (Port 8000)

Base URL: `http://localhost:8000`

### CVRP Request Management

#### 1. Poll for Pending Requests
**Endpoint:** `GET /api/solve-cvrp?action=poll`

**Description:** Used by MRA to poll for pending CVRP requests from the queue.

**Query Parameters:**
- `action` (required): Must be `"poll"`

**Response:**
- **200 OK** - Request available:
  ```json
  {
    "request_id": "uuid-string",
    "data": {
      "customers": [
        {
          "id": "1",
          "demand": 10,
          "x": 10.0,
          "y": 10.0
        }
      ]
    }
  }
  ```
- **204 No Content** - No pending requests

**Example:**
```bash
curl "http://localhost:8000/api/solve-cvrp?action=poll"
```

---

#### 2. Submit Solution
**Endpoint:** `POST /api/solve-cvrp?action=response`

**Description:** Used by MRA to submit a solution for a processed request.

**Query Parameters:**
- `action` (required): Must be `"response"`

**Request Body:**
```json
{
  "request_id": "uuid-string",
  "routes": [
    {
      "vehicleId": 1,
      "vehicleName": "DA1",
      "customers": [
        {
          "id": 1,
          "name": "1",
          "x": 10.0,
          "y": 10.0,
          "demand": 10
        }
      ],
      "totalDemand": 10,
      "totalDistance": 14.14
    }
  ],
  "unservedCustomers": [],
  "itemsDelivered": 10,
  "itemsTotal": 10,
  "totalDistance": 14.14
}
```

**Response:**
- **200 OK**:
  ```json
  {
    "status": "success",
    "message": "Solution received"
  }
  ```
- **400 Bad Request**: Invalid request_id or missing data

**Example:**
```bash
curl -X POST "http://localhost:8000/api/solve-cvrp?action=response" \
  -H "Content-Type: application/json" \
  -d '{"request_id": "uuid", "routes": [...]}'
```

---

#### 3. Submit New CVRP Request
**Endpoint:** `POST /api/solve-cvrp`

**Description:** Used by frontend to submit a new customer-only request. Vehicles and depot are already defined.

**Request Body:**
```json
{
  "customers": [
    {
      "id": "1",
      "demand": 10,
      "x": 10.0,
      "y": 10.0
    },
    {
      "id": "2",
      "demand": 15,
      "x": 20.0,
      "y": 20.0
    }
  ]
}
```

**Response:**
- **202 Accepted**:
  ```json
  {
    "request_id": "uuid-string",
    "status": "submitted",
    "message": "Request submitted successfully. Use polling to check for solution."
  }
  ```
- **400 Bad Request**: Invalid customer data

**Example:**
```bash
curl -X POST "http://localhost:8000/api/solve-cvrp" \
  -H "Content-Type: application/json" \
  -d '{"customers": [{"id": "1", "demand": 10, "x": 10.0, "y": 10.0}]}'
```

---

#### 4. Get Solution Status
**Endpoint:** `GET /api/solution/<request_id>`

**Description:** Check the status of a request and retrieve solution if available.

**Path Parameters:**
- `request_id` (required): UUID of the request

**Response:**
- **200 OK** - Solution available:
  ```json
  {
    "request_id": "uuid-string",
    "status": "completed",
    "solution": {
      "routes": [...],
      "unservedCustomers": [...],
      "itemsDelivered": 10,
      "itemsTotal": 10,
      "totalDistance": 14.14
    }
  }
  ```
- **200 OK** - Still processing:
  ```json
  {
    "request_id": "uuid-string",
    "status": "processing"
  }
  ```
- **404 Not Found**: Request not found

**Example:**
```bash
curl "http://localhost:8000/api/solution/abc-123-def-456"
```

---

### Vehicle Management

#### 5. Confirm Vehicles
**Endpoint:** `POST /api/vehicles/confirm`

**Description:** Confirms vehicle list and triggers agent creation. Called by frontend when vehicles are defined.

**Request Body:**
```json
{
  "vehicles": [
    {
      "name": "DA1",
      "capacity": 50,
      "maxDistance": 1000.0
    },
    {
      "name": "DA2",
      "capacity": 50,
      "maxDistance": 1000.0
    }
  ],
  "depot": {
    "name": "Depot",
    "x": 0.0,
    "y": 0.0
  }
}
```

**Response:**
- **200 OK**:
  ```json
  {
    "success": true,
    "message": "2 vehicles confirmed. Agents will be created.",
    "vehicles": ["DA1", "DA2"]
  }
  ```
- **400 Bad Request**: Invalid vehicle data

**Example:**
```bash
curl -X POST "http://localhost:8000/api/vehicles/confirm" \
  -H "Content-Type: application/json" \
  -d '{"vehicles": [...], "depot": {...}}'
```

---

#### 6. Poll Vehicle Configuration
**Endpoint:** `GET /api/vehicles/poll-config`

**Description:** Used by Main.java to poll for vehicle configuration and create agents.

**Response:**
- **200 OK** - Config available:
  ```json
  {
    "vehicles": [
      {
        "name": "DA1",
        "capacity": 50,
        "maxDistance": 1000.0
      }
    ],
    "depot": {
      "name": "Depot",
      "x": 0.0,
      "y": 0.0
    },
    "timestamp": 1234567890.123
  }
  ```
- **204 No Content** - No config available

**Note:** This endpoint consumes the config (one-time use). After calling, the config is cleared.

**Example:**
```bash
curl "http://localhost:8000/api/vehicles/poll-config"
```

---

### Agent Status Management

#### 7. Update Agent Status
**Endpoint:** `POST /api/agents/status`

**Description:** Used by agents to report their status (active, inactive, terminated).

**Request Body:**
```json
{
  "agent_name": "MRA",
  "agent_type": "mra",
  "status": "active",
  "info": {
    "depot_x": 0.0,
    "depot_y": 0.0
  }
}
```

**For DAs:**
```json
{
  "agent_name": "DA-DA1",
  "agent_type": "da",
  "status": "active",
  "info": {
    "capacity": 50,
    "speed": 1.0,
    "maxDistance": 1000.0
  }
}
```

**Response:**
- **200 OK**:
  ```json
  {
    "success": true,
    "agent": "MRA"
  }
  ```
- **400 Bad Request**: Missing required fields

**Example:**
```bash
curl -X POST "http://localhost:8000/api/agents/status" \
  -H "Content-Type: application/json" \
  -d '{"agent_name": "MRA", "agent_type": "mra", "status": "active", "info": {}}'
```

---

#### 8. Get Agent Status
**Endpoint:** `GET /api/agents/status`

**Description:** Get current status of all registered agents.

**Response:**
- **200 OK**:
  ```json
  {
    "agents": [
      {
        "name": "MRA",
        "type": "mra",
        "status": "active",
        "timestamp": 1234567890.123,
        "info": {
          "depot_x": 0.0,
          "depot_y": 0.0
        }
      },
      {
        "name": "DA-DA1",
        "type": "da",
        "status": "active",
        "timestamp": 1234567890.123,
        "info": {
          "capacity": 50,
          "speed": 1.0,
          "maxDistance": 1000.0
        }
      }
    ],
    "mra_count": 1,
    "da_count": 4,
    "total_active": 5
  }
  ```

**Example:**
```bash
curl "http://localhost:8000/api/agents/status"
```

---

### Vehicle Movement Tracking

#### 9. Update Vehicle Position
**Endpoint:** `POST /api/movement/update`

**Description:** Update position of a vehicle (called by agents or parsed from logs).

**Request Body:**
```json
{
  "vehicle_name": "DA1",
  "x": 5.0,
  "y": 5.0,
  "status": "moving",
  "route_id": "route-1",
  "target_x": 10.0,
  "target_y": 10.0
}
```

**Status Values:**
- `idle`: Vehicle is at depot, not moving
- `moving`: Vehicle is moving towards a target
- `at_customer`: Vehicle has arrived at a customer
- `at_depot`: Vehicle has returned to depot

**Response:**
- **200 OK**:
  ```json
  {
    "success": true,
    "vehicle": "DA1"
  }
  ```
- **400 Bad Request**: Missing vehicle_name

**Example:**
```bash
curl -X POST "http://localhost:8000/api/movement/update" \
  -H "Content-Type: application/json" \
  -d '{"vehicle_name": "DA1", "x": 5.0, "y": 5.0, "status": "moving"}'
```

---

#### 10. Get All Vehicle Positions
**Endpoint:** `GET /api/movement/all`

**Description:** Get current positions of all vehicles.

**Response:**
- **200 OK**:
  ```json
  {
    "vehicles": {
      "DA1": {
        "x": 5.0,
        "y": 5.0,
        "status": "moving",
        "route_id": "route-1",
        "target_x": 10.0,
        "target_y": 10.0,
        "timestamp": 1234567890.123
      },
      "DA2": {
        "x": 0.0,
        "y": 0.0,
        "status": "idle",
        "timestamp": 1234567890.123
      }
    },
    "timestamp": 1234567890.123
  }
  ```

**Example:**
```bash
curl "http://localhost:8000/api/movement/all"
```

---

#### 11. Get Vehicle Position
**Endpoint:** `GET /api/movement/<vehicle_name>`

**Description:** Get position for a specific vehicle.

**Path Parameters:**
- `vehicle_name` (required): Name of the vehicle (e.g., "DA1")

**Response:**
- **200 OK**:
  ```json
  {
    "vehicle": "DA1",
    "position": {
      "x": 5.0,
      "y": 5.0,
      "status": "moving",
      "route_id": "route-1",
      "target_x": 10.0,
      "target_y": 10.0,
      "timestamp": 1234567890.123
    }
  }
  ```
- **404 Not Found**: Vehicle not found

**Example:**
```bash
curl "http://localhost:8000/api/movement/DA1"
```

---

### Log Management

#### 12. List Available Logs
**Endpoint:** `GET /log`

**Description:** List all available agent log files.

**Response:**
- **200 OK**:
  ```json
  {
    "agents": [
      {
        "name": "mra",
        "log_file": "MRA_conversations.log",
        "endpoint": "/log/mra"
      },
      {
        "name": "DA1",
        "log_file": "DA-DA1_conversations.log",
        "endpoint": "/log/DA1"
      }
    ]
  }
  ```

**Example:**
```bash
curl "http://localhost:8000/log"
```

---

#### 13. Get Agent Log
**Endpoint:** `GET /log/<agent_name>`

**Description:** Get log content for a specific agent.

**Path Parameters:**
- `agent_name` (required): Name of the agent (e.g., "mra", "DA1")

**Response:**
- **200 OK**:
  ```json
  {
    "agent_name": "mra",
    "log_file": "MRA_conversations.log",
    "content": "[2024-01-15 10:30:00.125] *** EVENT: Agent started\n...",
    "size": 12345
  }
  ```
- **404 Not Found**: Log file not found

**Note:** Log files are stored directly in `logs/` folder with format `{agentName}_conversations.log`

**Example:**
```bash
curl "http://localhost:8000/log/mra"
curl "http://localhost:8000/log/DA1"
```

---

#### 14. Get MRA Log (Alias)
**Endpoint:** `GET /log/mra`

**Description:** Alias for getting MRA log. Always available.

**Response:** Same as `/log/<agent_name>` with `agent_name="MRA"`

**Example:**
```bash
curl "http://localhost:8000/log/mra"
```

---

#### 15. Get Latest Logs Info
**Endpoint:** `GET /api/latest-logs`

**Description:** Get list of available agents for log viewing (frontend compatibility).

**Response:**
- **200 OK**:
  ```json
  {
    "agents": ["mra", "DA1", "DA2", "DA3", "DA4"],
    "folder": null
  }
  ```

**Note:** `folder` is always `null` as logs are now stored directly in `logs/` folder (no subfolders).

**Example:**
```bash
curl "http://localhost:8000/api/latest-logs"
```

---

## Frontend API (Port 5000)

Base URL: `http://localhost:5000`

### Page Routes

#### 1. Home Page
**Endpoint:** `GET /`

**Description:** Main page. Redirects to vehicle setup if no vehicles defined, otherwise shows main dashboard.

**Response:** HTML page (main.html or vehicles.html)

---

#### 2. Vehicle Management Page
**Endpoint:** `GET /vehicles`

**Description:** Vehicle management page where users define vehicles.

**Response:** HTML page (vehicles.html)

---

### Vehicle Management

#### 3. Get Vehicles
**Endpoint:** `GET /api/vehicles`

**Description:** Get all defined vehicles from session.

**Response:**
- **200 OK**:
  ```json
  {
    "vehicles": [
      {
        "name": "DA1",
        "capacity": 50,
        "maxDistance": 1000.0
      },
      {
        "name": "DA2",
        "capacity": 50,
        "maxDistance": 1000.0
      }
    ]
  }
  ```

**Example:**
```bash
curl "http://localhost:5000/api/vehicles"
```

---

#### 4. Set Vehicles
**Endpoint:** `POST /api/vehicles`

**Description:** Define/update vehicles. Also sends confirmation to backend to create agents.

**Request Body:**
```json
{
  "vehicles": [
    {
      "name": "DA1",
      "capacity": 50,
      "maxDistance": 1000.0
    },
    {
      "name": "DA2",
      "capacity": 50,
      "maxDistance": 1000.0
    }
  ]
}
```

**Response:**
- **200 OK**:
  ```json
  {
    "success": true,
    "message": "2 vehicles defined. Agents are being created.",
    "vehicles": ["DA1", "DA2"]
  }
  ```
- **400 Bad Request**: Invalid vehicle data

**Example:**
```bash
curl -X POST "http://localhost:5000/api/vehicles" \
  -H "Content-Type: application/json" \
  -d '{"vehicles": [...]}'
```

---

### Request Management

#### 5. Submit Request
**Endpoint:** `POST /api/submit-request`

**Description:** Submit a customer-only request to backend. Vehicles must be defined first.

**Request Body:**
```json
{
  "customers": [
    {
      "id": "1",
      "demand": 10,
      "x": 10.0,
      "y": 10.0
    },
    {
      "id": "2",
      "demand": 15,
      "x": 20.0,
      "y": 20.0
    }
  ]
}
```

**Response:**
- **200 OK**:
  ```json
  {
    "success": true,
    "request_id": "uuid-string",
    "message": "Request submitted successfully"
  }
  ```
- **400 Bad Request**: No vehicles defined or invalid customer data
- **503 Service Unavailable**: Cannot connect to backend

**Example:**
```bash
curl -X POST "http://localhost:5000/api/submit-request" \
  -H "Content-Type: application/json" \
  -d '{"customers": [...]}'
```

---

#### 6. Get Solution (Proxy)
**Endpoint:** `GET /api/solution/<request_id>`

**Description:** Proxy to backend solution endpoint.

**Path Parameters:**
- `request_id` (required): UUID of the request

**Response:** Same as backend `/api/solution/<request_id>`

**Example:**
```bash
curl "http://localhost:5000/api/solution/abc-123-def-456"
```

---

### Agent Status (Proxy)

#### 7. Get Agent Status (Proxy)
**Endpoint:** `GET /api/agents/status`

**Description:** Proxy to backend agent status endpoint.

**Response:** Same as backend `/api/agents/status`

**Example:**
```bash
curl "http://localhost:5000/api/agents/status"
```

---

### Log Management (Proxy)

#### 8. List Logs (Proxy)
**Endpoint:** `GET /api/logs`

**Description:** Proxy to backend log listing endpoint.

**Response:** Same as backend `/log`

**Example:**
```bash
curl "http://localhost:5000/api/logs"
```

---

#### 9. Get Agent Log (Proxy)
**Endpoint:** `GET /api/logs/<agent_name>`

**Description:** Proxy to backend agent log endpoint.

**Path Parameters:**
- `agent_name` (required): Name of the agent

**Response:** Same as backend `/log/<agent_name>`

**Example:**
```bash
curl "http://localhost:5000/api/logs/mra"
curl "http://localhost:5000/api/logs/DA1"
```

---

### Vehicle Movement (Proxy)

#### 10. Get All Vehicle Positions (Proxy)
**Endpoint:** `GET /api/movement/all`

**Description:** Proxy to backend vehicle positions endpoint.

**Response:** Same as backend `/api/movement/all`

**Example:**
```bash
curl "http://localhost:5000/api/movement/all"
```

---

## Data Formats

### Customer Object
```json
{
  "id": "1",           // String or number
  "demand": 10,        // Integer - number of items
  "x": 10.0,          // Float - X coordinate
  "y": 10.0           // Float - Y coordinate
}
```

### Vehicle Object
```json
{
  "name": "DA1",              // String - Vehicle name
  "capacity": 50,              // Integer - Maximum items
  "maxDistance": 1000.0       // Float - Maximum travel distance
}
```

### Route Object
```json
{
  "vehicleId": 1,             // Integer - Vehicle index
  "vehicleName": "DA1",       // String - Vehicle name
  "customers": [              // Array of Customer objects
    {
      "id": 1,
      "name": "1",
      "x": 10.0,
      "y": 10.0,
      "demand": 10
    }
  ],
  "totalDemand": 10,           // Integer - Total demand in route
  "totalDistance": 14.14      // Float - Total route distance
}
```

### Solution Object
```json
{
  "routes": [                  // Array of Route objects
    {
      "vehicleId": 1,
      "vehicleName": "DA1",
      "customers": [...],
      "totalDemand": 10,
      "totalDistance": 14.14
    }
  ],
  "unservedCustomers": [       // Array of Customer objects
    {
      "id": 5,
      "name": "5",
      "x": 100.0,
      "y": 100.0,
      "demand": 25
    }
  ],
  "itemsDelivered": 10,        // Integer - Items successfully delivered
  "itemsTotal": 10,            // Integer - Total items requested
  "totalDistance": 14.14       // Float - Total distance traveled
}
```

---

## Error Responses

All endpoints may return error responses in the following format:

```json
{
  "error": "Error message description"
}
```

**Common HTTP Status Codes:**
- **200 OK**: Request successful
- **202 Accepted**: Request accepted for processing
- **204 No Content**: Request successful but no content to return
- **400 Bad Request**: Invalid request data
- **404 Not Found**: Resource not found
- **500 Internal Server Error**: Server error
- **503 Service Unavailable**: Cannot connect to backend

---

## API Workflow

### Typical Request Flow

1. **Define Vehicles** (Frontend → Backend):
   - `POST /api/vehicles` (Frontend) → Stores in session
   - `POST /api/vehicles/confirm` (Backend) → Confirms vehicles
   - `GET /api/vehicles/poll-config` (Main.java) → Creates agents

2. **Submit Request** (Frontend → Backend):
   - `POST /api/submit-request` (Frontend) → Proxies to backend
   - `POST /api/solve-cvrp` (Backend) → Adds to request queue

3. **Process Request** (MRA → Backend):
   - `GET /api/solve-cvrp?action=poll` (MRA) → Gets pending request
   - MRA solves problem and assigns routes
   - `POST /api/solve-cvrp?action=response` (MRA) → Submits solution

4. **Check Solution** (Frontend → Backend):
   - `GET /api/solution/<request_id>` (Frontend) → Proxies to backend
   - Returns solution when available

5. **Track Movement** (Agents → Backend):
   - `POST /api/movement/update` (DA) → Updates vehicle position
   - `GET /api/movement/all` (Frontend) → Gets all positions

---

## Notes

1. **Log Files**: All log files are stored directly in `logs/` folder (no subfolders) with format `{agentName}_conversations.log`. Files are appended to, not overwritten.

2. **Vehicle Names**: Vehicle names can be with or without "DA-" prefix. The system handles both formats.

3. **Request ID**: All requests are assigned a UUID that is used to track status and retrieve solutions.

4. **Polling**: The system uses polling for request processing. MRA polls for requests, and frontend polls for solutions.

5. **Agent Creation**: Agents are created when vehicles are confirmed via `/api/vehicles/confirm`. Main.java polls for this configuration.

6. **CORS**: Backend has CORS enabled for frontend integration.

7. **Session Management**: Frontend uses Flask sessions to store vehicle definitions.

---

## Testing

### Using cURL

**Submit a request:**
```bash
curl -X POST "http://localhost:8000/api/solve-cvrp" \
  -H "Content-Type: application/json" \
  -d '{
    "customers": [
      {"id": "1", "demand": 10, "x": 10.0, "y": 10.0},
      {"id": "2", "demand": 15, "x": 20.0, "y": 20.0}
    ]
  }'
```

**Check solution:**
```bash
curl "http://localhost:8000/api/solution/<request_id>"
```

**Get agent status:**
```bash
curl "http://localhost:8000/api/agents/status"
```

**Get vehicle positions:**
```bash
curl "http://localhost:8000/api/movement/all"
```

---

## Version

- **Backend API Version**: 1.0
- **Frontend API Version**: 1.0
- **Last Updated**: 2024

