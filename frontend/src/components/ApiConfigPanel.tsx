import { useState } from 'react';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Button } from './ui/button';
import { Save } from 'lucide-react';
import { toast } from 'sonner@2.0.3';

interface ApiConfigPanelProps {
  onApiUrlChange: (url: string) => void;
}

export function ApiConfigPanel({ onApiUrlChange }: ApiConfigPanelProps) {
  const [apiUrl, setApiUrl] = useState('http://localhost:8000/api/solve-cvrp');

  const handleSave = () => {
    if (!apiUrl.trim()) {
      toast.error('API URL cannot be empty');
      return;
    }
    onApiUrlChange(apiUrl);
    toast.success('API endpoint updated');
  };

  return (
    <div className="space-y-4">
      <h3>API Configuration</h3>
      <div className="space-y-3">
        <div className="space-y-2">
          <Label htmlFor="api-url">Backend API Endpoint</Label>
          <Input
            id="api-url"
            type="text"
            value={apiUrl}
            onChange={(e) => setApiUrl(e.target.value)}
            placeholder="http://localhost:8000/api/solve-cvrp"
          />
        </div>
        <Button onClick={handleSave} size="sm" className="w-full">
          <Save className="h-4 w-4 mr-2" />
          Save Configuration
        </Button>
        <div className="p-3 bg-muted rounded-md">
          <p className="text-muted-foreground">
            Configure your backend API endpoint. The JSON data will be sent to this URL when you click "Solve CVRP".
          </p>
        </div>
      </div>
    </div>
  );
}
