import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { Scenario, ScenarioRun } from '../../core/models';
import { ShortIdPipe } from '../../shared/pipes';

const ICONS: Record<string, string> = {
  HAPPY_PATH: 'check_circle',
  INSUFFICIENT_FUNDS: 'money_off',
  SANCTIONED_JURISDICTION: 'gpp_bad',
  HARD_LIMIT: 'block',
  CURRENCY_MISMATCH: 'currency_exchange',
  STRUCTURING_BURST: 'stacks',
  LOAD_BURST: 'speed',
};

@Component({
  selector: 'ps-scenario-launcher',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule, RouterLink, ShortIdPipe],
  styleUrl: './scenario-launcher.scss',
  template: `
    <div class="card">
      <h2>Scenarios</h2>
      <p class="sub">Sends real payments through the public API and Kafka</p>
      <div class="list">
        @for (s of scenarios(); track s.id) {
          <div class="scenario">
            <mat-icon class="icon">{{ icon(s.id) }}</mat-icon>
            <div class="text">
              <strong>{{ s.title }}</strong>
              <span>{{ s.description }}</span>
              <span class="expect"><span>Expect</span>{{ s.expected }}</span>
            </div>
            <button mat-stroked-button class="run" (click)="run(s)" [disabled]="running() !== null" [attr.aria-label]="'Run ' + s.title">
              @if (running() === s.id) { <mat-spinner diameter="16" /> } @else { <span class="run-inner"><mat-icon>play_arrow</mat-icon>Run</span> }
            </button>
          </div>
        } @empty {
          @if (loadFailed()) {
            <p class="muted">Couldn't load scenarios. <button mat-button (click)="load()">Retry</button></p>
          } @else {
            <p class="muted">Loading scenarios…</p>
          }
        }
      </div>
      @if (lastRun(); as r) {
        <div class="result" role="status">
          <mat-icon>task_alt</mat-icon>
          <div>
            Submitted <strong>{{ r.submitted }}</strong> payment{{ r.submitted === 1 ? '' : 's' }}
            @if (r.rejectedByApi) { · {{ r.rejectedByApi }} refused by the API }
            @if (r.paymentIds.length) {
              · <a [routerLink]="['/payments', r.paymentIds[r.paymentIds.length - 1]]">open journey of {{ r.paymentIds[r.paymentIds.length - 1] | shortId }}</a>
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class ScenarioLauncher {
  private readonly api = inject(ApiService);

  protected readonly scenarios = signal<Scenario[]>([]);
  protected readonly running = signal<string | null>(null);
  protected readonly lastRun = signal<ScenarioRun | null>(null);
  protected readonly loadFailed = signal(false);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loadFailed.set(false);
    this.api.scenarios().subscribe({
      next: s => this.scenarios.set(s),
      error: () => this.loadFailed.set(true),
    });
  }

  protected icon(id: string): string {
    return ICONS[id] ?? 'play_circle';
  }

  protected run(s: Scenario): void {
    this.running.set(s.id);
    this.api.runScenario(s.id).subscribe({
      next: run => {
        this.lastRun.set(run);
        this.running.set(null);
      },
      error: () => this.running.set(null),
    });
  }
}
