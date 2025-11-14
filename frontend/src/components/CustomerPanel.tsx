import { Customer } from '../types/cvrp';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Trash2 } from 'lucide-react';

interface CustomerPanelProps {
  customers: Customer[];
  onUpdateCustomer: (id: number, demand: number, timeWindow?: [number, number]) => void;
  onDeleteCustomer: (id: number) => void;
}

export function CustomerPanel({
  customers,
  onUpdateCustomer,
  onDeleteCustomer,
}: CustomerPanelProps) {
  return (
    <div className="h-full flex flex-col">
      {/* Fixed Header */}
      <h3 className="mb-3 flex-shrink-0">Customers</h3>
      
      {/* Scrollable List */}
      {customers.length === 0 ? (
        <p className="text-muted-foreground">
          Click on the map to add customers
        </p>
      ) : (
        <div className="space-y-2 overflow-y-auto flex-1 min-h-0">
          {customers.map((customer) => (
            <div
              key={customer.id}
              className="flex items-start gap-2 p-3 border rounded-md bg-card"
            >
              <div className="flex-1 space-y-2">
                <div className="flex items-center gap-2">
                  <Label htmlFor={`customer-${customer.id}`} className="min-w-16">
                    {customer.name}
                  </Label>
                  <Input
                    id={`customer-${customer.id}`}
                    type="number"
                    value={customer.demand}
                    onChange={(e) => {
                      const value = parseInt(e.target.value) || 0;
                      onUpdateCustomer(customer.id, Math.max(1, value), customer.timeWindow);
                    }}
                    className="w-20"
                    min="1"
                  />
                  <span className="text-muted-foreground">units</span>
                </div>
                <div className="text-xs text-muted-foreground pl-16">
                  ({customer.x.toFixed(0)}, {customer.y.toFixed(0)})
                </div>
                <div className="flex items-center gap-2 pl-16">
                  <Label htmlFor={`tw-start-${customer.id}`} className="text-xs">
                    Time Window:
                  </Label>
                  <Input
                    id={`tw-start-${customer.id}`}
                    type="number"
                    placeholder="Start"
                    value={customer.timeWindow?.[0] ?? ''}
                    onChange={(e) => {
                      const start = e.target.value ? parseInt(e.target.value) : undefined;
                      const end = customer.timeWindow?.[1];
                      // Allow partial input: create array if at least one value exists
                      const newTimeWindow = (start !== undefined || end !== undefined) 
                        ? [start ?? 0, end ?? 0] as [number, number]
                        : undefined;
                      onUpdateCustomer(customer.id, customer.demand, newTimeWindow);
                    }}
                    className="w-16 h-7 text-xs"
                  />
                  <span className="text-xs">-</span>
                  <Input
                    id={`tw-end-${customer.id}`}
                    type="number"
                    placeholder="End"
                    value={customer.timeWindow?.[1] ?? ''}
                    onChange={(e) => {
                      const start = customer.timeWindow?.[0];
                      const end = e.target.value ? parseInt(e.target.value) : undefined;
                      // Allow partial input: create array if at least one value exists
                      const newTimeWindow = (start !== undefined || end !== undefined)
                        ? [start ?? 0, end ?? 0] as [number, number]
                        : undefined;
                      onUpdateCustomer(customer.id, customer.demand, newTimeWindow);
                    }}
                    className="w-16 h-7 text-xs"
                  />
                </div>
              </div>
              <Button
                variant="ghost"
                size="icon"
                onClick={() => onDeleteCustomer(customer.id)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}