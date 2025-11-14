import { Warehouse } from 'lucide-react';

interface DepotPanelProps {
  x: number;
  y: number;
}

export function DepotPanel({ x, y }: DepotPanelProps) {
  return (
    <div className="p-4 border rounded-lg bg-card">
      <div className="flex items-center gap-2 mb-3">
        <Warehouse className="h-5 w-5" />
        <h3>Depot</h3>
      </div>
      <div className="space-y-2">
        <div className="flex items-center gap-2 text-sm">
          <span className="text-muted-foreground">X:</span>
          <span>{x.toFixed(0)}</span>
        </div>
        <div className="flex items-center gap-2 text-sm">
          <span className="text-muted-foreground">Y:</span>
          <span>{y.toFixed(0)}</span>
        </div>
      </div>
    </div>
  );
}
