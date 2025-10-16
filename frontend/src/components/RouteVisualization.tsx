import { Customer, Depot, Route } from '../types/cvrp';

interface RouteVisualizationProps {
  depot: Depot;
  customers: Customer[];
  routes: Route[];
  onAddCustomer: (x: number, y: number) => void;
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
}: RouteVisualizationProps) {
  const handleCanvasClick = (e: React.MouseEvent<SVGSVGElement>) => {
    const svg = e.currentTarget;
    const rect = svg.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * 800;
    const y = ((e.clientY - rect.top) / rect.height) * 600;
    onAddCustomer(x, y);
  };

  return (
    <div className="border rounded-lg overflow-hidden bg-white">
      <svg
        width="100%"
        height="100%"
        viewBox="0 0 800 600"
        className="cursor-crosshair"
        onClick={handleCanvasClick}
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
        <rect width="800" height="600" fill="url(#grid)" />

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
            <g key={customer.id}>
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
              >
                {customer.demand}
              </text>
              <text
                x={customer.x}
                y={customer.y - 20}
                textAnchor="middle"
                fill="#475569"
                fontSize="11"
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