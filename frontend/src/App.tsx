import { useState } from 'react';
import { Customer, Vehicle, Solution } from './types/cvrp';
import { solveCVRPBackend, exportFormattedJSON } from './utils/api';
import { RouteVisualization } from './components/RouteVisualization';
import { CustomerPanel } from './components/CustomerPanel';
import { DepotPanel } from './components/DepotPanel';
import { VehiclesPage } from './components/VehiclesPage';
import { SolutionPanel } from './components/SolutionPanel';
import { JsonFormatInfo } from './components/JsonFormatInfo';
import { Button } from './components/ui/button';
import { Toaster } from './components/ui/sonner';
import { Play, RotateCcw, Download, Loader2, Settings } from 'lucide-react';
import { toast } from 'sonner@2.0.3';

export default function App() {
  const [currentPage, setCurrentPage] = useState<'main' | 'vehicles'>('main');
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([
    { id: 1, name: 'Vehicle 1', capacity: 20 },
    { id: 2, name: 'Vehicle 2', capacity: 20 },
  ]);
  const [solution, setSolution] = useState<Solution | null>(null);
  const [solutionStatus, setSolutionStatus] = useState<'unsolved' | 'solving' | 'solved' | 'no-solution'>('unsolved');
  const [nextCustomerId, setNextCustomerId] = useState(1);
  const [nextVehicleId, setNextVehicleId] = useState(3);
  const [isLoading, setIsLoading] = useState(false);
  
  // Vehicle comparison tracking
  const [previousVehicles, setPreviousVehicles] = useState<Vehicle[] | null>(null);
  const [currentRequestVehicles, setCurrentRequestVehicles] = useState<Vehicle[] | null>(null);

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

  const handleAddVehicle = () => {
    const newVehicle: Vehicle = {
      id: nextVehicleId,
      name: `Vehicle ${nextVehicleId}`,
      capacity: 20,
    };
    setVehicles([...vehicles, newVehicle]);
    setNextVehicleId(nextVehicleId + 1);
    setSolution(null); // Clear solution when adding vehicle
    setSolutionStatus('unsolved');
  };

  const handleUpdateVehicle = (id: number, name: string, capacity: number) => {
    setVehicles(
      vehicles.map((v) => (v.id === id ? { ...v, name, capacity } : v))
    );
    setSolution(null); // Clear solution when updating
    setSolutionStatus('unsolved');
  };

  const handleDeleteVehicle = (id: number) => {
    setVehicles(vehicles.filter((v) => v.id !== id));
    setSolution(null); // Clear solution when deleting
    setSolutionStatus('unsolved');
  };

  // Compare two vehicle lists to see if they're the same
  const vehiclesAreEqual = (v1: Vehicle[], v2: Vehicle[]): boolean => {
    if (v1.length !== v2.length) return false;
    
    // Sort by id and compare
    const sorted1 = [...v1].sort((a, b) => a.id - b.id);
    const sorted2 = [...v2].sort((a, b) => a.id - b.id);
    
    return sorted1.every((vehicle, index) => {
      const other = sorted2[index];
      return vehicle.name === other.name && vehicle.capacity === other.capacity;
    });
  };

  const handleSolve = async () => {
    if (customers.length === 0) return;
    
    setIsLoading(true);
    setSolutionStatus('solving');
    
    // Determine if we should send vehicle data
    let vehiclesToSend: Vehicle[] | undefined = vehicles;
    
    if (previousVehicles && currentRequestVehicles) {
      // We have history - compare current vehicles with last request vehicles
      if (vehiclesAreEqual(vehicles, currentRequestVehicles)) {
        // Vehicles haven't changed since last request - don't send
        vehiclesToSend = undefined;
      }
    }
    
    try {
      // Send data to backend API
      const result = await solveCVRPBackend(customers, vehiclesToSend);
      
      // Update vehicle tracking
      setPreviousVehicles(currentRequestVehicles);
      setCurrentRequestVehicles([...vehicles]);
      
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
    exportFormattedJSON(customers, vehicles);
    toast.success('JSON data exported successfully!');
  };

  const handleReset = () => {
    setCustomers([]);
    setSolution(null);
    setSolutionStatus('unsolved');
    setNextCustomerId(1);
  };

  // Show vehicles page
  if (currentPage === 'vehicles') {
    return (
      <>
        <VehiclesPage
          vehicles={vehicles}
          vehicleStates={solution?.vehicleStates}
          onAddVehicle={handleAddVehicle}
          onUpdateVehicle={handleUpdateVehicle}
          onDeleteVehicle={handleDeleteVehicle}
          onBack={() => setCurrentPage('main')}
        />
        <Toaster />
      </>
    );
  }

  // Main page
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
            onClick={() => setCurrentPage('vehicles')}
            variant="outline"
            size="lg"
          >
            <Settings className="h-4 w-4 mr-2" />
            Manage Vehicles
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
                customers={customers}
                routes={solution?.routes || []}
                onAddCustomer={handleAddCustomer}
                onDeleteCustomer={handleDeleteCustomer}
              />
            </div>
          </div>

          {/* Control Panel - Depot and Customer panels */}
          <div className="h-[900px] flex flex-col gap-6">
            <DepotPanel x={800} y={600} />
            
            <div className="p-4 border rounded-lg bg-card flex-1 overflow-auto">
              <CustomerPanel
                customers={customers}
                onUpdateCustomer={handleUpdateCustomer}
                onDeleteCustomer={handleDeleteCustomer}
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
