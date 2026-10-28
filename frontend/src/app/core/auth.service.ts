import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map } from 'rxjs';

interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  subject: string;
}

interface Session {
  token: string;
  subject: string;
  expiresAt: number;
}

const STORAGE_KEY = 'paystream.session';

/**
 * Holds the reviewer's short-lived JWT. It is kept in sessionStorage (cleared when the tab
 * closes) rather than localStorage, and is only ever sent to our own /api origin.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(readSession());

  readonly isAuthenticated = computed(() => {
    const s = this.session();
    return s !== null && s.expiresAt > Date.now();
  });
  readonly subject = computed(() => this.session()?.subject ?? null);

  token(): string | null {
    return this.isAuthenticated() ? this.session()!.token : null;
  }

  loginAsReviewer(): Observable<void> {
    return this.http.post<TokenResponse>('/api/auth/demo', null).pipe(
      map(res => {
        const session: Session = {
          token: res.accessToken,
          subject: res.subject,
          // Renew a minute early so in-flight requests never race the expiry.
          expiresAt: Date.now() + (res.expiresIn - 60) * 1000,
        };
        writeSession(session);
        this.session.set(session);
      }),
    );
  }

  logout(): void {
    writeSession(null);
    this.session.set(null);
    void this.router.navigate(['/login']);
  }
}

function readSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

function writeSession(session: Session | null): void {
  try {
    if (session) {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Storage can be unavailable (private mode); the in-memory session still works.
  }
}
