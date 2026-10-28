import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { AccountDirectory } from '../core/account-directory.service';
import { AuthService } from '../core/auth.service';
import { LiveService } from '../core/live.service';

const SECTIONS: Record<string, string> = {
  dashboard: 'Overview',
  payments: 'Payments',
  ledger: 'Ledger',
  architecture: 'Architecture',
};

@Component({
  selector: 'ps-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatIconModule, MatButtonModule, MatTooltipModule],
  styleUrl: './shell.scss',
  template: `
    <mat-sidenav-container class="shell">
      <mat-sidenav #nav [mode]="isMobile() ? 'over' : 'side'" [opened]="!isMobile()" class="nav">
        <a class="brand" routerLink="/dashboard" aria-label="PayStream home">
          <img src="favicon.svg" alt="" width="26" height="26" />
          <span>PayStream</span>
          <span class="env">Local</span>
        </a>

        <nav aria-label="Main">
          <div class="group">Operate</div>
          @for (item of operate; track item.path) {
            <a [routerLink]="item.path" routerLinkActive="active" (click)="isMobile() && nav.close()">
              <mat-icon>{{ item.icon }}</mat-icon><span>{{ item.label }}</span>
            </a>
          }
          <div class="group">Learn</div>
          <a routerLink="/architecture" routerLinkActive="active" (click)="isMobile() && nav.close()">
            <mat-icon>schema</mat-icon><span>Architecture</span>
          </a>
          <a href="https://github.com/" target="_blank" rel="noopener">
            <mat-icon>code</mat-icon><span>Source code</span><mat-icon class="ext">north_east</mat-icon>
          </a>
        </nav>

        <div class="stack">
          <div class="stack-title">Running on</div>
          <div class="stack-list">
            @for (s of stack; track s) { <span>{{ s }}</span> }
          </div>
        </div>

        <div class="me">
          <div class="avatar">{{ initials() }}</div>
          <div class="who">
            <strong>Reviewer</strong>
            <span class="mono">{{ auth.subject() }}</span>
          </div>
          <button mat-icon-button (click)="auth.logout()" matTooltip="Sign out" aria-label="Sign out">
            <mat-icon>logout</mat-icon>
          </button>
        </div>
      </mat-sidenav>

      <mat-sidenav-content>
        <header class="topbar">
          @if (isMobile()) {
            <button mat-icon-button (click)="nav.toggle()" aria-label="Open navigation"><mat-icon>menu</mat-icon></button>
          }
          <div class="crumbs">
            <span class="muted">PayStream</span>
            <mat-icon>chevron_right</mat-icon>
            <span>{{ section() }}</span>
          </div>
          <span class="spacer"></span>
          <span class="live" [class.on]="live.state() === 'live'" [class.off]="live.state() === 'offline'" [matTooltip]="liveTooltip()">
            <span class="dot"></span>
            @if (live.state() === 'live') {
              Streaming <span class="rate">{{ rate() }} ev/s</span>
            } @else {
              {{ liveLabel() }}
            }
          </span>
        </header>
        <main>
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
})
export class Shell implements OnInit, OnDestroy {
  protected readonly auth = inject(AuthService);
  protected readonly live = inject(LiveService);
  private readonly accounts = inject(AccountDirectory);
  private readonly router = inject(Router);

  protected readonly operate = [
    { path: '/dashboard', label: 'Overview', icon: 'space_dashboard' },
    { path: '/payments', label: 'Payments', icon: 'swap_horiz' },
    { path: '/ledger', label: 'Ledger', icon: 'account_balance' },
  ];
  protected readonly stack = ['Spring Boot', 'Kafka', 'SQL Server', 'MongoDB', 'Angular'];

  protected readonly isMobile = toSignal(
    inject(BreakpointObserver).observe('(max-width: 900px)').pipe(map(r => r.matches)),
    { initialValue: false },
  );

  private readonly url = toSignal(
    this.router.events.pipe(filter(e => e instanceof NavigationEnd), map(e => (e as NavigationEnd).urlAfterRedirects)),
    { initialValue: this.router.url },
  );

  protected readonly section = computed(() => SECTIONS[this.url().split('/')[1]?.split('?')[0] ?? ''] ?? 'Overview');

  protected readonly initials = computed(() => (this.auth.subject() ?? 'r').replace('reviewer-', '').slice(0, 2).toUpperCase());

  protected readonly rate = computed(() => {
    const series = this.live.stats()?.throughputLast60s ?? [];
    const recent = series.slice(-5);
    return recent.length ? Math.round(recent.reduce((a, b) => a + b, 0) / recent.length) : 0;
  });

  protected readonly liveLabel = computed(() => ({
    live: 'Streaming',
    connecting: 'Connecting',
    reconnecting: 'Reconnecting',
    offline: 'Offline',
  })[this.live.state()]);

  protected readonly liveTooltip = computed(() =>
    this.live.state() === 'live'
      ? 'Kafka events streamed from the gateway over Server-Sent Events'
      : 'Live stream unavailable, retrying with back-off');

  ngOnInit(): void {
    this.live.connect();
    this.accounts.load();
  }

  ngOnDestroy(): void {
    this.live.disconnect();
  }
}
