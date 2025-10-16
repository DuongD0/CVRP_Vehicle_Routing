import { useState } from 'react';
import { Customer, Depot, Vehicle, Solution } from './types/cvrp';
import { solveCVRPBackend, exportFormattedJSON, setApiEndpoint } from './utils/api';
import { RouteVisualization } from './components/RouteVisualization';
import { CustomerPanel } from './components/CustomerPanel';
import { VehiclePanel } from './components/VehiclePanel';
import { SolutionPanel } from './components/SolutionPanel';
import { ApiConfigPanel } from './components/ApiConfigPanel';
import { JsonFormatInfo } from './components/JsonFormatInfo';
import { Button } from './components/ui/button';
import { Toaster } from './components/ui/sonner';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './components/ui/tabs';
import { Play, RotateCcw, Download, Loader2 } from 'lucide-react';
import { toast } from 'sonner@2.0.3';

export default function App() {
  const [depot] = useState<Depot>({ x: 400, y: 300, name: 'Depot' });
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([
    { id: 1, capacity: 20 },
    { id: 2, capacity: 20 },
  ]);
  const [solution, setSolution] = useState<Solution | null>(null);
  const [nextCustomerId, setNextCustomerId] = useState(1);
  const [nextVehicleId, setNextVehicleId] = useState(3);
  const [isLoading, setIsLoading] = useState(false);

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
  };

  const handleUpdateCustomer = (id: number, demand: number) => {
    setCustomers(
      customers.map((c) => (c.id === id ? { ...c, demand } : c))
    );
    setSolution(null); // Clear solution when updating
  };

  const handleDeleteCustomer = (id: number) => {
    setCustomers(customers.filter((c) => c.id !== id));
    setSolution(null); // Clear solution when deleting
  };

  const handleAddVehicle = () => {
    const newVehicle: Vehicle = {
      id: nextVehicleId,
      capacity: 20,
    };
    setVehicles([...vehicles, newVehicle]);
    setNextVehicleId(nextVehicleId + 1);
    setSolution(null); // Clear solution when adding vehicle
  };

  const handleUpdateVehicle = (id: number, capacity: number) => {
    setVehicles(
      vehicles.map((v) => (v.id === id ? { ...v, capacity } : v))
    );
    setSolution(null); // Clear solution when updating
  };

  const handleDeleteVehicle = (id: number) => {
    setVehicles(vehicles.filter((v) => v.id !== id));
    setSolution(null); // Clear solution when deleting
  };

  const handleSolve = async () => {
    if (customers.length === 0) return;
    
    setIsLoading(true);
    try {
      // Send data to backend API
      const result = await solveCVRPBackend(customers, vehicles, depot);
      setSolution(result);
      toast.success('CVRP solved successfully!');
    } catch (error) {
      console.error('Error solving CVRP:', error);
      toast.error('Failed to solve CVRP. Please check your backend API connection.');
      setSolution(null);
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
    setNextCustomerId(1);
  };

  const handleApiUrlChange = (url: string) => {
    setApiEndpoint(url);
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
          <div className="lg:col-span-2 space-y-4">
            <div className="aspect-[4/3]">
              <RouteVisualization
                depot={depot}
                customers={customers}
                routes={solution?.routes || []}
                onAddCustomer={handleAddCustomer}
              />
            </div>
          </div>

          {/* Control Panel */}
          <div className="space-y-6">
            {/* Vehicles */}
            <div className="p-4 border rounded-lg bg-card">
              <VehiclePanel
                vehicles={vehicles}
                onAddVehicle={handleAddVehicle}
                onUpdateVehicle={handleUpdateVehicle}
                onDeleteVehicle={handleDeleteVehicle}
              />
            </div>

            {/* Customers */}
            <div className="p-4 border rounded-lg bg-card">
              <CustomerPanel
                customers={customers}
                onUpdateCustomer={handleUpdateCustomer}
                onDeleteCustomer={handleDeleteCustomer}
              />
            </div>

            {/* Solution */}
            <div className="p-4 border rounded-lg bg-card">
              <SolutionPanel solution={solution} />
            </div>

            {/* API Configuration & Format */}
            <div className="p-4 border rounded-lg bg-card">
              <Tabs defaultValue="config">
                <TabsList className="grid w-full grid-cols-2">
                  <TabsTrigger value="config">API Config</TabsTrigger>
                  <TabsTrigger value="format">JSON Format</TabsTrigger>
                </TabsList>
                <TabsContent value="config" className="mt-4">
                  <ApiConfigPanel onApiUrlChange={handleApiUrlChange} />
                </TabsContent>
                <TabsContent value="format" className="mt-4">
                  <JsonFormatInfo />
                </TabsContent>
              </Tabs>
            </div>
          </div>
        </div>
      </div>
      <Toaster />
    </div>
  );
}