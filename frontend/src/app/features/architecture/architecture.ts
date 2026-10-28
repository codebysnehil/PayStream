import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

interface Decision {
  icon: string;
  title: string;
  problem: string;
  solution: string;
}

@Component({
  selector: 'ps-architecture',
  imports: [MatIconModule],
  styleUrl: './architecture.scss',
  template: `
    <div class="page">
      <div class="page-header">
        <div>
          <h1>How PayStream works</h1>
          <p>Six Spring Boot services, three Kafka topics, two SQL Server databases and a MongoDB audit store.</p>
        </div>
      </div>

      <div class="card diagram-card">
        <svg viewBox="0 0 960 420" role="img" aria-labelledby="arch-title" class="diagram">
          <title id="arch-title">PayStream architecture: browser to gateway, payment, fraud, ledger and audit services connected by Kafka</title>
          <defs>
            <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M0,0 L10,5 L0,10 z" class="arrowhead" />
            </marker>
          </defs>

          <!-- edges -->
          <path d="M120 210 H190" class="edge" marker-end="url(#arrow)" />
          <path d="M330 190 H400" class="edge" marker-end="url(#arrow)" />
          <path d="M540 190 C 580 190, 580 120, 610 120" class="edge" marker-end="url(#arrow)" />
          <path d="M610 140 C 585 140, 585 175, 560 175" class="edge" marker-end="url(#arrow)" />
          <path d="M540 210 H610" class="edge" marker-end="url(#arrow)" />
          <path d="M610 225 H560" class="edge" marker-end="url(#arrow)" />
          <path d="M540 245 C 580 245, 580 320, 610 320" class="edge" marker-end="url(#arrow)" />
          <path d="M400 230 H330" class="edge dashed" marker-end="url(#arrow)" />
          <path d="M470 270 V330" class="edge" />
          <path d="M260 250 V330" class="edge" />
          <path d="M750 230 V280" class="edge" />

          <!-- nodes -->
          <g class="node"><rect x="20" y="170" width="100" height="80" rx="12" /><text x="70" y="205">Angular</text><text x="70" y="225" class="small">console</text></g>
          <g class="node accent"><rect x="190" y="150" width="140" height="100" rx="12" /><text x="260" y="190">gateway</text><text x="260" y="210" class="small">JWT · rate limit</text><text x="260" y="228" class="small">SSE fan-out</text></g>
          <g class="node kafka"><rect x="400" y="150" width="140" height="120" rx="12" /><text x="470" y="185">Kafka</text><text x="470" y="207" class="small mono">payments.initiated</text><text x="470" y="225" class="small mono">payments.screened</text><text x="470" y="243" class="small mono">payments.settled</text></g>
          <g class="node"><rect x="610" y="90" width="150" height="60" rx="12" /><text x="685" y="117">payment</text><text x="685" y="135" class="small">outbox · state machine</text></g>
          <g class="node"><rect x="610" y="190" width="150" height="50" rx="12" /><text x="685" y="212">fraud</text><text x="685" y="228" class="small">rule engine</text></g>
          <g class="node"><rect x="610" y="290" width="150" height="60" rx="12" /><text x="685" y="317">ledger</text><text x="685" y="335" class="small">double-entry</text></g>
          <g class="node"><rect x="400" y="330" width="140" height="60" rx="12" /><text x="470" y="357">audit</text><text x="470" y="375" class="small">batch consumer</text></g>
          <g class="node store"><rect x="800" y="90" width="130" height="60" rx="12" /><text x="865" y="117">SQL Server</text><text x="865" y="135" class="small">payments + outbox</text></g>
          <g class="node store"><rect x="800" y="290" width="130" height="60" rx="12" /><text x="865" y="317">SQL Server</text><text x="865" y="335" class="small">ledger</text></g>
          <g class="node store"><rect x="190" y="330" width="140" height="60" rx="12" /><text x="260" y="357">Prometheus</text><text x="260" y="375" class="small">+ Grafana</text></g>
          <path d="M760 120 H800" class="edge" />
          <path d="M760 320 H800" class="edge" />
          <path d="M400 360 H330" class="edge" />
          <text x="590" y="400" class="small muted-text">MongoDB stores the audit timeline</text>
        </svg>
      </div>

      <h2 class="section">The lifecycle of one payment</h2>
      <ol class="steps">
        <li><strong>Intake.</strong> The gateway relays the reviewer's JWT to the payment service. In one SQL transaction, the payment service stores the payment and a <em>PaymentInitiated</em> outbox row, then replies <code>201</code>.</li>
        <li><strong>Relay.</strong> The outbox relay publishes the event to <code>payments.initiated</code>, keyed by the debtor account so every event for that account stays in order.</li>
        <li><strong>Screening.</strong> The fraud service scores the payment against configurable rules (hard limit, high value, structuring, velocity, jurisdiction) and publishes APPROVED or REJECTED.</li>
        <li><strong>Settlement.</strong> The ledger locks both accounts in a fixed order, posts a balanced debit/credit pair and records the outcome, all in one transaction.</li>
        <li><strong>Feedback.</strong> The payment service moves its state machine forward, the audit service appends to the timeline, and the gateway streams each event to this console.</li>
      </ol>

      <h2 class="section">Design decisions</h2>
      <div class="grid decisions">
        @for (d of decisions; track d.title) {
          <div class="card">
            <div class="row"><mat-icon>{{ d.icon }}</mat-icon><h3>{{ d.title }}</h3></div>
            <p><span class="label">Problem</span>{{ d.problem }}</p>
            <p><span class="label">Solution</span>{{ d.solution }}</p>
          </div>
        }
      </div>
    </div>
  `,
})
export class Architecture {
  protected readonly decisions: Decision[] = [
    {
      icon: 'outbox',
      title: 'Transactional outbox',
      problem: 'Writing to the database and publishing to Kafka cannot be atomic, so a crash between the two either loses an event or announces a payment that was never stored.',
      solution: 'The event is written to an outbox table in the same transaction as the payment. A relay publishes it afterwards, so delivery is at-least-once and never inconsistent.',
    },
    {
      icon: 'key',
      title: 'Idempotent API',
      problem: 'Clients retry after timeouts, and a retried payment must never move money twice.',
      solution: 'Every request carries an Idempotency-Key. A replay returns the original payment, reusing the key with a different body is rejected (422), and a UNIQUE constraint settles races.',
    },
    {
      icon: 'done_all',
      title: 'Exactly-once settlement',
      problem: 'Kafka redelivers messages, so the ledger will see some events twice.',
      solution: 'The settlement record is keyed by payment id. A duplicate can never post twice, and a redelivery replays the stored outcome, which also repairs a crash between commit and publish.',
    },
    {
      icon: 'lock',
      title: 'Deadlock-free locking',
      problem: 'A→B and B→A transfers running at the same time can deadlock on row locks.',
      solution: 'Both accounts are locked in a fixed (sorted) order in one query, a CHECK (balance ≥ 0) constraint backs up the application check, and transient lock failures are retried with back-off.',
    },
    {
      icon: 'swap_vert',
      title: 'Ordering by partition key',
      problem: 'Per-account rules such as velocity need events for each account in order, without a distributed lock.',
      solution: 'All topics are keyed by debtor account, so one consumer owns each account and can keep the sliding window in memory.',
    },
    {
      icon: 'bug_report',
      title: 'Poison-message isolation',
      problem: 'One malformed or failing message must not block its partition forever.',
      solution: 'ErrorHandlingDeserializer, exponential back-off retries, then a dead-letter topic (<topic>.DLT) for investigation.',
    },
  ];
}
