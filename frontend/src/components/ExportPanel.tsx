import { Customer, Depot, Vehicle, Solution } from '../types/cvrp';
import { Button } from './ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from './ui/select';
import {
  exportToJSON,
  exportCustomersCSV,
  exportVehiclesCSV,
  exportSolutionCSV,
  exportCompleteCSV,
  generateExportData,
} from '../utils/export-data';
import { Download, FileJson } from 'lucide-react';
import { useState } from 'react';

interface ExportPanelProps {
  depot: Depot;
  customers: Customer[];
  vehicles: Vehicle[];
  solution: Solution | null;
}

export function ExportPanel({
  depot,
  customers,
  vehicles,
  solution,
}: ExportPanelProps) {
  const [selectedCSVFormat, setSelectedCSVFormat] = useState<string>('complete');

  const handleExportJSON = () => {
    const data = generateExportData(depot, customers, vehicles, solution);
    exportToJSON(data);
  };

  const handleExportCSV = () => {
    switch (selectedCSVFormat) {
      case 'complete':
        exportCompleteCSV(depot, customers, vehicles, solution);
        break;
      case 'customers':
        exportCustomersCSV(customers);
        break;
      case 'vehicles':
        exportVehiclesCSV(vehicles);
        break;
      case 'solution':
        if (solution) {
          exportSolutionCSV(solution, depot);
        }
        break;
    }
  };

  const hasData = customers.length > 0 || vehicles.length > 0;
  const hasSolution = solution !== null;

  return (
    <div className="space-y-3">
      <h3>Export Data</h3>

      {!hasData ? (
        <p className="text-muted-foreground">
          Add customers and vehicles to enable export
        </p>
      ) : (
        <div className="space-y-3">
          {/* JSON Export */}
          <div className="space-y-2">
            <Button
              onClick={handleExportJSON}
              className="w-full"
              variant="outline"
            >
              <FileJson className="h-4 w-4 mr-2" />
              Export JSON
            </Button>
            <p className="text-muted-foreground">
              Complete data structure with all coordinates, demands, and capacities
            </p>
          </div>

          {/* CSV Export */}
          <div className="space-y-2">
            <div className="flex gap-2">
              <Select
                value={selectedCSVFormat}
                onValueChange={setSelectedCSVFormat}
              >
                <SelectTrigger className="flex-1">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="complete">Complete CSV</SelectItem>
                  <SelectItem value="customers">Customers Only</SelectItem>
                  <SelectItem value="vehicles">Vehicles Only</SelectItem>
                  <SelectItem value="solution" disabled={!hasSolution}>
                    Solution Routes
                  </SelectItem>
                </SelectContent>
              </Select>
              <Button
                onClick={handleExportCSV}
                disabled={selectedCSVFormat === 'solution' && !hasSolution}
                variant="outline"
                size="icon"
              >
                <Download className="h-4 w-4" />
              </Button>
            </div>
            <p className="text-muted-foreground">
              {selectedCSVFormat === 'complete' &&
                'All data in one CSV file with sections'}
              {selectedCSVFormat === 'customers' &&
                'Customer coordinates and demands'}
              {selectedCSVFormat === 'vehicles' && 'Vehicle capacities'}
              {selectedCSVFormat === 'solution' &&
                (hasSolution
                  ? 'Route sequences and assignments'
                  : 'Solve CVRP first to export solution')}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
