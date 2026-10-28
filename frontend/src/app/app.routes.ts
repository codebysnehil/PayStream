import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';

import { AuthService } from './core/auth.service';

const authGuard: CanActivateFn = () =>
  inject(AuthService).isAuthenticated() || inject(Router).createUrlTree(['/login']);

const guestGuard: CanActivateFn = () =>
  !inject(AuthService).isAuthenticated() || inject(Router).createUrlTree(['/dashboard']);

// Feature screens are lazy-loaded so the first paint only ships the shell.
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in · PayStream',
    loadComponent: () => import('./features/login/login').then(m => m.Login),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then(m => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard · PayStream',
        loadComponent: () => import('./features/dashboard/dashboard').then(m => m.Dashboard),
      },
      {
        path: 'payments',
        title: 'Payments · PayStream',
        loadComponent: () => import('./features/payments/payments').then(m => m.Payments),
      },
      {
        path: 'payments/:id',
        title: 'Payment journey · PayStream',
        loadComponent: () => import('./features/payments/payment-detail').then(m => m.PaymentDetail),
      },
      {
        path: 'ledger',
        title: 'Ledger · PayStream',
        loadComponent: () => import('./features/ledger/ledger').then(m => m.Ledger),
      },
      {
        path: 'architecture',
        title: 'Architecture · PayStream',
        loadComponent: () => import('./features/architecture/architecture').then(m => m.Architecture),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
