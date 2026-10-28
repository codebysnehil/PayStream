import { Component, computed, input } from '@angular/core';

import { PaymentStatus } from '../core/models';

const LABELS: Record<PaymentStatus, string> = {
  PENDING_SCREENING: 'Screening',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SETTLED: 'Settled',
  FAILED: 'Failed',
};

@Component({
  selector: 'ps-status-chip',
  template: `<span class="chip" [class]="tone()" [attr.aria-label]="'Status: ' + label()">{{ label() }}</span>`,
})
export class StatusChip {
  readonly status = input.required<PaymentStatus>();

  protected readonly label = computed(() => LABELS[this.status()]);
  protected readonly tone = computed(() => {
    switch (this.status()) {
      case 'SETTLED':
        return 'chip ok';
      case 'REJECTED':
      case 'FAILED':
        return 'chip bad';
      case 'APPROVED':
        return 'chip';
      default:
        return 'chip warn pending';
    }
  });
}
