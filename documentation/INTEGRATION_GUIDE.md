# Backend Integration Guide

## Overview

The system now supports two modes:
1. **File Mode**: Process a single config file (original behavior)
2. **Backend Mode**: Continuously poll Python backend for requests from frontend

## Architecture

```
Frontend (React/TypeScript)
    ↓ HTTP POST /api/solve-cvrp
Python Flask Backend (backend_server.py)
    ↓ HTTP GET /api/solve-cvrp?action=poll
Java Main (Backend Mode)
    ↓ Creates JADE Agents
MasterRoutingAgent + DeliveryAgents
    ↓ Solves Problem
    ↓ HTTP POST /api/solve-cvrp?action=response
Python Flask Backend
    ↓ HTTP GET /api/solution/<request_id>
Frontend (Polling)
```

## Data Format

### Request Format (Frontend → Backend → Java)

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
      "demand": 15,
      "x": 10.0,
      "y": 10.0,
      "timeWindow": [0, 50]  // Optional
    }
  ]
}
```

### Response Format (Java → Backend → Frontend)

```json
{
  "request_id": "...",
  "timestamp": "...",
  "configName": "...",
  "solveTimeMs": 12345,
  "summary": {
    "totalItemsRequested": 100,
    "totalItemsDelivered": 55,
    "totalDistance": 157.0,
    "numberOfRoutes": 2,
    "deliveryRate": 0.55,
    "unservedCustomers": 2
  },
  "routes": [
    {
      "routeId": 1,
      "vehicleName": "DA1",
      "totalDemand": 28,
      "totalDistance": 77.0,
      "customers": [...]
    }
  ],
  "unservedCustomers": [
    {
      "id": 1,
      "name": "C1",
      "x": 10.0,
      "y": 10.0,
      "demand": 25
    }
  ]
}
```

## Running the System

### 1. Start Python Backend

```bash
cd backend
python backend_server.py
```

The backend will start on `http://localhost:8000`

### 2. Start Java Agent System

```bash
java -cp target/classes:... project.Main
```

**Note**: The Java system now only operates in backend API mode. It does not accept local file arguments.

The Java system will:
- Connect to backend at `http://localhost:8000`
- Poll every 2 seconds for new requests
- Process requests and submit solutions
- Run continuously until stopped (Ctrl+C)

### 3. Start Frontend

```bash
cd frontend
npm run dev
```

The frontend will send requests to the backend, which will be picked up by the Java system.

## API Endpoints

### Backend Endpoints

1. **POST /api/solve-cvrp** - Submit new CVRP request (from frontend)
   - Returns: `{request_id, status, message}`

2. **GET /api/solve-cvrp?action=poll** - Poll for pending requests (from Java)
   - Returns: `{request_id, data}` or 204 No Content

3. **POST /api/solve-cvrp?action=response** - Submit solution (from Java)
   - Body: Solution JSON with `request_id`
   - Returns: `{status, message}`

4. **GET /api/solution/<request_id>** - Check solution status (from frontend)
   - Returns: `{request_id, status, solution?}`

## Features Supported

✅ New config format with depot, vehicles, customers  
✅ Time windows (TWVRP) support  
✅ Unserved customers tracking  
✅ Empty routes handling (when problem can't be solved)  
✅ Continuous polling mode  
✅ File-based mode (backward compatible)

## Troubleshooting

### Java can't connect to backend
- Ensure Python backend is running on port 8000
- Check firewall settings
- Verify `BACKEND_URL` in `BackendClient.java`

### No requests being processed
- Check backend logs for incoming requests
- Verify frontend is sending requests to correct endpoint
- Check Java polling logs

### Solutions not appearing in frontend
- Check backend logs for solution submissions
- Verify `request_id` matches between request and solution
- Check frontend polling interval

## Notes

- Backend mode disables JADE GUI (set to `false`)
- Each request creates new agents with unique names (includes request_id)
- Agents are cleaned up after each request
- Solution timeout is 60 seconds per request
- Polling interval is 2 seconds (configurable in `BackendClient`)

