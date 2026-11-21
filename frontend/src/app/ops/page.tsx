import type { Metadata } from "next";
import Link from "next/link";
import { api, errorMessage, type DeadLetter, type DeadLetterQueueInfo } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { getSession } from "@/lib/session";
import { hasRole } from "@/lib/tokens";
import { Icon } from "@/components/Icon";
import { EmptyState, ErrorPanel, Flash, PageHeader } from "@/components/ui";
import { remove, replay } from "./actions";

export const metadata: Metadata = { title: "Dead letters" };

const QUEUE_ROLE: Record<string, string> = {
  "sentinel.txn.ingested": "Transactions waiting for detection",
  "sentinel.detection.hits": "Rule hits waiting to become alerts",
  "sentinel.audit.events": "Audit events waiting to be chained",
};

export default async function OpsPage({ searchParams }: { searchParams: Promise<{ queue?: string; msg?: string; err?: string }> }) {
  const { queue, msg, err } = await searchParams;
  if (!hasRole(await getSession(), "ADMIN")) {
    return <ErrorPanel message="Dead-letter operations need the ADMIN role." />;
  }

  let queues: DeadLetterQueueInfo[] = [];
  let letters: DeadLetter[] | null = null;
  let error: string | null = null;
  const pick = (qs: DeadLetterQueueInfo[]) => queue ?? qs.find((q) => q.depth > 0)?.sourceQueue ?? qs[0]?.sourceQueue;
  try {
    queues = await api<DeadLetterQueueInfo[]>("/api/v1/ops/dlq");
    const selected = pick(queues);
    if (selected) letters = await api<DeadLetter[]>(`/api/v1/ops/dlq/${encodeURIComponent(selected)}?max=50`);
  } catch (e) {
    error = errorMessage(e);
  }
  const selected = pick(queues);

  return (
    <>
      <PageHeader
        eyebrow="Configuration"
        title="Dead letters"
        description="Messages that failed five delivery attempts (1 s, 2 s, 4 s, 8 s backoff) are parked here so the queue keeps flowing. Fix the cause, then replay. Delete only what can never be processed."
      />
      <Flash msg={msg} err={err} />
      {error && <ErrorPanel message={error} />}

      <div className="mb-5 grid gap-4 md:grid-cols-3">
        {queues.map((q) => (
          <Link
            key={q.sourceQueue}
            href={`/ops?queue=${encodeURIComponent(q.sourceQueue)}`}
            aria-current={q.sourceQueue === selected ? "true" : undefined}
            className={`card block p-5 transition-[border-color,transform] duration-150 hover:-translate-y-0.5 ${
              q.sourceQueue === selected ? "border-brand ring-3 ring-brand/10" : "hover:border-line-strong"
            }`}
          >
            <div className="eyebrow">{QUEUE_ROLE[q.sourceQueue] ?? "Queue"}</div>
            <div className="mt-2 flex items-baseline gap-2">
              <span className={`text-[28px] font-semibold leading-none tabular-nums ${q.depth > 0 ? "text-danger" : "text-ok"}`}>{q.depth}</span>
              <span className="text-[12px] text-muted">parked</span>
            </div>
            <div className="mt-2 truncate font-mono text-[11.5px] text-muted">{q.deadLetterQueue}</div>
          </Link>
        ))}
      </div>

      {selected && letters && (
        <section aria-labelledby="dlq-h" className="card overflow-hidden">
          <div className="card-head">
            <h2 id="dlq-h" className="card-title font-mono">{selected}</h2>
            {letters.length > 0 && (
              <form action={replay}>
                <input type="hidden" name="queue" value={selected} />
                <button className="btn btn-primary h-7" type="submit">Replay all {letters.length}</button>
              </form>
            )}
          </div>
          {letters.length === 0 ? (
            <EmptyState icon="check" title="Nothing parked">Every message on this queue was processed.</EmptyState>
          ) : (
            <ul>
              {letters.map((l) => (
                <li key={l.messageId} className="border-b border-line p-4 last:border-0">
                  <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                    <div className="text-[12px] text-muted">
                      <span className="font-mono text-ink-2">{l.messageId}</span> · {l.type ?? "unknown type"} · first sent {formatDateTime(l.firstSentAt)}
                    </div>
                    <div className="flex gap-2">
                      <form action={replay}>
                        <input type="hidden" name="queue" value={selected} />
                        <input type="hidden" name="messageId" value={l.messageId} />
                        <button className="btn h-7" type="submit">
                          <Icon name="activity" size={13} />
                          Replay
                        </button>
                      </form>
                      <form action={remove}>
                        <input type="hidden" name="queue" value={selected} />
                        <input type="hidden" name="messageId" value={l.messageId} />
                        <button className="btn btn-danger h-7" type="submit">Delete</button>
                      </form>
                    </div>
                  </div>
                  <pre className="overflow-x-auto rounded-lg bg-sunken p-3 font-mono text-[12px] leading-relaxed text-ink-2">{l.body}</pre>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </>
  );
}
