#!/usr/bin/env python3
"""
CVRP Frontend Web Application
Provides a web interface for:
- Vehicle management (define once)
- Customer definition
- Route visualization
- Agent logs and information
- Real-time vehicle movement tracking
"""

from flask import Flask, render_template, request, jsonify, session
from flask_cors import CORS
import json
import requests
import time
from pathlib import Path

app = Flask(__name__)
app.secret_key = 'cvrp-frontend-secret-key'  # For session management
CORS(app)

# Backend API URL
BACKEND_API = "http://localhost:8000"

# Store vehicles in session (in production, use database)
# Format: {name: {capacity, maxDistance}}
def get_vehicles_from_session():
    if 'vehicles' not in session:
        session['vehicles'] = {}
    return session['vehicles']

@app.route('/')
def index():
    """Main page - redirects to vehicle setup if no vehicles defined"""
    if not session.get('vehicles'):
        return render_template('vehicles.html')
    return render_template('main.html')

@app.route('/vehicles')
def vehicles():
    """Vehicle management page"""
    return render_template('vehicles.html')

@app.route('/api/vehicles', methods=['GET'])
def get_vehicles():
    """Get all defined vehicles"""
    vehicles = get_vehicles_from_session()
    vehicles_list = [
        {
            'name': name,
            'capacity': data['capacity'],
            'maxDistance': data['maxDistance']
        }
        for name, data in vehicles.items()
    ]
    return jsonify({'vehicles': vehicles_list})

@app.route('/api/vehicles', methods=['POST'])
def set_vehicles():
    """Set/update vehicles (define once)"""
    try:
        data = request.get_json()
        if not data or 'vehicles' not in data:
            return jsonify({'error': 'Invalid request'}), 400
        
        vehicles = {}
        for v in data['vehicles']:
            if 'name' not in v or 'capacity' not in v or 'maxDistance' not in v:
                return jsonify({'error': 'Each vehicle must have: name, capacity, maxDistance'}), 400
            vehicles[v['name']] = {
                'capacity': int(v['capacity']),
                'maxDistance': float(v['maxDistance'])
            }
        
        session['vehicles'] = vehicles
        
        # Send vehicle list to backend API to create agents immediately
        try:
            confirm_data = {
                'vehicles': [
                    {
                        'name': name,
                        'capacity': v['capacity'],
                        'maxDistance': v['maxDistance']
                    }
                    for name, v in vehicles.items()
                ],
                'depot': {
                    'name': 'Depot',
                    'x': 0.0,
                    'y': 0.0
                }
            }
            
            response = requests.post(
                f"{BACKEND_API}/api/vehicles/confirm",
                json=confirm_data,
                timeout=5
            )
            
            if response.status_code == 200:
                print(f"Vehicle confirmation sent to backend: {len(vehicles)} vehicles")
            else:
                print(f"Warning: Failed to confirm vehicles with backend: {response.status_code}")
        except requests.exceptions.RequestException as e:
            print(f"Warning: Could not connect to backend to confirm vehicles: {e}")
            # Continue anyway - vehicles are saved in session
        
        return jsonify({
            'success': True,
            'message': f'{len(vehicles)} vehicles defined. Agents are being created.',
            'vehicles': list(vehicles.keys())
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/submit-request', methods=['POST'])
def submit_request():
    """Submit customer-only request to backend"""
    try:
        data = request.get_json()
        if not data or 'customers' not in data:
            return jsonify({'error': 'Invalid request - customers required'}), 400
        
        # Verify vehicles are defined (but don't send them - DAs already created)
        vehicles = get_vehicles_from_session()
        if not vehicles:
            return jsonify({'error': 'No vehicles defined. Please define vehicles first.'}), 400
        
        # Build request with only customers (depot and vehicles already defined, DAs already created)
        request_data = {
            'customers': data['customers']
        }
        
        # Submit to backend
        response = requests.post(
            f"{BACKEND_API}/api/solve-cvrp",
            json=request_data,
            headers={'Content-Type': 'application/json'},
            timeout=5
        )
        
        if response.status_code == 202:
            result = response.json()
            return jsonify({
                'success': True,
                'request_id': result.get('request_id'),
                'message': 'Request submitted successfully'
            })
        else:
            return jsonify({'error': f'Backend error: {response.text}'}), response.status_code
            
    except requests.exceptions.RequestException as e:
        return jsonify({'error': f'Cannot connect to backend: {str(e)}'}), 503
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/solution/<request_id>', methods=['GET'])
def get_solution(request_id):
    """Get solution for a request (proxy to backend)"""
    try:
        response = requests.get(
            f"{BACKEND_API}/api/solution/{request_id}",
            timeout=5
        )
        return jsonify(response.json()), response.status_code
    except requests.exceptions.RequestException as e:
        return jsonify({'error': f'Cannot connect to backend: {str(e)}'}), 503

@app.route('/api/agents/status', methods=['GET'])
def get_agent_status():
    """Get current agent status (proxy to backend)"""
    try:
        response = requests.get(f"{BACKEND_API}/api/agents/status", timeout=5)
        return jsonify(response.json()), response.status_code
    except requests.exceptions.RequestException as e:
        return jsonify({'error': f'Cannot connect to backend: {str(e)}'}), 503

@app.route('/api/logs', methods=['GET'])
def get_logs():
    """Get list of available agent logs (proxy to backend)"""
    try:
        response = requests.get(f"{BACKEND_API}/log", timeout=5)
        return jsonify(response.json()), response.status_code
    except requests.exceptions.RequestException as e:
        return jsonify({'error': f'Cannot connect to backend: {str(e)}'}), 503

@app.route('/api/logs/<agent_name>', methods=['GET'])
def get_agent_log(agent_name):
    """Get log for specific agent (proxy to backend)"""
    try:
        response = requests.get(f"{BACKEND_API}/log/{agent_name}", timeout=5)
        if response.status_code == 200:
            return jsonify(response.json()), 200
        else:
            # Return error response from backend
            try:
                error_data = response.json()
                return jsonify(error_data), response.status_code
            except:
                return jsonify({'error': f'Backend returned status {response.status_code}'}), response.status_code
    except requests.exceptions.RequestException as e:
        return jsonify({'error': f'Cannot connect to backend: {str(e)}'}), 503

@app.route('/api/movement/all', methods=['GET'])
def get_movement():
    """Get all vehicle positions (proxy to backend)"""
    try:
        response = requests.get(f"{BACKEND_API}/api/movement/all", timeout=5)
        return jsonify(response.json()), response.status_code
    except requests.exceptions.RequestException as e:
        return jsonify({'error': f'Cannot connect to backend: {str(e)}'}), 503

@app.route('/api/test-cases/<filename>', methods=['GET'])
def get_test_case(filename):
    """Load test case from JSON file"""
    try:
        # Get project root (parent of frontend directory)
        project_root = Path(__file__).parent.parent
        test_case_path = project_root / 'test_cases' / filename
        
        if not test_case_path.exists():
            return jsonify({'error': f'Test case file not found: {filename}'}), 404
        
        with open(test_case_path, 'r') as f:
            test_data = json.load(f)
        
        return jsonify(test_data), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='localhost', port=5000, debug=True)

