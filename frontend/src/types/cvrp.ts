export interface Customer {
  id: number;
  x: number;
  y: number;
  demand: number;
  name: string;
}

export interface Depot {
  x: number;
  y: number;
  name: string;
}

export interface Vehicle {
  id: number;
  name: string;
  capacity: number;
}

export interface Route {
  vehicleId: number | string;
  vehicleName?: string;  // Optional: vehicle agent name
  routeId?: string;  // Optional: route identifier
  customers: Customer[];
  totalDemand: number;
  totalDistance: number;
}

export interface Solution {
  routes: Route[];
  totalDistance: number;
  vehicleStates?: { [vehicleName: string]: string };  // Optional: vehicle states
  availableVehicleCount?: number;  // Optional: available vehicle count
  meta?: {
    solver: string;
    solve_time_ms: number;
  };
}