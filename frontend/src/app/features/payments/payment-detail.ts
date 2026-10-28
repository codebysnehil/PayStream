import { CurrencyPipe, DatePipe, JsonPipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { debounceTime, filter, forkJoin } from 'rxjs';

import { AccountDirectory } from '../../core/account-directory.service';
import { ApiService } from '../../core/api.service';
import { LiveService } from '../../core/live.service';
import { AuditEvent, Payment } from '../../core/models';
import { MaskIbanPipe, ReasonPipe } from '../../shared/pipes';
import { StatusChip } from '../../shared/status-chip';

const STEPS: Record<AuditEvent['eventType'], { title: string; service: string; icon: string }> = {
  PaymentInitiated: { title: 'Payment accepted', service: 'payment-service → outbox → Kafka', icon: 'input' },
  PaymentScreened: { title: 'Fraud screening', service: 'fraud-service (rule engine)', icon: 'policy' },
  PaymentSettled: { title: 'Ledger settlement', service: 'ledger-service (double-entry)', icon: 'account_balance' },
};

@Component({
  selector: 'ps-payment-detail',
  imports: [CurrencyPipe, DatePipe, JsonPipe, MatButtonModule, MatIconModule, RouterLink, StatusChip, MaskIbanPipe, ReasonPipe],
  styleUrl: './payment-detail.scss',
  template: `
    <div class="page">
      <a routerLink="/payments" class="back"><mat-icon>arrow_back</mat-icon>All payments</a>

      @if (payment(); as p) {
        <div class="page-header">
          <div>
            <h1>{{ p.amount | currency: p.currency }} <ps-status-chip [status]="p.status" /></h1>
            <p class="mono">{{ p.id }}</p>
          </div>
        </div>

        <section class="grid grid-2">
          <div class="card">
            <h2>Journey across services</h2>
            <p class="sub">Read from the audit trail in MongoDB. Each step is a Kafka event with its partition and offset.</p>
            <ol class="timeline">
              @for (e of journey(); track e.eventId; let first = $first) {
                <li [class]="tone(e)">
                  <span class="node"><mat-icon>{{ step(e).icon }}</mat-icon></span>
                  <div class="body">
                    <div class="row">
                      <strong>{{ step(e).title }}</strong>
                      <span class="spacer"></span>
                      <span class="mono muted">{{ e.occurredAt | date: 'HH:mm:ss.SSS' }}</span>
                      @if (!first) { <span class="delta mono">+{{ delta(e) }} ms</span> }
                    </div>
                    <div class="muted small">{{ step(e).service }}</div>
                    <div class="facts">
                      <span class="mono">{{ e.topic }} · partition {{ e.partition }} · offset {{ e.offset }}</span>
                      @if (e.eventType === 'PaymentScreened') {
                        <span>Decision <strong>{{ e.payload['decision'] }}</strong> · risk score <strong>{{ e.payload['riskScore'] }}</strong></span>
                      }
                      @if (e.eventType === 'PaymentSettled') {
                        <span>Outcome <strong>{{ e.payload['outcome'] }}</strong></span>
                      }
                      @for (r of reasons(e); track r) { <span class="tag">{{ r | reason }}</span> }
                    </div>
                    <details>
                      <summary>Event payload</summary>
                      <pre>{{ e.payload | json }}</pre>
                    </details>
                  </div>
                </li>
              } @empty {
                <li class="pending"><span class="node"><mat-icon>hourglass_top</mat-icon></span><div class="body">Waiting for the first event…</div></li>
              }
              @if (!terminal() && journey().length) {
                <li class="pending"><span class="node"><mat-icon>hourglass_top</mat-icon></span><div class="body muted">Next step in progress…</div></li>
              }
            </ol>
          </div>

          <div class="card facts-card">
            <h2>Details</h2>
            <dl>
              <dt>From</dt><dd>{{ accounts.holder(p.debtorAccount) }}<br /><span class="mono muted">{{ p.debtorAccount | maskIban }}</span></dd>
              <dt>To</dt><dd>{{ accounts.holder(p.creditorAccount) }}<br /><span class="mono muted">{{ p.creditorAccount | maskIban }}</span></dd>
              <dt>Beneficiary country</dt><dd>{{ p.beneficiaryCountry }}</dd>
              <dt>Risk score</dt>
              <dd>
                @if (p.riskScore != null) {
                  <div class="risk"><span [style.width.%]="p.riskScore" [class.high]="p.riskScore >= 70"></span></div>
                  {{ p.riskScore }} / 100 <span class="muted">(rejects at 70)</span>
                } @else { — }
              </dd>
              <dt>End-to-end</dt><dd>{{ totalMs() != null ? totalMs() + ' ms' : '—' }}</dd>
              <dt>Created</dt><dd>{{ p.createdAt | date: 'medium' }}</dd>
              <dt>Last update</dt><dd>{{ p.updatedAt | date: 'medium' }}</dd>
            </dl>
          </div>
        </section>
      } @else if (notFound()) {
        <div class="card empty">Payment not found.</div>
      } @else {
        <div class="card empty">Loading…</div>
      }
    </div>
  `,
})
export class PaymentDetail {
  readonly id = input.required<string>();

  private readonly api = inject(ApiService);
  protected readonly accounts = inject(AccountDirectory);

  protected readonly payment = signal<Payment | null>(null);
  protected readonly journey = signal<AuditEvent[]>([]);
  protected readonly notFound = signal(false);

  protected readonly terminal = computed(() => {
    const s = this.payment()?.status;
    return s === 'SETTLED' || s === 'FAILED' || s === 'REJECTED';
  });

  protected readonly totalMs = computed(() => {
    const j = this.journey();
    return j.length > 1 && this.terminal()
      ? Date.parse(j[j.length - 1].occurredAt) - Date.parse(j[0].occurredAt)
      : null;
  });

  constructor() {
    effect(() => this.load(this.id()));
    inject(LiveService).events$
      .pipe(filter(e => e.paymentId === this.id()), debounceTime(300), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.load(this.id()));
  }

  protected step(e: AuditEvent) {
    return STEPS[e.eventType];
  }

  protected delta(e: AuditEvent): number {
    const first = this.journey()[0];
    return Math.max(0, Date.parse(e.occurredAt) - Date.parse(first.occurredAt));
  }

  protected reasons(e: AuditEvent): string[] {
    const reasons = e.payload['reasons'];
    if (Array.isArray(reasons)) {
      return reasons as string[];
    }
    const reason = e.payload['reason'];
    return typeof reason === 'string' ? [reason] : [];
  }

  protected tone(e: AuditEvent): string {
    const p = e.payload;
    return p['decision'] === 'REJECTED' || p['outcome'] === 'FAILED' ? 'bad' : 'ok';
  }

  private load(id: string): void {
    forkJoin({ payment: this.api.payment(id), journey: this.api.journey(id) }).subscribe({
      next: ({ payment, journey }) => {
        this.payment.set(payment);
        this.journey.set(journey);
      },
      error: err => this.notFound.set(err.status === 404 || err.status === 400),
    });
  }
}
