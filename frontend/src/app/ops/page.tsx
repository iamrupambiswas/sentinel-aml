import Link from "next/link";
import { api, errorMessage, type DeadLetter, type DeadLetterQueueInfo } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { getSession } from "@/lib/session";
import { hasRole } from "@/lib/tokens";
import { ErrorPanel, Flash, PageHeader } from "@/components/ui";
import { remove, replay } from "./actions";

export default async function OpsPage({ searchParams }: { searchParams: Promise<{ queue?: string; msg?: string; err?: string }> }) {
  const { queue, msg, err } = await searchParams;
  if (!hasRole(await getSession(), "ADMIN")) {
    return <ErrorPanel message="Dead-letter operations need the ADMIN role." />;
  }

  let queues: DeadLetterQueueInfo[] = [];
  let letters: DeadLetter[] | null = null;
  let error: string | null = null;
  try {
    queues = await api<DeadLetterQueueInfo[]>("/api/v1/ops/dlq");
    const selected = queue ?? queues.find((q) => q.depth > 0)?.sourceQueue;
    if (selected) letters = await api<DeadLetter[]>(`/api/v1/ops/dlq/${encodeURIComponent(selected)}?max=50`);
  } catch (e) {
    error = errorMessage(e);
  }
  const selected = queue ?? queues.find((q) => q.depth > 0)?.sourceQueue;

  return (
    <>
      <PageHeader
        title="Dead letters"
        subtitle="Messages that failed 5 delivery attempts (1 s → 2 s → 4 s → 8 s backoff). Fix the cause, then replay; delete only what can never be processed."
      />
      <Flash msg={msg} err={err} />
      {error && <ErrorPanel message={error} />}

      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        {queues.map((q) => (
          <Link
            key={q.sourceQueue}
            href={`/ops?queue=${encodeURIComponent(q.sourceQueue)}`}
            aria-current={q.sourceQueue === selected ? "true" : undefined}
            className={`card block p-4 hover:border-accent ${q.sourceQueue === selected ? "border-accent" : ""}`}
          >
            <div className="font-mono text-sm">{q.sourceQueue}</div>
            <div className="mt-1 flex items-baseline gap-2">
              <span className={`text-2xl font-semibold tabular-nums ${q.depth > 0 ? "text-danger" : ""}`}>{q.depth}</span>
              <span className="text-xs text-muted">in {q.deadLetterQueue}</span>
            </div>
          </Link>
        ))}
      </div>

      {selected && letters && (
        <section aria-labelledby="dlq-h" className="card">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line p-3">
            <h2 id="dlq-h" className="font-mono text-sm font-semibold">{selected}</h2>
            {letters.length > 0 && (
              <form action={replay}>
                <input type="hidden" name="queue" value={selected} />
                <button className="btn btn-primary" type="submit">Replay all {letters.length}</button>
              </form>
            )}
          </div>
          {letters.length === 0 ? (
            <p className="p-6 text-center text-sm text-muted">No dead letters. Every message on this queue was processed.</p>
          ) : (
            <ul>
              {letters.map((l) => (
                <li key={l.messageId} className="border-b border-line p-3 last:border-0">
                  <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                    <div className="text-xs text-muted">
                      <span className="font-mono">{l.messageId}</span> · type {l.type ?? "unknown"} · first sent {formatDateTime(l.firstSentAt)}
                    </div>
                    <div className="flex gap-2">
                      <form action={replay}>
                        <input type="hidden" name="queue" value={selected} />
                        <input type="hidden" name="messageId" value={l.messageId} />
                        <button className="btn" type="submit">Replay</button>
                      </form>
                      <form action={remove}>
                        <input type="hidden" name="queue" value={selected} />
                        <input type="hidden" name="messageId" value={l.messageId} />
                        <button className="btn btn-danger" type="submit">Delete</button>
                      </form>
                    </div>
                  </div>
                  <pre className="overflow-x-auto rounded bg-canvas p-2 font-mono text-xs">{l.body}</pre>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </>
  );
}
