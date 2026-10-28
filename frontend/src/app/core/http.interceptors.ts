import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/** Attaches the bearer token to our own API calls only, never to third-party URLs. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const isApi = req.url.startsWith('/api/') && !req.url.startsWith('/api/auth/');
  const authed = token && isApi ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authed).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && isApi) {
        auth.logout();
      }
      return throwError(() => err);
    }),
  );
};

/**
 * Surfaces platform-level failures (offline, throttled, server errors) in one consistent place.
 * Business errors (4xx validation, idempotency conflicts) are left to the calling component.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const message =
        err.status === 0 ? 'Cannot reach the PayStream API. Is the backend running?'
        : err.status === 429 ? `Slow down a little: rate limit reached. Retry in ${err.headers.get('Retry-After') ?? 'a few'}s.`
        : err.status >= 500 ? (err.error?.detail ?? 'A backend service failed. Please try again.')
        : null;
      if (message) {
        snackBar.open(message, 'Dismiss', { duration: 6000 });
      }
      return throwError(() => err);
    }),
  );
};
