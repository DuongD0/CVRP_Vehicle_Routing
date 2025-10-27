import { Customer, Vehicle, Solution, Route } from '../types/cvrp';

// API endpoint - hardcoded default
const API_ENDPOINT = 'http://localhost:8000/api/solve-cvrp';


// User request data format
// vehicles is optional - can send customer data only if vehicle fleet unchanged
export interface CVRPRequest {
  vehicles?: { [key: string]: number };  // Optional: vehicle_name: capacity or vehicle_1: capacity
  customers: { [key: string]: [number[], number] };  // Required: customer_id: [[x, y], demand]
}

// Backend response data format (new format with vehicle states)
export interface CVRPResponse {
  request_id: string;
  vehicle_state?: { [vehicleName: string]: string };  // Optional: vehicle states (free/absent)
  available_vehicle_count?: number;  // Optional: count of available vehicles
  routes: Array<{
    route_id?: string;  // Optional: route identifier
    vehicle_id?: number;  // Optional: numeric vehicle ID
    vehicle_agent?: string;  // Optional: vehicle agent name
    customers: Array<{
      id: number;
      x: number;
      y: number;
      demand: number;
      name: string;
    }>;
    total_demand: number;
    total_distance: number;
  }>;
  total_distance: number;
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
    customers: {},
  };

  // Format vehicles: name: capacity (only if provided)
  if (vehicles && vehicles.length > 0) {
    formattedData.vehicles = {};
    vehicles.forEach((vehicle) => {
      formattedData.vehicles![vehicle.name || `Vehicle ${vehicle.id}`] = vehicle.capacity;
    });
  }

  // Format customers: customer_i: [[x, y], demand]
  customers.forEach((customer) => {
    formattedData.customers[`customer_${customer.id}`] = [
      [customer.x, customer.y],
      customer.demand,
    ];
  });

  return formattedData;
}

/**
 * Converts backend response to frontend Solution format
 */
export function parseBackendResponse(response: CVRPResponse): Solution {
  const routes: Route[] = response.routes.map((route, index) => ({
    vehicleId: route.vehicle_id || route.vehicle_agent as any || index + 1,  // Support both formats
    vehicleName: route.vehicle_agent,  // Optional: vehicle agent name
    routeId: route.route_id,  // Optional: route ID
    customers: route.customers,
    totalDemand: route.total_demand,
    totalDistance: route.total_distance,
  }));

  return {
    routes,
    totalDistance: response.total_distance,
    vehicleStates: response.vehicle_state,  // Optional: vehicle states
    availableVehicleCount: response.available_vehicle_count,  // Optional: available count
    meta: response.meta,  // Optional: solver metadata
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
    return await pollForSolution(data.request_id);

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
async function pollForSolution(requestId: string, maxAttempts: number = 30): Promise<Solution> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    try {
      const status = await checkSolutionStatus(requestId);
      
      if (status.status === 'completed' && status.solution) {
        return parseBackendResponse(status.solution);
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
