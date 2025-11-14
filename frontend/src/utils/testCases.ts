import { Customer, Vehicle } from '../types/cvrp';
import { testCaseData, TestCaseData } from './testCaseData';

export interface TestCase {
  name: string;
  fileName: string;
  description: string;
  category: 'Basic CVRP' | 'Time Window CVRP';
}

export const TEST_CASES: TestCase[] = [
  // Basic CVRP cases
  { name: 'Small Case', fileName: 'case_small.json', description: '5 customers, basic scenario', category: 'Basic CVRP' },
  { name: 'Capacity Shortfall', fileName: 'case_capacity_shortfall.json', description: 'Insufficient vehicle capacity', category: 'Basic CVRP' },
  { name: 'Many Customers', fileName: 'case_many_customers.json', description: 'Large number of customers', category: 'Basic CVRP' },
  { name: 'Random Seeded', fileName: 'case_random_seeded.json', description: 'Randomly generated case', category: 'Basic CVRP' },
  { name: 'Tight Distance', fileName: 'case_tight_distance.json', description: 'Limited vehicle range', category: 'Basic CVRP' },
  
  // Time Window CVRP cases
  { name: 'TW: Basic', fileName: 'case_twvrp_basic.json', description: 'Basic time windows', category: 'Time Window CVRP' },
  { name: 'TW: Capacity Shortfall', fileName: 'case_twvrp_capacity_shortfall.json', description: 'Time windows + capacity issues', category: 'Time Window CVRP' },
  { name: 'TW: Conflicting', fileName: 'case_twvrp_conflicting.json', description: 'Conflicting time constraints', category: 'Time Window CVRP' },
  { name: 'TW: Early/Late', fileName: 'case_twvrp_early_late.json', description: 'Early and late deliveries', category: 'Time Window CVRP' },
  { name: 'TW: Impossible', fileName: 'case_twvrp_impossible.json', description: 'Impossible time windows', category: 'Time Window CVRP' },
  { name: 'TW: Mixed', fileName: 'case_twvrp_mixed.json', description: 'Mixed window sizes', category: 'Time Window CVRP' },
  { name: 'TW: Overlapping', fileName: 'case_twvrp_overlapping.json', description: 'Overlapping time windows', category: 'Time Window CVRP' },
  { name: 'TW: Sequential', fileName: 'case_twvrp_sequential.json', description: 'Sequential delivery windows', category: 'Time Window CVRP' },
  { name: 'TW: Tight Windows', fileName: 'case_twvrp_tight_windows.json', description: 'Very narrow time windows', category: 'Time Window CVRP' },
  { name: 'TW: Wide Windows', fileName: 'case_twvrp_wide_windows.json', description: 'Flexible time windows', category: 'Time Window CVRP' },
];

/**
 * Transform coordinates from old system (0,0 depot) to new system (800,600 depot)
 * We'll scale the coordinates to fit nicely in our visualization area
 */
function transformCoordinates(oldX: number, oldY: number): { x: number; y: number } {
  const DEPOT_X = 800;
  const DEPOT_Y = 600;
  const SCALE_FACTOR = 10; // Scale factor to make coordinates visible
  
  return {
    x: DEPOT_X + oldX * SCALE_FACTOR,
    y: DEPOT_Y + oldY * SCALE_FACTOR,
  };
}

/**
 * Load and transform a test case
 */
export function loadTestCase(fileName: string): {
  customers: Customer[];
  vehicles: Vehicle[];
} {
  const data = testCaseData[fileName];
  
  if (!data) {
    throw new Error(`Test case not found: ${fileName}`);
  }
  
  // Transform vehicles
  const vehicles: Vehicle[] = data.vehicles.map((v, index) => ({
    id: index + 1,
    name: v.name,
    capacity: v.capacity,
    maxDistance: v.maxDistance,
  }));
  
  // Transform customers with coordinate transformation
  const customers: Customer[] = data.customers.map((c, index) => {
    const transformed = transformCoordinates(c.x, c.y);
    return {
      id: parseInt(c.id) || index + 1,
      name: `C${c.id}`,
      demand: c.demand,
      x: transformed.x,
      y: transformed.y,
      timeWindow: c.timeWindow,
    };
  });
  
  return { customers, vehicles };
}

/**
 * Get test case info by fileName
 */
export function getTestCaseInfo(fileName: string): TestCase | undefined {
  return TEST_CASES.find(tc => tc.fileName === fileName);
}
