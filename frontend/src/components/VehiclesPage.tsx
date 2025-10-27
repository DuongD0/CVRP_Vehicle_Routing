import { Vehicle } from '../types/cvrp';
import { VehiclePanel } from './VehiclePanel';
import { Button } from './ui/button';
import { ArrowLeft } from 'lucide-react';

interface VehiclesPageProps {
  vehicles: Vehicle[];
  vehicleStates?: { [vehicleName: string]: string };
  onAddVehicle: () => void;
  onUpdateVehicle: (id: number, name: string, capacity: number) => void;
  onDeleteVehicle: (id: number) => void;
  onBack: () => void;
}

export function VehiclesPage({
  vehicles,
  vehicleStates,
  onAddVehicle,
  onUpdateVehicle,
  onDeleteVehicle,
  onBack,
}: VehiclesPageProps) {
  return (
    <div className="size-full bg-muted/30 overflow-auto">
      <div className="container mx-auto p-6 space-y-6">
        {/* Header */}
        <div className="flex items-center gap-4">
          <Button onClick={onBack} variant="outline" size="icon">
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div className="space-y-2">
            <h1>Vehicle Management</h1>
            <p className="text-muted-foreground">
              Configure your fleet of vehicles with their names and capacities
            </p>
          </div>
        </div>

        {/* Vehicle Panel */}
        <div className="max-w-3xl">
          <div className="p-6 border rounded-lg bg-card">
            <VehiclePanel
              vehicles={vehicles}
              vehicleStates={vehicleStates}
              onAddVehicle={onAddVehicle}
              onUpdateVehicle={onUpdateVehicle}
              onDeleteVehicle={onDeleteVehicle}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
