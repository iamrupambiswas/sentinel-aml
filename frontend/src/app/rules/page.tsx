import type { Metadata } from "next";
import Link from "next/link";
import { api, errorMessage, type RuleView } from "@/lib/api";
import { formatThreshold, formatWindow } from "@/lib/format";
import { ErrorPanel, PageHeader } from "@/components/ui";

export const metadata: Metadata = { title: "Detection rules" };

export default async function RulesPage() {
  let rules: RuleView[] | null = null;
  let error: string | null = null;
  try {
    rules = await api<RuleView[]>("/api/v1/rules");
  } catch (e) {
    error = errorMessage(e);
  }

  return (
    <>
      <PageHeader
        eyebrow="Configuration"
        title="Detection rules"
        description="Thresholds live in the database and reload on every detection pod within seconds of a change. Live means this pod already runs the saved version."
      />
      {error && <ErrorPanel message={error} />}
      {rules && (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="table min-w-[780px]">
              <thead>
                <tr>
                  <th className="th">Rule</th>
                  <th className="th">Threshold</th>
                  <th className="th">Window</th>
                  <th className="th">Weight</th>
                  <th className="th">State</th>
                  <th className="th text-right">Version</th>
                </tr>
              </thead>
              <tbody>
                {rules.map((r) => {
                  const live = r.active && r.loadedVersion === r.version;
                  return (
                    <tr key={r.ruleCode} className="row">
                      <td className="td">
                        <Link href={`/rules/${encodeURIComponent(r.ruleCode)}`} className="font-medium text-ink hover:text-brand">{r.ruleName}</Link>
                        <div className="font-mono text-[11.5px] text-muted">{r.ruleCode}</div>
                      </td>
                      <td className="td font-mono tabular-nums text-ink-2">{formatThreshold(r.ruleCode, r.thresholdAmount)}</td>
                      <td className="td text-ink-2">{formatWindow(r.windowMinutes)}</td>
                      <td className="td">
                        <div className="flex items-center gap-2">
                          <div className="h-1.5 w-16 overflow-hidden rounded-full bg-sunken" aria-hidden>
                            <div className="h-full rounded-full bg-brand" style={{ width: `${Number(r.weight)}%` }} />
                          </div>
                          <span className="font-mono text-[12px] tabular-nums">{Number(r.weight).toFixed(0)}</span>
                        </div>
                      </td>
                      <td className="td">
                        {!r.active ? (
                          <span className="chip bg-sunken text-muted">Disabled</span>
                        ) : live ? (
                          <span className="chip bg-ok-soft text-ok">Live</span>
                        ) : (
                          <span className="chip bg-warn-soft text-warn">Reloading · pod on v{r.loadedVersion ?? "–"}</span>
                        )}
                      </td>
                      <td className="td text-right font-mono tabular-nums">v{r.version}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}
