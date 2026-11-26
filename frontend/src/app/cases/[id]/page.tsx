import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError, api, errorMessage, type CaseDetail, type Priority } from "@/lib/api";
import { CASE_STATUS_LABEL, PRIORITY_LABEL, actionLabel, formatDateTime, ruleLabel, timeAgo } from "@/lib/format";
import { getSession } from "@/lib/session";
import { hasRole } from "@/lib/tokens";
import { Icon } from "@/components/Icon";
import { SubmitButton } from "@/components/SubmitButton";

import { BackLink, CaseStatusBadge, EmptyState, ErrorPanel, KeyValue, PageHeader, PriorityBadge, RiskScore, StatusBadge } from "@/components/ui";
import { addAlert, addNote, assignToMe, changeCaseStatus, updateOwnerAndPriority } from "./actions";

export const metadata: Metadata = { title: "Case" };

const PRIORITIES: Priority[] = ["HIGH", "MEDIUM", "LOW"];

/** Audit details are canonical JSON; show the meaningful fields as text. */
function describe(details: string): string {
  try {
    const d = JSON.parse(details) as Record<string, unknown>;
    if (d.from !== undefined && d.to !== undefined) return `${String(d.from).replaceAll("_", " ").toLowerCase()} → ${String(d.to).replaceAll("_", " ").toLowerCase()}`;
    if (d.alertId) return `alert ${String(d.alertId).slice(0, 8)}…`;
    if (Array.isArray(d.alerts)) return `${d.alerts.length} alert${d.alerts.length === 1 ? "" : "s"}`;
    if (d.length) return `${d.length} characters`;
    return "";
  } catch {
    return "";
  }
}

export default async function CasePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const session = await getSession();
  const isSenior = hasRole(session, "SENIOR_ANALYST");

  let detail: CaseDetail | null = null;
  let error: string | null = null;
  try {
    detail = await api<CaseDetail>(`/api/v1/cases/${encodeURIComponent(id)}`);
  } catch (e) {
    if (e instanceof ApiError && (e.status === 404 || e.status === 400)) notFound(); // unknown or malformed ID
    error = errorMessage(e);
  }
  if (!detail) {
    return (
      <>
        <BackLink href="/cases">Cases</BackLink>
        <ErrorPanel message={error ?? "Case not found."} />
      </>
    );
  }

  const c = detail.summary;
  const closed = c.status === "CLOSED_NO_ACTION" || c.status === "CLOSED_REPORTED";
  const progress = detail.allowedNextStatuses.filter((s) => s === "INVESTIGATING");
  const closing = detail.allowedNextStatuses.filter((s) => s.startsWith("CLOSED"));

  return (
    <>
      <BackLink href="/cases">Cases</BackLink>
      <PageHeader
        eyebrow={<span className="font-mono normal-case tracking-normal">{c.caseNumber}</span>}
        title={c.title}
        description={
          <>
            Opened by <span className="text-ink-2">{c.createdBy}</span> {timeAgo(c.createdAt)} · {c.alertCount} alert{c.alertCount === 1 ? "" : "s"}
          </>
        }
        actions={
          <>
            <PriorityBadge priority={c.priority} />
            <CaseStatusBadge status={c.status} />
          </>
        }
      />

      <div className="grid gap-5 xl:grid-cols-[1fr_340px]">
        <div className="min-w-0 space-y-5">
          {closed && (
            <section className="card border-ok/30 p-5">
              <div className="eyebrow mb-1.5">Resolution · {formatDateTime(detail.closedAt)}</div>
              <p className="whitespace-pre-wrap text-[14px] leading-relaxed text-ink">{detail.resolution}</p>
            </section>
          )}

          <section aria-labelledby="alerts-h" className="card overflow-hidden">
            <div className="card-head">
              <h2 id="alerts-h" className="card-title">Alerts in this case</h2>
            </div>
            <ul>
              {detail.alerts.map((a) => (
                <li key={a.id} className="border-b border-line last:border-0">
                  <Link href={`/alerts/${a.id}`} className="row flex items-center gap-4 px-4 py-3">
                    <RiskScore score={a.riskScore} />
                    <div className="min-w-0 flex-1">
                      <div className="text-[13px]">
                        <span className="font-mono font-medium text-ink">{a.accountId}</span>
                        <span className="text-muted"> · {ruleLabel(a.ruleCode)} · {a.hitCount} hit{a.hitCount === 1 ? "" : "s"}</span>
                      </div>
                      <div className="mt-0.5 truncate text-[12.5px] text-muted">{a.latestExplanation}</div>
                    </div>
                    <StatusBadge status={a.status} />
                  </Link>
                </li>
              ))}
            </ul>
            {!closed && (
              <form action={addAlert} className="flex gap-2 border-t border-line bg-sunken/40 px-4 py-3">
                <input type="hidden" name="id" value={c.id} />
                <label className="sr-only" htmlFor="alertId">Alert ID to add</label>
                <input id="alertId" name="alertId" placeholder="Paste an alert ID to add it" className="input font-mono" />
                <SubmitButton icon={<Icon name="plus" size={14} />}>Add</SubmitButton>
              </form>
            )}
          </section>

          <section aria-labelledby="notes-h" className="card overflow-hidden">
            <div className="card-head">
              <h2 id="notes-h" className="card-title">Investigation notes</h2>
              <span className="text-[12px] text-muted">{detail.notes.length}</span>
            </div>
            {!closed && (
              <form action={addNote} className="border-b border-line p-4">
                <input type="hidden" name="id" value={c.id} />
                <label className="sr-only" htmlFor="body">New note</label>
                <textarea id="body" name="body" rows={3} maxLength={5000} required className="input" placeholder="What you found, who you contacted, what is next" />
                <div className="mt-2 flex justify-end">
                  <SubmitButton className="btn btn-primary">Add note</SubmitButton>
                </div>
              </form>
            )}
            {detail.notes.length === 0 ? (
              <EmptyState icon="ledger" title="No notes yet" />
            ) : (
              <ul>
                {detail.notes.map((n) => (
                  <li key={n.id} className="border-b border-line px-4 py-3.5 last:border-0">
                    <div className="mb-1 flex items-center gap-2 text-[12px] text-muted">
                      <span className="font-medium text-ink-2">{n.author}</span>· {formatDateTime(n.createdAt)}
                    </div>
                    <p className="whitespace-pre-wrap text-[13.5px] leading-relaxed text-ink">{n.body}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <aside className="space-y-5">
          <section className="card p-5">
            <KeyValue
              items={[
                ["Owner", c.assignee ?? <span className="text-muted">Unassigned</span>],
                ["Priority", PRIORITY_LABEL[c.priority]],
                ["Status", CASE_STATUS_LABEL[c.status]],
                ["Updated", formatDateTime(c.updatedAt)],
                ["Version", <span key="v" className="font-mono">{c.version}</span>],
              ]}
            />
          </section>

          {!closed && (
            <section aria-labelledby="manage-h" className="card space-y-4 p-5">
              <h2 id="manage-h" className="card-title">Manage</h2>
              {c.assignee !== session?.username && (
                <form action={assignToMe}>
                  <input type="hidden" name="id" value={c.id} />
                  <input type="hidden" name="expectedVersion" value={c.version} />
                  <SubmitButton className="btn w-full" icon={<Icon name="user" size={14} />}>
                    Assign to me
                  </SubmitButton>
                </form>
              )}
              {progress.map((s) => (
                <form key={s}>
                  <input type="hidden" name="id" value={c.id} />
                  <input type="hidden" name="expectedVersion" value={c.version} />
                  <SubmitButton className="btn btn-primary w-full" formAction={changeCaseStatus.bind(null, s)}>
                    Start investigating
                  </SubmitButton>
                </form>
              ))}
              <form action={updateOwnerAndPriority} className="space-y-3 border-t border-line pt-4">
                <input type="hidden" name="id" value={c.id} />
                <input type="hidden" name="expectedVersion" value={c.version} />
                <input type="hidden" name="currentAssignee" value={c.assignee ?? ""} />
                <input type="hidden" name="currentPriority" value={c.priority} />
                <div>
                  <label className="label" htmlFor="assignee">Owner</label>
                  <input id="assignee" name="assignee" defaultValue={c.assignee ?? ""} placeholder="Username, empty = unassigned" className="input" />
                </div>
                <div>
                  <label className="label" htmlFor="priority">Priority</label>
                  <select id="priority" name="priority" defaultValue={c.priority} className="input">
                    {PRIORITIES.map((p) => <option key={p} value={p}>{PRIORITY_LABEL[p]}</option>)}
                  </select>
                </div>
                <SubmitButton className="btn w-full">Save</SubmitButton>
              </form>
              <form className="space-y-3 border-t border-line pt-4">
                <input type="hidden" name="id" value={c.id} />
                <input type="hidden" name="expectedVersion" value={c.version} />
                <div>
                  <label className="label" htmlFor="resolution">Close with a resolution</label>
                  <textarea
                    id="resolution"
                    name="resolution"
                    rows={3}
                    maxLength={5000}
                    className="input"
                    disabled={!isSenior}
                    placeholder={isSenior ? "What was decided and why" : "Closing a case needs a senior analyst"}
                  />
                </div>
                <div className="flex flex-col gap-2">
                  {closing.map((s) => (
                    <SubmitButton key={s} className={`btn ${s === "CLOSED_REPORTED" ? "btn-danger" : ""}`} formAction={changeCaseStatus.bind(null, s)} disabled={!isSenior}>
                      {s === "CLOSED_REPORTED" ? "Close · reported to FIU" : "Close · no action needed"}
                    </SubmitButton>
                  ))}
                </div>
              </form>
            </section>
          )}

          <section aria-labelledby="history-h" className="card overflow-hidden">
            <div className="card-head">
              <h2 id="history-h" className="card-title">History</h2>
              <span className="flex items-center gap-1 text-[11.5px] text-muted" title="Every entry is part of the hash-chained audit trail">
                <Icon name="shield" size={13} />
                Audited
              </span>
            </div>
            <ol className="px-4 py-2">
              {detail.history.map((h) => (
                <li key={h.seq} className="relative py-2.5 pl-5 before:absolute before:left-[3px] before:top-0 before:h-full before:w-px before:bg-line last:before:h-4">
                  <span className="absolute left-0 top-[15px] h-[7px] w-[7px] rounded-full bg-brand ring-2 ring-surface" aria-hidden />
                  <div className="text-[13px]">
                    <span className="font-medium text-ink">{actionLabel(h.action).replace(/^Case /, "")}</span>
                    {describe(h.details) && <span className="text-muted"> · {describe(h.details)}</span>}
                  </div>
                  <div className="text-[12px] text-muted">
                    {h.actor} · {timeAgo(h.occurredAt)}
                  </div>
                </li>
              ))}
            </ol>
          </section>
        </aside>
      </div>
    </>
  );
}
