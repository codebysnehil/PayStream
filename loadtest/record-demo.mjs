// Records a ~1 minute walkthrough of the running console as video.
//   docker compose up -d   (stack must be running)
//   npm i playwright && npx playwright install chromium
//   node loadtest/record-demo.mjs ./out      -> ./out/*.webm  (convert with ffmpeg)
import { chromium } from 'playwright';

const BASE = process.env.BASE_URL ?? 'http://localhost:4200';
const OUT = process.argv[2] ?? './demo-video';
const W = 1920, H = 1080;

// BROWSER_CHANNEL=msedge|chrome uses an installed browser instead of Playwright's bundled Chromium.
const browser = await chromium.launch(process.env.BROWSER_CHANNEL ? { channel: process.env.BROWSER_CHANNEL } : {});
const context = await browser.newContext({
  viewport: { width: W, height: H },
  colorScheme: process.env.COLOR_SCHEME === 'dark' ? 'dark' : 'light',
  recordVideo: { dir: OUT, size: { width: W, height: H } },
});
const page = await context.newPage();
const pause = ms => page.waitForTimeout(ms);

// Caption bar so the clip explains itself without audio.
async function caption(text) {
  await page.evaluate(t => {
    let el = document.getElementById('demo-caption');
    if (!el) {
      el = document.createElement('div');
      el.id = 'demo-caption';
      Object.assign(el.style, {
        position: 'fixed', left: '50%', bottom: '28px', transform: 'translateX(-50%)', zIndex: 99999,
        background: 'rgba(2, 6, 23, 0.88)', color: '#e2e8f0', border: '1px solid rgba(56,189,248,0.5)',
        padding: '12px 22px', borderRadius: '12px', font: '600 20px Inter, system-ui, sans-serif',
        boxShadow: '0 10px 30px rgba(0,0,0,0.4)', maxWidth: '80vw', textAlign: 'center', pointerEvents: 'none',
      });
      document.body.appendChild(el);
    }
    el.textContent = t;
  }, text);
}

async function scrollMain(y) {
  await page.evaluate(top => document.querySelector('mat-sidenav-content')?.scrollTo({ top, behavior: 'smooth' }), y);
  await pause(900);
}

async function runScenario(title, wait) {
  await page.getByRole('button', { name: `Run ${title}` }).click();
  await pause(wait);
}

// 1. Sign in
await page.goto(`${BASE}/login`);
await page.waitForLoadState('networkidle');
await caption('PayStream: event-driven payments on Spring Boot microservices + Kafka');
await pause(3500);
await caption('Reviewer sign-in issues a short-lived JWT');
await page.getByRole('button', { name: /Continue as reviewer/ }).click();
await page.waitForURL('**/dashboard');
await page.getByRole('button', { name: 'Run Load burst' }).waitFor();
await pause(1500);

// 2. Load burst: the live feed and KPIs react in real time
await scrollMain(560);
await caption('Load burst: 200 payments → outbox → Kafka → fraud rules → double-entry ledger');
await runScenario('Load burst', 1500);
await pause(4500);
await scrollMain(0);
await caption('KPIs and throughput are computed live from the Kafka event stream');
await pause(5000);

// 3. Fraud and failure scenarios
await scrollMain(560);
await caption('Fraud rule: payment to a high-risk jurisdiction is REJECTED');
await runScenario('High-risk jurisdiction', 2500);
await caption('Ledger control: £900 from an account holding £500 FAILS with insufficient funds');
await runScenario('Insufficient funds', 2500);
await caption('Structuring + velocity: six £9,500 payments in seconds; the sixth is blocked');
await runScenario('Structuring burst', 4000);
await scrollMain(0);
await caption('Stopped payments, aggregated by fraud rule and ledger failure');
await pause(3000);

// 4. Payment journey across services
await scrollMain(560);
await caption('Click any payment to follow its journey across services');
await page.locator('tr.clickable', { hasText: 'Rejected' }).first().click();
await page.waitForURL('**/payments/*');
await pause(1200);
await caption('Audit trail (MongoDB): each hop with Kafka partition/offset, fraud score and reasons');
await pause(4500);

// 5. New payment form with idempotency key
await page.goto(`${BASE}/payments`);
await page.getByRole('button', { name: /New payment/ }).waitFor();
await caption('Payments: read from SQL Server, refreshed as events arrive');
await pause(2500);
await page.getByRole('button', { name: /New payment/ }).click();
await caption('Validated form; one Idempotency-Key per submission, so a retry can never pay twice');
await page.locator('mat-select[formcontrolname=debtorAccount]').click();
await page.getByRole('option', { name: /Northwind Treasury/ }).click();
await page.locator('mat-select[formcontrolname=creditorAccount]').click();
await page.getByRole('option', { name: /Fabrikam Suppliers/ }).click();
await page.locator('input[formcontrolname=amount]').pressSequentially('2500', { delay: 90 });
await pause(1200);
await page.getByRole('button', { name: 'Send payment' }).click();
await page.getByRole('button', { name: 'Open' }).click({ timeout: 10_000 });
await page.waitForURL('**/payments/*');
await caption('Accepted → screened → settled in milliseconds');
await pause(4000);

// 6. Ledger
await page.goto(`${BASE}/ledger`);
await page.locator('tr.clickable', { hasText: 'Northwind Treasury' }).waitFor();
await caption('Double-entry ledger: every settlement posts a balanced debit and credit');
await page.locator('tr.clickable', { hasText: 'Northwind Treasury' }).click();
await pause(4500);

// 7. Architecture
await page.goto(`${BASE}/architecture`);
await caption('Outbox, idempotency, exactly-once settlement, ordered locking, DLTs');
await pause(3000);
await scrollMain(900);
await pause(2500);
await caption('Java 21 · Spring Boot · Kafka · SQL Server · MongoDB · Angular · Docker · K8s');
await pause(3000);

await context.close();
await browser.close();
console.log('video written to', OUT);
