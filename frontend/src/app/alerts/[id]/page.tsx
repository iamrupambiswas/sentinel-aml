import Link from "next/link";
import { api, errorMessage, type AlertDetail } from "@/lib/api";
import { STATUS_LABEL, formatDateTime, ruleLabel } from "@/lib/format";
import { ErrorPanel, Flash, PageHeader, RiskScore, StatusBadge } from "@/components/ui";
import { changeStatus } from "./actions";

export default async function AlertDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ msg?: string; err?: string }>;
}) {
  const { id } = await params;
  const { msg, err } = await searchParams;

  let detail: AlertDetail | null = null;
  let error: string | null = null;
  try {
    detail = await api<AlertDetail>(`/api/v1/alerts/${encodeURIComponent(id)}`);
  } catch (e) {
    error = errorMessage(e);
  }

  if (!detail) {
    return (
      <>
        <Link href="/alerts" className="mb-4 inline-block text-sm text-accent hover:underline">← Alert queue</Link>
        <ErrorPanel message={error ?? "Alert not found."} />
      </>
    );
  }

  const a = detail.alert;
  const closed = detail.allowedNextStatuses.length === 0;

  return (
    <>
      <Link href="/alerts" className="mb-4 inline-block text-sm text-accent hover:underline">← Alert queue</Link>
      <PageHeader
        title={`${ruleLabel(a.ruleCode)} on ${a.accountId}`}
        subtitle={<span className="font-mono text-xs">{detail.dedupKey}</span>}
        actions={<div className="flex items-center gap-2"><StatusBadge status={a.status} /><RiskScore score={a.riskScore} /></div>}
      />
      <Flash msg={msg} err={err} />

      <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
        <section aria-labelledby="hits-h" className="min-w-0">
          <div className="card mb-4 p-4">
            <div className="label">Latest explanation</div>
            <p className="text-sm">{a.latestExplanation}</p>
          </div>

          <h2 id="hits-h" className="mb-2 text-sm font-semibold">
            Evidence · {detail.hits.length} hit{detail.hits.length === 1 ? "" : "s"}
          </h2>
          <ol className="space-y-3">
            {detail.hits.map((h) => (
              <li key={`${h.eventId}-${h.ruleCode}`} className="card p-4">
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2 text-sm">
                  <span className="font-mono text-xs text-muted">txn {h.transactionId}</span>
                  <span className="flex items-center gap-2 text-xs text-muted">
                    weight {Number(h.weight).toFixed(0)} · score <RiskScore score={h.riskScore} />
                  </span>
                </div>
                <p className="text-sm">{h.explanation}</p>
                <div className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-xs text-muted">
                  <span>Transaction time {formatDateTime(h.txnTimestamp)}</span>
                  <span>Detected {formatDateTime(h.detectedAt)}</span>
                </div>
                {h.evidenceTxnIds.length > 0 && (
                  <details className="mt-2 text-xs">
                    <summary className="cursor-pointer text-accent">
                      {h.evidenceTxnIds.length} transaction{h.evidenceTxnIds.length === 1 ? "" : "s"} in the pattern
                    </summary>
                    <ul className="mt-1 space-y-0.5 font-mono text-muted">
                      {h.evidenceTxnIds.map((t) => <li key={t}>{t}</li>)}
                    </ul>
                  </details>
                )}
              </li>
            ))}
          </ol>
        </section>

        <aside className="space-y-4">
          <div className="card p-4 text-sm">
            <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5">
              <dt className="text-muted">Account</dt><dd className="font-mono">{a.accountId}</dd>
              <dt className="text-muted">Window</dt><dd className="font-mono">{a.windowBucket}</dd>
              <dt className="text-muted">Hits</dt><dd className="tabular-nums">{a.hitCount}</dd>
              <dt className="text-muted">First txn</dt><dd>{formatDateTime(a.firstTxnAt)}</dd>
              <dt className="text-muted">Last txn</dt><dd>{formatDateTime(a.lastTxnAt)}</dd>
              <dt className="text-muted">Version</dt><dd className="tabular-nums">{a.version}</dd>
            </dl>
            {detail.statusNote && (
              <div className="mt-3 border-t border-line pt-3">
                <div className="label">Last note · {formatDateTime(detail.statusChangedAt)}</div>
                <p className="whitespace-pre-wrap">{detail.statusNote}</p>
              </div>
            )}
          </div>

          <form className="card p-4">
            <h2 className="mb-3 text-sm font-semibold">Decision</h2>
            {closed ? (
              <p className="text-sm text-muted">Closed alerts are final. New hits in the same window still attach as evidence.</p>
            ) : (
              <>
                <input type="hidden" name="id" value={a.id} />
                <input type="hidden" name="expectedVersion" value={a.version} />
                <label className="label" htmlFor="note">Note (optional)</label>
                <textarea id="note" name="note" rows={3} maxLength={2000} className="input mb-3" placeholder="What you checked, why you decided" />
                <div className="flex flex-col gap-2">
                  {detail.allowedNextStatuses.map((s) => (
                    <button
                      key={s}
                      type="submit"
                      formAction={changeStatus.bind(null, s)}
                      className={`btn justify-start ${s === "CLOSED_CONFIRMED" || s === "ESCALATED" ? "btn-danger" : s === "UNDER_REVIEW" ? "btn-primary" : ""}`}
                    >
                      Move to {STATUS_LABEL[s]}
                    </button>
                  ))}
                </div>
              </>
            )}
          </form>
        </aside>
      </div>
    </>
  );
}
