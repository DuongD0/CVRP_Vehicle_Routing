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
  capacity: number;
}

export interface Route {
  vehicleId: number;
  customers: Customer[];
  totalDemand: number;
  totalDistance: number;
}

export interface Solution {
  routes: Route[];
  totalDistance: number;
}