import Link from "next/link";
import { api, errorMessage, type RuleView } from "@/lib/api";
import { formatThreshold, formatWindow } from "@/lib/format";
import { ErrorPanel, PageHeader } from "@/components/ui";

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
        title="Detection rules"
        subtitle="Thresholds live in the database. “Live” means this detection pod already runs the saved version."
      />
      {error && <ErrorPanel message={error} />}
      {rules && (
        <div className="card overflow-x-auto">
          <table className="w-full min-w-[720px] border-collapse">
            <thead>
              <tr>
                <th className="th">Rule</th>
                <th className="th">Threshold</th>
                <th className="th">Window</th>
                <th className="th text-right">Weight</th>
                <th className="th">State</th>
                <th className="th text-right">Version</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((r) => {
                const live = r.active && r.loadedVersion === r.version;
                return (
                  <tr key={r.ruleCode} className="hover:bg-canvas">
                    <td className="td">
                      <Link href={`/rules/${encodeURIComponent(r.ruleCode)}`} className="font-medium text-accent hover:underline">{r.ruleName}</Link>
                      <div className="font-mono text-xs text-muted">{r.ruleCode}</div>
                    </td>
                    <td className="td tabular-nums">{formatThreshold(r.ruleCode, r.thresholdAmount)}</td>
                    <td className="td">{formatWindow(r.windowMinutes)}</td>
                    <td className="td text-right tabular-nums">{Number(r.weight).toFixed(0)}</td>
                    <td className="td">
                      {!r.active ? (
                        <span className="text-muted">Disabled</span>
                      ) : live ? (
                        <span className="text-ok">Live</span>
                      ) : (
                        <span className="text-warn">Reloading (pod has v{r.loadedVersion ?? "–"})</span>
                      )}
                    </td>
                    <td className="td text-right tabular-nums">v{r.version}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
