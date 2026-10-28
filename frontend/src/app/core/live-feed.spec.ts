import { applyLiveEvent, LivePayment, statusOf } from './live-feed';
import { LiveEvent } from './models';

const ID = '7a1c2e9f-0000-4000-8000-000000000001';

const initiated: LiveEvent = {
  stage: 'INITIATED', paymentId: ID, occurredAt: '2026-03-01T09:00:00.000Z',
  debtorAccount: 'GB82PAYS10000000000001', creditorAccount: 'GB82PAYS10000000000002', amount: 120, currency: 'GBP',
};
const approved: LiveEvent = { stage: 'SCREENED', paymentId: ID, occurredAt: '2026-03-01T09:00:00.010Z', decision: 'APPROVED', riskScore: 0, reasons: [] };
const rejected: LiveEvent = { stage: 'SCREENED', paymentId: ID, occurredAt: '2026-03-01T09:00:00.010Z', decision: 'REJECTED', riskScore: 80, reasons: ['HIGH_RISK_JURISDICTION'] };
const settled: LiveEvent = { stage: 'SETTLED', paymentId: ID, occurredAt: '2026-03-01T09:00:00.045Z', outcome: 'SETTLED' };

describe('statusOf', () => {
  it('maps each stage to a payment status', () => {
    expect(statusOf(initiated)).toBe('PENDING_SCREENING');
    expect(statusOf(approved)).toBe('APPROVED');
    expect(statusOf(rejected)).toBe('REJECTED');
    expect(statusOf({ ...settled, outcome: 'FAILED' })).toBe('FAILED');
  });
});

describe('applyLiveEvent', () => {
  it('builds one row per payment and computes end-to-end latency', () => {
    let feed: LivePayment[] = [];
    for (const e of [initiated, approved, settled]) {
      feed = applyLiveEvent(feed, e);
    }
    expect(feed).toHaveLength(1);
    expect(feed[0].status).toBe('SETTLED');
    expect(feed[0].amount).toBe(120);
    expect(feed[0].latencyMs).toBe(45);
  });

  it('never moves a payment backwards when events arrive out of order', () => {
    // screened and settled are different topics, so settlement can be seen first
    let feed = applyLiveEvent([], initiated);
    feed = applyLiveEvent(feed, settled);
    feed = applyLiveEvent(feed, approved);
    expect(feed[0].status).toBe('SETTLED');
    expect(feed[0].debtorAccount).toBe('GB82PAYS10000000000001');
  });

  it('ignores updates for payments that already scrolled out of the feed', () => {
    const feed = applyLiveEvent([], settled);
    expect(feed).toEqual([]);
  });

  it('keeps rejection reasons and the risk score', () => {
    const feed = applyLiveEvent(applyLiveEvent([], initiated), rejected);
    expect(feed[0].status).toBe('REJECTED');
    expect(feed[0].riskScore).toBe(80);
    expect(feed[0].reasons).toEqual(['HIGH_RISK_JURISDICTION']);
  });

  it('is bounded and puts new payments first', () => {
    let feed: LivePayment[] = [];
    for (let i = 0; i < 5; i++) {
      feed = applyLiveEvent(feed, { ...initiated, paymentId: `p-${i}` }, 3);
    }
    expect(feed.map(p => p.paymentId)).toEqual(['p-4', 'p-3', 'p-2']);
  });

  it('does not mutate the previous feed', () => {
    const before = applyLiveEvent([], initiated);
    const snapshot = structuredClone(before);
    applyLiveEvent(before, approved);
    expect(before).toEqual(snapshot);
  });
});
