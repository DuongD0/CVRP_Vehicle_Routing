# Consistency Analysis: Work To Do Items

This document analyzes whether all features mentioned in `work to do.txt` are properly supported across backend, agent, and frontend components.

## Summary of Work To Do Items

### Backend Requirements:
1. Process different types of data:
   - **vehicle-only**: terminate/create corresponding DAs (keep existing, terminate missing, initiate new)
   - **customer-only**: use existing list of DAs to solve the problem
   - **agent-related**: DA state should be passed to API to update state on website
   - **logs**: (empty - needs specification)
2. Separate APIs for these data types

### Agent Requirements:
1. **DA (Delivery Agent)**:
   - If MRA queries when DAs are delivering, they may respond "busy"
   - States: busy (when delivering) and free (when at/arrived back to depot)
   - When arrived back to depot, must notify depot (depot doesn't need to respond, conversation must be logged)
2. **Main**:
   - Based on data type from API: terminate, start, or do nothing to DAs

### Frontend Requirements:
1. Vehicle page should send separate information from request page (main page)
2. Requests (not from test cases) should NOT contain vehicle information
3. Requests from test cases will still include vehicle (for demonstration)
4. Vehicle page should have save button, force user to save changes before exit
5. Saving changes sends vehicles data to API (same format as current vehicle data)
6. Icon that expands window for viewing log, user can choose which agent to view log from

---

## Consistency Analysis

### ✅ **CONSISTENT FEATURES**

#### 1. Vehicle-Only Data Processing
- **Backend**: ❌ **MISSING** - No API endpoint to handle `vehicle_only` data type
- **Agent (Main)**: ❌ **MISSING** - No logic to terminate/create DAs based on vehicle-only data
- **Frontend**: ✅ **EXISTS** - `saveVehiclesOnly()` function in `api.ts` (line 340-385)
- **Status**: **INCONSISTENT** - Frontend ready, but backend and agent don't support it

#### 2. Customer-Only Data Processing
- **Backend**: ❌ **MISSING** - No API endpoint to handle `customer_only` data type
- **Agent (Main)**: ❌ **MISSING** - No logic to use existing DAs for customer-only requests
- **Frontend**: ✅ **EXISTS** - `submitCustomerRequest()` function in `api.ts` (line 390-436)
- **Status**: **INCONSISTENT** - Frontend ready, but backend and agent don't support it

#### 3. Separate APIs for Data Types
- **Backend**: ❌ **MISSING** - Currently only has `/api/solve-cvrp` endpoint
- **Frontend**: ✅ **EXISTS** - Has separate functions: `saveVehiclesOnly()`, `submitCustomerRequest()`, `submitVehicleCustomerRequest()`
- **Status**: **INCONSISTENT** - Frontend expects separate endpoints, backend doesn't provide them

#### 4. Vehicle Page Save Button & Unsaved Changes
- **Frontend**: ✅ **EXISTS** - `VehicleManagementPage.tsx` has:
  - Save button (line 156-172)
  - Unsaved changes detection (line 38-42)
  - UnsavedChangesModal (line 325-330)
  - Forces save before exit (line 103-110)
- **Status**: **CONSISTENT** - Fully implemented in frontend

#### 5. Log Viewer with Agent Selection
- **Frontend**: ✅ **EXISTS** - `LogViewerSheet.tsx`:
  - Icon/button to expand log window (in `RequestWorkspace.tsx` line 252-257)
  - User can choose which agent to view (tabs for MRA, DA1, DA2, DA3 - line 166-178)
  - Fetches logs via `fetchAgentLogs()` API (line 74)
- **Backend**: ❌ **MISSING** - No `/api/logs` endpoint (referenced in `api.ts` line 504)
- **Status**: **INCONSISTENT** - Frontend ready, backend missing

---

### ❌ **MISSING FEATURES**

#### 1. DA State Management (busy/free)
- **Agent (DA)**: ❌ **MISSING** - No state tracking (busy/free)
  - Current code doesn't track state explicitly
  - No "busy" response when MRA queries during delivery
  - No state field in vehicle info response
- **Backend**: ❌ **MISSING** - No API to receive DA state updates
- **Frontend**: ❌ **MISSING** - No UI to display DA states (though `vehicleStates` exists in Solution type)
- **Status**: **NOT IMPLEMENTED** - Needs implementation in all layers

#### 2. DA Notify Depot on Arrival
- **Agent (DA)**: ❌ **MISSING** - No notification to depot when returning
  - `DeliveryAgent.java` returns to depot (line 652-690) but doesn't send notification
  - No message sent to MRA/depot when `isMoving = false` and at depot
- **Agent (MRA/Depot)**: ❌ **MISSING** - No handler for DA arrival notifications
- **Status**: **NOT IMPLEMENTED** - Needs implementation in agent layer

#### 3. Main Agent DA Management (terminate/start/do nothing)
- **Agent (Main)**: ❌ **MISSING** - No logic to:
  - Terminate DAs that are not in the vehicle list
  - Keep DAs that are in the list (already exist)
  - Start DAs that are in the list but not initiated
- **Status**: **NOT IMPLEMENTED** - Needs implementation in Main.java

#### 4. DA Busy Response to MRA Queries
- **Agent (DA)**: ❌ **MISSING** - `VehicleInfoQueryHandler` (line 108-149) always responds with vehicle info
  - No check for `isMoving` or `assignedRouteId` status
  - Should respond "busy" when delivering
- **Status**: **NOT IMPLEMENTED** - Needs modification in DeliveryAgent.java

#### 5. Logs API Endpoint
- **Backend**: ❌ **MISSING** - No `/api/logs` endpoint
  - Frontend expects: `GET /api/logs?request_id={id}&agent={name}`
- **Status**: **NOT IMPLEMENTED** - Needs implementation in backend_server.py

---

### ⚠️ **PARTIALLY IMPLEMENTED**

#### 1. Vehicle Data Format
- **Frontend**: ✅ Sends vehicles in correct format via `saveVehiclesOnly()`
- **Backend**: ⚠️ Current `/api/solve-cvrp` accepts vehicles but doesn't distinguish data types
- **Status**: **PARTIAL** - Format exists but type handling missing

#### 2. Test Case vs Regular Request Separation
- **Frontend**: ✅ **EXISTS** - `RequestWorkspace.tsx`:
  - `isFromTestCase` flag (line 60)
  - Test cases use `submitVehicleCustomerRequest()` (line 155-159)
  - Regular requests use `submitCustomerRequest()` (line 163)
- **Backend**: ❌ Doesn't distinguish between test case and regular requests
- **Status**: **PARTIAL** - Frontend handles it, backend doesn't

---

## Critical Gaps Summary

### High Priority (Blocks Core Functionality):
1. **Backend**: Missing separate API endpoints for:
   - Vehicle-only requests (`POST /api/vehicles` or similar)
   - Customer-only requests (separate from current endpoint)
   - Agent state updates (`POST /api/agent-state` or similar)
   - Log retrieval (`GET /api/logs`)

2. **Agent (Main)**: Missing DA lifecycle management:
   - Terminate DAs not in vehicle list
   - Keep existing DAs that are in list
   - Start new DAs from vehicle list

3. **Agent (DA)**: Missing state management:
   - Track busy/free state
   - Respond "busy" when MRA queries during delivery
   - Notify depot when returning

### Medium Priority (Enhances Functionality):
1. **Backend**: Missing data type handling (`data_type` field processing)
2. **Agent (MRA)**: Missing handler for DA arrival notifications
3. **Frontend**: Missing real-time DA state display (though structure exists)

### Low Priority (Nice to Have):
1. **Backend**: Logs endpoint specification (currently empty in work to do)

---

## Recommendations

### For Backend:
1. Add `/api/vehicles` endpoint for vehicle-only requests
2. Add `/api/customers` endpoint for customer-only requests (or modify existing to handle `data_type`)
3. Add `/api/agent-state` endpoint to receive DA state updates
4. Add `/api/logs` endpoint to serve agent logs
5. Modify `/api/solve-cvrp` to handle `data_type` field and route accordingly

### For Agent (Main):
1. Add logic to compare vehicle list from API with existing DAs
2. Implement DA termination for vehicles not in list
3. Implement DA creation for vehicles in list but not started
4. Keep existing DAs that match vehicle list

### For Agent (DA):
1. Add `state` field (busy/free)
2. Modify `VehicleInfoQueryHandler` to check state and respond "busy" if delivering
3. Add notification to depot when returning (after `returnToDepot()` completes)
4. Include state in vehicle info response

### For Agent (MRA):
1. Add handler for DA arrival notifications
2. Update internal state tracking for DA availability

### For Frontend:
1. Already well-implemented, but may need updates once backend APIs are ready
2. Consider adding real-time state polling for DA states

---

## Conclusion

**Overall Status: INCONSISTENT**

The frontend is mostly ready with the required features, but the backend and agent layers are missing critical implementations. The work to do items are not fully supported across all layers, creating a significant gap between frontend expectations and backend/agent capabilities.

**Priority Actions:**
1. Implement backend API endpoints for different data types
2. Implement DA state management in agent layer
3. Implement DA lifecycle management in Main.java
4. Implement log serving endpoint in backend


