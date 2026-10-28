import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { RouterLink } from '@angular/router';
import { debounceTime, filter } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { LiveService } from '../../core/live.service';
import { Account, IntegrityReport, LedgerEntry, Page } from '../../core/models';
import { ShortIdPipe } from '../../shared/pipes';

@Component({
  selector: 'ps-ledger',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, MatIconModule, MatPaginatorModule, RouterLink, ShortIdPipe],
  styleUrl: './ledger.scss',
  template: `
    <div class="page">
      <div class="page-header">
        <div>
          <h1>Ledger</h1>
          <p>Double-entry journal in SQL Server. Each settlement posts one debit and one matching credit in the same transaction.</p>
        </div>
      </div>

      @if (integrity(); as i) {
        <div class="integrity card" [class.bad]="!i.balanced" role="status">
          <div class="seal"><mat-icon>{{ i.balanced ? 'verified' : 'error' }}</mat-icon></div>
          <div>
            <strong>{{ i.balanced ? 'Ledger balanced' : 'Ledger out of balance' }}</strong>
            <span class="desc">Control check · total debits equal total credits per currency · re-run on every settlement</span>
          </div>
          <span class="spacer"></span>
          @for (c of i.currencies; track c.currency) {
            <div class="totals mono">
              <span><b>Ccy</b>{{ c.currency }}</span>
              <span><b>Dr</b>{{ c.debits | number: '1.2-2' }}</span>
              <span><b>Cr</b>{{ c.credits | number: '1.2-2' }}</span>
            </div>
          }
        </div>
      }

      <section class="grid grid-2 gap-top">
        <div class="card">
          <h2>Accounts</h2>
          <p class="sub">{{ accounts().length }} accounts · balances update as payments settle</p>
          <div class="filter">
            <input type="search" placeholder="Filter by name or IBAN" (input)="query.set($any($event.target).value)" aria-label="Filter accounts" />
          </div>
          <div class="table-wrap scroll">
            <table class="data">
              <thead><tr><th>Holder</th><th>Account</th><th>Ccy</th><th class="num">Balance</th></tr></thead>
              <tbody>
                @for (a of filtered(); track a.accountNumber) {
                  <tr class="clickable" [class.selected]="a.accountNumber === selected()?.accountNumber"
                      (click)="select(a)" tabindex="0" (keydown.enter)="select(a)">
                    <td>{{ a.holderName }}</td>
                    <td class="mono muted">{{ a.accountNumber }}</td>
                    <td>{{ a.currency }}</td>
                    <td class="num amount">{{ a.balance | number: '1.2-2' }}<span class="ccy">{{ a.currency }}</span></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>

        <div class="card">
          @if (selected(); as a) {
            <h2>{{ a.holderName }}</h2>
            <p class="sub mono">{{ a.accountNumber }} · {{ a.balance | currency: a.currency }}</p>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>Time</th><th>Payment</th><th>Dr / Cr</th><th class="num">Amount</th></tr></thead>
                <tbody>
                  @for (e of entries()?.content ?? []; track e.id) {
                    <tr>
                      <td>{{ e.createdAt | date: 'dd MMM HH:mm:ss' }}</td>
                      <td class="mono"><a [routerLink]="['/payments', e.paymentId]">{{ e.paymentId | shortId }}</a></td>
                      <td class="muted">{{ e.direction === 'DEBIT' ? 'Debit' : 'Credit' }}</td>
                      <td class="num amount" [class.dr]="e.direction === 'DEBIT'" [class.cr]="e.direction === 'CREDIT'">{{ e.direction === 'DEBIT' ? '−' : '+' }}{{ e.amount | number: '1.2-2' }}<span class="ccy">{{ e.currency }}</span></td>
                    </tr>
                  } @empty {
                    <tr><td colspan="4" class="empty">No journal entries for this account yet.</td></tr>
                  }
                </tbody>
              </table>
            </div>
            <mat-paginator [length]="entries()?.page?.totalElements ?? 0" [pageSize]="pageSize" [pageIndex]="pageIndex()"
                           [hidePageSize]="true" (page)="page($event)" aria-label="Journal entries pages" />
          } @else {
            <div class="empty">Select an account to see its journal entries.</div>
          }
        </div>
      </section>
    </div>
  `,
})
export class Ledger {
  private readonly api = inject(ApiService);

  protected readonly accounts = signal<Account[]>([]);
  protected readonly integrity = signal<IntegrityReport | null>(null);
  protected readonly selected = signal<Account | null>(null);
  protected readonly entries = signal<Page<LedgerEntry> | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly query = signal('');
  protected readonly pageSize = 12;

  protected readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    return q ? this.accounts().filter(a => a.holderName.toLowerCase().includes(q) || a.accountNumber.toLowerCase().includes(q)) : this.accounts();
  });

  constructor() {
    this.refresh();
    inject(LiveService).events$
      .pipe(filter(e => e.stage === 'SETTLED'), debounceTime(1000), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.refresh());
  }

  protected select(a: Account): void {
    this.selected.set(a);
    this.pageIndex.set(0);
    this.loadEntries();
  }

  protected page(e: PageEvent): void {
    this.pageIndex.set(e.pageIndex);
    this.loadEntries();
  }

  private refresh(): void {
    this.api.integrity().subscribe(i => this.integrity.set(i));
    this.api.accounts().subscribe(accounts => {
      this.accounts.set(accounts);
      const current = this.selected();
      const next = (current && accounts.find(a => a.accountNumber === current.accountNumber)) ?? accounts[0] ?? null;
      this.selected.set(next);
      this.loadEntries();
    });
  }

  private loadEntries(): void {
    const a = this.selected();
    if (!a) {
      return;
    }
    const page = this.pageIndex();
    this.api.entries(a.accountNumber, page, this.pageSize).subscribe(p => {
      // Drop late responses for an account or page the user has already moved away from.
      if (this.selected()?.accountNumber === a.accountNumber && this.pageIndex() === page) {
        this.entries.set(p);
      }
    });
  }
}
