// Write path: hold a free seat, then pay with mock UPI. Every iteration uses a different seat.
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://127.0.0.1:7580';
const ITERATIONS = Number(__ENV.ITERATIONS || 1000);
const VUS = Number(__ENV.VUS || 20);
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  thresholds: { 'http_req_duration{name:hold}': ['p(95)<1000'], 'http_req_duration{name:pay}': ['p(95)<1000'], checks: ['rate>0.99'] },
  setupTimeout: '120s',
  scenarios: { book: { executor: 'shared-iterations', vus: VUS, iterations: ITERATIONS, maxDuration: '5m' } },
};

export function setup() {
  const tokens = [];
  for (let i = 0; i < VUS; i++) {
    const body = JSON.stringify({ fullName: `Load ${i}`, email: `load-${Date.now()}-${i}@example.com`,
      password: 'load-test-pass-1', phone: '9876543210' });
    tokens.push(http.post(`${BASE}/api/auth/register`, body, { headers: JSON_HEADERS }).json('accessToken'));
  }
  const seats = [];
  const pairs = [['DEL', 'BOM'], ['BOM', 'DEL'], ['BLR', 'DEL'], ['BOM', 'BLR'], ['MAA', 'HYD'], ['CCU', 'DEL']];
  for (let d = 2; d <= 6 && seats.length < ITERATIONS; d++) {
    const date = new Date(Date.now() + d * 86400000).toISOString().slice(0, 10);
    for (const [from, to] of pairs) {
      const flights = http.get(`${BASE}/api/flights/search?from=${from}&to=${to}&date=${date}&size=50`).json('content');
      for (const f of flights) {
        for (const s of http.get(`${BASE}/api/schedules/${f.scheduleId}/seats`).json()) {
          if (s.status === 'AVAILABLE' && s.seatClass === 'ECONOMY') seats.push([f.scheduleId, s.id]);
        }
      }
    }
  }
  if (seats.length < ITERATIONS) throw new Error(`only ${seats.length} free seats`);
  return { tokens, seats };
}

export default function (data) {
  const [scheduleId, seatId] = data.seats[exec.scenario.iterationInTest];
  const auth = { ...JSON_HEADERS, Authorization: `Bearer ${data.tokens[exec.vu.idInTest % data.tokens.length]}` };
  const hold = http.post(`${BASE}/api/bookings`, JSON.stringify({ scheduleId,
    passengers: [{ fullName: 'Load Test', age: 30, gender: 'FEMALE', seatId }] }),
    { headers: { ...auth, 'Idempotency-Key': `hold-${exec.scenario.iterationInTest}-${Date.now()}` }, tags: { name: 'hold' } });
  if (!check(hold, { 'hold 201': (r) => r.status === 201 })) return;
  const pay = http.post(`${BASE}/api/bookings/${hold.json('id')}/payments`,
    JSON.stringify({ method: 'UPI', upiId: 'load@okbank' }),
    { headers: { ...auth, 'Idempotency-Key': `pay-booking-${hold.json('id')}` }, tags: { name: 'pay' } });
  check(pay, { 'confirmed': (r) => r.status === 200 && r.json('bookingStatus') === 'CONFIRMED' });
}
