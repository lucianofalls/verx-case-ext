import http from 'k6/http';
import { check, fail, sleep } from 'k6';

const rate = Number(__ENV.RATE || '50');
const duration = __ENV.DURATION || '5m';
const preAllocatedVUs = Number(__ENV.PREALLOCATED_VUS || String(Math.max(20, rate)));
const maxVUs = Number(__ENV.MAX_VUS || String(Math.max(100, rate * 4)));

const consolidationUrl = __ENV.CONSOLIDATION_URL || 'http://localhost:8082';
const transactionUrl = __ENV.TRANSACTION_URL || 'http://localhost:8081';
const token = __ENV.TOKEN || '';
const currency = __ENV.CURRENCY || 'BRL';

export const options = {
  scenarios: {
    daily_balance: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs,
      tags: { test_type: 'load' },
    },
  },
  thresholds: {
    'http_req_failed{test_type:load}': ['rate<0.05'],
    'http_req_duration{test_type:load}': ['p(95)<500'],
    'checks{test_type:load}': ['rate>0.95'],
    dropped_iterations: ['count==0'],
  },
};

function authHeaders(extra = {}) {
  return {
    Authorization: `Bearer ${token}`,
    ...extra,
  };
}

export function setup() {
  if (!token) {
    fail('TOKEN é obrigatório. Gere-o com ./scripts/get-local-token.sh');
  }

  const businessDate = __ENV.DATE || new Date().toISOString().slice(0, 10);
  const merchantId = __ENV.MERCHANT_ID || `PERF-${Date.now()}`;
  const shouldSeed = (__ENV.SEED || 'true').toLowerCase() !== 'false';

  if (shouldSeed) {
    const occurredAt = `${businessDate}T15:00:00Z`;
    const idempotencyKey = `k6-${merchantId}-${Date.now()}`;
    const payload = JSON.stringify({
      merchantId,
      type: 'CREDIT',
      amount: '1.0000',
      currency,
      description: 'k6 performance baseline',
      occurredAt,
    });

    const create = http.post(
      `${transactionUrl}/v1/transactions`,
      payload,
      {
        headers: authHeaders({
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey,
        }),
        tags: { test_type: 'setup', name: 'seed transaction' },
      },
    );

    if (create.status !== 201 && create.status !== 200) {
      fail(`Falha ao preparar lançamento de teste: HTTP ${create.status} - ${create.body}`);
    }
  }

  const balanceUrl =
    `${consolidationUrl}/v1/merchants/${merchantId}/daily-balances/${businessDate}?currency=${currency}`;

  const deadline = Date.now() + Number(__ENV.PROJECTION_TIMEOUT_MS || '30000');
  let lastStatus = 0;

  while (Date.now() < deadline) {
    const response = http.get(balanceUrl, {
      headers: authHeaders(),
      tags: { test_type: 'setup', name: 'wait projection' },
    });

    lastStatus = response.status;
    if (response.status === 200) {
      return { merchantId, businessDate, balanceUrl };
    }
    sleep(0.5);
  }

  fail(
    `Projeção não ficou disponível antes do teste de carga. Último HTTP: ${lastStatus}. ` +
    'O benchmark não será executado sobre um 404/403 inválido.',
  );
}

export default function (data) {
  const response = http.get(data.balanceUrl, {
    headers: authHeaders(),
    tags: { test_type: 'load', name: 'GET daily balance' },
  });

  check(
    response,
    {
      'daily balance returns 200': (r) => r.status === 200,
      'response is JSON': (r) =>
        (r.headers['Content-Type'] || '').toLowerCase().includes('application/json'),
    },
    { test_type: 'load' },
  );
}
