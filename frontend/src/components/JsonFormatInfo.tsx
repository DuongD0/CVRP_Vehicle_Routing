import { Card } from './ui/card';
import { Badge } from './ui/badge';

export function JsonFormatInfo() {
  const exampleJson = `{
  "vehicles": {
    "vehicle_1": 20,
    "vehicle_2": 20
  },
  "customers": {
    "customer_1": [[150, 200], 5],
    "customer_2": [[300, 150], 8],
    "customer_3": [[500, 400], 3]
  }
}`;

  const responseExample = `{
  "routes": [
    {
      "vehicle_id": 1,
      "customers": [
        {
          "id": 1,
          "x": 150,
          "y": 200,
          "demand": 5,
          "name": "C1"
        }
      ],
      "total_demand": 5,
      "total_distance": 320.5
    }
  ],
  "total_distance": 640.2,
  "unserved_customers": []
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
              <strong>vehicles:</strong> Object with vehicle IDs as keys
            </p>
            <p className="text-muted-foreground ml-4">
              • Value: capacity (number only)
            </p>
            <p className="text-muted-foreground">
              <strong>customers:</strong> Object with customer IDs as keys
            </p>
            <p className="text-muted-foreground ml-4">
              • Value: [[x, y], demand] - coordinates and demand
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
        </div>
      </div>
    </div>
  );
}
