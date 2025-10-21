import { useState } from 'react';
import { Customer, Depot, Route } from '../types/cvrp';
import { Button } from './ui/button';
import { ZoomIn, ZoomOut, Maximize2, Trash2 } from 'lucide-react';

interface RouteVisualizationProps {
  depot: Depot;
  customers: Customer[];
  routes: Route[];
  onAddCustomer: (x: number, y: number) => void;
  onDeleteCustomer: (id: number) => void;
}

const COLORS = [
  '#3b82f6', // blue
  '#ef4444', // red
  '#10b981', // green
  '#f59e0b', // amber
  '#8b5cf6', // violet
  '#ec4899', // pink
  '#06b6d4', // cyan
  '#f97316', // orange
];

export function RouteVisualization({
  depot,
  customers,
  routes,
  onAddCustomer,
  onDeleteCustomer,
}: RouteVisualizationProps) {
  const [zoom, setZoom] = useState(1);
  const [panX, setPanX] = useState(0);
  const [panY, setPanY] = useState(0);
  const [isPanning, setIsPanning] = useState(false);
  const [lastMousePos, setLastMousePos] = useState({ x: 0, y: 0 });
  const [wasPanning, setWasPanning] = useState(false);

  const handleCanvasClick = (e: React.MouseEvent<SVGSVGElement>) => {
    // Don't add customer if user was panning (holding Ctrl or Shift)
    if (wasPanning) {
      setWasPanning(false);
      return;
    }
    
    const svg = e.currentTarget;
    const rect = svg.getBoundingClientRect();
    // Convert screen coordinates to SVG coordinates considering viewBox
    const x = ((e.clientX - rect.left) / rect.width) * (1200 / zoom) + (-panX);
    const y = ((e.clientY - rect.top) / rect.height) * (900 / zoom) + (-panY);
    onAddCustomer(x, y);
  };

  const handleCustomerClick = (e: React.MouseEvent, customerId: number) => {
    e.stopPropagation();
  };

  const handleCustomerRightClick = (e: React.MouseEvent, customerId: number) => {
    e.preventDefault();
    e.stopPropagation();
    onDeleteCustomer(customerId);
  };

  const handleZoomIn = () => {
    setZoom(prev => Math.min(prev * 1.2, 5));
  };

  const handleZoomOut = () => {
    setZoom(prev => Math.max(prev / 1.2, 0.5));
  };

  const handleResetView = () => {
    setZoom(1);
    setPanX(0);
    setPanY(0);
  };

  const handleMouseDown = (e: React.MouseEvent<SVGSVGElement>) => {
    if (e.button === 1 || (e.button === 0 && (e.shiftKey || e.ctrlKey))) { // Middle mouse button or Shift/Ctrl + left click
      setIsPanning(true);
      setWasPanning(true);
      setLastMousePos({ x: e.clientX, y: e.clientY });
      e.preventDefault();
    }
  };

  const handleMouseMove = (e: React.MouseEvent<SVGSVGElement>) => {
    if (isPanning) {
      const deltaX = e.clientX - lastMousePos.x;
      const deltaY = e.clientY - lastMousePos.y;
      setPanX(prev => prev + deltaX / zoom);
      setPanY(prev => prev + deltaY / zoom);
      setLastMousePos({ x: e.clientX, y: e.clientY });
    }
  };

  const handleMouseUp = () => {
    setIsPanning(false);
  };

  const handleWheel = (e: React.WheelEvent<SVGSVGElement>) => {
    e.preventDefault();
    const delta = e.deltaY > 0 ? 0.9 : 1.1;
    setZoom(prev => Math.max(0.5, Math.min(prev * delta, 5)));
  };

  return (
    <div className="border rounded-lg overflow-hidden bg-white relative h-full">
      {/* Zoom Controls */}
      <div className="absolute top-3 right-3 z-10 flex flex-col gap-2">
        <Button
          variant="secondary"
          size="icon"
          onClick={handleZoomIn}
          title="Zoom In"
        >
          <ZoomIn className="h-4 w-4" />
        </Button>
        <Button
          variant="secondary"
          size="icon"
          onClick={handleZoomOut}
          title="Zoom Out"
        >
          <ZoomOut className="h-4 w-4" />
        </Button>
        <Button
          variant="secondary"
          size="icon"
          onClick={handleResetView}
          title="Reset View"
        >
          <Maximize2 className="h-4 w-4" />
        </Button>
      </div>

      {/* Info Text */}
      <div className="absolute bottom-3 left-3 z-10 bg-black/60 text-white px-3 py-2 rounded text-sm">
        Click to add | Right-click customer to delete | Ctrl/Shift+Drag to pan | Scroll to zoom
      </div>

      <svg
        width="100%"
        height="100%"
        viewBox={`${-panX} ${-panY} ${1200/zoom} ${900/zoom}`}
        className={isPanning ? "cursor-grabbing" : "cursor-crosshair"}
        onClick={handleCanvasClick}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
      >
        {/* Background grid */}
        <defs>
          <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
            <path
              d="M 40 0 L 0 0 0 40"
              fill="none"
              stroke="#e5e7eb"
              strokeWidth="0.5"
            />
          </pattern>
        </defs>
        {/* Dynamic grid that fills the entire viewBox */}
        <rect 
          x={-panX} 
          y={-panY} 
          width={1200/zoom} 
          height={900/zoom} 
          fill="url(#grid)" 
        />

        {/* Draw routes */}
        {routes.map((route, routeIndex) => {
          const color = COLORS[routeIndex % COLORS.length];
          const customers = route.customers;

          return (
            <g key={routeIndex}>
              {/* Lines */}
              {customers.length > 0 && (
                <>
                  {/* Depot to first customer */}
                  <line
                    x1={depot.x}
                    y1={depot.y}
                    x2={customers[0].x}
                    y2={customers[0].y}
                    stroke={color}
                    strokeWidth="2"
                    strokeDasharray="5,5"
                  />
                  
                  {/* Customer to customer */}
                  {customers.map((customer, i) => {
                    if (i < customers.length - 1) {
                      return (
                        <line
                          key={i}
                          x1={customer.x}
                          y1={customer.y}
                          x2={customers[i + 1].x}
                          y2={customers[i + 1].y}
                          stroke={color}
                          strokeWidth="2"
                        />
                      );
                    }
                    return null;
                  })}
                  
                  {/* Last customer back to depot */}
                  <line
                    x1={customers[customers.length - 1].x}
                    y1={customers[customers.length - 1].y}
                    x2={depot.x}
                    y2={depot.y}
                    stroke={color}
                    strokeWidth="2"
                    strokeDasharray="5,5"
                  />
                </>
              )}
            </g>
          );
        })}

        {/* Draw depot */}
        <g>
          <rect
            x={depot.x - 15}
            y={depot.y - 15}
            width="30"
            height="30"
            fill="#1e293b"
            stroke="#0f172a"
            strokeWidth="2"
          />
          <text
            x={depot.x}
            y={depot.y + 5}
            textAnchor="middle"
            fill="white"
            fontSize="12"
          >
            D
          </text>
        </g>

        {/* Draw customers */}
        {customers.map((customer) => {
          // Find which route this customer belongs to
          const routeIndex = routes.findIndex((route) =>
            route.customers.some((c) => c.id === customer.id)
          );
          const color = routeIndex >= 0 ? COLORS[routeIndex % COLORS.length] : '#94a3b8';

          return (
            <g 
              key={customer.id}
              onClick={(e) => handleCustomerClick(e, customer.id)}
              onContextMenu={(e) => handleCustomerRightClick(e, customer.id)}
              style={{ cursor: 'pointer' }}
            >
              <circle
                cx={customer.x}
                cy={customer.y}
                r="12"
                fill={color}
                stroke="white"
                strokeWidth="2"
              />
              <text
                x={customer.x}
                y={customer.y + 4}
                textAnchor="middle"
                fill="white"
                fontSize="10"
                pointerEvents="none"
              >
                {customer.demand}
              </text>
              <text
                x={customer.x}
                y={customer.y - 20}
                textAnchor="middle"
                fill="#475569"
                fontSize="11"
                pointerEvents="none"
              >
                {customer.name}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}