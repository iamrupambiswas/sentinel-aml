import type { Metadata } from "next";
import { api, errorMessage, type RuleVersionView, type RuleView } from "@/lib/api";
import { formatDateTime, formatThreshold, formatWindow, thresholdLabel } from "@/lib/format";
import { getSession } from "@/lib/session";
import { hasRole } from "@/lib/tokens";
import { BackLink, ErrorPanel, Flash, KeyValue, PageHeader } from "@/components/ui";
import { updateRule } from "../actions";

export const metadata: Metadata = { title: "Rule" };

export default async function RulePage({
  params,
  searchParams,
}: {
  params: Promise<{ code: string }>;
  searchParams: Promise<{ msg?: string; err?: string }>;
}) {
  const { code } = await params;
  const { msg, err } = await searchParams;
  const isAdmin = hasRole(await getSession(), "ADMIN");

  let rule: RuleView | null = null;
  let versions: RuleVersionView[] = [];
  let error: string | null = null;
  try {
    [rule, versions] = await Promise.all([
      api<RuleView>(`/api/v1/rules/${encodeURIComponent(code)}`),
      api<RuleVersionView[]>(`/api/v1/rules/${encodeURIComponent(code)}/versions`),
    ]);
  } catch (e) {
    error = errorMessage(e);
  }

  if (!rule) {
    return (
      <>
        <BackLink href="/rules">Detection rules</BackLink>
        <ErrorPanel message={error ?? "Rule not found."} />
      </>
    );
  }
  const live = rule.active && rule.loadedVersion === rule.version;

  return (
    <>
      <BackLink href="/rules">Detection rules</BackLink>
      <PageHeader
        eyebrow={<span className="font-mono normal-case tracking-normal">{rule.ruleCode}</span>}
        title={rule.ruleName}
        description={rule.description}
        actions={
          <span className={`chip ${!rule.active ? "bg-sunken text-muted" : live ? "bg-ok-soft text-ok" : "bg-warn-soft text-warn"}`}>
            {!rule.active ? "Disabled" : live ? `Live · v${rule.version}` : "Reloading"}
          </span>
        }
      />
      <Flash msg={msg} err={err} />

      <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
        {isAdmin ? (
          <form action={updateRule} className="card space-y-4 p-5">
            <h2 className="card-title">Change this rule</h2>
            <input type="hidden" name="ruleCode" value={rule.ruleCode} />
            <input type="hidden" name="expectedVersion" value={rule.version} />
            <input type="hidden" name="currentThreshold" value={rule.thresholdAmount ?? ""} />
            <input type="hidden" name="currentWindow" value={rule.windowMinutes} />
            <input type="hidden" name="currentWeight" value={rule.weight} />
            <input type="hidden" name="currentActive" value={String(rule.active)} />
            <div>
              <label className="label" htmlFor="thresholdAmount">{thresholdLabel(rule.ruleCode)}</label>
              <input id="thresholdAmount" name="thresholdAmount" type="number" min={0} step="any" defaultValue={rule.thresholdAmount ?? ""} className="input font-mono" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="label" htmlFor="windowMinutes">Window (minutes)</label>
                <input id="windowMinutes" name="windowMinutes" type="number" min={0} max={527040} defaultValue={rule.windowMinutes} className="input font-mono" />
              </div>
              <div>
                <label className="label" htmlFor="weight">Weight (0–100)</label>
                <input id="weight" name="weight" type="number" min={0} max={100} step="0.01" defaultValue={rule.weight} className="input font-mono" />
              </div>
            </div>
            <label className="flex items-center gap-2 text-[13px] text-ink-2">
              <input type="checkbox" name="active" defaultChecked={rule.active} className="h-4 w-4 accent-[var(--brand)]" />
              Rule is active
            </label>
            <div>
              <label className="label" htmlFor="reason">Reason</label>
              <textarea id="reason" name="reason" required maxLength={500} rows={2} className="input" placeholder="e.g. Raise CTR to ₹10 lakh per circular 2026/11" />
            </div>
            <button className="btn btn-primary w-full" type="submit">Save as version {rule.version + 1}</button>
            <p className="text-[12px] leading-relaxed text-muted">
              Saved under your username in the version history and the audit trail. If someone else saved first, you’ll be asked to reload.
            </p>
          </form>
        ) : (
          <section className="card space-y-4 p-5">
            <KeyValue
              items={[
                ["Threshold", formatThreshold(rule.ruleCode, rule.thresholdAmount)],
                ["Window", formatWindow(rule.windowMinutes)],
                ["Weight", Number(rule.weight).toFixed(0)],
                ["Active", rule.active ? "Yes" : "No"],
              ]}
            />
            <p className="border-t border-line pt-3 text-[12px] text-muted">Only administrators can change rules.</p>
          </section>
        )}

        <section aria-labelledby="history-h" className="card min-w-0 overflow-hidden">
          <div className="card-head">
            <h2 id="history-h" className="card-title">Version history</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="table min-w-[640px]">
              <thead>
                <tr>
                  <th className="th">Version</th>
                  <th className="th">Threshold</th>
                  <th className="th">Window</th>
                  <th className="th">Weight</th>
                  <th className="th">By</th>
                  <th className="th">Reason</th>
                  <th className="th">When</th>
                </tr>
              </thead>
              <tbody>
                {versions.map((v) => (
                  <tr key={v.version} className="row">
                    <td className="td font-mono">v{v.version}{!v.active && <span className="text-muted"> · off</span>}</td>
                    <td className="td font-mono">{formatThreshold(rule.ruleCode, v.thresholdAmount)}</td>
                    <td className="td">{formatWindow(v.windowMinutes)}</td>
                    <td className="td font-mono">{Number(v.weight).toFixed(0)}</td>
                    <td className="td font-medium">{v.changedBy}</td>
                    <td className="td text-muted">{v.reason}</td>
                    <td className="td whitespace-nowrap text-muted">{formatDateTime(v.changedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </>
  );
}
