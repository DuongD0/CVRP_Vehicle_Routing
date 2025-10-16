import { Solution } from '../types/cvrp';
import { Alert, AlertDescription } from './ui/alert';
import { Truck, Route as RouteIcon } from 'lucide-react';

interface SolutionPanelProps {
  solution: Solution | null;
}

export function SolutionPanel({ solution }: SolutionPanelProps) {
  if (!solution) {
    return (
      <div className="space-y-3">
        <h3>Solution</h3>
        <p className="text-muted-foreground">
          Click "Solve CVRP" to generate routes
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <h3>Solution</h3>

      {/* Summary Stats */}
      <div className="grid grid-cols-3 gap-3">
        <div className="p-3 border rounded-md bg-card">
          <div className="flex items-center gap-2 text-muted-foreground mb-1">
            <Truck className="h-4 w-4" />
            <span>Vehicles</span>
          </div>
          <div>{solution.routes.length}</div>
        </div>
        <div className="p-3 border rounded-md bg-card">
          <div className="flex items-center gap-2 text-muted-foreground mb-1">
            <RouteIcon className="h-4 w-4" />
            <span>Distance</span>
          </div>
          <div>{solution.totalDistance.toFixed(1)}</div>
        </div>
      </div>

      {/* Route details */}
      <div className="space-y-2">
        <h4>Routes</h4>
        <div className="space-y-2 max-h-[300px] overflow-y-auto">
          {solution.routes.map((route, index) => (
            <div
              key={index}
              className="p-3 border rounded-md bg-card space-y-2"
            >
              <div className="flex items-center justify-between">
                <span>Vehicle {route.vehicleId}</span>
                <span className="text-muted-foreground">
                  {route.customers.length} stops
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Load: {route.totalDemand}</span>
                <span className="text-muted-foreground">
                  Distance: {route.totalDistance.toFixed(1)}
                </span>
              </div>
              <div className="flex flex-wrap gap-1">
                {route.customers.map((customer, i) => (
                  <span
                    key={customer.id}
                    className="px-2 py-1 bg-secondary rounded text-secondary-foreground"
                  >
                    {customer.name}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}