#!/usr/bin/env bash
# Fires the 200-payment LOAD_BURST scenario through the gateway, then reports per-stage latency
# (from event timestamps in the MongoDB audit trail) for the payments in that burst only.
#   ./loadtest/measure-burst.sh            (gateway on localhost:8085, stack from docker compose)
set -euo pipefail
GATEWAY="${GATEWAY:-http://localhost:8085}"

TOKEN=$(curl -sf -X POST "$GATEWAY/api/auth/demo" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
START=$(date -u +%Y-%m-%dT%H:%M:%S.000Z)
RUN=$(curl -sf -X POST "$GATEWAY/api/scenarios/LOAD_BURST" -H "Authorization: Bearer $TOKEN")
echo "Submitted: $(echo "$RUN" | grep -o '"submitted":[0-9]*'), refused by API: $(echo "$RUN" | grep -o '"rejectedByApi":[0-9]*')"
sleep "${SETTLE_WAIT:-15}"

docker compose exec -T mongodb mongosh --quiet paystream_audit --eval "
const since = new Date('$START');
const rows = db.audit_events.aggregate([
  { \$match: { occurredAt: { \$gte: since } } },
  { \$group: { _id: '\$paymentId', ev: { \$push: { t: '\$eventType', at: '\$occurredAt' } } } },
]).toArray().map(r => Object.fromEntries(r.ev.map(e => [e.t, e.at])));
const ms = (a, b) => new Date(b) - new Date(a);
const pct = (xs, q) => { const s = xs.slice().sort((a, b) => a - b); return s.length ? s[Math.min(s.length - 1, Math.ceil(q * s.length) - 1)] : 'n/a'; };
const report = (name, xs) => print(name.padEnd(28) + 'n=' + String(xs.length).padEnd(5) + ' p50=' + pct(xs, .5) + 'ms  p95=' + pct(xs, .95) + 'ms  p99=' + pct(xs, .99) + 'ms  max=' + pct(xs, 1) + 'ms');
const done = rows.filter(r => r.PaymentInitiated && r.PaymentScreened && r.PaymentSettled);
report('initiated -> screened', rows.filter(r => r.PaymentInitiated && r.PaymentScreened).map(r => ms(r.PaymentInitiated, r.PaymentScreened)));
report('screened  -> settled', done.map(r => ms(r.PaymentScreened, r.PaymentSettled)));
report('end-to-end (intake->ledger)', done.map(r => ms(r.PaymentInitiated, r.PaymentSettled)));
const first = Math.min(...rows.filter(r => r.PaymentInitiated).map(r => +new Date(r.PaymentInitiated)));
const last = Math.max(...done.map(r => +new Date(r.PaymentSettled)));
print('burst wall-clock: ' + (last - first) + 'ms for ' + done.length + ' settled payments');
"
