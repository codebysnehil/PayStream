export type PaymentStatus = 'PENDING_SCREENING' | 'APPROVED' | 'REJECTED' | 'SETTLED' | 'FAILED';

export interface Payment {
  id: string;
  debtorAccount: string;
  creditorAccount: string;
  amount: number;
  currency: string;
  beneficiaryCountry: string;
  status: PaymentStatus;
  riskScore: number | null;
  statusReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePaymentRequest {
  debtorAccount: string;
  creditorAccount: string;
  amount: number;
  currency: string;
  beneficiaryCountry: string;
}

export interface Account {
  accountNumber: string;
  holderName: string;
  currency: string;
  balance: number;
  updatedAt: string;
}

export interface LedgerEntry {
  id: number;
  paymentId: string;
  direction: 'DEBIT' | 'CREDIT';
  amount: number;
  currency: string;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface IntegrityReport {
  balanced: boolean;
  currencies: { currency: string; debits: number; credits: number; balanced: boolean }[];
}

export interface AuditEvent {
  eventId: string;
  paymentId: string;
  eventType: 'PaymentInitiated' | 'PaymentScreened' | 'PaymentSettled';
  topic: string;
  partition: number;
  offset: number;
  occurredAt: string;
  recordedAt: string;
  payload: Record<string, unknown>;
}

export type LiveStage = 'INITIATED' | 'SCREENED' | 'SETTLED';

export interface LiveEvent {
  stage: LiveStage;
  paymentId: string;
  occurredAt: string;
  debtorAccount?: string;
  creditorAccount?: string;
  amount?: number;
  currency?: string;
  beneficiaryCountry?: string;
  decision?: 'APPROVED' | 'REJECTED';
  riskScore?: number;
  reasons?: string[];
  outcome?: 'SETTLED' | 'FAILED';
  reason?: string;
}

export interface LiveStats {
  since: string;
  initiated: number;
  approved: number;
  rejected: number;
  settled: number;
  failed: number;
  p50LatencyMs: number | null;
  p95LatencyMs: number | null;
  throughputLast60s: number[];
  rejectionReasons: Record<string, number>;
  failureReasons: Record<string, number>;
}

export interface Scenario {
  id: string;
  title: string;
  description: string;
  expected: string;
}

export interface ScenarioRun {
  scenario: string;
  expected: string;
  submitted: number;
  rejectedByApi: number;
  paymentIds: string[];
}

export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}
