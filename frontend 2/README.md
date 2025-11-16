# CVRP Agent System - Python Frontend

A Python-based web frontend for monitoring and visualizing the CVRP agent system.

## Features

- **Agent Logs**: Load and display logs from different agents (MRA, DA1, DA2, etc.)
- **DA Movement Visualization**: Visualize delivery agent movements on an interactive plot
- **Test Case Management**: Load and view test case configurations

## Installation

1. Install Python dependencies:
```bash
pip install -r requirements.txt
```

## Running

Start the Flask server:
```bash
python app.py
```

The frontend will be available at `http://localhost:5000`

## Usage

### Agent Logs Tab
1. Select a log folder from the dropdown (folders are in `../logs/`)
2. Select an agent (MRA, DA1, DA2, etc.)
3. Click "Load Logs" to view the agent's conversation log

### DA Movement Tab
1. Select a log folder
2. Click "Load Movement" to visualize DA movements on a plot
3. Each DA's path is shown in a different color

### Test Cases Tab
1. View all available test cases in the `frontend 2` folder
2. Click on a test case card to view its details
3. Click "Refresh Test Cases" to reload the list

## API Endpoints

- `GET /api/log-folders` - Get list of log folders
- `GET /api/logs/<folder>/<agent>` - Get logs for specific agent
- `GET /api/logs/<folder>/agents` - Get list of agents in folder
- `GET /api/da-movements/<folder>` - Get DA movement data
- `GET /api/test-cases` - Get list of test cases
- `GET /api/test-cases/<filename>` - Get specific test case data

