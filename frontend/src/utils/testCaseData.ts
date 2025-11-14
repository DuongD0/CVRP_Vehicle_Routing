// Test case data embedded directly in TypeScript
// This avoids JSON import issues in the build environment

export interface TestCaseData {
  depot: {
    name: string;
    x: number;
    y: number;
  };
  vehicles: Array<{
    name: string;
    capacity: number;
    maxDistance: number;
  }>;
  customers: Array<{
    id: string;
    demand: number;
    x: number;
    y: number;
    timeWindow?: [number, number];
  }>;
}

export const testCaseData: Record<string, TestCaseData> = {
  'case_small.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 50, maxDistance: 1000.0 },
      { name: 'DA2', capacity: 50, maxDistance: 1000.0 }
    ],
    customers: [
      { id: '1', demand: 10, x: 10.0, y: 10.0 },
      { id: '2', demand: 15, x: 20.0, y: 20.0 },
      { id: '3', demand: 12, x: 30.0, y: 10.0 },
      { id: '4', demand: 8, x: 15.0, y: 30.0 },
      { id: '5', demand: 5, x: 25.0, y: 25.0 }
    ]
  },

  'case_capacity_shortfall.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 30, maxDistance: 1000.0 },
      { name: 'DA2', capacity: 30, maxDistance: 1000.0 }
    ],
    customers: [
      { id: '1', demand: 25, x: 10.0, y: 10.0 },
      { id: '2', demand: 20, x: 20.0, y: 20.0 },
      { id: '3', demand: 18, x: 30.0, y: 10.0 },
      { id: '4', demand: 15, x: 15.0, y: 30.0 },
      { id: '5', demand: 12, x: 25.0, y: 25.0 },
      { id: '6', demand: 10, x: 35.0, y: 15.0 }
    ]
  },

  'case_many_customers.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 50, maxDistance: 2000.0 },
      { name: 'DA2', capacity: 50, maxDistance: 2000.0 },
      { name: 'DA3', capacity: 50, maxDistance: 2000.0 },
      { name: 'DA4', capacity: 50, maxDistance: 2000.0 },
      { name: 'DA5', capacity: 50, maxDistance: 2000.0 }
    ],
    customers: [
      { id: '1', demand: 8, x: 10.0, y: 10.0 },
      { id: '2', demand: 12, x: 20.0, y: 20.0 },
      { id: '3', demand: 10, x: 30.0, y: 10.0 },
      { id: '4', demand: 15, x: 15.0, y: 30.0 },
      { id: '5', demand: 8, x: 25.0, y: 25.0 },
      { id: '6', demand: 10, x: 35.0, y: 15.0 },
      { id: '7', demand: 12, x: 40.0, y: 40.0 },
      { id: '8', demand: 9, x: 50.0, y: 20.0 },
      { id: '9', demand: 11, x: 60.0, y: 30.0 },
      { id: '10', demand: 13, x: 70.0, y: 10.0 },
      { id: '11', demand: 7, x: 80.0, y: 40.0 },
      { id: '12', demand: 14, x: 90.0, y: 20.0 },
      { id: '13', demand: 9, x: 100.0, y: 30.0 },
      { id: '14', demand: 11, x: 110.0, y: 15.0 },
      { id: '15', demand: 10, x: 120.0, y: 25.0 },
      { id: '16', demand: 8, x: 130.0, y: 35.0 },
      { id: '17', demand: 12, x: 140.0, y: 20.0 },
      { id: '18', demand: 9, x: 150.0, y: 30.0 },
      { id: '19', demand: 13, x: 160.0, y: 10.0 },
      { id: '20', demand: 10, x: 170.0, y: 40.0 }
    ]
  },

  'case_random_seeded.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 40, maxDistance: 1500.0 },
      { name: 'DA2', capacity: 40, maxDistance: 1500.0 },
      { name: 'DA3', capacity: 40, maxDistance: 1500.0 }
    ],
    customers: [
      { id: '1', demand: 14, x: 23.5, y: 18.2 },
      { id: '2', demand: 11, x: 42.1, y: 31.7 },
      { id: '3', demand: 9, x: 12.8, y: 27.4 },
      { id: '4', demand: 16, x: 38.9, y: 14.3 },
      { id: '5', demand: 13, x: 51.2, y: 22.9 },
      { id: '6', demand: 8, x: 29.3, y: 35.6 },
      { id: '7', demand: 15, x: 17.6, y: 41.2 },
      { id: '8', demand: 10, x: 45.8, y: 28.5 },
      { id: '9', demand: 12, x: 33.4, y: 19.8 },
      { id: '10', demand: 7, x: 26.7, y: 44.1 }
    ]
  },

  'case_tight_distance.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 50, maxDistance: 300.0 },
      { name: 'DA2', capacity: 50, maxDistance: 300.0 },
      { name: 'DA3', capacity: 50, maxDistance: 300.0 }
    ],
    customers: [
      { id: '1', demand: 10, x: 10.0, y: 10.0 },
      { id: '2', demand: 12, x: 20.0, y: 20.0 },
      { id: '3', demand: 8, x: 30.0, y: 10.0 },
      { id: '4', demand: 15, x: 15.0, y: 30.0 },
      { id: '5', demand: 9, x: 25.0, y: 25.0 },
      { id: '6', demand: 11, x: 35.0, y: 15.0 },
      { id: '7', demand: 13, x: 40.0, y: 40.0 },
      { id: '8', demand: 10, x: 50.0, y: 20.0 }
    ]
  },

  'case_twvrp_basic.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 50, maxDistance: 1000.0 },
      { name: 'DA2', capacity: 50, maxDistance: 1000.0 }
    ],
    customers: [
      { id: '1', demand: 15, x: 10.0, y: 10.0, timeWindow: [0, 50] },
      { id: '2', demand: 20, x: 20.0, y: 20.0, timeWindow: [20, 70] },
      { id: '3', demand: 18, x: 30.0, y: 10.0, timeWindow: [40, 90] },
      { id: '4', demand: 12, x: 15.0, y: 30.0, timeWindow: [30, 80] },
      { id: '5', demand: 10, x: 25.0, y: 25.0, timeWindow: [50, 100] },
      { id: '6', demand: 8, x: 35.0, y: 15.0, timeWindow: [60, 110] }
    ]
  },

  'case_twvrp_capacity_shortfall.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 30, maxDistance: 1000.0 },
      { name: 'DA2', capacity: 30, maxDistance: 1000.0 }
    ],
    customers: [
      { id: '1', demand: 25, x: 10.0, y: 10.0, timeWindow: [0, 60] },
      { id: '2', demand: 22, x: 20.0, y: 20.0, timeWindow: [30, 90] },
      { id: '3', demand: 20, x: 30.0, y: 10.0, timeWindow: [60, 120] },
      { id: '4', demand: 18, x: 15.0, y: 30.0, timeWindow: [40, 100] }
    ]
  },

  'case_twvrp_conflicting.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 50, maxDistance: 1000.0 },
      { name: 'DA2', capacity: 50, maxDistance: 1000.0 }
    ],
    customers: [
      { id: '1', demand: 15, x: 10.0, y: 10.0, timeWindow: [0, 20] },
      { id: '2', demand: 12, x: 50.0, y: 50.0, timeWindow: [15, 35] },
      { id: '3', demand: 18, x: 100.0, y: 10.0, timeWindow: [30, 50] },
      { id: '4', demand: 14, x: 150.0, y: 50.0, timeWindow: [45, 65] }
    ]
  },

  'case_twvrp_early_late.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 50, maxDistance: 1500.0 },
      { name: 'DA2', capacity: 50, maxDistance: 1500.0 }
    ],
    customers: [
      { id: '1', demand: 10, x: 10.0, y: 10.0, timeWindow: [0, 30] },
      { id: '2', demand: 12, x: 20.0, y: 20.0, timeWindow: [5, 35] },
      { id: '3', demand: 15, x: 30.0, y: 10.0, timeWindow: [100, 130] },
      { id: '4', demand: 13, x: 15.0, y: 30.0, timeWindow: [110, 140] },
      { id: '5', demand: 11, x: 25.0, y: 25.0, timeWindow: [50, 80] }
    ]
  },

  'case_twvrp_impossible.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 50, maxDistance: 1000.0 }
    ],
    customers: [
      { id: '1', demand: 20, x: 10.0, y: 10.0, timeWindow: [0, 10] },
      { id: '2', demand: 20, x: 100.0, y: 100.0, timeWindow: [5, 15] },
      { id: '3', demand: 20, x: 200.0, y: 10.0, timeWindow: [10, 20] }
    ]
  },

  'case_twvrp_mixed.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 50, maxDistance: 1200.0 },
      { name: 'DA2', capacity: 50, maxDistance: 1200.0 }
    ],
    customers: [
      { id: '1', demand: 10, x: 10.0, y: 10.0, timeWindow: [0, 100] },
      { id: '2', demand: 15, x: 20.0, y: 20.0, timeWindow: [20, 40] },
      { id: '3', demand: 12, x: 30.0, y: 10.0, timeWindow: [0, 120] },
      { id: '4', demand: 18, x: 15.0, y: 30.0, timeWindow: [50, 60] },
      { id: '5', demand: 8, x: 25.0, y: 25.0, timeWindow: [0, 150] }
    ]
  },

  'case_twvrp_overlapping.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 50, maxDistance: 1000.0 },
      { name: 'DA2', capacity: 50, maxDistance: 1000.0 }
    ],
    customers: [
      { id: '1', demand: 15, x: 10.0, y: 10.0, timeWindow: [0, 60] },
      { id: '2', demand: 12, x: 20.0, y: 20.0, timeWindow: [20, 80] },
      { id: '3', demand: 18, x: 30.0, y: 10.0, timeWindow: [40, 100] },
      { id: '4', demand: 14, x: 15.0, y: 30.0, timeWindow: [30, 90] },
      { id: '5', demand: 10, x: 25.0, y: 25.0, timeWindow: [50, 110] }
    ]
  },

  'case_twvrp_sequential.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 50, maxDistance: 1000.0 }
    ],
    customers: [
      { id: '1', demand: 10, x: 10.0, y: 10.0, timeWindow: [0, 20] },
      { id: '2', demand: 12, x: 20.0, y: 20.0, timeWindow: [25, 45] },
      { id: '3', demand: 15, x: 30.0, y: 10.0, timeWindow: [50, 70] },
      { id: '4', demand: 8, x: 15.0, y: 30.0, timeWindow: [75, 95] }
    ]
  },

  'case_twvrp_tight_windows.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 50, maxDistance: 1000.0 },
      { name: 'DA2', capacity: 50, maxDistance: 1000.0 }
    ],
    customers: [
      { id: '1', demand: 15, x: 10.0, y: 10.0, timeWindow: [10, 20] },
      { id: '2', demand: 12, x: 20.0, y: 20.0, timeWindow: [30, 40] },
      { id: '3', demand: 18, x: 30.0, y: 10.0, timeWindow: [50, 60] },
      { id: '4', demand: 14, x: 15.0, y: 30.0, timeWindow: [70, 80] },
      { id: '5', demand: 10, x: 25.0, y: 25.0, timeWindow: [90, 100] }
    ]
  },

  'case_twvrp_wide_windows.json': {
    depot: { name: 'mra', x: 0.0, y: 0.0 },
    vehicles: [
      { name: 'DA1', capacity: 50, maxDistance: 1500.0 },
      { name: 'DA2', capacity: 50, maxDistance: 1500.0 }
    ],
    customers: [
      { id: '1', demand: 15, x: 10.0, y: 10.0, timeWindow: [0, 200] },
      { id: '2', demand: 12, x: 20.0, y: 20.0, timeWindow: [0, 200] },
      { id: '3', demand: 18, x: 30.0, y: 10.0, timeWindow: [0, 200] },
      { id: '4', demand: 14, x: 15.0, y: 30.0, timeWindow: [0, 200] },
      { id: '5', demand: 10, x: 25.0, y: 25.0, timeWindow: [0, 200] },
      { id: '6', demand: 8, x: 35.0, y: 15.0, timeWindow: [0, 200] }
    ]
  }
};
