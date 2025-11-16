#!/usr/bin/env python3
"""
Python Frontend for CVRP Agent System
Features:
- Load and display agent logs (real-time during execution)
- Visualize DA movement when executing routes
- Load test cases (scenarios) and visualize them
- Interactive map to pick nodes with demand
- Integration with backend API (localhost:8000)
"""

from flask import Flask, render_template, jsonify, request
import os
import json
import re
import requests
from pathlib import Path
from datetime import datetime
from threading import Thread
import time

app = Flask(__name__)
app.config['JSON_AS_ASCII'] = False

# Base directories
BASE_DIR = Path(__file__).parent
LOGS_DIR = BASE_DIR.parent / "logs"
TEST_CASES_DIR = BASE_DIR

# Backend API endpoint
BACKEND_API = "http://localhost:8000"


@app.route('/')
def index():
    """Main page"""
    return render_template('index.html')


@app.route('/api/log-folders')
def get_log_folders():
    """Get list of available log folders"""
    if not LOGS_DIR.exists():
        return jsonify([])
    
    folders = []
    for folder in sorted(LOGS_DIR.iterdir(), reverse=True):
        if folder.is_dir():
            folders.append({
                'name': folder.name,
                'path': str(folder.relative_to(LOGS_DIR))
            })
    
    return jsonify(folders)


@app.route('/api/logs/<folder_name>/<agent_name>')
def get_agent_logs(folder_name, agent_name):
    """Get logs for a specific agent from a log folder"""
    log_file = LOGS_DIR / folder_name / f"{agent_name}_conversations.log"
    
    if not log_file.exists():
        return jsonify({'entries': [], 'error': 'Log file not found'})
    
    entries = []
    try:
        with open(log_file, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                
                # Parse log entry: [timestamp] content
                match = re.match(r'\[([^\]]+)\]\s*(.+)', line)
                if match:
                    timestamp_str, message = match.groups()
                    entries.append({
                        'timestamp': timestamp_str,
                        'message': message,
                        'level': _classify_log_level(message)
                    })
                else:
                    # Line without timestamp (continuation)
                    if entries:
                        entries[-1]['message'] += '\n' + line
    
    except Exception as e:
        return jsonify({'entries': [], 'error': str(e)})
    
    return jsonify({'entries': entries})


@app.route('/api/logs/<folder_name>/agents')
def get_agents_in_folder(folder_name):
    """Get list of agents that have logs in a folder"""
    folder_path = LOGS_DIR / folder_name
    
    if not folder_path.exists():
        return jsonify([])
    
    agents = []
    for log_file in folder_path.glob("*_conversations.log"):
        agent_name = log_file.stem.replace("_conversations", "")
        agents.append(agent_name)
    
    return jsonify(sorted(agents))


@app.route('/api/test-cases')
def get_test_cases():
    """Get list of available test cases"""
    test_cases = []
    
    for json_file in TEST_CASES_DIR.glob("case_*.json"):
        try:
            with open(json_file, 'r') as f:
                data = json.load(f)
                test_cases.append({
                    'filename': json_file.name,
                    'name': json_file.stem.replace('_', ' ').title(),
                    'depot': data.get('depot', {}),
                    'vehicles_count': len(data.get('vehicles', [])),
                    'customers_count': len(data.get('customers', []))
                })
        except Exception as e:
            print(f"Error reading {json_file}: {e}")
    
    return jsonify(sorted(test_cases, key=lambda x: x['filename']))


@app.route('/api/test-cases/<filename>')
def get_test_case(filename):
    """Get full test case data"""
    test_file = TEST_CASES_DIR / filename
    
    if not test_file.exists():
        return jsonify({'error': 'Test case not found'}), 404
    
    try:
        with open(test_file, 'r') as f:
            data = json.load(f)
        return jsonify(data)
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/da-movements/<folder_name>')
def get_da_movements(folder_name):
    """Extract DA movement information from logs with detailed tracking"""
    folder_path = LOGS_DIR / folder_name
    
    if not folder_path.exists():
        return jsonify({'movements': {}, 'routes': {}, 'queue_status': {}})
    
    movements = {}
    routes = {}
    queue_status = {}
    
    # Find all DA log files
    for log_file in folder_path.glob("DA-*_conversations.log"):
        agent_name = log_file.stem.replace("_conversations", "")
        da_name = agent_name.replace("DA-", "")
        
        positions = []
        route_info = []
        queue_size = 0
        current_route = None
        
        try:
            with open(log_file, 'r', encoding='utf-8') as f:
                content = f.read()
                lines = content.split('\n')
                
                for line in lines:
                    # Track route queue
                    if 'added to queue' in line.lower():
                        queue_match = re.search(r'Queue size:\s*(\d+)', line)
                        if queue_match:
                            queue_size = int(queue_match.group(1))
                    
                    if 'Processing route from queue' in line:
                        route_match = re.search(r'route from queue:\s*([^\s]+)', line)
                        if route_match:
                            current_route = route_match.group(1)
                        queue_match = re.search(r'Remaining routes in queue:\s*(\d+)', line)
                        if queue_match:
                            queue_size = int(queue_match.group(1))
                    
                    # Track route assignments
                    if 'ROUTE_ASSIGNMENT' in line or 'Route' in line and 'ACCEPTED' in line:
                        route_id_match = re.search(r'Route\s+(\d+|[^\s]+)', line)
                        if route_id_match:
                            route_id = route_id_match.group(1)
                            route_info.append({
                                'route_id': route_id,
                                'status': 'queued' if 'added to queue' in line.lower() else 'executing',
                                'timestamp': re.match(r'\[([^\]]+)\]', line).group(1) if re.match(r'\[([^\]]+)\]', line) else None
                            })
                    
                    # Track position updates
                    position_keywords = ['ARRIVED at customer', 'RETURNED to depot', 'Moving to', 'Starting route', 'at (', 'Position']
                    if any(keyword in line for keyword in position_keywords):
                        pos_match = re.search(r'\(([0-9.]+),\s*([0-9.]+)\)', line)
                        if pos_match:
                            x, y = float(pos_match.group(1)), float(pos_match.group(2))
                            
                            time_match = re.match(r'\[([^\]]+)\]', line)
                            timestamp = time_match.group(1) if time_match else None
                            
                            if 'ARRIVED at customer' in line:
                                event_type = 'arrival'
                                customer_match = re.search(r'customer\s+([^\s(]+)', line, re.IGNORECASE)
                                customer = customer_match.group(1) if customer_match else None
                            elif 'RETURNED to depot' in line:
                                event_type = 'depot'
                                customer = None
                            elif 'Starting route' in line:
                                event_type = 'route_start'
                                customer = None
                            else:
                                event_type = 'moving'
                                customer = None
                            
                            positions.append({
                                'timestamp': timestamp,
                                'x': x,
                                'y': y,
                                'event': event_type,
                                'customer': customer
                            })
        except Exception as e:
            print(f"Error parsing {log_file}: {e}")
        
        if positions:
            movements[da_name] = positions
        if route_info:
            routes[da_name] = route_info
        queue_status[da_name] = {
            'queue_size': queue_size,
            'current_route': current_route
        }
    
    return jsonify({
        'movements': movements,
        'routes': routes,
        'queue_status': queue_status
    })


@app.route('/api/agent-communication/<folder_name>')
def get_agent_communication(folder_name):
    """Extract agent communication flow from logs"""
    folder_path = LOGS_DIR / folder_name
    
    if not folder_path.exists():
        return jsonify({'communications': []})
    
    communications = []
    
    # Read MRA log for communication patterns
    mra_log = folder_path / "MRA_conversations.log"
    if mra_log.exists():
        try:
            with open(mra_log, 'r', encoding='utf-8') as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    
                    # Extract communication events
                    if '>>> SENT MESSAGE' in line or '<<< RECEIVED MESSAGE' in line:
                        comm = {
                            'type': 'sent' if '>>>' in line else 'received',
                            'timestamp': None,
                            'from': None,
                            'to': None,
                            'content': None,
                            'conversation_id': None
                        }
                        
                        time_match = re.match(r'\[([^\]]+)\]', line)
                        if time_match:
                            comm['timestamp'] = time_match.group(1)
                        
                        # Read next few lines to get message details
                        lines_to_read = 10
                        continue
                    
                    # Extract vehicle info queries
                    if 'QUERY_VEHICLE_INFO' in line or 'Vehicle info query' in line:
                        communications.append({
                            'type': 'query',
                            'message': 'MRA queries DA for vehicle info',
                            'timestamp': re.match(r'\[([^\]]+)\]', line).group(1) if re.match(r'\[([^\]]+)\]', line) else None
                        })
                    
                    # Extract route assignments
                    if 'Route assignment' in line or 'ROUTE_ASSIGNMENT' in line:
                        communications.append({
                            'type': 'route_assignment',
                            'message': 'MRA assigns route to DA',
                            'timestamp': re.match(r'\[([^\]]+)\]', line).group(1) if re.match(r'\[([^\]]+)\]', line) else None
                        })
                    
                    # Extract route acceptance/rejection
                    if 'ROUTE_ACCEPTED' in line or 'ROUTE_REJECTED' in line:
                        communications.append({
                            'type': 'route_response',
                            'message': line,
                            'timestamp': re.match(r'\[([^\]]+)\]', line).group(1) if re.match(r'\[([^\]]+)\]', line) else None
                        })
        except Exception as e:
            print(f"Error parsing MRA log: {e}")
    
    return jsonify({'communications': communications})


@app.route('/api/system-state/<folder_name>')
def get_system_state(folder_name):
    """Get overall system state from logs"""
    folder_path = LOGS_DIR / folder_name
    
    if not folder_path.exists():
        return jsonify({'state': {}})
    
    state = {
        'mra': {
            'status': 'unknown',
            'vehicles_queried': 0,
            'routes_assigned': 0,
            'unserved_customers': 0
        },
        'das': {}
    }
    
    # Read MRA log
    mra_log = folder_path / "MRA_conversations.log"
    if mra_log.exists():
        try:
            with open(mra_log, 'r', encoding='utf-8') as f:
                content = f.read()
                
                # Count vehicle queries
                vehicle_queries = len(re.findall(r'Querying DA:', content))
                state['mra']['vehicles_queried'] = vehicle_queries
                
                # Count route assignments
                route_assignments = len(re.findall(r'Assigning route', content))
                state['mra']['routes_assigned'] = route_assignments
                
                # Find unserved customers
                unserved_match = re.search(r'Unserved customers:\s*(\d+)', content)
                if unserved_match:
                    state['mra']['unserved_customers'] = int(unserved_match.group(1))
                
                if 'Solution found' in content:
                    state['mra']['status'] = 'solved'
                elif 'Solving' in content:
                    state['mra']['status'] = 'solving'
        except Exception as e:
            print(f"Error reading MRA log: {e}")
    
    # Read DA logs
    for log_file in folder_path.glob("DA-*_conversations.log"):
        agent_name = log_file.stem.replace("_conversations", "")
        da_name = agent_name.replace("DA-", "")
        
        da_state = {
            'status': 'idle',
            'queue_size': 0,
            'current_route': None,
            'routes_completed': 0
        }
        
        try:
            with open(log_file, 'r', encoding='utf-8') as f:
                content = f.read()
                
                # Count completed routes
                completed = len(re.findall(r'RETURNED to depot.*Route.*completed', content))
                da_state['routes_completed'] = completed
                
                # Find queue size
                queue_match = re.search(r'Queue size:\s*(\d+)', content)
                if queue_match:
                    da_state['queue_size'] = int(queue_match.group(1))
                
                # Find current route
                current_route_match = re.search(r'Processing.*route from queue:\s*([^\s]+)', content)
                if current_route_match:
                    da_state['current_route'] = current_route_match.group(1)
                    da_state['status'] = 'executing'
                elif da_state['queue_size'] > 0:
                    da_state['status'] = 'queued'
                elif 'RETURNED to depot' in content.split('\n')[-50:]:  # Check recent lines
                    da_state['status'] = 'idle'
        except Exception as e:
            print(f"Error reading DA log {log_file}: {e}")
        
        state['das'][da_name] = da_state
    
    return jsonify({'state': state})


@app.route('/api/submit-request', methods=['POST'])
def submit_request():
    """Submit CVRP request to backend API"""
    try:
        data = request.get_json()
        
        # Submit to backend
        response = requests.post(
            f"{BACKEND_API}/api/solve-cvrp",
            json=data,
            headers={'Content-Type': 'application/json'},
            timeout=10
        )
        
        if response.status_code == 202:
            return jsonify(response.json()), 202
        else:
            return jsonify({'error': response.text}), response.status_code
            
    except requests.exceptions.ConnectionError:
        return jsonify({'error': 'Cannot connect to backend API. Make sure backend is running on localhost:8000'}), 503
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/solution/<request_id>')
def get_solution(request_id):
    """Get solution status from backend API"""
    try:
        response = requests.get(
            f"{BACKEND_API}/api/solution/{request_id}",
            timeout=10
        )
        
        if response.status_code == 200:
            return jsonify(response.json()), 200
        else:
            return jsonify({'error': response.text}), response.status_code
            
    except requests.exceptions.ConnectionError:
        return jsonify({'error': 'Cannot connect to backend API'}), 503
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/latest-logs')
def get_latest_logs():
    """Get latest log folder and agents for real-time monitoring"""
    if not LOGS_DIR.exists():
        return jsonify({'folder': None, 'agents': []})
    
    # Get most recent folder
    folders = sorted([f for f in LOGS_DIR.iterdir() if f.is_dir()], reverse=True)
    if not folders:
        return jsonify({'folder': None, 'agents': []})
    
    latest_folder = folders[0]
    agents = []
    
    for log_file in latest_folder.glob("*_conversations.log"):
        agent_name = log_file.stem.replace("_conversations", "")
        agents.append(agent_name)
    
    return jsonify({
        'folder': latest_folder.name,
        'agents': sorted(agents)
    })


def _classify_log_level(message):
    """Classify log level based on message content"""
    message_upper = message.upper()
    if 'ERROR' in message_upper or 'FAILED' in message_upper:
        return 'error'
    elif 'WARNING' in message_upper or 'WARN' in message_upper:
        return 'warning'
    elif 'EVENT' in message_upper or '***' in message:
        return 'event'
    elif '>>>' in message or '<<<' in message:
        return 'message'
    else:
        return 'info'


if __name__ == '__main__':
    print("Starting CVRP Python Frontend...")
    print(f"Logs directory: {LOGS_DIR}")
    print(f"Test cases directory: {TEST_CASES_DIR}")
    app.run(host='0.0.0.0', port=5000, debug=True)

