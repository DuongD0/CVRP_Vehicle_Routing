import { Customer } from '../types/cvrp';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Trash2 } from 'lucide-react';

interface CustomerPanelProps {
  customers: Customer[];
  onUpdateCustomer: (id: number, demand: number) => void;
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
              className="flex items-center gap-2 p-3 border rounded-md bg-card"
            >
              <div className="flex-1">
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
                      onUpdateCustomer(customer.id, Math.max(1, value));
                    }}
                    className="w-20"
                    min="1"
                  />
                  <span className="text-muted-foreground">units</span>
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