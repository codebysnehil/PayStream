import { LiveEvent, PaymentStatus } from './models';

/** A payment as seen on the live feed, assembled from the events received so far. */
export interface LivePayment {
  paymentId: string;
  status: PaymentStatus;
  debtorAccount?: string;
  creditorAccount?: string;
  amount?: number;
  currency?: string;
  riskScore?: number;
  reasons: string[];
  initiatedAt?: string;
  updatedAt: string;
  latencyMs?: number;
}

const RANK: Record<PaymentStatus, number> = {
  PENDING_SCREENING: 0,
  APPROVED: 1,
  REJECTED: 2,
  SETTLED: 2,
  FAILED: 2,
};

export function statusOf(event: LiveEvent): PaymentStatus {
  switch (event.stage) {
    case 'INITIATED':
      return 'PENDING_SCREENING';
    case 'SCREENED':
      return event.decision === 'REJECTED' ? 'REJECTED' : 'APPROVED';
    case 'SETTLED':
      return event.outcome === 'FAILED' ? 'FAILED' : 'SETTLED';
  }
}

/**
 * Pure reducer: merges one event into the feed (newest first, bounded). Events for the same
 * payment can arrive in any order across topics, so the status only ever moves forward.
 */
export function applyLiveEvent(feed: readonly LivePayment[], event: LiveEvent, max = 50): LivePayment[] {
  const index = feed.findIndex(p => p.paymentId === event.paymentId);
  // Only intake creates a row. Later events for a payment that has already scrolled out of the
  // bounded feed would otherwise come back as empty rows during bursts.
  if (index < 0 && event.stage !== 'INITIATED') {
    return feed as LivePayment[];
  }
  const existing: LivePayment = index >= 0
    ? feed[index]
    : { paymentId: event.paymentId, status: 'PENDING_SCREENING', reasons: [], updatedAt: event.occurredAt };

  const incoming = statusOf(event);
  const merged: LivePayment = {
    ...existing,
    debtorAccount: existing.debtorAccount ?? event.debtorAccount,
    creditorAccount: existing.creditorAccount ?? event.creditorAccount,
    amount: existing.amount ?? event.amount,
    currency: existing.currency ?? event.currency,
    riskScore: event.riskScore ?? existing.riskScore,
    reasons: event.reasons?.length ? event.reasons : event.reason ? [event.reason] : existing.reasons,
    initiatedAt: event.stage === 'INITIATED' ? event.occurredAt : existing.initiatedAt,
    status: RANK[incoming] >= RANK[existing.status] ? incoming : existing.status,
    updatedAt: event.occurredAt,
  };
  if (merged.initiatedAt && (merged.status === 'SETTLED' || merged.status === 'FAILED' || merged.status === 'REJECTED')) {
    merged.latencyMs = Math.max(0, Date.parse(merged.updatedAt) - Date.parse(merged.initiatedAt));
  }

  if (index >= 0) {
    const next = feed.slice();
    next[index] = merged;
    return next;
  }
  return [merged, ...feed].slice(0, max);
}
