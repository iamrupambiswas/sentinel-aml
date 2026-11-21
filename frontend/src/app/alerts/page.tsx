import Link from "next/link";
import { ALERT_STATUSES, api, errorMessage, type AlertSummary, type Page } from "@/lib/api";
import { RULE_LABEL, STATUS_LABEL, formatDateTime, ruleLabel } from "@/lib/format";
import { ErrorPanel, PageHeader, RiskScore, StatusBadge } from "@/components/ui";

type Search = { status?: string; accountId?: string; ruleCode?: string; page?: string };

const PAGE_SIZE = 25;

export default async function AlertQueuePage({ searchParams }: { searchParams: Promise<Search> }) {
  const sp = await searchParams;
  const page = Math.max(0, Number(sp.page ?? 0) || 0);
  const query = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (sp.status) query.set("status", sp.status);
  if (sp.accountId?.trim()) query.set("accountId", sp.accountId.trim());
  if (sp.ruleCode) query.set("ruleCode", sp.ruleCode);

  let data: Page<AlertSummary> | null = null;
  let error: string | null = null;
  try {
    data = await api<Page<AlertSummary>>(`/api/v1/alerts?${query}`);
  } catch (e) {
    error = errorMessage(e);
  }

  const link = (p: number) => {
    const q = new URLSearchParams(query);
    q.set("page", String(p));
    q.delete("size");
    return `/alerts?${q}`;
  };

  return (
    <>
      <PageHeader
        title="Alert queue"
        subtitle="Highest risk first, then most recent activity. One alert per account, rule and time window; new alerts appear live."
      />

      <form className="card mb-4 grid gap-3 p-3 sm:grid-cols-[1fr_1fr_1fr_auto]" role="search">
        <div>
          <label className="label" htmlFor="status">Status</label>
          <select id="status" name="status" defaultValue={sp.status ?? ""} className="input">
            <option value="">Any status</option>
            {ALERT_STATUSES.map((s) => (
              <option key={s} value={s}>{STATUS_LABEL[s]}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="label" htmlFor="ruleCode">Rule</label>
          <select id="ruleCode" name="ruleCode" defaultValue={sp.ruleCode ?? ""} className="input">
            <option value="">Any rule</option>
            {Object.entries(RULE_LABEL).map(([code, label]) => (
              <option key={code} value={code}>{label}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="label" htmlFor="accountId">Account</label>
          <input id="accountId" name="accountId" defaultValue={sp.accountId ?? ""} placeholder="e.g. ACC-RAPID-01" className="input font-mono" />
        </div>
        <div className="flex items-end gap-2">
          <button className="btn btn-primary" type="submit">Filter</button>
          <Link className="btn" href="/alerts">Clear</Link>
        </div>
      </form>

      {error && <ErrorPanel message={error} />}

      {data && (
        <div className="card overflow-x-auto">
          <table className="w-full min-w-[760px] border-collapse">
            <thead>
              <tr>
                <th className="th">Risk</th>
                <th className="th">Account</th>
                <th className="th">Rule</th>
                <th className="th">Status</th>
                <th className="th text-right">Hits</th>
                <th className="th">Latest explanation</th>
                <th className="th">Last activity</th>
              </tr>
            </thead>
            <tbody>
              {data.content.length === 0 && (
                <tr>
                  <td className="td py-10 text-center text-muted" colSpan={7}>No alerts match these filters.</td>
                </tr>
              )}
              {data.content.map((a) => (
                <tr key={a.id} className="hover:bg-canvas">
                  <td className="td"><RiskScore score={a.riskScore} /></td>
                  <td className="td font-mono">
                    <Link href={`/alerts/${a.id}`} className="text-accent hover:underline">{a.accountId}</Link>
                  </td>
                  <td className="td whitespace-nowrap">{ruleLabel(a.ruleCode)}</td>
                  <td className="td"><StatusBadge status={a.status} /></td>
                  <td className="td text-right tabular-nums">{a.hitCount}</td>
                  <td className="td max-w-md text-muted"><span className="line-clamp-2">{a.latestExplanation}</span></td>
                  <td className="td whitespace-nowrap text-muted">{formatDateTime(a.lastDetectedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex items-center justify-between px-3 py-2 text-sm text-muted">
            <span>{data.totalElements} alert{data.totalElements === 1 ? "" : "s"}</span>
            <span className="flex items-center gap-2">
              {page > 0 && <Link className="btn" href={link(page - 1)}>Previous</Link>}
              <span>Page {data.totalPages === 0 ? 0 : page + 1} of {data.totalPages}</span>
              {page + 1 < data.totalPages && <Link className="btn" href={link(page + 1)}>Next</Link>}
            </span>
          </div>
        </div>
      )}
    </>
  );
}
