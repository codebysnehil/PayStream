import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { debounceTime } from 'rxjs';

import { AccountDirectory } from '../../core/account-directory.service';
import { ApiService } from '../../core/api.service';
import { LiveService } from '../../core/live.service';
import { Payment, PaymentStatus } from '../../core/models';
import { ReasonPipe, ShortIdPipe } from '../../shared/pipes';
import { StatusChip } from '../../shared/status-chip';
import { PaymentForm } from './payment-form';

type Filter = 'ALL' | 'IN_FLIGHT' | PaymentStatus;

@Component({
  selector: 'ps-payments',
  imports: [DecimalPipe, DatePipe, MatButtonModule, MatButtonToggleModule, MatIconModule, StatusChip, ShortIdPipe, ReasonPipe],
  styles: `.toolbar { padding: 14px 16px; border-bottom: 1px solid var(--ps-border); }`,
  template: `
    <div class="page">
      <div class="page-header">
        <div>
          <h1>Payments</h1>
          <p>The 100 most recent payments from SQL Server, refreshed as events arrive.</p>
        </div>
        <button mat-flat-button (click)="newPayment()"><mat-icon>add</mat-icon>New payment</button>
      </div>

      <div class="card flush">
        <div class="row toolbar">
          <mat-button-toggle-group [value]="filter()" (change)="filter.set($event.value)" aria-label="Filter by status" hideSingleSelectionIndicator>
            @for (f of filters; track f.value) {
              <mat-button-toggle [value]="f.value">{{ f.label }} <span class="muted">{{ count(f.value) }}</span></mat-button-toggle>
            }
          </mat-button-toggle-group>
        </div>
        <div class="table-wrap">
          <table class="data">
            <thead>
              <tr>
                <th>Created</th><th>Payment</th><th>From</th><th>To</th><th class="num">Amount</th>
                <th>Country</th><th>Status</th><th class="num">Risk</th><th>Reason</th>
              </tr>
            </thead>
            <tbody>
              @for (p of visible(); track p.id) {
                <tr class="clickable" (click)="open(p.id)" tabindex="0" (keydown.enter)="open(p.id)">
                  <td class="muted">{{ p.createdAt | date: 'dd MMM, HH:mm:ss' }}</td>
                  <td class="mono muted">{{ p.id | shortId }}</td>
                  <td>{{ accounts.holder(p.debtorAccount) }}</td>
                  <td>{{ accounts.holder(p.creditorAccount) }}</td>
                  <td class="num amount">{{ p.amount | number: '1.2-2' }}<span class="ccy">{{ p.currency }}</span></td>
                  <td>{{ p.beneficiaryCountry }}</td>
                  <td><ps-status-chip [status]="p.status" /></td>
                  <td class="num">{{ p.riskScore ?? '—' }}</td>
                  <td>
                    @if (p.statusReason) {
                      @for (r of p.statusReason.split(','); track r) { <span class="tag" [class.bad]="p.status === 'REJECTED' || p.status === 'FAILED'">{{ r | reason }}</span> }
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="9" class="empty">{{ loading() ? 'Loading…' : 'No payments match this filter yet.' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
})
export class Payments {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly accounts = inject(AccountDirectory);

  protected readonly payments = signal<Payment[]>([]);
  protected readonly loading = signal(true);
  protected readonly filter = signal<Filter>('ALL');
  protected readonly filters: { value: Filter; label: string }[] = [
    { value: 'ALL', label: 'All' },
    { value: 'IN_FLIGHT', label: 'In flight' },
    { value: 'SETTLED', label: 'Settled' },
    { value: 'REJECTED', label: 'Rejected' },
    { value: 'FAILED', label: 'Failed' },
  ];

  protected readonly visible = computed(() => {
    const f = this.filter();
    return this.payments().filter(p => matches(p, f));
  });

  constructor() {
    this.refresh();
    // Re-read from the source of truth when the stream says something changed (debounced for bursts).
    inject(LiveService).events$
      .pipe(debounceTime(750), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.refresh());
  }

  protected count(f: Filter): number {
    return this.payments().filter(p => matches(p, f)).length;
  }

  protected newPayment(): void {
    this.dialog.open(PaymentForm, { width: '640px', maxWidth: '95vw', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((created?: Payment) => {
        if (created) {
          this.snackBar.open(`Payment ${created.id.slice(0, 8)} accepted. Follow its journey.`, 'Open', { duration: 6000 })
            .onAction().subscribe(() => this.open(created.id));
          this.refresh();
        }
      });
  }

  protected open(id: string): void {
    void this.router.navigate(['/payments', id]);
  }

  private refresh(): void {
    this.api.recentPayments(100).subscribe({
      next: p => {
        this.payments.set(p);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}

function matches(p: Payment, f: Filter): boolean {
  if (f === 'ALL') {
    return true;
  }
  if (f === 'IN_FLIGHT') {
    return p.status === 'PENDING_SCREENING' || p.status === 'APPROVED';
  }
  return p.status === f;
}
