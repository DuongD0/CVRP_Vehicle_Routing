export interface Customer {
  id: number;
  x: number;
  y: number;
  demand: number;
  name: string;
  timeWindow?: [number, number];  // Optional: time window constraint [start, end]
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
  maxDistance?: number;  // Optional: maximum distance constraint
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
  summary?: {
    totalItemsRequested: number;
    totalItemsDelivered: number;
    totalDistance: number;
    numberOfRoutes: number;
    deliveryRate: number;
    unservedCustomers: number;
  };
  unservedCustomers?: Customer[];  // Customers that couldn't be served
  timestamp?: string;
  configName?: string;
  solveTimeMs?: number;
}