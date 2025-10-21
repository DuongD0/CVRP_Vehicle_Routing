import { useState } from 'react';
import { Customer, Depot, Vehicle, Solution } from './types/cvrp';
import { solveCVRPBackend, exportFormattedJSON } from './utils/api';
import { RouteVisualization } from './components/RouteVisualization';
import { CustomerPanel } from './components/CustomerPanel';
import { VehiclePanel } from './components/VehiclePanel';
import { DepotPanel } from './components/DepotPanel';
import { SolutionPanel } from './components/SolutionPanel';
import { JsonFormatInfo } from './components/JsonFormatInfo';
import { Button } from './components/ui/button';
import { Toaster } from './components/ui/sonner';
import { Play, RotateCcw, Download, Loader2 } from 'lucide-react';
import { toast } from 'sonner@2.0.3';
import { Resizable } from 're-resizable';

export default function App() {
  const [depot, setDepot] = useState<Depot>({ x: 600, y: 450, name: 'Depot' });
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([
    { id: 1, capacity: 20 },
    { id: 2, capacity: 20 },
  ]);
  const [solution, setSolution] = useState<Solution | null>(null);
  const [solutionStatus, setSolutionStatus] = useState<'unsolved' | 'solving' | 'solved' | 'no-solution'>('unsolved');
  const [nextCustomerId, setNextCustomerId] = useState(1);
  const [nextVehicleId, setNextVehicleId] = useState(3);
  const [isLoading, setIsLoading] = useState(false);
  
  // Panel heights (total must equal 852px - the available space after gaps)
  const [vehiclePanelHeight, setVehiclePanelHeight] = useState(180);
  const [customerPanelHeight, setCustomerPanelHeight] = useState(260);
  const [depotPanelHeight, setDepotPanelHeight] = useState(412);

  const handleAddCustomer = (x: number, y: number) => {
    const newCustomer: Customer = {
      id: nextCustomerId,
      x,
      y,
      demand: 5,
      name: `C${nextCustomerId}`,
    };
    setCustomers([...customers, newCustomer]);
    setNextCustomerId(nextCustomerId + 1);
    setSolution(null); // Clear solution when adding new customer
    setSolutionStatus('unsolved');
  };

  const handleUpdateCustomer = (id: number, demand: number) => {
    setCustomers(
      customers.map((c) => (c.id === id ? { ...c, demand } : c))
    );
    setSolution(null); // Clear solution when updating
    setSolutionStatus('unsolved');
  };

  const handleDeleteCustomer = (id: number) => {
    // Remove the customer
    const filteredCustomers = customers.filter((c) => c.id !== id);
    
    // Reallocate IDs immediately - renumber all customers sequentially
    const reindexedCustomers = filteredCustomers.map((customer, index) => ({
      ...customer,
      id: index + 1,
      name: `C${index + 1}`
    }));
    
    setCustomers(reindexedCustomers);
    setNextCustomerId(reindexedCustomers.length + 1);
    setSolution(null); // Clear solution when deleting
    setSolutionStatus('unsolved');
  };

  const handleUpdateDepot = (x: number, y: number) => {
    setDepot({ ...depot, x, y });
    setSolution(null);
    setSolutionStatus('unsolved');
  };

  const handleAddVehicle = () => {
    const newVehicle: Vehicle = {
      id: nextVehicleId,
      capacity: 20,
    };
    setVehicles([...vehicles, newVehicle]);
    setNextVehicleId(nextVehicleId + 1);
    setSolution(null); // Clear solution when adding vehicle
    setSolutionStatus('unsolved');
  };

  const handleUpdateVehicle = (id: number, capacity: number) => {
    setVehicles(
      vehicles.map((v) => (v.id === id ? { ...v, capacity } : v))
    );
    setSolution(null); // Clear solution when updating
    setSolutionStatus('unsolved');
  };

  const handleDeleteVehicle = (id: number) => {
    setVehicles(vehicles.filter((v) => v.id !== id));
    setSolution(null); // Clear solution when deleting
    setSolutionStatus('unsolved');
  };

  const handleSolve = async () => {
    if (customers.length === 0) return;
    
    setIsLoading(true);
    setSolutionStatus('solving');
    try {
      // Send data to backend API
      const result = await solveCVRPBackend(customers, vehicles, depot);
      
      // Check if we got a valid solution with routes
      if (!result || !result.routes || result.routes.length === 0) {
        setSolution(null);
        setSolutionStatus('no-solution');
        toast.error('No feasible solution found');
      } else {
        setSolution(result);
        setSolutionStatus('solved');
        toast.success('CVRP solved successfully!');
      }
    } catch (error) {
      console.error('Error solving CVRP:', error);
      toast.error('Failed to solve CVRP. Please check your backend API connection.');
      setSolution(null);
      setSolutionStatus('no-solution');
    } finally {
      setIsLoading(false);
    }
  };

  const handleExportJSON = () => {
    if (customers.length === 0) {
      toast.error('No data to export. Please add customers first.');
      return;
    }
    exportFormattedJSON(customers, vehicles, depot);
    toast.success('JSON data exported successfully!');
  };

  const handleReset = () => {
    setCustomers([]);
    setSolution(null);
    setSolutionStatus('unsolved');
    setNextCustomerId(1);
  };

  return (
    <div className="size-full bg-muted/30 overflow-auto">
      <div className="container mx-auto p-6 space-y-6">
        {/* Header */}
        <div className="space-y-2">
          <h1>Capacitated Vehicle Routing Problem</h1>
          <p className="text-muted-foreground">
            Click on the map to add customers, configure vehicle capacities, and solve the routing problem
          </p>
        </div>

        {/* Action buttons */}
        <div className="flex gap-3">
          <Button
            onClick={handleSolve}
            disabled={customers.length === 0 || isLoading}
            size="lg"
          >
            {isLoading ? (
              <>
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                Solving...
              </>
            ) : (
              <>
                <Play className="h-4 w-4 mr-2" />
                Solve CVRP
              </>
            )}
          </Button>
          <Button
            onClick={handleExportJSON}
            variant="outline"
            disabled={customers.length === 0}
            size="lg"
          >
            <Download className="h-4 w-4 mr-2" />
            Export JSON
          </Button>
          <Button
            onClick={handleReset}
            variant="outline"
            disabled={customers.length === 0}
            size="lg"
          >
            <RotateCcw className="h-4 w-4 mr-2" />
            Reset
          </Button>
        </div>

        {/* Main content */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Visualization */}
          <div className="lg:col-span-2 h-[900px]">
            <div className="h-full">
              <RouteVisualization
                depot={depot}
                customers={customers}
                routes={solution?.routes || []}
                onAddCustomer={handleAddCustomer}
                onDeleteCustomer={handleDeleteCustomer}
              />
            </div>
          </div>

          {/* Control Panel - 3 resizable panels matching map height */}
          <div className="h-[900px] flex flex-col gap-6">
            <Resizable
              size={{ width: '100%', height: vehiclePanelHeight }}
              onResizeStop={(e, direction, ref, d) => {
                const newHeight = vehiclePanelHeight + d.height;
                const availableSpace = 852; // 900px - 48px gaps
                const otherPanelsHeight = customerPanelHeight + depotPanelHeight;
                const minHeight = 100;
                const maxHeight = availableSpace - 2 * minHeight;
                
                if (newHeight >= minHeight && newHeight <= maxHeight) {
                  setVehiclePanelHeight(newHeight);
                  // Distribute the change proportionally to other panels
                  const ratio = customerPanelHeight / (customerPanelHeight + depotPanelHeight);
                  const remaining = availableSpace - newHeight;
                  setCustomerPanelHeight(remaining * ratio);
                  setDepotPanelHeight(remaining * (1 - ratio));
                }
              }}
              enable={{ bottom: true }}
              minHeight={100}
              maxHeight={652}
            >
              <div className="p-4 border rounded-lg bg-card h-full">
                <VehiclePanel
                  vehicles={vehicles}
                  onAddVehicle={handleAddVehicle}
                  onUpdateVehicle={handleUpdateVehicle}
                  onDeleteVehicle={handleDeleteVehicle}
                />
              </div>
            </Resizable>

            <Resizable
              size={{ width: '100%', height: customerPanelHeight }}
              onResizeStop={(e, direction, ref, d) => {
                const newHeight = customerPanelHeight + d.height;
                const availableSpace = 852;
                const minHeight = 100;
                const maxHeight = availableSpace - vehiclePanelHeight - minHeight;
                
                if (newHeight >= minHeight && newHeight <= maxHeight) {
                  setCustomerPanelHeight(newHeight);
                  setDepotPanelHeight(availableSpace - vehiclePanelHeight - newHeight);
                }
              }}
              enable={{ bottom: true }}
              minHeight={100}
              maxHeight={652}
            >
              <div className="p-4 border rounded-lg bg-card h-full">
                <CustomerPanel
                  customers={customers}
                  onUpdateCustomer={handleUpdateCustomer}
                  onDeleteCustomer={handleDeleteCustomer}
                />
              </div>
            </Resizable>

            <div className="p-4 border rounded-lg bg-card flex-1">
              <DepotPanel
                depot={depot}
                onUpdateDepot={handleUpdateDepot}
              />
            </div>
          </div>
        </div>

        {/* Solution and Info panels - full width below */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Solution */}
          <div className="p-4 border rounded-lg bg-card">
            <SolutionPanel 
              solution={solution} 
              solutionStatus={solutionStatus}
            />
          </div>

          {/* JSON Format Info */}
          <div className="p-4 border rounded-lg bg-card">
            <JsonFormatInfo />
          </div>
        </div>
      </div>
      <Toaster />
    </div>
  );
}
