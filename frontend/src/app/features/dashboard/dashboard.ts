import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';

import { AccountDirectory } from '../../core/account-directory.service';
import { LiveService } from '../../core/live.service';
import { ReasonPipe, ShortIdPipe } from '../../shared/pipes';
import { StatusChip } from '../../shared/status-chip';
import { ScenarioLauncher } from './scenario-launcher';
import { ThroughputChart } from './throughput-chart';

@Component({
  selector: 'ps-dashboard',
  imports: [
    DatePipe, DecimalPipe, PercentPipe, MatIconModule, MatTooltipModule,
    StatusChip, ShortIdPipe, ReasonPipe, ScenarioLauncher, ThroughputChart,
  ],
  styleUrl: './dashboard.scss',
  template: `
    <div class="page">
      <div class="page-header">
        <div>
          <div class="eyebrow">
            <span class="live-dot"></span>
            Live from Kafka{{ stats() ? ' · since ' + (stats()!.since | date: 'HH:mm:ss') : '' }}
          </div>
          <h1>Overview</h1>
          <p>Every figure is computed from the payment event stream as it happens.</p>
        </div>
      </div>

      <section class="card kpi-strip" aria-label="Key indicators">
        <div class="kpi">
          <div class="label">Payments initiated</div>
          <div class="value">{{ stats()?.initiated ?? 0 | number }}</div>
          <div class="hint">{{ currentRate() }}/s right now</div>
        </div>
        <div class="kpi">
          <div class="label">Fraud approval rate</div>
          <div class="value">{{ approvalRate() === null ? '–' : (approvalRate()! | percent: '1.1-1') }}</div>
          <div class="hint">{{ stats()?.rejected ?? 0 | number }} rejected by rules</div>
        </div>
        <div class="kpi">
          <div class="label">Settlement success</div>
          <div class="value">{{ settlementRate() === null ? '–' : (settlementRate()! | percent: '1.1-1') }}</div>
          <div class="hint">{{ stats()?.failed ?? 0 | number }} failed in the ledger</div>
        </div>
        <div class="kpi" matTooltip="Initiated event → settled event, measured on event timestamps">
          <div class="label">End-to-end latency <span class="code">p95</span></div>
          <div class="value">{{ formatMs(stats()?.p95LatencyMs) }}</div>
          <div class="hint">p50 {{ formatMs(stats()?.p50LatencyMs) }} · intake to settlement</div>
        </div>
      </section>

      <section class="grid grid-2 gap-top">
        <div class="card">
          <div class="card-header">
            <div>
              <h2>Throughput</h2>
              <p class="sub">Payments initiated per second · last 60 seconds</p>
            </div>
            <div class="peak"><span class="muted">Peak</span> <strong>{{ peak() }}</strong><span class="muted">/s</span></div>
          </div>
          <ps-throughput-chart [series]="stats()?.throughputLast60s ?? []" />
        </div>
        <div class="card">
          <h2>Stopped payments</h2>
          <p class="sub">Fraud rule hits and ledger failures, by reason</p>
          @for (r of reasons(); track r.code) {
            <div class="reason">
              <div class="row">
                <span class="src" [class.ledger]="r.source === 'ledger'">{{ r.source === 'ledger' ? 'Ledger' : 'Fraud' }}</span>
                <span>{{ r.code | reason }}</span><span class="spacer"></span><strong class="mono">{{ r.count }}</strong>
              </div>
              <div class="bar"><span [style.width.%]="r.share" [class.ledger]="r.source === 'ledger'"></span></div>
            </div>
          } @empty {
            <div class="empty-state">
              <mat-icon>verified_user</mat-icon>
              <span>Nothing stopped yet. Run the <strong>high-risk</strong> or <strong>structuring</strong> scenario.</span>
            </div>
          }
        </div>
      </section>

      <section class="grid grid-2 gap-top">
        <div class="card flush feed">
          <div class="card-head">
            <h2>Live payments</h2>
            <p class="sub">Rows update in place as screening and settlement events arrive</p>
          </div>
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Payment</th><th>From / to</th><th class="num">Amount</th><th>Status</th>
                  <th class="num">Risk</th><th>Reason</th><th class="num">Latency</th>
                </tr>
              </thead>
              <tbody>
                @for (p of live.feed(); track p.paymentId) {
                  <tr class="clickable fresh" (click)="open(p.paymentId)" tabindex="0" (keydown.enter)="open(p.paymentId)">
                    <td class="mono muted">{{ p.paymentId | shortId }}</td>
                    <td class="route"><span>{{ accounts.holder(p.debtorAccount) }}</span><span class="muted">→ {{ accounts.holder(p.creditorAccount) }}</span></td>
                    <td class="num amount">
                      @if (p.amount != null) { {{ p.amount | number: '1.2-2' }}<span class="ccy">{{ p.currency }}</span> } @else { – }
                    </td>
                    <td><ps-status-chip [status]="p.status" /></td>
                    <td class="num"><span class="risk" [class.high]="(p.riskScore ?? 0) >= 70" [class.mid]="(p.riskScore ?? 0) >= 30 && (p.riskScore ?? 0) < 70">{{ p.riskScore ?? '–' }}</span></td>
                    <td>
                      @for (r of p.reasons; track r) { <span class="tag" [class.bad]="p.status === 'REJECTED' || p.status === 'FAILED'">{{ r | reason }}</span> }
                    </td>
                    <td class="num mono muted">{{ p.latencyMs != null ? formatMs(p.latencyMs) : '' }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="7" class="empty">No payments yet. Run a scenario to start the stream.</td></tr>
                }
              </tbody>
            </table>
          </div>
        </div>
        <ps-scenario-launcher />
      </section>
    </div>
  `,
})
export class Dashboard {
  protected readonly live = inject(LiveService);
  protected readonly accounts = inject(AccountDirectory);
  private readonly router = inject(Router);

  protected readonly stats = this.live.stats;

  protected readonly currentRate = computed(() => {
    const recent = (this.stats()?.throughputLast60s ?? []).slice(-5);
    return recent.length ? Math.round(recent.reduce((a, b) => a + b, 0) / recent.length) : 0;
  });

  protected readonly peak = computed(() => Math.max(0, ...(this.stats()?.throughputLast60s ?? [])));

  protected readonly approvalRate = computed(() => {
    const s = this.stats();
    const screened = s ? s.approved + s.rejected : 0;
    return screened ? s!.approved / screened : null;
  });

  protected readonly settlementRate = computed(() => {
    const s = this.stats();
    const done = s ? s.settled + s.failed : 0;
    return done ? s!.settled / done : null;
  });

  protected readonly reasons = computed(() => {
    const s = this.stats();
    if (!s) {
      return [];
    }
    const all = [
      ...Object.entries(s.rejectionReasons).map(([code, count]) => ({ code, count, source: 'fraud' as const })),
      ...Object.entries(s.failureReasons).map(([code, count]) => ({ code, count, source: 'ledger' as const })),
    ].sort((a, b) => b.count - a.count);
    const max = all[0]?.count ?? 1;
    return all.map(r => ({ ...r, share: (r.count / max) * 100 }));
  });

  protected formatMs(ms: number | null | undefined): string {
    if (ms == null) {
      return '–';
    }
    return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
  }

  protected open(id: string): void {
    void this.router.navigate(['/payments', id]);
  }
}
