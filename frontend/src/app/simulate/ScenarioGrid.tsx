"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import { Icon } from "@/components/Icon";
import { SubmitButton } from "@/components/SubmitButton";
import { ruleLabel } from "@/lib/format";
import { SCENARIOS, type Scenario } from "@/lib/simulation";
import { runScenario, type ScenarioResult } from "./actions";

type Risk = "LOW" | "MEDIUM" | "HIGH";
const RISKS: { value: Risk; label: string; hint: string }[] = [
  // detection-service CustomerRiskLookup: the alert's risk score is multiplied by these
  { value: "LOW", label: "Low", hint: "score × 1.0" },
  { value: "MEDIUM", label: "Medium", hint: "score × 1.15" },
  { value: "HIGH", label: "High", hint: "score × 1.3" },
];

/** The six one-click scenarios, sharing one "customer risk" choice. */
export function ScenarioGrid() {
  const [risk, setRisk] = useState<Risk>("MEDIUM");
  return (
    <section aria-labelledby="scenarios-h" className="mb-8">
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="scenarios-h" className="text-[15px] font-semibold tracking-tight text-ink">Scenarios</h2>
          <p className="mt-0.5 text-[13px] text-muted">Each run creates a new demo customer and account, then sends the transactions. One alert per run.</p>
        </div>
        <div>
          <div className="label mb-1">Customer risk</div>
          <div role="radiogroup" aria-label="Customer risk for new demo customers" className="flex rounded-lg border border-line bg-surface p-0.5 shadow-card">
            {RISKS.map((r) => (
              <button
                key={r.value}
                type="button"
                role="radio"
                aria-checked={risk === r.value}
                title={`Risk ${r.label.toLowerCase()}: ${r.hint}`}
                onClick={() => setRisk(r.value)}
                className={`rounded-md px-3 py-1 text-[12.5px] transition-colors ${
                  risk === r.value ? "bg-brand-soft font-medium text-brand" : "text-muted hover:text-ink"
                }`}
              >
                {r.label}
              </button>
            ))}
          </div>
        </div>
      </div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {SCENARIOS.map((s, i) => (
          <ScenarioCard key={s.key} scenario={s} risk={risk} index={i} />
        ))}
      </div>
    </section>
  );
}

function ScenarioCard({ scenario: s, risk, index }: { scenario: Scenario; risk: Risk; index: number }) {
  const [result, action] = useActionState<ScenarioResult | null, FormData>(runScenario, null);
  return (
    <article className="card flex flex-col p-5 animate-rise" style={{ animationDelay: `${index * 40}ms` }}>
      <div className="mb-3 flex items-center justify-between gap-2">
        <span className="chip bg-sunken font-mono text-muted">{ruleLabel(s.ruleCode)}</span>
        {s.key === "behavioral" && <span className="text-[11.5px] text-muted">takes ~10 s</span>}
      </div>
      <h3 className="text-[14.5px] font-semibold tracking-tight text-ink">{s.title}</h3>
      <p className="mt-1 text-[13px] leading-relaxed text-muted">{s.story}</p>
      <ol className="mt-3 space-y-1.5 text-[12.5px] text-ink-2">
        {s.steps.map((step, i) => (
          <li key={i} className="flex gap-2">
            <span className="mt-px flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-sunken font-mono text-[10px] text-muted">{i + 1}</span>
            {step}
          </li>
        ))}
      </ol>
      <div className="mt-auto pt-4">
        <form action={action}>
          <input type="hidden" name="scenario" value={s.key} />
          <input type="hidden" name="risk" value={risk} />
          <SubmitButton className="btn btn-primary w-full" icon={<Icon name="play" size={13} />}>
            Run scenario
          </SubmitButton>
        </form>
        {result && (
          <div
            role={result.ok ? "status" : "alert"}
            className={`mt-3 rounded-lg border px-3 py-2.5 text-[12.5px] animate-rise ${
              result.ok ? "border-ok/30 bg-ok-soft" : "border-danger/30 bg-danger-soft"
            }`}
          >
            <div className={result.ok ? "text-ok" : "text-danger"}>{result.message}</div>
            {result.accountId && (
              <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-ink-2">
                <span className="font-mono">{result.accountId}</span>
                {result.customerName && <span className="text-muted">{result.customerName}</span>}
                <Link href={`/alerts?accountId=${encodeURIComponent(result.accountId)}`} className="inline-flex items-center gap-0.5 text-brand hover:underline">
                  Alerts <Icon name="chevron" size={11} />
                </Link>
                <Link href={`/customers?account=${encodeURIComponent(result.accountId)}`} className="text-brand hover:underline">
                  Customer
                </Link>
              </div>
            )}
          </div>
        )}
      </div>
    </article>
  );
}
