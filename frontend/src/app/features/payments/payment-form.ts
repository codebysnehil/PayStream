import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { AccountDirectory } from '../../core/account-directory.service';
import { ApiService } from '../../core/api.service';
import { Payment, ProblemDetail } from '../../core/models';
import { MaskIbanPipe } from '../../shared/pipes';

export const COUNTRIES = [
  { code: 'GB', name: 'United Kingdom' },
  { code: 'DE', name: 'Germany' },
  { code: 'FR', name: 'France' },
  { code: 'IN', name: 'India' },
  { code: 'US', name: 'United States' },
  { code: 'IR', name: 'Iran (high-risk)' },
  { code: 'KP', name: 'North Korea (high-risk)' },
];

const distinctAccounts: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const debtor = group.get('debtorAccount')?.value;
  return debtor && debtor === group.get('creditorAccount')?.value ? { sameAccount: true } : null;
};

/**
 * Client-side rules mirror the API's Bean Validation constraints for fast feedback; the server
 * stays the source of truth and its field errors are mapped back onto the form.
 */
@Component({
  selector: 'ps-payment-form',
  imports: [
    ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule, CurrencyPipe, MaskIbanPipe,
  ],
  styles: `
    form { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 16px; padding-top: 8px; }
    .full { grid-column: 1 / -1; }
    .key { font-family: var(--ps-mono); font-size: 0.75rem; color: var(--ps-muted); display: flex; align-items: center; gap: 6px; }
    .key mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .server-error { grid-column: 1 / -1; color: var(--ps-bad); background: var(--ps-bad-bg); padding: 10px 12px; border-radius: 8px; font-size: 0.85rem; }
    @media (max-width: 600px) { form { grid-template-columns: 1fr; } }
  `,
  template: `
    <h2 mat-dialog-title>New payment</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="payment-form" (ngSubmit)="submit()" novalidate>
        <mat-form-field class="full">
          <mat-label>From (debtor)</mat-label>
          <mat-select formControlName="debtorAccount">
            @for (a of accounts.accounts(); track a.accountNumber) {
              <mat-option [value]="a.accountNumber">{{ a.holderName }} · {{ a.accountNumber | maskIban }} · {{ a.balance | currency: a.currency }}</mat-option>
            }
          </mat-select>
          @if (form.controls.debtorAccount.hasError('server')) { <mat-error>{{ form.controls.debtorAccount.getError('server') }}</mat-error> }
          @else { <mat-error>Choose the account to pay from</mat-error> }
        </mat-form-field>

        <mat-form-field class="full">
          <mat-label>To (creditor)</mat-label>
          <mat-select formControlName="creditorAccount">
            @for (a of accounts.accounts(); track a.accountNumber) {
              <mat-option [value]="a.accountNumber">{{ a.holderName }} · {{ a.accountNumber | maskIban }} · {{ a.currency }}</mat-option>
            }
          </mat-select>
          <mat-error>Choose the account to pay into</mat-error>
          @if (form.hasError('sameAccount')) { <mat-hint class="mat-mdc-form-field-error">Debtor and creditor must differ</mat-hint> }
        </mat-form-field>

        <mat-form-field>
          <mat-label>Amount</mat-label>
          <input matInput type="number" inputmode="decimal" formControlName="amount" min="0.01" step="0.01" />
          <span matTextPrefix>{{ form.controls.currency.value === 'EUR' ? '€' : '£' }}&nbsp;</span>
          @if (form.controls.amount.hasError('server')) { <mat-error>{{ form.controls.amount.getError('server') }}</mat-error> }
          @else if (form.controls.amount.hasError('pattern')) { <mat-error>At most 2 decimal places</mat-error> }
          @else { <mat-error>Enter an amount of at least 0.01</mat-error> }
        </mat-form-field>

        <mat-form-field>
          <mat-label>Currency</mat-label>
          <mat-select formControlName="currency">
            <mat-option value="GBP">GBP</mat-option>
            <mat-option value="EUR">EUR</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field class="full">
          <mat-label>Beneficiary country</mat-label>
          <mat-select formControlName="beneficiaryCountry">
            @for (c of countries; track c.code) { <mat-option [value]="c.code">{{ c.name }} ({{ c.code }})</mat-option> }
          </mat-select>
        </mat-form-field>

        @if (serverError()) { <div class="server-error" role="alert">{{ serverError() }}</div> }

        <div class="full key" title="Re-submitting this form reuses the key, so a retry can never create a duplicate payment">
          <mat-icon>key</mat-icon> Idempotency-Key: {{ idempotencyKey }}
        </div>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close type="button">Cancel</button>
      <button mat-flat-button type="submit" form="payment-form" [disabled]="submitting() || !valid()">
        @if (submitting()) { <mat-spinner diameter="18" /> } @else { Send payment }
      </button>
    </mat-dialog-actions>
  `,
})
export class PaymentForm {
  private readonly api = inject(ApiService);
  private readonly dialogRef = inject(MatDialogRef<PaymentForm, Payment>);
  protected readonly accounts = inject(AccountDirectory);
  protected readonly countries = COUNTRIES;

  /** One key per form instance: a retry after a timeout replays instead of paying twice. */
  protected readonly idempotencyKey = crypto.randomUUID();
  protected readonly submitting = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    debtorAccount: ['', Validators.required],
    creditorAccount: ['', Validators.required],
    amount: [null as unknown as number, [Validators.required, Validators.min(0.01), Validators.pattern(/^\d{1,15}(\.\d{1,2})?$/)]],
    currency: ['GBP', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
    beneficiaryCountry: ['GB', [Validators.required, Validators.pattern(/^[A-Z]{2}$/)]],
  }, { validators: distinctAccounts });

  private readonly status = toSignal(this.form.statusChanges, { initialValue: this.form.status });
  protected readonly valid = computed(() => this.status() === 'VALID');

  constructor() {
    this.accounts.load();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.serverError.set(null);
    this.api.createPayment(this.form.getRawValue(), this.idempotencyKey).subscribe({
      next: res => this.dialogRef.close(res.body ?? undefined),
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        const problem = err.error as ProblemDetail | null;
        if (problem?.errors) {
          for (const [field, message] of Object.entries(problem.errors)) {
            this.form.get(field)?.setErrors({ server: message });
          }
        }
        this.serverError.set(err.status === 0 ? 'Network error: it is safe to retry, the same idempotency key will be reused.'
          : problem?.detail ?? problem?.title ?? 'The payment could not be submitted.');
      },
    });
  }
}
