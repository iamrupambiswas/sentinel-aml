import type { Metadata } from "next";
import Link from "next/link";
import { api, errorMessage, type AuditEntry, type Page } from "@/lib/api";
import { actionLabel, formatDateTime } from "@/lib/format";
import { getSession } from "@/lib/session";
import { hasRole } from "@/lib/tokens";
import { Icon } from "@/components/Icon";
import { EmptyState, ErrorPanel, Flash, PageHeader, Pager } from "@/components/ui";
import { verifyChain } from "./actions";

export const metadata: Metadata = { title: "Audit trail" };

const ENTITY_TYPES = ["ALERT", "CASE", "CUSTOMER", "ACCOUNT", "RULE", "DEAD_LETTER"];

type Search = { entityType?: string; entityId?: string; actor?: string; page?: string; msg?: string; err?: string };

function entityLink(e: AuditEntry): string | null {
  switch (e.entityType) {
    case "ALERT": return `/alerts/${e.entityId}`;
    case "CASE": return `/cases/${Number(e.entityId.replace(/^CASE-0*/, ""))}`;
    case "CUSTOMER": return `/customers?id=${encodeURIComponent(e.entityId)}`;
    case "ACCOUNT": return `/customers?account=${encodeURIComponent(e.entityId)}`;
    case "RULE": return `/rules/${encodeURIComponent(e.entityId)}`;
    case "DEAD_LETTER": return "/ops";
    default: return null;
  }
}

function prettyDetails(details: string): string {
  try {
    return Object.entries(JSON.parse(details) as Record<string, unknown>)
      .map(([k, v]) => `${k}: ${Array.isArray(v) ? `${v.length} item${v.length === 1 ? "" : "s"}` : String(v)}`)
      .join(" · ");
  } catch {
    return details;
  }
}

export default async function AuditPage({ searchParams }: { searchParams: Promise<Search> }) {
  const sp = await searchParams;
  const session = await getSession();
  if (!hasRole(session, "SENIOR_ANALYST")) {
    return <ErrorPanel message="The audit trail is available to senior analysts and administrators." />;
  }
  const isAdmin = hasRole(session, "ADMIN");
  const page = Math.max(0, Number(sp.page ?? 0) || 0);
  const q = new URLSearchParams({ page: String(page), size: "50" });
  for (const k of ["entityType", "entityId", "actor"] as const) if (sp[k]?.trim()) q.set(k, sp[k]!.trim());

  let data: Page<AuditEntry> | null = null;
  let error: string | null = null;
  try {
    data = await api<Page<AuditEntry>>(`/api/v1/audit?${q}`);
  } catch (e) {
    error = errorMessage(e);
  }
  const link = (p: number) => {
    const n = new URLSearchParams(q);
    n.set("page", String(p));
    n.delete("size");
    return `/audit?${n}`;
  };

  return (
    <>
      <PageHeader
        eyebrow="Investigation"
        title="Audit trail"
        description="Every decision, from every service, in one append-only log. Each entry carries the hash of the one before it, so a change anywhere is detectable."
        actions={
          isAdmin && (
            <form action={verifyChain}>
              <button className="btn">
                <Icon name="shield" size={14} />
                Verify chain
              </button>
            </form>
          )
        }
      />
      <Flash msg={sp.msg} err={sp.err} />

      <form role="search" className="card mb-5 grid gap-3 p-4 sm:grid-cols-[1fr_1.4fr_1fr_auto]">
        <div>
          <label className="label" htmlFor="entityType">Record type</label>
          <select id="entityType" name="entityType" defaultValue={sp.entityType ?? ""} className="input">
            <option value="">Any</option>
            {ENTITY_TYPES.map((t) => <option key={t} value={t}>{t.replace("_", " ").toLowerCase()}</option>)}
          </select>
        </div>
        <div>
          <label className="label" htmlFor="entityId">Record ID</label>
          <input id="entityId" name="entityId" defaultValue={sp.entityId ?? ""} className="input font-mono" placeholder="CASE-000001, customer UUID, rule code…" />
        </div>
        <div>
          <label className="label" htmlFor="actor">User</label>
          <input id="actor" name="actor" defaultValue={sp.actor ?? ""} className="input" placeholder="e.g. senior1" />
        </div>
        <div className="flex items-end gap-2">
          <button className="btn btn-primary">Filter</button>
          <Link href="/audit" className="btn btn-ghost">Reset</Link>
        </div>
      </form>

      {error && <ErrorPanel message={error} />}
      {data && (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="table min-w-[920px]">
              <thead>
                <tr>
                  <th className="th w-16">#</th>
                  <th className="th">When</th>
                  <th className="th">Who</th>
                  <th className="th">What</th>
                  <th className="th">Record</th>
                  <th className="th">Details</th>
                  <th className="th">Hash</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((e) => {
                  const href = entityLink(e);
                  return (
                    <tr key={e.seq} className="row">
                      <td className="td font-mono text-muted">{e.seq}</td>
                      <td className="td whitespace-nowrap text-ink-2">{formatDateTime(e.occurredAt)}</td>
                      <td className="td">
                        <span className="font-medium text-ink">{e.actor}</span>
                        <div className="text-[11.5px] text-muted">{e.service}</div>
                      </td>
                      <td className="td whitespace-nowrap">
                        <span className={`chip ${e.action === "PII_REVEALED" ? "bg-warn-soft text-warn" : "bg-sunken text-ink-2"}`}>{actionLabel(e.action)}</span>
                      </td>
                      <td className="td">
                        <div className="text-[11px] uppercase tracking-wide text-muted">{e.entityType.replace("_", " ")}</div>
                        {href ? (
                          <Link href={href} className="font-mono text-[12px] text-brand hover:underline">{e.entityId.length > 20 ? `${e.entityId.slice(0, 13)}…` : e.entityId}</Link>
                        ) : (
                          <span className="font-mono text-[12px]">{e.entityId}</span>
                        )}
                      </td>
                      <td className="td max-w-xs text-[12.5px] text-muted"><span className="line-clamp-2">{prettyDetails(e.details)}</span></td>
                      <td className="td font-mono text-[11.5px] text-muted" title={e.hash}>{e.hash.slice(0, 10)}…</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {data.content.length === 0 && <EmptyState icon="ledger" title="No entries match" />}
          </div>
          <div className="border-t border-line">
            <Pager page={page} totalPages={data.totalPages} total={data.totalElements} noun="entry" plural="entries" link={link} />
          </div>
        </div>
      )}
    </>
  );
}
