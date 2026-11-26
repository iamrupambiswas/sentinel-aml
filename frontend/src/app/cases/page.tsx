import type { Metadata } from "next";
import Link from "next/link";
import { api, errorMessage, type CaseStatus, type CaseSummary, type Page } from "@/lib/api";
import { CASE_STATUS_LABEL, timeAgo } from "@/lib/format";
import { getSession } from "@/lib/session";
import { CaseStatusBadge, EmptyState, ErrorPanel, PageHeader, Pager, PriorityBadge, RiskScore } from "@/components/ui";

export const metadata: Metadata = { title: "Cases" };

const STATUSES: CaseStatus[] = ["OPEN", "INVESTIGATING", "CLOSED_NO_ACTION", "CLOSED_REPORTED"];

type Search = { view?: string; status?: string; page?: string };

export default async function CasesPage({ searchParams }: { searchParams: Promise<Search> }) {
  const sp = await searchParams;
  const me = (await getSession())?.username ?? "";
  const view = sp.view === "mine" || sp.view === "all" ? sp.view : "open";
  const page = Math.max(0, Number(sp.page ?? 0) || 0);

  const q = new URLSearchParams({ page: String(page), size: "25" });
  if (view === "open") q.set("open", "true");
  if (view === "mine") q.set("assignee", me);
  if (sp.status) q.set("status", sp.status);

  let data: Page<CaseSummary> | null = null;
  let error: string | null = null;
  try {
    data = await api<Page<CaseSummary>>(`/api/v1/cases?${q}`);
  } catch (e) {
    error = errorMessage(e);
  }

  const tabs = [
    { key: "open", label: "Open" },
    { key: "mine", label: "Assigned to me" },
    { key: "all", label: "All" },
  ];
  const link = (p: number) => `/cases?${new URLSearchParams({ view, page: String(p), ...(sp.status ? { status: sp.status } : {}) })}`;

  return (
    <>
      <PageHeader
        eyebrow="Monitoring"
        title="Cases"
        description="A case groups the alerts you investigate together, with an owner, notes and a recorded outcome. Open one from any alert."
      />

      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Case views" className="flex rounded-lg border border-line bg-surface p-0.5 shadow-card">
          {tabs.map((t) => (
            <Link
              key={t.key}
              href={`/cases?view=${t.key}`}
              aria-current={view === t.key ? "page" : undefined}
              className={`rounded-md px-3 py-1.5 text-[13px] transition-colors ${
                view === t.key ? "bg-brand-soft font-medium text-brand" : "text-muted hover:text-ink"
              }`}
            >
              {t.label}
            </Link>
          ))}
        </nav>
        {view === "all" && (
          <form className="flex items-center gap-2">
            <input type="hidden" name="view" value="all" />
            <label className="sr-only" htmlFor="status">Status</label>
            <select id="status" name="status" defaultValue={sp.status ?? ""} className="input h-8 w-auto">
              <option value="">Any status</option>
              {STATUSES.map((s) => <option key={s} value={s}>{CASE_STATUS_LABEL[s]}</option>)}
            </select>
            <button className="btn">Apply</button>
          </form>
        )}
      </div>

      {error && <ErrorPanel message={error} />}

      {data && (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="table min-w-[860px]">
              <thead>
                <tr>
                  <th className="th">Case</th>
                  <th className="th">Priority</th>
                  <th className="th">Status</th>
                  <th className="th">Owner</th>
                  <th className="th text-right">Alerts</th>
                  <th className="th">Highest risk</th>
                  <th className="th">Updated</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((c) => (
                  <tr key={c.id} className="row">
                    <td className="td max-w-sm">
                      <Link href={`/cases/${c.id}`} className="block font-medium text-ink hover:text-brand">{c.title}</Link>
                      <span className="font-mono text-[12px] text-muted">{c.caseNumber}</span>
                    </td>
                    <td className="td"><PriorityBadge priority={c.priority} /></td>
                    <td className="td"><CaseStatusBadge status={c.status} /></td>
                    <td className="td text-ink-2">{c.assignee ?? <span className="text-muted">Unassigned</span>}</td>
                    <td className="td text-right font-mono tabular-nums">{c.alertCount}</td>
                    <td className="td">{c.maxRisk != null ? <RiskScore score={c.maxRisk} /> : "—"}</td>
                    <td className="td whitespace-nowrap text-muted">{timeAgo(c.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {data.content.length === 0 && (
              <EmptyState icon="folder" title={view === "mine" ? "Nothing assigned to you" : "No cases here"}>
                Open an alert and use <span className="font-medium text-ink-2">Open case</span> to start an investigation.
              </EmptyState>
            )}
          </div>
          <div className="border-t border-line">
            <Pager page={page} totalPages={data.totalPages} total={data.totalElements} noun="case" link={link} />
          </div>
        </div>
      )}
    </>
  );
}
