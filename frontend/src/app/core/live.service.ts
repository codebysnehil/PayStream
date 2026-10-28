import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';

import { AuthService } from './auth.service';
import { LivePayment, applyLiveEvent } from './live-feed';
import { LiveEvent, LiveStats } from './models';

export type ConnectionState = 'offline' | 'connecting' | 'live' | 'reconnecting';

/**
 * Single Server-Sent Events connection to the gateway, shared by every screen. Reconnects with
 * capped exponential back-off and exposes state as signals.
 */
@Injectable({ providedIn: 'root' })
export class LiveService implements OnDestroy {
  private readonly auth = inject(AuthService);

  readonly state = signal<ConnectionState>('offline');
  readonly stats = signal<LiveStats | null>(null);
  readonly feed = signal<LivePayment[]>([]);
  /** Raw event stream for screens that react to specific payments. */
  readonly events$ = new Subject<LiveEvent>();

  private source: EventSource | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private attempts = 0;

  connect(): void {
    if (this.source) {
      return;
    }
    const token = this.auth.token();
    if (!token) {
      this.state.set('offline');
      return;
    }
    this.state.set(this.attempts === 0 ? 'connecting' : 'reconnecting');
    const source = new EventSource(`/api/live/stream?access_token=${encodeURIComponent(token)}`);
    this.source = source;

    source.onopen = () => {
      this.attempts = 0;
      this.state.set('live');
    };
    source.addEventListener('stats', e => this.stats.set(JSON.parse((e as MessageEvent).data) as LiveStats));
    source.addEventListener('payment', e => {
      const event = JSON.parse((e as MessageEvent).data) as LiveEvent;
      this.feed.update(feed => applyLiveEvent(feed, event));
      this.events$.next(event);
    });
    source.onerror = () => {
      source.close();
      this.source = null;
      this.state.set('reconnecting');
      const delay = Math.min(30_000, 1_000 * 2 ** this.attempts++);
      this.retryTimer = setTimeout(() => this.connect(), delay);
    };
  }

  disconnect(): void {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    this.source?.close();
    this.source = null;
    this.attempts = 0;
    this.state.set('offline');
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
