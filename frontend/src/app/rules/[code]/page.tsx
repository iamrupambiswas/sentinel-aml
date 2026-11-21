import Link from "next/link";
import { api, errorMessage, type RuleVersionView, type RuleView } from "@/lib/api";
import { formatDateTime, formatThreshold, formatWindow, thresholdLabel } from "@/lib/format";
import { getSession } from "@/lib/session";
import { hasRole } from "@/lib/tokens";
import { ErrorPanel, Flash, PageHeader } from "@/components/ui";
import { updateRule } from "../actions";

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

  return (
    <>
      <Link href="/rules" className="mb-4 inline-block text-sm text-accent hover:underline">← Rules</Link>
      {!rule ? (
        <ErrorPanel message={error ?? "Rule not found."} />
      ) : (
        <>
          <PageHeader title={rule.ruleName} subtitle={rule.description} />
          <Flash msg={msg} err={err} />
          <div className="grid gap-4 lg:grid-cols-[360px_1fr]">
            {isAdmin ? (
              <form action={updateRule} className="card space-y-3 p-4">
                <h2 className="text-sm font-semibold">Change rule · now v{rule.version}</h2>
                <input type="hidden" name="ruleCode" value={rule.ruleCode} />
                <input type="hidden" name="expectedVersion" value={rule.version} />
                <input type="hidden" name="currentThreshold" value={rule.thresholdAmount ?? ""} />
                <input type="hidden" name="currentWindow" value={rule.windowMinutes} />
                <input type="hidden" name="currentWeight" value={rule.weight} />
                <input type="hidden" name="currentActive" value={String(rule.active)} />
                <div>
                  <label className="label" htmlFor="thresholdAmount">{thresholdLabel(rule.ruleCode)}</label>
                  <input id="thresholdAmount" name="thresholdAmount" type="number" min={0} step="any" defaultValue={rule.thresholdAmount ?? ""} className="input tabular-nums" />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="label" htmlFor="windowMinutes">Window (minutes)</label>
                    <input id="windowMinutes" name="windowMinutes" type="number" min={0} max={527040} defaultValue={rule.windowMinutes} className="input tabular-nums" />
                  </div>
                  <div>
                    <label className="label" htmlFor="weight">Weight (0–100)</label>
                    <input id="weight" name="weight" type="number" min={0} max={100} step="0.01" defaultValue={rule.weight} className="input tabular-nums" />
                  </div>
                </div>
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" name="active" defaultChecked={rule.active} /> Active
                </label>
                <div>
                  <label className="label" htmlFor="reason">Reason (required, audited)</label>
                  <textarea id="reason" name="reason" required maxLength={500} rows={2} className="input" placeholder="e.g. Raise CTR to ₹10 lakh per circular 2026/11" />
                </div>
                <button className="btn btn-primary w-full" type="submit">Save as v{rule.version + 1}</button>
                <p className="text-xs text-muted">Recorded under your username. If someone saved meanwhile, you will be asked to reload.</p>
              </form>
            ) : (
              <div className="card p-4 text-sm">
                <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5">
                  <dt className="text-muted">Threshold</dt><dd className="tabular-nums">{formatThreshold(rule.ruleCode, rule.thresholdAmount)}</dd>
                  <dt className="text-muted">Window</dt><dd>{formatWindow(rule.windowMinutes)}</dd>
                  <dt className="text-muted">Weight</dt><dd className="tabular-nums">{Number(rule.weight).toFixed(2)}</dd>
                  <dt className="text-muted">Active</dt><dd>{rule.active ? "Yes" : "No"}</dd>
                </dl>
                <p className="mt-3 text-xs text-muted">Only ADMIN can change rules.</p>
              </div>
            )}

            <section aria-labelledby="history-h" className="card min-w-0 overflow-x-auto">
              <h2 id="history-h" className="px-3 pt-3 text-sm font-semibold">Version history</h2>
              <table className="w-full min-w-[640px] border-collapse">
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
                    <tr key={v.version}>
                      <td className="td tabular-nums">v{v.version}{!v.active && <span className="text-muted"> · off</span>}</td>
                      <td className="td tabular-nums">{formatThreshold(rule.ruleCode, v.thresholdAmount)}</td>
                      <td className="td">{formatWindow(v.windowMinutes)}</td>
                      <td className="td tabular-nums">{Number(v.weight).toFixed(0)}</td>
                      <td className="td">{v.changedBy}</td>
                      <td className="td text-muted">{v.reason}</td>
                      <td className="td whitespace-nowrap text-muted">{formatDateTime(v.changedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          </div>
        </>
      )}
    </>
  );
}
