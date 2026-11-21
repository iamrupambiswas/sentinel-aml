import Link from "next/link";
import { ALERT_STATUSES, api, errorMessage, type AlertStats, type AlertSummary, type DeadLetterQueueInfo, type Page } from "@/lib/api";
import { STATUS_LABEL, formatDateTime, ruleLabel } from "@/lib/format";
import { getSession } from "@/lib/session";
import { hasRole } from "@/lib/tokens";
import { ErrorPanel, PageHeader, RiskScore, Stat, StatusBadge } from "@/components/ui";

export default async function Dashboard() {
  const session = await getSession();
  const isAdmin = hasRole(session, "ADMIN");

  let stats: AlertStats | null = null;
  let top: AlertSummary[] = [];
  let dlq: DeadLetterQueueInfo[] = [];
  let error: string | null = null;
  try {
    [stats, top, dlq] = await Promise.all([
      api<AlertStats>("/api/v1/alerts/stats"),
      api<Page<AlertSummary>>("/api/v1/alerts?status=NEW&size=5").then((p) => p.content),
      isAdmin ? api<DeadLetterQueueInfo[]>("/api/v1/ops/dlq") : Promise.resolve([]),
    ]);
  } catch (e) {
    error = errorMessage(e);
  }
  const open = stats ? (stats.alertsByStatus.NEW ?? 0) + (stats.alertsByStatus.UNDER_REVIEW ?? 0) + (stats.alertsByStatus.ESCALATED ?? 0) : 0;
  const parked = dlq.reduce((n, q) => n + q.depth, 0);

  return (
    <>
      <PageHeader title={`Good to see you, ${session?.username ?? "analyst"}`} subtitle="Where the work is right now." />
      {error && <ErrorPanel message={error} />}

      {stats && (
        <div className="mb-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Stat label="Open alerts" value={open} hint="New, under review or escalated" />
          <Stat label="Total alerts" value={stats.alerts} />
          <Stat label="Hits folded per alert" value={stats.hitsPerAlert.toFixed(2)} hint={`${stats.hits} hits → ${stats.alerts} alerts by de-duplication`} />
          {isAdmin ? (
            <Stat label="Dead letters" value={<span className={parked ? "text-danger" : ""}>{parked}</span>} hint={parked ? "Needs attention: see Dead letters" : "Every queue is clean"} />
          ) : (
            <Stat label="Escalated" value={stats.alertsByStatus.ESCALATED ?? 0} />
          )}
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
        <section aria-labelledby="new-h" className="card min-w-0">
          <div className="flex items-center justify-between border-b border-line p-3">
            <h2 id="new-h" className="text-sm font-semibold">Highest-risk new alerts</h2>
            <Link href="/alerts?status=NEW" className="text-sm text-accent hover:underline">All new →</Link>
          </div>
          {top.length === 0 ? (
            <p className="p-6 text-center text-sm text-muted">No new alerts. The queue is clear.</p>
          ) : (
            <ul>
              {top.map((a) => (
                <li key={a.id} className="border-b border-line last:border-0">
                  <Link href={`/alerts/${a.id}`} className="flex items-center gap-3 p-3 hover:bg-canvas">
                    <RiskScore score={a.riskScore} />
                    <div className="min-w-0 flex-1">
                      <div className="text-sm"><span className="font-mono">{a.accountId}</span> · {ruleLabel(a.ruleCode)}</div>
                      <div className="truncate text-xs text-muted">{a.latestExplanation}</div>
                    </div>
                    <span className="hidden whitespace-nowrap text-xs text-muted sm:inline">{formatDateTime(a.lastDetectedAt)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        {stats && (
          <section aria-labelledby="status-h" className="card p-4">
            <h2 id="status-h" className="mb-3 text-sm font-semibold">Alerts by status</h2>
            <ul className="space-y-2">
              {ALERT_STATUSES.map((s) => {
                const n = stats!.alertsByStatus[s] ?? 0;
                const pct = stats!.alerts ? Math.round((n / stats!.alerts) * 100) : 0;
                return (
                  <li key={s}>
                    <Link href={`/alerts?status=${s}`} className="block rounded p-1 hover:bg-canvas">
                      <div className="mb-1 flex items-center justify-between text-sm">
                        <StatusBadge status={s} />
                        <span className="tabular-nums">{n}</span>
                      </div>
                      <div className="h-1.5 rounded bg-canvas" aria-hidden>
                        <div className="h-1.5 rounded bg-accent" style={{ width: `${pct}%` }} />
                      </div>
                      <span className="sr-only">{STATUS_LABEL[s]}: {n} of {stats!.alerts}</span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </section>
        )}
      </div>
    </>
  );
}
