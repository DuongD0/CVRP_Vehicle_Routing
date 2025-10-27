# Customer-Only Request Fix ✅

## Issue

When sending a request with only customers (no vehicles), the system was returning **Error 400: Missing required fields**.

According to the workflow plan (VRP_Test_Cases.txt lines 70-76), the system should support two formats:
- **Format 1 (Customer data only)**: Used when vehicle fleet unchanged
- **Format 2 (Full data with vehicles)**: Used when vehicle fleet changes

## Root Cause

**backend_server.py line 119:**
```python
if 'vehicles' not in cvrp_data or 'customers' not in cvrp_data:
    return jsonify({'error': 'Missing required fields: vehicles and customers'}), 400
```

This validation required BOTH fields, but `vehicles` should be optional.

## Solution

### 1. Backend API - Made Vehicles Optional

**backend/backend_server.py:**

**Before:**
```python
# Validate required fields
if 'vehicles' not in cvrp_data or 'customers' not in cvrp_data:
    return jsonify({'error': 'Missing required fields: vehicles and customers'}), 400

print(f"Received vehicles: {list(cvrp_data['vehicles'].keys())}")
```

**After:**
```python
# Validate required fields (vehicles is optional if fleet unchanged)
if 'customers' not in cvrp_data:
    return jsonify({'error': 'Missing required field: customers'}), 400

# Log the request type
if 'vehicles' in cvrp_data:
    print(f"Received vehicles: {list(cvrp_data['vehicles'].keys())}")
    print(f"Request type: Full data (vehicles + customers)")
else:
    print(f"Request type: Customers only (vehicle fleet unchanged)")
```

### 2. Depot Agent - Vehicle Caching

**src/main/java/project/Agent/Depot.java:**

**Added field:**
```java
private Map<String, Object> cachedVehicles;  // Cached vehicle list for customer-only requests
```

**Updated parseAPIData() method:**
```java
if (vehiclesStart != -1 && vehiclesEnd != -1) {
    // Parse vehicles from request
    // ...
    // Cache the vehicles for future customer-only requests
    this.cachedVehicles = new HashMap<>(vehicles);
} else {
    // No vehicles in request - use cached vehicles from previous request
    if (this.cachedVehicles != null && !this.cachedVehicles.isEmpty()) {
        vehicles = new HashMap<>(this.cachedVehicles);
        System.out.println("Depot: No vehicles in request - using cached vehicle list");
        for (Map.Entry<String, Object> entry : vehicles.entrySet()) {
            System.out.println("Depot: Using cached vehicle '" + entry.getKey() + "' with capacity " + entry.getValue());
        }
    } else {
        System.err.println("Depot: WARNING - No vehicles in request and no cached vehicles available!");
    }
}
```

## Usage

### Request 1: Full Data (Vehicles + Customers)
```json
{
  "vehicles": {
    "Thanh": 20,
    "Chang": 20,
    "ASd": 20
  },
  "customers": {
    "customer_1": [[320, 171], 5],
    "customer_2": [[374, 435], 5]
  }
}
```

**Console Output:**
```
Request type: Full data (vehicles + customers)
Received vehicles: ['Thanh', 'Chang', 'ASd']
Depot: Found vehicle 'Thanh' with capacity 20
Depot: Found vehicle 'Chang' with capacity 20
Depot: Found vehicle 'ASd' with capacity 20
```

### Request 2: Customers Only (Vehicle Fleet Unchanged)
```json
{
  "customers": {
    "customer_1": [[500, 600], 8],
    "customer_2": [[700, 800], 12],
    "customer_3": [[900, 1000], 15]
  }
}
```

**Console Output:**
```
Request type: Customers only (vehicle fleet unchanged)
Depot: No vehicles in request - using cached vehicle list
Depot: Using cached vehicle 'Thanh' with capacity 20
Depot: Using cached vehicle 'Chang' with capacity 20
Depot: Using cached vehicle 'ASd' with capacity 20
```

## Benefits

✅ **Reduced payload** - Don't need to send vehicle data on every request  
✅ **Faster requests** - Less data to parse  
✅ **Workflow compliance** - Matches the specified workflow in VRP_Test_Cases.txt  
✅ **Backward compatible** - Still accepts full data format  

## Edge Cases Handled

### No Cached Vehicles
If a customer-only request is sent before any vehicle data has been cached:
```
Depot: WARNING - No vehicles in request and no cached vehicles available!
```
The system will continue with an empty vehicle list, which will be caught in the Delivery Agent.

### Vehicle List Changes
When vehicles are included in a request, the cache is updated:
1. First request: `{Thanh: 20, Chang: 20}` → Cached
2. Second request: Customers only → Uses cached `{Thanh: 20, Chang: 20}`
3. Third request: `{Thanh: 20, Chang: 20, NewVehicle: 30}` → Cache updated
4. Fourth request: Customers only → Uses new cached list

## Testing

### Test Case 1: Full Data Request
```bash
curl -X POST http://localhost:8000/api/solve-cvrp \
  -H "Content-Type: application/json" \
  -d '{
    "vehicles": {"Thanh": 20, "Chang": 20},
    "customers": {
      "customer_1": [[320, 171], 5],
      "customer_2": [[374, 435], 5]
    }
  }'
```

**Expected:** ✅ Success (202 Accepted)

### Test Case 2: Customers Only (After Test 1)
```bash
curl -X POST http://localhost:8000/api/solve-cvrp \
  -H "Content-Type: application/json" \
  -d '{
    "customers": {
      "customer_1": [[500, 600], 8],
      "customer_2": [[700, 800], 12]
    }
  }'
```

**Expected:** ✅ Success (202 Accepted) - Uses cached vehicles

### Test Case 3: Customers Only (No Cache)
Restart the JADE system without sending any requests first, then:
```bash
curl -X POST http://localhost:8000/api/solve-cvrp \
  -H "Content-Type: application/json" \
  -d '{
    "customers": {
      "customer_1": [[500, 600], 8]
    }
  }'
```

**Expected:** ⚠️ Warning logged, but request accepted. Will fail when trying to solve (no vehicles).

## Files Modified

1. ✅ `backend/backend_server.py` - Made vehicles optional, added request type logging
2. ✅ `src/main/java/project/Agent/Depot.java` - Added vehicle caching, handle missing vehicles

## Status

🎉 **FIXED - Ready to use!**

You can now send:
1. Full requests (vehicles + customers)
2. Customer-only requests (when vehicle fleet unchanged)

The system will automatically use cached vehicles for customer-only requests.


