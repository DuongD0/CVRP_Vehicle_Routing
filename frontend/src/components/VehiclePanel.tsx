import { Vehicle } from '../types/cvrp';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Badge } from './ui/badge';
import { Plus, Trash2 } from 'lucide-react';

interface VehiclePanelProps {
  vehicles: Vehicle[];
  vehicleStates?: { [vehicleName: string]: string };
  onAddVehicle: () => void;
  onUpdateVehicle: (id: number, name: string, capacity: number, maxDistance: number) => void;
  onDeleteVehicle: (id: number) => void;
}

export function VehiclePanel({
  vehicles,
  vehicleStates,
  onAddVehicle,
  onUpdateVehicle,
  onDeleteVehicle,
}: VehiclePanelProps) {
  const getVehicleState = (vehicleName: string) => {
    if (!vehicleStates) return null;
    return vehicleStates[vehicleName];
  };

  const getStateBadgeVariant = (state: string) => {
    switch (state.toLowerCase()) {
      case 'free':
        return 'secondary';
      case 'absent':
        return 'outline';
      case 'busy':
      case 'in use':
        return 'default';
      default:
        return 'outline';
    }
  };

  return (
    <div className="h-full flex flex-col">
      {/* Fixed Header */}
      <div className="flex items-center justify-between mb-3 flex-shrink-0">
        <h3>Vehicles</h3>
        <Button onClick={onAddVehicle} size="sm">
          <Plus className="h-4 w-4 mr-1" />
          Add Vehicle
        </Button>
      </div>

      {/* Vehicle Speed Info */}
      <div className="mb-3 p-2 bg-muted/50 rounded-md flex-shrink-0">
        <p className="text-sm text-muted-foreground">
          Vehicle Speed: <span className="font-medium text-foreground">10 unit / time unit</span>
        </p>
      </div>

      {/* Scrollable List */}
      <div className="space-y-3 overflow-y-auto flex-1 min-h-0">
        {vehicles.map((vehicle) => {
          const state = getVehicleState(vehicle.name);
          return (
            <div
              key={vehicle.id}
              className="flex flex-col gap-2 p-3 border rounded-md bg-card"
            >
              <div className="flex items-center gap-2">
                <Label htmlFor={`vehicle-name-${vehicle.id}`} className="min-w-16">
                  Name:
                </Label>
                <Input
                  id={`vehicle-name-${vehicle.id}`}
                  type="text"
                  value={vehicle.name}
                  onChange={(e) => {
                    onUpdateVehicle(vehicle.id, e.target.value, vehicle.capacity, vehicle.maxDistance || 1000);
                  }}
                  className="flex-1"
                  placeholder="Vehicle name"
                />
                {state && (
                  <Badge variant={getStateBadgeVariant(state)}>
                    {state}
                  </Badge>
                )}
              </div>
              <div className="flex items-center gap-2">
                <Label htmlFor={`vehicle-capacity-${vehicle.id}`} className="min-w-16">
                  Capacity:
                </Label>
                <Input
                  id={`vehicle-capacity-${vehicle.id}`}
                  type="number"
                  value={vehicle.capacity}
                  onChange={(e) => {
                    const value = parseInt(e.target.value) || 0;
                    onUpdateVehicle(vehicle.id, vehicle.name, Math.max(1, value), vehicle.maxDistance || 1000);
                  }}
                  className="w-24"
                  min="1"
                />
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={() => onDeleteVehicle(vehicle.id)}
                  disabled={vehicles.length === 1}
                  className="ml-auto"
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
              <div className="flex items-center gap-2">
                <Label htmlFor={`vehicle-maxdist-${vehicle.id}`} className="min-w-16">
                  Max Dist:
                </Label>
                <Input
                  id={`vehicle-maxdist-${vehicle.id}`}
                  type="number"
                  value={vehicle.maxDistance || 1000}
                  onChange={(e) => {
                    const value = parseFloat(e.target.value) || 0;
                    onUpdateVehicle(vehicle.id, vehicle.name, vehicle.capacity, Math.max(1, value));
                  }}
                  className="w-24"
                  min="1"
                  step="100"
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}