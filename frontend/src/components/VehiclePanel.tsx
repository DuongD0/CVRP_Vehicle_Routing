import { Vehicle } from '../types/cvrp';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Plus, Trash2 } from 'lucide-react';

interface VehiclePanelProps {
  vehicles: Vehicle[];
  onAddVehicle: () => void;
  onUpdateVehicle: (id: number, capacity: number) => void;
  onDeleteVehicle: (id: number) => void;
}

export function VehiclePanel({
  vehicles,
  onAddVehicle,
  onUpdateVehicle,
  onDeleteVehicle,
}: VehiclePanelProps) {
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

      {/* Scrollable List */}
      <div className="space-y-2 overflow-y-auto flex-1 min-h-0">
        {vehicles.map((vehicle) => (
          <div
            key={vehicle.id}
            className="flex items-center gap-2 p-3 border rounded-md bg-card"
          >
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <Label htmlFor={`vehicle-${vehicle.id}`} className="min-w-24">
                  Vehicle {vehicle.id}
                </Label>
                <Input
                  id={`vehicle-${vehicle.id}`}
                  type="number"
                  value={vehicle.capacity}
                  onChange={(e) => {
                    const value = parseInt(e.target.value) || 0;
                    onUpdateVehicle(vehicle.id, Math.max(1, value));
                  }}
                  className="w-20"
                  min="1"
                />
                <span className="text-muted-foreground">capacity</span>
              </div>
            </div>
            <Button
              variant="ghost"
              size="icon"
              onClick={() => onDeleteVehicle(vehicle.id)}
              disabled={vehicles.length === 1}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        ))}
      </div>
    </div>
  );
}