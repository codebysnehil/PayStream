import { Injectable, computed, inject, signal } from '@angular/core';
import { retry } from 'rxjs';

import { ApiService } from './api.service';
import { Account } from './models';

/** Caches account holder names so tables can show "Northwind Treasury" instead of a raw IBAN. */
@Injectable({ providedIn: 'root' })
export class AccountDirectory {
  private readonly api = inject(ApiService);
  private readonly list = signal<Account[]>([]);
  private loading = false;

  readonly accounts = this.list.asReadonly();
  private readonly byNumber = computed(() => new Map(this.list().map(a => [a.accountNumber, a])));

  load(force = false): void {
    if (this.loading || (!force && this.list().length > 0)) {
      return;
    }
    this.loading = true;
    // Retry so a gateway that is still starting does not leave raw IBANs on screen for the session.
    this.api.accounts().pipe(retry({ count: 5, delay: 2000 })).subscribe({
      next: accounts => this.list.set(accounts),
      complete: () => (this.loading = false),
      error: () => (this.loading = false),
    });
  }

  holder(accountNumber: string | undefined): string {
    if (!accountNumber) {
      return '—';
    }
    return this.byNumber().get(accountNumber)?.holderName ?? accountNumber;
  }
}
