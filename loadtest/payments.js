// Load test for the payment intake API.
//   docker compose up -d
//   k6 run loadtest/payments.js                    (default 200 req/s for 1 minute)
//   k6 run -e RATE=500 -e DURATION=2m loadtest/payments.js
import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8081';

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 200),
      timeUnit: '1s',
      duration: __ENV.DURATION || '1m',
      preAllocatedVUs: 50,
      maxVUs: 300,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<200'],
  },
};

const ACCOUNTS = [
  'GB82PAYS10000000000001',
  'GB82PAYS10000000000002',
  'GB82PAYS10000000000003',
];

export function setup() {
  const res = http.post(`${BASE}/auth/token?subject=k6`);
  return { token: res.json('access_token') };
}

export default function (data) {
  const debtor = ACCOUNTS[Math.floor(Math.random() * ACCOUNTS.length)];
  const creditor = ACCOUNTS.find((a) => a !== debtor);
  const body = JSON.stringify({
    debtorAccount: debtor,
    creditorAccount: creditor,
    amount: (Math.random() * 500 + 1).toFixed(2),
    currency: 'GBP',
    beneficiaryCountry: 'GB',
  });
  const res = http.post(`${BASE}/api/v1/payments`, body, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
      'Idempotency-Key': uuidv4(),
    },
  });
  check(res, { 'created': (r) => r.status === 201 });
}
