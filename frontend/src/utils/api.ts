import { Customer, Vehicle, Solution, Route, Depot } from '../types/cvrp';

// API endpoint - hardcoded default
const API_ENDPOINT = 'http://localhost:8000/api/solve-cvrp';


// User request data format - new format with depot, vehicles array, and customers array
export interface CVRPRequest {
  depot: {
    name: string;
    x: number;
    y: number;
  };
  vehicles: Array<{
    name: string;
    capacity: number;
    maxDistance: number;
  }>;
  customers: Array<{
    id: string;
    demand: number;
    x: number;
    y: number;
    timeWindow?: [number, number];  // Optional: time window constraint
  }>;
}

// Backend response data format (new format with vehicle states)
export interface CVRPResponse {
  timestamp?: string;
  configName?: string;
  solveTimeMs?: number;
  summary?: {
    totalItemsRequested: number;
    totalItemsDelivered: number;
    totalDistance: number;
    numberOfRoutes: number;
    deliveryRate: number;
    unservedCustomers: number;
  };
  routes: Array<{
    routeId?: number;  // Route identifier
    vehicleName?: string;  // Vehicle name (can be "unknown")
    totalDemand: number;
    totalDistance: number;
    customers: Array<{
      id: number;
      name: string;
      x: number;
      y: number;
      demand: number;
    }>;
  }>;
  unservedCustomers?: Array<{
    id: number;
    name: string;
    x: number;
    y: number;
    demand: number;
  }>;
  // Legacy fields for backward compatibility
  request_id?: string;
  vehicle_state?: { [vehicleName: string]: string };
  available_vehicle_count?: number;
  total_distance?: number;
  meta?: {
    solver: string;
    solve_time_ms: number;
  };
}

/**
 * Formats the CVRP data for backend API
 */
export function formatCVRPRequest(
  customers: Customer[],
  vehicles?: Vehicle[]
): CVRPRequest {
  const formattedData: CVRPRequest = {
    depot: {
      name: 'Depot',
      x: 800.0,
      y: 600.0,
    },
    vehicles: [],
    customers: [],
  };

  // Format vehicles array with name, capacity, and maxDistance
  if (vehicles && vehicles.length > 0) {
    formattedData.vehicles = vehicles.map((vehicle) => ({
      name: vehicle.name || `Vehicle ${vehicle.id}`,
      capacity: vehicle.capacity,
      maxDistance: vehicle.maxDistance || 1000.0,  // Default max distance if not provided
    }));
  }

  // Format customers array with id, demand, x, y
  customers.forEach((customer) => {
    const customerData: {
      id: string;
      demand: number;
      x: number;
      y: number;
      timeWindow?: [number, number];
    } = {
      id: `${customer.id}`,
      demand: customer.demand,
      x: customer.x,
      y: customer.y,
    };
    
    // Only include timeWindow if it's defined and both values are actually set (not default 0,0)
    if (customer.timeWindow && !(customer.timeWindow[0] === 0 && customer.timeWindow[1] === 0)) {
      customerData.timeWindow = customer.timeWindow;
    }
    
    formattedData.customers.push(customerData);
  });

  return formattedData;
}

/**
 * Converts backend response to frontend Solution format
 */
export function parseBackendResponse(response: CVRPResponse, originalCustomers?: Customer[]): Solution {
  // Create a map of original customer data by ID for coordinate lookup
  const customerMap = new Map<number, Customer>();
  if (originalCustomers) {
    originalCustomers.forEach(c => customerMap.set(c.id, c));
  }

  const routes: Route[] = response.routes.map((route, index) => {
    // Map customers and restore their coordinates from original data
    const customers = route.customers.map(c => {
      const original = customerMap.get(c.id);
      return {
        id: c.id,
        name: c.name,
        x: original?.x ?? c.x,  // Use original coordinates if available
        y: original?.y ?? c.y,
        demand: c.demand,
      };
    });

    return {
      vehicleId: route.routeId ?? index + 1,
      vehicleName: route.vehicleName,
      routeId: route.routeId?.toString(),
      customers,
      totalDemand: route.totalDemand,
      totalDistance: route.totalDistance,
    };
  });

  // Map unserved customers
  const unservedCustomers = response.unservedCustomers?.map(c => ({
    id: c.id,
    name: c.name,
    x: c.x,
    y: c.y,
    demand: c.demand,
  }));

  return {
    routes,
    totalDistance: response.summary?.totalDistance ?? response.total_distance ?? 0,
    vehicleStates: response.vehicle_state,
    availableVehicleCount: response.available_vehicle_count,
    meta: response.meta,
    summary: response.summary,
    unservedCustomers,
    timestamp: response.timestamp,
    configName: response.configName,
    solveTimeMs: response.solveTimeMs,
  };
}

/**
 * Sends CVRP problem to backend and returns solution (updated for async processing)
 */
export async function solveCVRPBackend(
  customers: Customer[],
  vehicles?: Vehicle[]
): Promise<Solution> {
  const requestData = formatCVRPRequest(customers, vehicles);

  try {
    const response = await fetch(API_ENDPOINT, {
      method: 'POST',
      mode: 'cors',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify(requestData),
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Backend API error (${response.status}): ${errorText || response.statusText}`);
    }

    // The data contains an ID which acts like a key to identify the requests
    const data = await response.json();
    return await pollForSolution(data.request_id, customers);

  } catch (error) {
    console.error('Error calling backend API:', error);
    if (error instanceof TypeError && error.message === 'Failed to fetch') {
      throw new Error('Cannot connect to backend. Please ensure:\n1. Backend server is running\n2. CORS is enabled\n3. API URL is correct');
    }
    throw error;
  }
}

/**
 * Polls for solution completion
 */
async function pollForSolution(requestId: string, originalCustomers: Customer[], maxAttempts: number = 30): Promise<Solution> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    try {
      const status = await checkSolutionStatus(requestId);
      
      if (status.status === 'completed' && status.solution) {
        return parseBackendResponse(status.solution, originalCustomers);
      } else if (status.status === 'pending' || status.status === 'processing') {
        // Wait 1 second before next attempt
        await new Promise(resolve => setTimeout(resolve, 1000));
        continue;
      } else {
        throw new Error(`Unexpected status: ${status.status}`);
      }
    } catch (error) {
      if (attempt === maxAttempts - 1) {
        throw error;
      }
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
  }
  
  throw new Error('Timeout waiting for solution');
}

/**
 * Checks solution status for a request
 */
export async function checkSolutionStatus(requestId: string): Promise<{
  request_id: string;
  status: string;
  solution?: CVRPResponse;
}> {
  try {
    const response = await fetch(`http://localhost:8000/api/solution/${requestId}`, {
      method: 'GET',
      mode: 'cors',
      headers: {
        'Accept': 'application/json',
      },
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Backend API error (${response.status}): ${errorText || response.statusText}`);
    }

    const data = await response.json();
    return data;
  } catch (error) {
    console.error('Error checking solution status:', error);
    if (error instanceof TypeError && error.message === 'Failed to fetch') {
      throw new Error('Cannot connect to backend. Please ensure:\n1. Backend server is running\n2. CORS is enabled\n3. API URL is correct');
    }
    throw error;
  }
}

/**
 * Export the formatted JSON data for debugging or manual use
 */
export function exportFormattedJSON(
  customers: Customer[],
  vehicles: Vehicle[]
): void {
  const data = formatCVRPRequest(customers, vehicles);
  console.log('CVRP Request Data:', JSON.stringify(data, null, 2));
  
  const jsonString = JSON.stringify(data, null, 2);
  const blob = new Blob([jsonString], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `cvrp-request-${Date.now()}.json`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}