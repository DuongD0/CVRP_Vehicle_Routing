import { Solution } from '../types/cvrp';
import { Alert, AlertDescription } from './ui/alert';
import { Truck, Route as RouteIcon, AlertCircle, XCircle, HelpCircle, CheckCircle, Clock } from 'lucide-react';

interface SolutionPanelProps {
  solution: Solution | null;
  solutionStatus: 'unsolved' | 'solving' | 'solved' | 'no-solution';
}

export function SolutionPanel({ solution, solutionStatus }: SolutionPanelProps) {
  // Unsolved state
  if (solutionStatus === 'unsolved') {
    return (
      <div className="space-y-3">
        <h3>Solution</h3>
        <Alert>
          <HelpCircle className="h-4 w-4" />
          <AlertDescription>
            Click "Solve CVRP" to generate routes
          </AlertDescription>
        </Alert>
      </div>
    );
  }

  // Solving state
  if (solutionStatus === 'solving') {
    return (
      <div className="space-y-3">
        <h3>Solution</h3>
        <Alert>
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>
            Solving CVRP problem...
          </AlertDescription>
        </Alert>
      </div>
    );
  }

  // No solution state (backend returned no routes)
  if (solutionStatus === 'no-solution' || !solution || solution.routes.length === 0) {
    return (
      <div className="space-y-3">
        <h3>Solution</h3>
        <Alert variant="destructive">
          <XCircle className="h-4 w-4" />
          <AlertDescription>
            Cannot solve: No feasible solution found. The problem may be over-constrained (e.g., vehicle capacities too small for customer demands).
          </AlertDescription>
        </Alert>
      </div>
    );
  }

  // Solved state
  return (
    <div className="flex flex-col h-full">
      <h3 className="mb-4">Solution</h3>

      {/* Summary Stats */}
      <div className="grid grid-cols-3 gap-3 mb-4">
        <div className="p-3 border rounded-md bg-card">
          <div className="flex items-center gap-2 text-muted-foreground mb-1">
            <Truck className="h-4 w-4" />
            <span>Routes</span>
          </div>
          <div>{solution.routes.length}</div>
        </div>
        {solution.availableVehicleCount !== undefined && (
          <div className="p-3 border rounded-md bg-card">
            <div className="flex items-center gap-2 text-muted-foreground mb-1">
              <CheckCircle className="h-4 w-4" />
              <span>Available</span>
            </div>
            <div>{solution.availableVehicleCount}</div>
          </div>
        )}
        <div className="p-3 border rounded-md bg-card">
          <div className="flex items-center gap-2 text-muted-foreground mb-1">
            <RouteIcon className="h-4 w-4" />
            <span>Distance</span>
          </div>
          <div>{solution.totalDistance.toFixed(1)}</div>
        </div>
      </div>

      {/* Metadata */}
      {solution.meta && (
        <div className="mb-4 p-3 border rounded-md bg-card/50">
          <div className="flex items-center justify-between text-sm">
            <div className="flex items-center gap-2 text-muted-foreground">
              <Clock className="h-3 w-3" />
              <span>{solution.meta.solver}</span>
            </div>
            <span className="text-muted-foreground">
              {solution.meta.solve_time_ms}ms
            </span>
          </div>
        </div>
      )}

      {/* Route details - scrollable */}
      <div className="flex flex-col min-h-0 flex-1">
        <h4 className="mb-2">Routes</h4>
        <div className="space-y-2 overflow-y-auto flex-1">
          {solution.routes.map((route, index) => (
            <div
              key={route.routeId || index}
              className="p-3 border rounded-md bg-card space-y-2"
            >
              <div className="flex items-center justify-between">
                <div className="flex flex-col">
                  <span>{route.vehicleName || `Vehicle ${route.vehicleId}`}</span>
                  <div className="flex gap-2 text-xs text-muted-foreground">
                    {route.routeId && <span>Route: {route.routeId}</span>}
                    {route.vehicleName && route.vehicleName !== `Vehicle ${route.vehicleId}` && (
                      <span>ID: {route.vehicleId}</span>
                    )}
                  </div>
                </div>
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
