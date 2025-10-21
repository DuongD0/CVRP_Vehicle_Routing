import { Depot } from '../types/cvrp';
import { Input } from './ui/input';
import { Label } from './ui/label';

interface DepotPanelProps {
  depot: Depot;
  onUpdateDepot: (x: number, y: number) => void;
}

export function DepotPanel({ depot, onUpdateDepot }: DepotPanelProps) {
  return (
    <div className="h-full flex flex-col">
      {/* Fixed Header */}
      <h3 className="mb-3 flex-shrink-0">Depot</h3>
      
      {/* Scrollable Content */}
      <div className="space-y-3 overflow-y-auto flex-1 min-h-0">
        <div className="flex items-center gap-2 p-3 border rounded-md bg-card">
          <div className="flex-1 space-y-3">
            <div className="flex items-center gap-2">
              <Label htmlFor="depot-x" className="min-w-16">
                X:
              </Label>
              <Input
                id="depot-x"
                type="number"
                value={depot.x}
                onChange={(e) => {
                  const value = parseFloat(e.target.value) || 0;
                  onUpdateDepot(value, depot.y);
                }}
                className="w-24"
              />
            </div>
            <div className="flex items-center gap-2">
              <Label htmlFor="depot-y" className="min-w-16">
                Y:
              </Label>
              <Input
                id="depot-y"
                type="number"
                value={depot.y}
                onChange={(e) => {
                  const value = parseFloat(e.target.value) || 0;
                  onUpdateDepot(depot.x, value);
                }}
                className="w-24"
              />
            </div>
          </div>
        </div>
        <p className="text-muted-foreground">
          Starting point for all vehicle routes
        </p>
      </div>
    </div>
  );
}
