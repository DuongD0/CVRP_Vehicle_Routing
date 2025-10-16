import { Customer, Vehicle, Depot, Solution, Route } from '../types/cvrp';

// API endpoint - can be updated dynamically
let API_ENDPOINT = 'http://localhost:8000/api/solve-cvrp';

/*
Keep the endpoint static for easier testing

export function setApiEndpoint(url: string) {
  API_ENDPOINT = url;
}
*/

// User request data format
export interface CVRPRequest {
  vehicles: { [key: string]: number };
  customers: { [key: string]: [number[], number] };
}

// Backend response data format
export interface CVRPResponse {
  routes: Array<{
    vehicle_id: number;
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
}

/**
 * Formats the CVRP data for backend API
 */
export function formatCVRPRequest(
  customers: Customer[],
  vehicles: Vehicle[],
  depot: Depot
): CVRPRequest {
  const formattedData: CVRPRequest = {
    vehicles: {},
    customers: {},
  };

  // Format vehicles: vehicle_j: capacity
  vehicles.forEach((vehicle) => {
    formattedData.vehicles[`vehicle_${vehicle.id}`] = vehicle.capacity;
  });

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
  const routes: Route[] = response.routes.map((route) => ({
    vehicleId: route.vehicle_id,
    customers: route.customers,
    totalDemand: route.total_demand,
    totalDistance: route.total_distance,
  }));

  return {
    routes,
    totalDistance: response.total_distance
  };
}

/**
 * Sends CVRP problem to backend and returns solution (updated for async processing)
 */
export async function solveCVRPBackend(
  customers: Customer[],
  vehicles: Vehicle[],
  depot: Depot
): Promise<Solution> {
  const requestData = formatCVRPRequest(customers, vehicles, depot);

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
  vehicles: Vehicle[],
  depot: Depot
): void {
  const data = formatCVRPRequest(customers, vehicles, depot);
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
