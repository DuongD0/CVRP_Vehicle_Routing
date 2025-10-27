#!/usr/bin/env python3
"""
Simple Flask backend server for CVRP API
This server handles requests from the Depot agent and provides an API endpoint
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import json
import time
import uuid

app = Flask(__name__)
CORS(app)  # Enable CORS for frontend integration

class CVRPBackendServer:
    def __init__(self):
        self.requests = {}
        self.solutions = {}
        
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
                
            # Validate required fields (vehicles is optional if fleet unchanged)
            if 'customers' not in cvrp_data:
                return jsonify({'error': 'Missing required field: customers'}), 400
            
            # Log the request type
            if 'vehicles' in cvrp_data:
                print(f"Received vehicles: {list(cvrp_data['vehicles'].keys())}")
                print(f"Request type: Full data (vehicles + customers)")
            else:
                print(f"Request type: Customers only (vehicle fleet unchanged)")
                
            print(f"Received CVRP request from frontend: {json.dumps(cvrp_data, indent=2)}")
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
            
def start_backend_server(host='localhost', port=8000, debug=False):
    print(f"Starting CVRP Backend Server on {host}:{port}")
    print("Available endpoints:")
    print("  GET  /api/solve-cvrp?action=poll     - Poll for pending requests")
    print("  POST /api/solve-cvrp?action=response - Submit solution")
    print("  POST /api/solve-cvrp                  - Submit new CVRP request")
    print("  GET  /api/solution/<request_id>      - Check solution status")
    app.run(host=host, port=port, debug=debug, use_reloader=False)

if __name__ == '__main__':
    start_backend_server(debug=True)
