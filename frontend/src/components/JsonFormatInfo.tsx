import { Card } from './ui/card';
import { Badge } from './ui/badge';

export function JsonFormatInfo() {
  const exampleJson = `{
  "depot": {
    "name": "Depot",
    "x": 800.0,
    "y": 600.0
  },
  "vehicles": [
    {
      "name": "Vehicle 1",
      "capacity": 20,
      "maxDistance": 1000.0
    },
    {
      "name": "Vehicle 2",
      "capacity": 20,
      "maxDistance": 1000.0
    }
  ],
  "customers": [
    {
      "id": "1",
      "demand": 5,
      "x": 950.0,
      "y": 750.0,
      "timeWindow": [0, 50]
    },
    {
      "id": "2",
      "demand": 8,
      "x": 1100.0,
      "y": 700.0
    }
  ]
}`;

  const responseExample = `{
  "timestamp": "2024-11-13T10:30:00Z",
  "configName": "Basic CVRP",
  "solveTimeMs": 245,
  "summary": {
    "totalItemsRequested": 13,
    "totalItemsDelivered": 11,
    "totalDistance": 850.5,
    "numberOfRoutes": 2,
    "deliveryRate": 0.846,
    "unservedCustomers": 2
  },
  "routes": [
    {
      "routeId": 1,
      "vehicleName": "Vehicle 1",
      "totalDemand": 23,
      "totalDistance": 450.2,
      "customers": [
        {
          "id": 1,
          "name": "C1",
          "x": 950.0,
          "y": 750.0,
          "demand": 5
        },
        {
          "id": 2,
          "name": "C2",
          "x": 1100.0,
          "y": 700.0,
          "demand": 8
        }
      ]
    },
    {
      "routeId": 2,
      "vehicleName": "Vehicle 2",
      "totalDemand": 18,
      "totalDistance": 400.3,
      "customers": [
        {
          "id": 3,
          "name": "C3",
          "x": 1050.0,
          "y": 800.0,
          "demand": 10
        }
      ]
    }
  ],
  "unservedCustomers": [
    {
      "id": 4,
      "name": "C4",
      "x": 1500.0,
      "y": 1200.0,
      "demand": 15
    },
    {
      "id": 5,
      "name": "C5",
      "x": 1600.0,
      "y": 1300.0,
      "demand": 12
    }
  ]
}`;

  return (
    <div className="space-y-4">
      <div>
        <h3>API JSON Format</h3>
        <p className="text-muted-foreground mt-2">
          The application sends data to your backend in the following format:
        </p>
      </div>

      <div className="space-y-4">
        <div>
          <div className="flex items-center gap-2 mb-2">
            <Badge variant="secondary">Request Format</Badge>
          </div>
          <Card className="p-4 bg-muted">
            <pre className="text-xs overflow-auto">
              <code>{exampleJson}</code>
            </pre>
          </Card>
          <div className="mt-2 space-y-1">
            <p className="text-muted-foreground">
              <strong>depot:</strong> Object with depot details
            </p>
            <p className="text-muted-foreground ml-4">
              • name: Name of the depot
            </p>
            <p className="text-muted-foreground ml-4">
              • x: X-coordinate of the depot
            </p>
            <p className="text-muted-foreground ml-4">
              • y: Y-coordinate of the depot
            </p>
            <p className="text-muted-foreground">
              <strong>vehicles:</strong> Array of vehicle objects
            </p>
            <p className="text-muted-foreground ml-4">
              • name: Name of the vehicle
            </p>
            <p className="text-muted-foreground ml-4">
              • capacity: Capacity of the vehicle
            </p>
            <p className="text-muted-foreground ml-4">
              • maxDistance: Maximum distance the vehicle can travel
            </p>
            <p className="text-muted-foreground">
              <strong>customers:</strong> Array of customer objects
            </p>
            <p className="text-muted-foreground ml-4">
              • id: ID of the customer
            </p>
            <p className="text-muted-foreground ml-4">
              • demand: Demand of the customer
            </p>
            <p className="text-muted-foreground ml-4">
              • x: X-coordinate of the customer
            </p>
            <p className="text-muted-foreground ml-4">
              • y: Y-coordinate of the customer
            </p>
            <p className="text-muted-foreground ml-4">
              • timeWindow: Time window for the customer (optional)
            </p>
          </div>
        </div>

        <div>
          <div className="flex items-center gap-2 mb-2">
            <Badge variant="secondary">Expected Response</Badge>
          </div>
          <Card className="p-4 bg-muted">
            <pre className="text-xs overflow-auto">
              <code>{responseExample}</code>
            </pre>
          </Card>
          <div className="mt-2 space-y-1">
            <p className="text-muted-foreground">
              <strong>timestamp:</strong> Timestamp of the solution (optional)
            </p>
            <p className="text-muted-foreground">
              <strong>configName:</strong> Name of the configuration used (optional)
            </p>
            <p className="text-muted-foreground">
              <strong>solveTimeMs:</strong> Time taken to solve in milliseconds (optional)
            </p>
            <p className="text-muted-foreground">
              <strong>summary:</strong> Summary statistics object (optional)
            </p>
            <p className="text-muted-foreground ml-4">
              • totalItemsRequested: Total number of customers
            </p>
            <p className="text-muted-foreground ml-4">
              • totalItemsDelivered: Number of customers served
            </p>
            <p className="text-muted-foreground ml-4">
              • totalDistance: Total distance of all routes
            </p>
            <p className="text-muted-foreground ml-4">
              • numberOfRoutes: Number of routes created
            </p>
            <p className="text-muted-foreground ml-4">
              • deliveryRate: Percentage of customers served (0-1)
            </p>
            <p className="text-muted-foreground ml-4">
              • unservedCustomers: Number of customers not served
            </p>
            <p className="text-muted-foreground">
              <strong>routes:</strong> Array of route objects (required)
            </p>
            <p className="text-muted-foreground ml-4">
              • routeId: Unique identifier for the route
            </p>
            <p className="text-muted-foreground ml-4">
              • vehicleName: Name of the vehicle assigned
            </p>
            <p className="text-muted-foreground ml-4">
              • totalDemand: Total demand served in this route
            </p>
            <p className="text-muted-foreground ml-4">
              • totalDistance: Total distance of this route
            </p>
            <p className="text-muted-foreground ml-4">
              • customers: Array of customer objects in route order
            </p>
            <p className="text-muted-foreground">
              <strong>unservedCustomers:</strong> Array of customers that couldn't be served (optional)
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}