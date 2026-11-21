import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError, api, errorMessage, type AlertDetail } from "@/lib/api";
import { STATUS_LABEL, formatDateTime, ruleLabel } from "@/lib/format";
import { Icon } from "@/components/Icon";
import { SubmitButton } from "@/components/SubmitButton";

import { BackLink, ErrorPanel, KeyValue, PageHeader, RiskScore, StatusBadge } from "@/components/ui";
import { addToCase, changeStatus, openCase } from "./actions";

export const metadata: Metadata = { title: "Alert" };

export default async function AlertDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  let detail: AlertDetail | null = null;
  let error: string | null = null;
  try {
    detail = await api<AlertDetail>(`/api/v1/alerts/${encodeURIComponent(id)}`);
  } catch (e) {
    if (e instanceof ApiError && (e.status === 404 || e.status === 400)) notFound(); // unknown or malformed ID
    error = errorMessage(e);
  }

  if (!detail) {
    return (
      <>
        <BackLink href="/alerts">Alerts</BackLink>
        <ErrorPanel message={error ?? "Alert not found."} />
      </>
    );
  }

  const a = detail.alert;
  const closed = detail.allowedNextStatuses.length === 0;
  const caseNumber = detail.caseId ? `CASE-${String(detail.caseId).padStart(6, "0")}` : null;

  return (
    <>
      <BackLink href="/alerts">Alerts</BackLink>
      <PageHeader
        eyebrow={<span className="font-mono normal-case tracking-normal">{detail.dedupKey}</span>}
        title={
          <span className="flex flex-wrap items-center gap-3">
            {ruleLabel(a.ruleCode)} on <span className="font-mono">{a.accountId}</span>
          </span>
        }
        actions={
          <>
            <StatusBadge status={a.status} />
            <RiskScore score={a.riskScore} size="lg" />
          </>
        }
      />

      <div className="grid gap-5 xl:grid-cols-[1fr_340px]">
        <div className="min-w-0 space-y-5">
          <section className="card p-5">
            <div className="eyebrow mb-2">Latest explanation</div>
            <p className="text-[14px] leading-relaxed text-ink">{a.latestExplanation}</p>
          </section>

          <section aria-labelledby="hits-h" className="card overflow-hidden">
            <div className="card-head">
              <h2 id="hits-h" className="card-title">Evidence</h2>
              <span className="text-[12px] text-muted">{detail.hits.length} hit{detail.hits.length === 1 ? "" : "s"} folded into this alert</span>
            </div>
            <ol>
              {detail.hits.map((h, i) => (
                <li key={`${h.eventId}-${h.ruleCode}`} className="border-b border-line p-5 last:border-0">
                  <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                    <span className="flex items-center gap-2 text-[12px] text-muted">
                      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-sunken font-mono text-[11px] text-ink-2">{i + 1}</span>
                      <span className="font-mono">txn {h.transactionId}</span>
                    </span>
                    <span className="flex items-center gap-2 text-[12px] text-muted">
                      weight {Number(h.weight).toFixed(0)} <RiskScore score={h.riskScore} />
                    </span>
                  </div>
                  <p className="text-[13.5px] leading-relaxed text-ink">{h.explanation}</p>
                  <div className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-[12px] text-muted">
                    <span>Transaction {formatDateTime(h.txnTimestamp)}</span>
                    <span>Detected {formatDateTime(h.detectedAt)}</span>
                  </div>
                  {h.evidenceTxnIds.length > 0 && (
                    <details className="group mt-3 text-[12.5px]">
                      <summary className="inline-flex cursor-pointer list-none items-center gap-1 text-brand">
                        <Icon name="chevron" size={12} className="transition-transform group-open:rotate-90" />
                        {h.evidenceTxnIds.length} transaction{h.evidenceTxnIds.length === 1 ? "" : "s"} in the pattern
                      </summary>
                      <ul className="mt-2 space-y-1 rounded-lg bg-sunken p-3 font-mono text-[12px] text-ink-2">
                        {h.evidenceTxnIds.map((t) => <li key={t}>{t}</li>)}
                      </ul>
                    </details>
                  )}
                </li>
              ))}
            </ol>
          </section>
        </div>

        <aside className="space-y-5">
          <section className="card p-5">
            <KeyValue
              items={[
                ["Account", <Link key="a" href={`/customers?account=${encodeURIComponent(a.accountId)}`} className="font-mono text-brand hover:underline">{a.accountId}</Link>],
                ["Window", <span key="w" className="font-mono">{a.windowBucket}</span>],
                ["Hits", <span key="h" className="font-mono">{a.hitCount}</span>],
                ["First txn", formatDateTime(a.firstTxnAt)],
                ["Last txn", formatDateTime(a.lastTxnAt)],
                ["Version", <span key="v" className="font-mono">{a.version}</span>],
              ]}
            />
            {detail.statusNote && (
              <div className="mt-4 border-t border-line pt-4">
                <div className="eyebrow mb-1">Last note · {formatDateTime(detail.statusChangedAt)}</div>
                <p className="whitespace-pre-wrap text-[13px] text-ink">{detail.statusNote}</p>
              </div>
            )}
          </section>

          <section aria-labelledby="decision-h" className="card p-5">
            <h2 id="decision-h" className="card-title mb-3">Decision</h2>
            {closed ? (
              <p className="text-[13px] text-muted">Closed alerts are final. New hits in the same window still attach as evidence.</p>
            ) : (
              <form className="space-y-3">
                <input type="hidden" name="id" value={a.id} />
                <input type="hidden" name="expectedVersion" value={a.version} />
                <div>
                  <label className="label" htmlFor="note">Note <span className="font-normal text-muted">(optional)</span></label>
                  <textarea id="note" name="note" rows={3} maxLength={2000} className="input" placeholder="What you checked and why you decided" />
                </div>
                <div className="flex flex-col gap-2">
                  {detail.allowedNextStatuses.map((s) => (
                    <SubmitButton
                      key={s}
                      formAction={changeStatus.bind(null, s)}
                      className={`btn justify-start ${s === "UNDER_REVIEW" ? "btn-primary" : s === "ESCALATED" || s === "CLOSED_CONFIRMED" ? "btn-danger" : ""}`}
                    >
                      Move to {STATUS_LABEL[s]}
                    </SubmitButton>
                  ))}
                </div>
              </form>
            )}
          </section>

          <section aria-labelledby="case-h" className="card p-5">
            <h2 id="case-h" className="card-title mb-3">Case</h2>
            {caseNumber ? (
              <Link href={`/cases/${detail.caseId}`} className="flex items-center justify-between rounded-lg border border-line px-3 py-2.5 text-[13px] hover:border-brand">
                <span>
                  Part of <span className="font-mono font-medium">{caseNumber}</span>
                </span>
                <Icon name="chevron" size={13} className="text-muted" />
              </Link>
            ) : (
              <div className="space-y-4">
                <form action={openCase} className="space-y-2">
                  <input type="hidden" name="alertId" value={a.id} />
                  <label className="label" htmlFor="case-title">Open a new case</label>
                  <input
                    id="case-title"
                    name="title"
                    required
                    maxLength={200}
                    className="input"
                    defaultValue={`${ruleLabel(a.ruleCode)} on ${a.accountId}`}
                  />
                  <SubmitButton className="btn w-full" icon={<Icon name="plus" size={14} />}>
                    Open case
                  </SubmitButton>
                </form>
                <form action={addToCase} className="space-y-2 border-t border-line pt-4">
                  <input type="hidden" name="alertId" value={a.id} />
                  <label className="label" htmlFor="case-number">Or add to an existing case</label>
                  <div className="flex gap-2">
                    <input id="case-number" name="caseNumber" required placeholder="CASE-000001" className="input font-mono" />
                    <SubmitButton>Add</SubmitButton>
                  </div>
                </form>
              </div>
            )}
          </section>
        </aside>
      </div>
    </>
  );
}
