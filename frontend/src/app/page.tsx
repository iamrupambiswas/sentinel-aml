import Link from "next/link";
import {
  ALERT_STATUSES,
  api,
  errorMessage,
  type AlertStats,
  type AlertSummary,
  type AuditEntry,
  type CaseSummary,
  type DeadLetterQueueInfo,
  type Page,
} from "@/lib/api";
import { STATUS_LABEL, actionLabel, ruleLabel, timeAgo } from "@/lib/format";
import { getSession } from "@/lib/session";
import { hasRole } from "@/lib/tokens";
import { Icon } from "@/components/Icon";
import { CaseStatusBadge, EmptyState, ErrorPanel, PageHeader, PriorityBadge, RiskScore, StatTile } from "@/components/ui";

const BAR_TONE: Record<string, string> = {
  NEW: "bg-info",
  UNDER_REVIEW: "bg-warn",
  ESCALATED: "bg-danger",
  CLOSED_CONFIRMED: "bg-danger/60",
  CLOSED_FALSE_POSITIVE: "bg-line-strong",
};

function greeting(): string {
  const h = Number(new Intl.DateTimeFormat("en-IN", { hour: "numeric", hour12: false, timeZone: "Asia/Kolkata" }).format(new Date()));
  return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
}

export default async function Overview() {
  const session = await getSession();
  const isAdmin = hasRole(session, "ADMIN");
  const isSenior = hasRole(session, "SENIOR_ANALYST");

  let stats: AlertStats | null = null;
  let top: AlertSummary[] = [];
  let openCases: Page<CaseSummary> | null = null;
  let dlq: DeadLetterQueueInfo[] = [];
  let activity: AuditEntry[] = [];
  let error: string | null = null;
  try {
    [stats, top, openCases, dlq, activity] = await Promise.all([
      api<AlertStats>("/api/v1/alerts/stats"),
      api<Page<AlertSummary>>("/api/v1/alerts?status=NEW&size=6").then((p) => p.content),
      api<Page<CaseSummary>>("/api/v1/cases?open=true&size=5"),
      isAdmin ? api<DeadLetterQueueInfo[]>("/api/v1/ops/dlq") : Promise.resolve([]),
      isSenior ? api<Page<AuditEntry>>("/api/v1/audit?size=6").then((p) => p.content) : Promise.resolve([]),
    ]);
  } catch (e) {
    error = errorMessage(e);
  }
  const open = stats ? (stats.alertsByStatus.NEW ?? 0) + (stats.alertsByStatus.UNDER_REVIEW ?? 0) + (stats.alertsByStatus.ESCALATED ?? 0) : 0;
  const parked = dlq.reduce((n, q) => n + q.depth, 0);

  return (
    <>
      <PageHeader
        eyebrow="Overview"
        title={`${greeting()}, ${session?.username ?? ""}`}
        description="What needs a decision now. Numbers update by themselves as new alerts arrive."
        actions={
          <Link href="/alerts?status=NEW" className="btn btn-primary">
            Work the queue
            <Icon name="chevron" size={13} />
          </Link>
        }
      />
      {error && <ErrorPanel message={error} />}

      {stats && (
        <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatTile label="Open alerts" value={open} hint="New, under review or escalated" href="/alerts" tone={open ? "danger" : "ok"} />
          <StatTile label="Open cases" value={openCases?.totalElements ?? 0} hint="Open or being investigated" href="/cases" />
          <StatTile
            label="Noise removed"
            value={`${stats.hitsPerAlert.toFixed(2)}×`}
            hint={`${stats.hits} rule hits folded into ${stats.alerts} alerts`}
          />
          {isAdmin ? (
            <StatTile label="Dead letters" value={parked} tone={parked ? "danger" : "ok"} hint={parked ? "Messages need attention" : "Every queue is clean"} href="/ops" />
          ) : (
            <StatTile label="Escalated" value={stats.alertsByStatus.ESCALATED ?? 0} hint="Awaiting a senior decision" href="/alerts?status=ESCALATED" />
          )}
        </div>
      )}

      <div className="grid gap-5 xl:grid-cols-[1fr_340px]">
        <section aria-labelledby="attention-h" className="card min-w-0 overflow-hidden">
          <div className="card-head">
            <h2 id="attention-h" className="card-title">Needs attention</h2>
            <Link href="/alerts?status=NEW" className="text-[12.5px] text-brand hover:underline">All new alerts</Link>
          </div>
          {top.length === 0 ? (
            <EmptyState icon="check" title="No new alerts">The queue is clear. New alerts appear here as soon as they are detected.</EmptyState>
          ) : (
            <ul>
              {top.map((a) => (
                <li key={a.id} className="border-b border-line last:border-0">
                  <Link href={`/alerts/${a.id}`} className="row flex items-center gap-4 px-4 py-3">
                    <RiskScore score={a.riskScore} />
                    <div className="min-w-0 flex-1">
                      <div className="text-[13px] text-ink">
                        <span className="font-mono font-medium">{a.accountId}</span>
                        <span className="text-muted"> · {ruleLabel(a.ruleCode)}</span>
                        {a.hitCount > 1 && <span className="chip ml-2 bg-sunken text-muted">{a.hitCount} hits</span>}
                      </div>
                      <div className="mt-0.5 truncate text-[12.5px] text-muted">{a.latestExplanation}</div>
                    </div>
                    <span className="hidden whitespace-nowrap text-[12px] text-muted sm:block">{timeAgo(a.lastDetectedAt)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        {stats && (
          <section aria-labelledby="pipeline-h" className="card p-5">
            <h2 id="pipeline-h" className="card-title mb-4">Alert pipeline</h2>
            <ul className="space-y-3.5">
              {ALERT_STATUSES.map((s) => {
                const n = stats!.alertsByStatus[s] ?? 0;
                const pct = stats!.alerts ? Math.round((n / stats!.alerts) * 100) : 0;
                return (
                  <li key={s}>
                    <Link href={`/alerts?status=${s}`} className="group block">
                      <div className="mb-1.5 flex items-center justify-between text-[12.5px]">
                        <span className="text-ink-2 group-hover:text-ink">{STATUS_LABEL[s]}</span>
                        <span className="font-mono tabular-nums text-muted">{n}</span>
                      </div>
                      <div className="h-1.5 overflow-hidden rounded-full bg-sunken" aria-hidden>
                        <div className={`h-full rounded-full ${BAR_TONE[s]} transition-[width] duration-700`} style={{ width: `${Math.max(pct, n ? 3 : 0)}%` }} />
                      </div>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </section>
        )}
      </div>

      <div className={`mt-5 grid gap-5 ${isSenior ? "xl:grid-cols-2" : ""}`}>
        <section aria-labelledby="cases-h" className="card min-w-0 overflow-hidden">
          <div className="card-head">
            <h2 id="cases-h" className="card-title">Open cases</h2>
            <Link href="/cases" className="text-[12.5px] text-brand hover:underline">All cases</Link>
          </div>
          {!openCases || openCases.content.length === 0 ? (
            <EmptyState icon="folder" title="No open cases">Open a case from any alert to group related alerts and record your investigation.</EmptyState>
          ) : (
            <ul>
              {openCases.content.map((c) => (
                <li key={c.id} className="border-b border-line last:border-0">
                  <Link href={`/cases/${c.id}`} className="row flex items-center gap-4 px-4 py-3">
                    <PriorityBadge priority={c.priority} />
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[13px] font-medium text-ink">{c.title}</div>
                      <div className="mt-0.5 text-[12px] text-muted">
                        <span className="font-mono">{c.caseNumber}</span> · {c.alertCount} alert{c.alertCount === 1 ? "" : "s"} · {c.assignee ?? "unassigned"}
                      </div>
                    </div>
                    <CaseStatusBadge status={c.status} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        {isSenior && (
          <section aria-labelledby="activity-h" className="card min-w-0 overflow-hidden">
            <div className="card-head">
              <h2 id="activity-h" className="card-title">Recent activity</h2>
              <Link href="/audit" className="text-[12.5px] text-brand hover:underline">Audit trail</Link>
            </div>
            {activity.length === 0 ? (
              <EmptyState icon="ledger" title="Nothing recorded yet" />
            ) : (
              <ol className="px-4 py-2">
                {activity.map((e) => (
                  <li key={e.seq} className="relative flex gap-3 py-2.5 pl-4 before:absolute before:left-[3px] before:top-0 before:h-full before:w-px before:bg-line last:before:h-4">
                    <span className="absolute left-0 top-[15px] h-[7px] w-[7px] rounded-full border-2 border-surface bg-brand" aria-hidden />
                    <div className="min-w-0 flex-1 text-[13px]">
                      <span className="font-medium text-ink">{e.actor}</span>
                      <span className="text-muted"> · {actionLabel(e.action)} · </span>
                      <span className="font-mono text-[12px] text-ink-2">{e.entityId.length > 18 ? `${e.entityId.slice(0, 8)}…` : e.entityId}</span>
                    </div>
                    <span className="whitespace-nowrap text-[12px] text-muted">{timeAgo(e.occurredAt)}</span>
                  </li>
                ))}
              </ol>
            )}
          </section>
        )}
      </div>
    </>
  );
}
