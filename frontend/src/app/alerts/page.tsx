import type { Metadata } from "next";
import Link from "next/link";
import { ALERT_STATUSES, api, errorMessage, type AlertSummary, type Page } from "@/lib/api";
import { RULE_LABEL, STATUS_LABEL, ruleLabel, timeAgo } from "@/lib/format";
import { Icon } from "@/components/Icon";
import { EmptyState, ErrorPanel, PageHeader, Pager, RiskScore, StatusBadge } from "@/components/ui";

export const metadata: Metadata = { title: "Alerts" };

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
  const filtered = Boolean(sp.status || sp.accountId || sp.ruleCode);

  return (
    <>
      <PageHeader
        eyebrow="Monitoring"
        title="Alerts"
        description="One alert per account, rule and time window. Highest risk first; new alerts appear without refreshing."
      />

      <form role="search" className="card mb-5 grid gap-3 p-4 sm:grid-cols-[1fr_1fr_1.3fr_auto]">
        <div>
          <label className="label" htmlFor="status">Status</label>
          <select id="status" name="status" defaultValue={sp.status ?? ""} className="input">
            <option value="">Any status</option>
            {ALERT_STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
        </div>
        <div>
          <label className="label" htmlFor="ruleCode">Rule</label>
          <select id="ruleCode" name="ruleCode" defaultValue={sp.ruleCode ?? ""} className="input">
            <option value="">Any rule</option>
            {Object.entries(RULE_LABEL).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
          </select>
        </div>
        <div>
          <label className="label" htmlFor="accountId">Account</label>
          <div className="relative">
            <Icon name="search" size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
            <input id="accountId" name="accountId" defaultValue={sp.accountId ?? ""} placeholder="e.g. ACC-RAPID-01" className="input pl-8 font-mono" />
          </div>
        </div>
        <div className="flex items-end gap-2">
          <button className="btn btn-primary" type="submit">Apply</button>
          {filtered && <Link className="btn btn-ghost" href="/alerts">Reset</Link>}
        </div>
      </form>

      {error && <ErrorPanel message={error} />}

      {data && (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="table min-w-[820px]">
              <thead>
                <tr>
                  <th className="th w-20">Risk</th>
                  <th className="th">Account</th>
                  <th className="th">Rule</th>
                  <th className="th">Status</th>
                  <th className="th text-right">Hits</th>
                  <th className="th">Latest explanation</th>
                  <th className="th">Last activity</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((a) => (
                  <tr key={a.id} className="row">
                    <td className="td"><RiskScore score={a.riskScore} /></td>
                    <td className="td">
                      <Link href={`/alerts/${a.id}`} className="font-mono font-medium text-ink hover:text-brand">{a.accountId}</Link>
                    </td>
                    <td className="td whitespace-nowrap text-ink-2">{ruleLabel(a.ruleCode)}</td>
                    <td className="td"><StatusBadge status={a.status} /></td>
                    <td className="td text-right font-mono tabular-nums">{a.hitCount}</td>
                    <td className="td max-w-md text-muted"><span className="line-clamp-2">{a.latestExplanation}</span></td>
                    <td className="td whitespace-nowrap text-muted">{timeAgo(a.lastDetectedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {data.content.length === 0 && (
              <EmptyState icon="search" title={filtered ? "No alerts match these filters" : "No alerts yet"}>
                {filtered ? "Try another status or rule, or reset the filters." : "Alerts appear here as soon as detection flags a transaction."}
              </EmptyState>
            )}
          </div>
          <div className="border-t border-line">
            <Pager page={page} totalPages={data.totalPages} total={data.totalElements} noun="alert" link={link} />
          </div>
        </div>
      )}
    </>
  );
}
