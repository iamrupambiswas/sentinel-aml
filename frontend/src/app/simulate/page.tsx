import type { Metadata } from "next";
import { Icon } from "@/components/Icon";
import { SubmitButton } from "@/components/SubmitButton";
import { ErrorPanel, PageHeader } from "@/components/ui";
import { getSession } from "@/lib/session";
import { hasRole } from "@/lib/tokens";
import { seedCustomers, sendTransaction } from "./actions";
import { CsvUpload } from "./CsvUpload";
import { ScenarioGrid } from "./ScenarioGrid";

export const metadata: Metadata = { title: "Simulation" };

const TYPES = ["WIRE", "NEFT", "RTGS", "IMPS", "UPI", "CASH_DEPOSIT", "CASH_WITHDRAWAL", "CARD"];
const CURRENCIES = ["INR", "USD", "EUR", "GBP", "AED"];

export default async function SimulatePage() {
  if (!hasRole(await getSession(), "ADMIN")) {
    return <ErrorPanel message="The simulation page is for administrators." retry={false} />;
  }

  return (
    <>
      <PageHeader
        eyebrow="Demo"
        title="Simulation"
        description="Stands in for the bank's own systems: onboard customers and send transactions, so detection, alerts and cases run end to end. Everything goes through the same API a real core-banking feed uses."
      />

      <div className="mb-6 flex items-start gap-3 rounded-xl border border-brand/25 bg-brand-soft/60 px-4 py-3 text-[13px] text-ink-2">
        <Icon name="flask" size={16} className="mt-0.5 shrink-0 text-brand" />
        <p>
          Run a scenario and watch: a notification pops up when detection raises the alert, and the Overview and Alerts pages update
          by themselves. Demo customers are clearly fake (made-up names, fake national IDs, accounts named <span className="font-mono">ACC-DEMO-…</span>).
        </p>
      </div>

      <ScenarioGrid />

      <div className="grid gap-5 xl:grid-cols-[1.15fr_1fr]">
        <CsvUpload />

        <div className="space-y-5">
          <section aria-labelledby="txn-h" className="card p-5">
            <h2 id="txn-h" className="card-title mb-1 flex items-center gap-2">
              <Icon name="activity" size={15} className="text-muted" />
              Send one transaction
            </h2>
            <p className="mb-4 text-[13px] text-muted">
              Accounts starting with <span className="font-mono">EXT-</span> are outside the bank. Use an account from a scenario to add to its story.
            </p>
            <form action={sendTransaction} className="space-y-3">
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <label className="label" htmlFor="src">From account</label>
                  <input id="src" name="sourceAccountId" required defaultValue="EXT-CASH" className="input font-mono" />
                </div>
                <div>
                  <label className="label" htmlFor="dst">To account</label>
                  <input id="dst" name="destinationAccountId" required placeholder="ACC-DEMO-…" className="input font-mono" />
                </div>
              </div>
              <div className="grid grid-cols-[1.4fr_0.8fr_1.2fr] gap-3">
                <div>
                  <label className="label" htmlFor="amount">Amount</label>
                  <input id="amount" name="amount" type="number" min="0.01" step="0.01" required placeholder="250000" className="input font-mono" />
                </div>
                <div>
                  <label className="label" htmlFor="currency">Currency</label>
                  <select id="currency" name="currency" defaultValue="INR" className="input">
                    {CURRENCIES.map((c) => <option key={c}>{c}</option>)}
                  </select>
                </div>
                <div>
                  <label className="label" htmlFor="type">Type</label>
                  <select id="type" name="type" defaultValue="WIRE" className="input">
                    {TYPES.map((t) => <option key={t} value={t}>{t.replaceAll("_", " ").toLowerCase()}</option>)}
                  </select>
                </div>
              </div>
              <details className="group">
                <summary className="inline-flex cursor-pointer list-none items-center gap-1 text-[12.5px] text-muted hover:text-ink">
                  <Icon name="chevron" size={11} className="transition-transform group-open:rotate-90" />
                  Counterparty (optional)
                </summary>
                <div className="mt-3 grid grid-cols-[1.6fr_1fr] gap-3">
                  <div>
                    <label className="label" htmlFor="cpName">Name</label>
                    <input id="cpName" name="counterpartyName" maxLength={255} placeholder="e.g. Viktor Strand" className="input" />
                  </div>
                  <div>
                    <label className="label" htmlFor="cpCountry">Country</label>
                    <input id="cpCountry" name="counterpartyCountry" maxLength={3} pattern="[A-Za-z]{2,3}" placeholder="IN, IRN…" className="input font-mono uppercase" />
                  </div>
                </div>
              </details>
              <SubmitButton className="btn btn-primary w-full" icon={<Icon name="play" size={13} />}>
                Send transaction
              </SubmitButton>
            </form>
          </section>

          <section aria-labelledby="seed-h" className="card p-5">
            <h2 id="seed-h" className="card-title mb-1 flex items-center gap-2">
              <Icon name="user" size={15} className="text-muted" />
              Bulk customers
            </h2>
            <p className="mb-4 text-[13px] text-muted">
              Adds 5,000 synthetic customers with accounts and risk ratings, so lookups and lists look like a real bank. Takes a few seconds.
            </p>
            <form action={seedCustomers}>
              <SubmitButton className="btn w-full">Seed 5,000 customers</SubmitButton>
            </form>
          </section>
        </div>
      </div>
    </>
  );
}
