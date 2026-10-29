// Read path: direct search + multi-stop routes. Run with the rate limiter off
// (APP_RATE_LIMIT_SEARCH_ENABLED=false), otherwise you mostly measure 429s.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://127.0.0.1:7580';
const PAIRS = [['DEL', 'BOM'], ['BOM', 'DEL'], ['BLR', 'DEL'], ['BOM', 'BLR'], ['DEL', 'BLR'], ['CCU', 'MAA']];

export const options = {
  scenarios: {
    search: { executor: 'constant-vus', vus: 20, duration: '30s', exec: 'search' },
    routes: { executor: 'constant-vus', vus: 20, duration: '30s', exec: 'routes', startTime: '35s' },
  },
  // Loose limits; they're here mostly so the summary breaks numbers out per endpoint.
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{name:search}': ['p(95)<500'],
    'http_req_duration{name:routes}': ['p(95)<1000'],
    'http_reqs{name:search}': ['count>0'],
    'http_reqs{name:routes}': ['count>0'],
  },
};

function day() {
  const d = new Date(Date.now() + (1 + Math.floor(Math.random() * 5)) * 86400000);
  return d.toISOString().slice(0, 10);
}

export function search() {
  const [from, to] = PAIRS[Math.floor(Math.random() * PAIRS.length)];
  const r = http.get(`${BASE}/api/flights/search?from=${from}&to=${to}&date=${day()}`, { tags: { name: 'search' } });
  check(r, { '200': (x) => x.status === 200 });
}

export function routes() {
  const [from, to] = PAIRS[Math.floor(Math.random() * PAIRS.length)];
  const r = http.get(`${BASE}/api/flights/routes?from=${from}&to=${to}&date=${day()}&maxStops=2`, { tags: { name: 'routes' } });
  check(r, { '200': (x) => x.status === 200 });
}
