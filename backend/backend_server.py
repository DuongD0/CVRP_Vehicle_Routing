#!/usr/bin/env python3
"""
Simple Flask backend server for CVRP API
This server handles requests from the Depot agent and provides an API endpoint
"""

from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
import json
import time
import uuid
import os
from pathlib import Path

app = Flask(__name__)
CORS(app)  # Enable CORS for frontend integration

# Get the project root directory (parent of backend directory)
BACKEND_DIR = Path(__file__).parent.absolute()
PROJECT_ROOT = BACKEND_DIR.parent
LOGS_DIR = PROJECT_ROOT / "logs"

class CVRPBackendServer:
    def __init__(self):
        self.requests = {}
        self.solutions = {}
        self.registered_agents = set()  # Track registered agents for log endpoints
        # Use absolute path to logs directory relative to project root
        self.logs_base_dir = LOGS_DIR
        # Movement tracking: {vehicle_name: {'x': float, 'y': float, 'status': str, 'route_id': str, 'timestamp': float}}
        self.vehicle_positions = {}
        # Vehicle configuration for agent creation
        self.vehicle_config = None  # Stores confirmed vehicle list
        self.vehicle_config_timestamp = None
        # Agent status tracking: {agent_name: {'status': str, 'type': str, 'timestamp': float}}
        # status: 'active', 'inactive', 'terminated'
        # type: 'mra', 'da'
        self.agent_status = {}
        
    def add_request(self, request_data):
        """Add a new CVRP request"""
        request_id = str(uuid.uuid4())
        self.requests[request_id] = {
            'data': request_data,
            'status': 'pending',
            'timestamp': time.time()
        }
        print(f"Added new CVRP request: {request_id}")
        return request_id
    
    def get_pending_request(self):
        """Get the oldest pending request"""
        for req_id, req_data in self.requests.items():
            if req_data['status'] == 'pending':
                # Create a copy to avoid mutating original
                req_data_copy = req_data.copy()
                req_data_copy['status'] = 'processing'
                req_data['status'] = 'processing'
                print(f"Returning pending request: {req_id}")
                return req_id, req_data['data']
        return None, None
    
    def add_solution(self, request_id, solution_data):
        """Add a solution for a request"""
        if request_id in self.requests:
            self.requests[request_id]['status'] = 'completed'
            self.solutions[request_id] = {
                'solution': solution_data,
                'timestamp': time.time()
            }
            print(f"Added solution for request: {request_id}")
            return True
        return False
    
    def get_solution(self, request_id):
        """Get solution for a request"""
        if request_id in self.solutions:
            return self.solutions[request_id]['solution']
        return None

# Global server instance
server = CVRPBackendServer()

@app.route('/api/solve-cvrp', methods=['GET'])
def poll_request():
    """Poll endpoint for getting pending CVRP requests"""
    action = request.args.get('action', 'poll')
    
    if action == 'poll':
        # Return pending request if available
        req_id, req_data = server.get_pending_request()
        if req_id and req_data:
            return jsonify({
                'request_id': req_id,
                'data': req_data
            }), 200
        else:
            return '', 204  # No Content - no pending requests
    else:
        return jsonify({'error': 'Invalid action'}), 400

@app.route('/api/solve-cvrp', methods=['POST'])
def submit_solution():
    """Submit solution for a CVRP request"""
    action = request.args.get('action', None)
    print(f"POST /api/solve-cvrp called with action={action}")
    
    if action == 'response':
        # This is a solution from the Depot agent
        try:
            solution_data = request.get_json()
            if not solution_data:
                return jsonify({'error': 'No JSON data provided'}), 400
                
            request_id = solution_data.get('request_id')
            
            if not request_id:
                return jsonify({'error': 'Missing request_id'}), 400
            
            # Add the solution to the server
            success = server.add_solution(request_id, solution_data)
            
            if success:
                return jsonify({'status': 'success', 'message': 'Solution received'}), 200
            else:
                return jsonify({'error': 'Invalid request_id'}), 400
                
        except Exception as e:
            return jsonify({'error': str(e)}), 500
    else:
        # This is a new CVRP request from the frontend
        try:
            cvrp_data = request.get_json()
            if not cvrp_data:
                return jsonify({'error': 'No JSON data provided'}), 400
                
            # Validate required fields - only customers are required
            # Depot and vehicles are NOT in requests - they are set when vehicles are confirmed
            if 'customers' not in cvrp_data or not isinstance(cvrp_data['customers'], list):
                return jsonify({'error': 'Missing or invalid field: customers (must be array)'}), 400
            
            # Validate customers structure
            for i, customer in enumerate(cvrp_data['customers']):
                if 'id' not in customer or 'demand' not in customer or 'x' not in customer or 'y' not in customer:
                    return jsonify({'error': f'Customer {i} must have: id, demand, x, y'}), 400
            
            print(f"Received CVRP request from frontend: {len(cvrp_data['customers'])} customers (using existing DAs)")
            request_id = server.add_request(cvrp_data)
            
            # Return immediately with request_id for polling
            return jsonify({
                'request_id': request_id,
                'status': 'submitted',
                'message': 'Request submitted successfully. Use polling to check for solution.'
            }), 202  # Accepted status
            
        except Exception as e:
            print(f"Error processing frontend request: {str(e)}")
            return jsonify({'error': str(e)}), 500

@app.route('/api/solution/<request_id>', methods=['GET'])
def get_solution_status(request_id):
    """Check solution status for a request"""
    try:
        if request_id in server.requests:
            request_info = server.requests[request_id]
            if request_id in server.solutions:
                solution = server.get_solution(request_id)
                return jsonify({
                    'request_id': request_id,
                    'status': 'completed',
                    'solution': solution
                }), 200
            else:
                return jsonify({
                    'request_id': request_id,
                    'status': request_info['status']
                }), 200
        else:
            return jsonify({'error': 'Request not found'}), 404
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/log/mra', methods=['GET'])
def get_mra_log():
    """Get MRA log - always available at /log/mra"""
    return get_agent_log('MRA')

@app.route('/log/<agent_name>', methods=['GET'])
def get_agent_log(agent_name=None):
    """Get log for a specific agent"""
    if agent_name is None:
        agent_name = request.view_args.get('agent_name')
    
    if not agent_name:
        return jsonify({'error': 'Agent name required'}), 400
    
    try:
        # Logs are now directly in logs/ folder (no subfolders)
        # Create logs directory if it doesn't exist
        server.logs_base_dir.mkdir(parents=True, exist_ok=True)
        
        if not server.logs_base_dir.exists():
            return jsonify({
                'error': 'Logs directory not found',
                'path': str(server.logs_base_dir),
                'absolute_path': str(server.logs_base_dir.absolute())
            }), 404
        
        # Determine log file name based on actual file structure
        # MRA log file: "MRA_conversations.log"
        # DA log files: "DA-{agent_name}_conversations.log" (e.g., "DA-DA1_conversations.log")
        log_file_name = None
        if agent_name.lower() == 'mra':
            log_file_name = "MRA_conversations.log"
        else:
            # For DAs: format is "DA-{agent_name}_conversations.log"
            # If agent_name already starts with "DA-", use it as-is
            # Otherwise, add "DA-" prefix
            if agent_name.startswith("DA-"):
                # Already has DA- prefix, use directly
                log_file_name = f"{agent_name}_conversations.log"
            else:
                # Add DA- prefix
                log_file_name = f"DA-{agent_name}_conversations.log"
        
        # Look for log file directly in logs/ folder
        log_file = server.logs_base_dir / log_file_name
        
        if not log_file.exists():
            # List available log files for debugging
            available_files = [f.name for f in server.logs_base_dir.glob("*_conversations.log")] if server.logs_base_dir.exists() else []
            return jsonify({
                'error': f'Log file not found for agent: {agent_name}',
                'searched_file': log_file_name,
                'searched_path': str(log_file),
                'logs_directory': str(server.logs_base_dir),
                'available_files': available_files,
                'message': 'Log file may not exist yet. Agents create log files when they start processing requests.'
            }), 404
        
        # Read and return log file content
        with open(log_file, 'r', encoding='utf-8') as f:
            log_content = f.read()
        
        return jsonify({
            'agent_name': agent_name,
            'log_file': str(log_file.relative_to(server.logs_base_dir)),
            'content': log_content,
            'size': len(log_content)
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/movement/update', methods=['POST'])
def update_vehicle_position():
    """Update vehicle position (called by agents or parsed from logs)"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'error': 'No JSON data provided'}), 400
        
        vehicle_name = data.get('vehicle_name')
        if not vehicle_name:
            return jsonify({'error': 'vehicle_name required'}), 400
        
        position_data = {
            'x': data.get('x', 0.0),
            'y': data.get('y', 0.0),
            'status': data.get('status', 'idle'),  # idle, moving, at_customer, at_depot
            'route_id': data.get('route_id'),
            'target_x': data.get('target_x'),
            'target_y': data.get('target_y'),
            'timestamp': time.time()
        }
        
        server.vehicle_positions[vehicle_name] = position_data
        return jsonify({'success': True, 'vehicle': vehicle_name}), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/movement/all', methods=['GET'])
def get_all_vehicle_positions():
    """Get all vehicle positions"""
    return jsonify({
        'vehicles': server.vehicle_positions,
        'timestamp': time.time()
    }), 200

@app.route('/api/vehicles/confirm', methods=['POST'])
def confirm_vehicles():
    """Confirm vehicle list and trigger agent creation"""
    try:
        data = request.get_json()
        if not data or 'vehicles' not in data:
            return jsonify({'error': 'Invalid request - vehicles required'}), 400
        
        vehicles = data['vehicles']
        if not isinstance(vehicles, list) or len(vehicles) == 0:
            return jsonify({'error': 'At least one vehicle required'}), 400
        
        # Validate vehicle data
        for v in vehicles:
            if 'name' not in v or 'capacity' not in v or 'maxDistance' not in v:
                return jsonify({'error': 'Each vehicle must have: name, capacity, maxDistance'}), 400
        
        # Store vehicle configuration
        server.vehicle_config = {
            'vehicles': vehicles,
            'depot': data.get('depot', {'name': 'Depot', 'x': 0.0, 'y': 0.0})
        }
        server.vehicle_config_timestamp = time.time()
        
        print(f"Vehicle configuration confirmed: {len(vehicles)} vehicles")
        for v in vehicles:
            print(f"  - {v['name']}: capacity={v['capacity']}, maxDistance={v['maxDistance']}")
        
        return jsonify({
            'success': True,
            'message': f'{len(vehicles)} vehicles confirmed. Agents will be created.',
            'vehicles': [v['name'] for v in vehicles]
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/vehicles/poll-config', methods=['GET'])
def poll_vehicle_config():
    """Poll endpoint for Main.java to get vehicle configuration and create agents"""
    if server.vehicle_config is None:
        return '', 204  # No Content - no vehicle config available
    
    # Return config and mark as consumed (Main.java will create agents)
    config = server.vehicle_config.copy()
    server.vehicle_config = None  # Consume the config (one-time use)
    
    return jsonify({
        'vehicles': config['vehicles'],
        'depot': config['depot'],
        'timestamp': server.vehicle_config_timestamp
    }), 200

@app.route('/api/agents/status', methods=['POST'])
def update_agent_status():
    """Update agent status (called by agents when they start/stop)"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'error': 'No JSON data provided'}), 400
        
        agent_name = data.get('agent_name')
        agent_type = data.get('agent_type')  # 'mra' or 'da'
        status = data.get('status')  # 'active', 'inactive', 'terminated'
        info = data.get('info', {})  # Additional info (depot coords for MRA, capacity/speed for DA)
        
        if not agent_name or not agent_type or not status:
            return jsonify({'error': 'agent_name, agent_type, and status required'}), 400
        
        server.agent_status[agent_name] = {
            'status': status,
            'type': agent_type,
            'timestamp': time.time(),
            'info': info
        }
        
        print(f"Agent status updated: {agent_name} ({agent_type}) -> {status}")
        return jsonify({'success': True, 'agent': agent_name}), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/agents/status', methods=['GET'])
def get_agent_status():
    """Get current status of all agents"""
    agents = []
    for agent_name, agent_info in server.agent_status.items():
        agent_data = {
            'name': agent_name,
            'type': agent_info['type'],
            'status': agent_info['status'],
            'timestamp': agent_info['timestamp']
        }
        # Add additional info if available
        if 'info' in agent_info and agent_info['info']:
            agent_data['info'] = agent_info['info']
        agents.append(agent_data)
    
    # Sort: MRA first, then DAs
    agents.sort(key=lambda x: (x['type'] != 'mra', x['name']))
    
    return jsonify({
        'agents': agents,
        'mra_count': sum(1 for a in agents if a['type'] == 'mra' and a['status'] == 'active'),
        'da_count': sum(1 for a in agents if a['type'] == 'da' and a['status'] == 'active'),
        'total_active': sum(1 for a in agents if a['status'] == 'active')
    }), 200

@app.route('/api/movement/<vehicle_name>', methods=['GET'])
def get_vehicle_position(vehicle_name):
    """Get position for a specific vehicle"""
    if vehicle_name in server.vehicle_positions:
        return jsonify({
            'vehicle': vehicle_name,
            'position': server.vehicle_positions[vehicle_name]
        }), 200
    else:
        return jsonify({'error': 'Vehicle not found'}), 404

@app.route('/log', methods=['GET'])
def list_available_logs():
    """List all available agent logs"""
    try:
        # Create logs directory if it doesn't exist
        server.logs_base_dir.mkdir(parents=True, exist_ok=True)
        
        if not server.logs_base_dir.exists():
            return jsonify({'agents': []}), 200
        
        agents = []
        
        # Find all log files directly in logs/ folder
        for log_file in server.logs_base_dir.glob("*_conversations.log"):
            full_name = log_file.stem.replace("_conversations", "")
            
            # Extract agent name for API access
            # MRA files: "MRA" -> agent_name = "mra"
            # DA files: "DA-DA1" -> agent_name = "DA1" (remove "DA-" prefix for API)
            if full_name == "MRA":
                agent_name = "mra"
            elif full_name.startswith("DA-"):
                # Remove "DA-" prefix for API access (e.g., "DA-DA1" -> "DA1")
                agent_name = full_name[3:]
            else:
                agent_name = full_name
            
            agents.append({
                'name': agent_name,
                'log_file': str(log_file.name),
                'endpoint': f'/log/{agent_name}'
            })
        
        # Always include MRA if file exists
        if 'mra' not in [a['name'] for a in agents] and (server.logs_base_dir / "MRA_conversations.log").exists():
            agents.append({
                'name': 'mra',
                'log_file': 'MRA_conversations.log',
                'endpoint': '/log/mra'
            })
        
        return jsonify({'agents': agents}), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/latest-logs', methods=['GET'])
def get_latest_logs():
    """Get latest logs info (for frontend compatibility)"""
    try:
        # Create logs directory if it doesn't exist
        server.logs_base_dir.mkdir(parents=True, exist_ok=True)
        
        if not server.logs_base_dir.exists():
            return jsonify({'agents': [], 'folder': None}), 200
        
        agents = []
        
        # Find all log files directly in logs/ folder
        for log_file in server.logs_base_dir.glob("*_conversations.log"):
            full_name = log_file.stem.replace("_conversations", "")
            
            # Extract agent name for API access
            # MRA files: "MRA" -> agent_name = "mra"
            # DA files: "DA-DA1" -> agent_name = "DA1" (remove "DA-" prefix for API)
            if full_name == "MRA":
                agent_name = "mra"
            elif full_name.startswith("DA-"):
                # Remove "DA-" prefix for API access (e.g., "DA-DA1" -> "DA1")
                agent_name = full_name[3:]
            else:
                agent_name = full_name
            
            if agent_name not in agents:
                agents.append(agent_name)
        
        # Always include MRA if file exists
        if 'mra' not in agents and (server.logs_base_dir / "MRA_conversations.log").exists():
            agents.append('mra')
        
        return jsonify({
            'agents': agents,
            'folder': None  # No folder structure anymore
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500
            
def start_backend_server(host='localhost', port=8000, debug=False):
    print(f"Starting CVRP Backend Server on {host}:{port}")
    print(f"Logs directory: {LOGS_DIR}")
    print(f"Logs directory exists: {LOGS_DIR.exists()}")
    if LOGS_DIR.exists():
        log_files = list(LOGS_DIR.glob("*_conversations.log"))
        print(f"Found {len(log_files)} log files: {[f.name for f in log_files]}")
    else:
        print("Logs directory does not exist yet - will be created when needed")
    print("Available endpoints:")
    print("  GET  /api/solve-cvrp?action=poll     - Poll for pending requests")
    print("  POST /api/solve-cvrp?action=response - Submit solution")
    print("  POST /api/solve-cvrp                  - Submit new CVRP request")
    print("  GET  /api/solution/<request_id>      - Check solution status")
    print("  GET  /log/mra                        - Get MRA log")
    print("  GET  /log/<agent_name>               - Get agent log")
    print("  GET  /log                            - List available agent logs")
    app.run(host=host, port=port, debug=debug, use_reloader=False)

if __name__ == '__main__':
    start_backend_server(debug=True)
