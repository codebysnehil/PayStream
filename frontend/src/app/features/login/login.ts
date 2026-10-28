import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'ps-login',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  styleUrl: './login.scss',
  template: `
    <div class="wrap">
      <header class="top">
        <div class="brand"><img src="favicon.svg" alt="" width="26" height="26" /> PayStream</div>
        <span class="muted small">Portfolio project · all data is fictional</span>
      </header>

      <main class="hero">
        <section class="pitch">
          <span class="badge"><span class="dot"></span>Event-driven payments platform</span>
          <h1>Every payment, screened and settled <em>exactly once.</em></h1>
          <p>
            Spring Boot microservices that talk over Apache Kafka. Each payment goes through idempotent intake, real-time fraud
            rules and a double-entry ledger, and every hop is recorded in an audit trail.
          </p>

          <div class="pipeline" aria-hidden="true">
            @for (stage of stages; track stage.label; let i = $index) {
              <div class="stage">
                <div class="node"><mat-icon>{{ stage.icon }}</mat-icon></div>
                <strong>{{ stage.label }}</strong>
                <span>{{ stage.detail }}</span>
              </div>
              @if (i < stages.length - 1) {
                <div class="link"><span class="packet" [style.animation-delay.ms]="i * 450"></span></div>
              }
            }
          </div>
        </section>

        <section class="panel" aria-labelledby="signin">
          <h2 id="signin">Open the operations console</h2>
          <p class="muted">
            Sign in as a reviewer to watch live payments, trigger fraud scenarios and inspect the ledger.
          </p>
          <button mat-flat-button class="cta" (click)="signIn()" [disabled]="busy()">
            @if (busy()) {
              <mat-spinner diameter="18" />
            } @else {
              <span class="cta-inner">Continue as reviewer <mat-icon>arrow_forward</mat-icon></span>
            }
          </button>
          @if (error()) {
            <p class="error" role="alert">{{ error() }}</p>
          }
          <ul class="facts">
            <li><mat-icon>key</mat-icon>Short-lived, least-privilege JWT. In production this is OIDC SSO.</li>
            <li><mat-icon>shield_lock</mat-icon>No personal data collected; accounts are fictional.</li>
          </ul>
        </section>
      </main>
    </div>
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly stages = [
    { label: 'Intake', detail: 'idempotent API + outbox', icon: 'input' },
    { label: 'Kafka', detail: 'keyed by account', icon: 'conversion_path' },
    { label: 'Fraud', detail: 'real-time rules', icon: 'policy' },
    { label: 'Ledger', detail: 'double-entry', icon: 'account_balance' },
  ];

  protected signIn(): void {
    this.busy.set(true);
    this.error.set(null);
    this.auth.loginAsReviewer().subscribe({
      next: () => void this.router.navigate(['/dashboard']),
      error: () => {
        this.busy.set(false);
        this.error.set('Could not reach the PayStream gateway. Make sure the backend is running.');
      },
    });
  }
}
