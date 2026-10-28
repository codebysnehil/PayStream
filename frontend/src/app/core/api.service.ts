import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  Account, AuditEvent, CreatePaymentRequest, IntegrityReport, LedgerEntry, Page, Payment, Scenario, ScenarioRun,
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  recentPayments(limit = 100): Observable<Payment[]> {
    return this.http.get<Payment[]>('/api/payments', { params: { limit } });
  }

  payment(id: string): Observable<Payment> {
    return this.http.get<Payment>(`/api/payments/${encodeURIComponent(id)}`);
  }

  /** The caller owns the idempotency key so a retry of the same submission reuses it. */
  createPayment(request: CreatePaymentRequest, idempotencyKey: string): Observable<HttpResponse<Payment>> {
    return this.http.post<Payment>('/api/payments', request, {
      headers: { 'Idempotency-Key': idempotencyKey },
      observe: 'response',
    });
  }

  journey(id: string): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`/api/payments/${encodeURIComponent(id)}/journey`);
  }

  accounts(): Observable<Account[]> {
    return this.http.get<Account[]>('/api/accounts');
  }

  entries(accountNumber: string, page: number, size: number): Observable<Page<LedgerEntry>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<LedgerEntry>>(`/api/accounts/${encodeURIComponent(accountNumber)}/entries`, { params });
  }

  integrity(): Observable<IntegrityReport> {
    return this.http.get<IntegrityReport>('/api/ledger/integrity');
  }

  scenarios(): Observable<Scenario[]> {
    return this.http.get<Scenario[]>('/api/scenarios');
  }

  runScenario(id: string): Observable<ScenarioRun> {
    return this.http.post<ScenarioRun>(`/api/scenarios/${encodeURIComponent(id)}`, null);
  }
}
