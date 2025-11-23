# CVRP Frontend Web Application

A comprehensive web interface for the CVRP Multi-Agent System.

## Features

- **Vehicle Management**: Define vehicles once, reuse for all requests
- **Customer Definition**: Add customers with demand and coordinates
- **Route Visualization**: Interactive map showing routes, vehicles, and unserved customers
- **Agent Information**: View agent status and details
- **Log Viewer**: Real-time agent log viewing
- **Movement Tracking**: Visualize vehicle movement on routes

## Installation

```bash
cd "frontend"
pip install -r requirements.txt
```

## Running

1. Make sure the backend server is running on `http://localhost:8000`
2. Make sure Main.java is running to process requests
3. Start the frontend:

```bash
python app.py
```

4. Open browser to `http://localhost:5000`

## Usage

1. **Define Vehicles** (First Time):

   - Go to Vehicle Setup page
   - Add vehicles with name, capacity, and max distance
   - Click "Save & Continue"
2. **Add Customers**:

   - On main page, go to "Customers" tab
   - Add customers with ID, demand, and coordinates
   - Click "Submit Request" to send to backend
3. **View Solution**:

   - Map automatically updates when solution is received
   - Routes are displayed with different colors
   - Unserved customers are shown in orange
4. **View Logs**:

   - Go to "Logs" tab
   - Select an agent from dropdown
   - View real-time logs

## API Endpoints

- `GET /` - Main dashboard
- `GET /vehicles` - Vehicle setup page
- `GET /api/vehicles` - Get all vehicles
- `POST /api/vehicles` - Set/update vehicles
- `POST /api/submit-request` - Submit customer-only request
- `GET /api/solution/<request_id>` - Get solution status
- `GET /api/logs` - List available agents
- `GET /api/logs/<agent_name>` - Get agent log
- `GET /api/movement/all` - Get vehicle positions

## Notes

- Vehicles are stored in session (in production, use a database)
- Depot is at (0,0) but displayed in the center of the map
- Requests only send customer data - vehicles are managed separately
- The system automatically polls for solutions after submitting a request
