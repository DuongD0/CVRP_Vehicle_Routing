# Changelog

## [Unreleased] - 2025-11-13

### Added
- **Unserved Customer Tracking**: Solution results now include a list of customers that could not be served
  - Added `unservedCustomers` field to `SolutionResult` class
  - Unserved customers are tracked during solution extraction
  - Unserved customers are included in JSON output and console summary
  - Unserved customers include customer ID, name, coordinates, and demand

- **Time-Windowed VRP (TWVRP) Support**: Full implementation of time window constraints
  - Added optional `timeWindow` field to `CustomerConfig` in JSON configuration
  - Time windows are specified as `[earliest, latest]` in minutes from depot start
  - Automatic detection of time windows in configuration
  - Backward compatible with existing CVRP configurations (without time windows)
  - Time window constraints are enforced by OR-Tools solver
  - Vehicles can wait at customer locations if they arrive early
  - Customers with violated time windows may be left unserved

- **Documentation**: Comprehensive documentation added
  - `docs/CAPACITY_SHORTFALL_ALGORITHM.md`: Explains how the algorithm handles demand > capacity scenarios
  - `docs/TWVRP_IMPLEMENTATION.md`: Describes TWVRP implementation and usage
  - Both documents include examples, implementation details, and usage instructions

### Modified
- **SolutionResult**: Added `unservedCustomers` list to track customers that could not be served
- **VRPSolver Interface**: Updated to accept optional `timeWindows` parameter
- **ORToolsSolver**: 
  - Updated to handle time windows (TWVRP mode)
  - Tracks unserved customers during solution extraction
  - Automatically detects if time windows are present
  - Outputs unserved customers in solution summary
- **JsonConfigReader**: Added support for reading optional `timeWindow` field from JSON configuration
- **CustomerRequest**: Added optional `timeWindow` field
- **DepotProblemAssembler**: Extracts and passes time windows to solver
- **MasterRoutingAgent**: 
  - Passes time windows from config to customer requests
  - Updates unserved customers with proper coordinates and names
  - Logs unserved customer information
- **JsonResultLogger**: 
  - Includes unserved customers in JSON output
  - Displays unserved customers in console summary
  - Added `unservedCustomers` count to summary statistics
- **Main.java**: Updated output messages to reflect TWVRP support and unserved customer tracking

### Technical Details

#### Unserved Customer Tracking
- Unserved customers are identified by tracking which nodes are visited during route extraction
- Nodes that are not visited in any route are marked as unserved
- Unserved customers are populated with proper coordinates and names by MasterRoutingAgent
- Unserved customers are included in both JSON output and console summary

#### Time Window Implementation
- Time windows are optional in the configuration (backward compatible)
- If any customer has a time window, TWVRP mode is enabled
- Time dimension is added to OR-Tools routing model
- Time windows are enforced using `cumulVar(index).setRange(earliest, latest)`
- Transit time equals distance (1:1 ratio)
- Service time is assumed to be 0
- Vehicles can wait up to 30 time units at customer locations

#### Algorithm Behavior
- When demand > capacity, the solver uses disjunctions with penalties to prioritize customer service
- The algorithm maximizes items delivered (primary objective) and minimizes distance (secondary objective)
- Unserved customers are tracked and reported in the solution
- Time window constraints may cause additional customers to be left unserved

### Files Changed
- `src/main/java/project/General/SolutionResult.java`
- `src/main/java/project/Solver/VRPSolver.java`
- `src/main/java/project/Solver/ORToolsSolver.java`
- `src/main/java/project/Utils/JsonConfigReader.java`
- `src/main/java/project/General/CustomerRequest.java`
- `src/main/java/project/Agent/DepotProblemAssembler.java`
- `src/main/java/project/Agent/MasterRoutingAgent.java`
- `src/main/java/project/Utils/JsonResultLogger.java`
- `src/main/java/project/Main.java`

### Documentation Added
- `docs/CAPACITY_SHORTFALL_ALGORITHM.md`
- `docs/TWVRP_IMPLEMENTATION.md`
- `CHANGELOG.md`

### Testing
- All changes are backward compatible with existing CVRP configurations
- Time windows are optional and do not affect existing functionality
- Unserved customer tracking works for both CVRP and TWVRP modes
- No breaking changes to existing API

### Known Limitations
- Service time at customer locations is assumed to be 0
- Transit time equals distance (1:1 ratio) - no variable vehicle speeds
- Time windows are in minutes from depot start (time 0)
- Soft time windows are not supported (hard constraints only)

### Future Enhancements
- Variable service times at different customers
- Variable vehicle speeds
- Configurable time units
- Soft time windows with penalties
- Detailed reasons for time window violations
- Service time configuration in JSON

