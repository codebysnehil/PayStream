import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { PaymentStatus } from '../core/models';
import { MaskIbanPipe, ReasonPipe, ShortIdPipe } from './pipes';
import { StatusChip } from './status-chip';

describe('pipes', () => {
  it('shortens payment ids', () => {
    expect(new ShortIdPipe().transform('7a1c2e9f-1234-4000-8000-000000000001')).toBe('7a1c2e9f');
    expect(new ShortIdPipe().transform(null)).toBe('');
  });

  it('masks IBANs', () => {
    expect(new MaskIbanPipe().transform('GB82PAYS10000000000001')).toBe('GB82 ···· 0001');
  });

  it('humanises reason codes', () => {
    expect(new ReasonPipe().transform('INSUFFICIENT_FUNDS')).toBe('Insufficient funds');
  });
});

@Component({
  imports: [StatusChip],
  template: `<ps-status-chip [status]="status()" />`,
})
class Host {
  readonly status = signal<PaymentStatus>('PENDING_SCREENING');
}

describe('StatusChip', () => {
  it('renders a label and tone for each status', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const chip = (): HTMLElement => fixture.nativeElement.querySelector('.chip');

    expect(chip().textContent).toBe('Screening');
    expect(chip().classList).toContain('pending');

    fixture.componentInstance.status.set('SETTLED');
    await fixture.whenStable();
    expect(chip().textContent).toBe('Settled');
    expect(chip().classList).toContain('ok');

    fixture.componentInstance.status.set('REJECTED');
    await fixture.whenStable();
    expect(chip().classList).toContain('bad');
  });
});
