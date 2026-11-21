import type { Metadata } from "next";
import Link from "next/link";
import { ApiError, api, errorMessage, type AccountView, type CustomerWithAccounts, type RiskRating } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { getSession } from "@/lib/session";
import { hasRole } from "@/lib/tokens";
import { Icon } from "@/components/Icon";
import { SubmitButton } from "@/components/SubmitButton";
import { EmptyState, ErrorPanel, KeyValue, PageHeader, RatingBadge } from "@/components/ui";
import { changeRating } from "./actions";
import { RevealNationalId } from "./RevealNationalId";

export const metadata: Metadata = { title: "Customers" };

const RATINGS: RiskRating[] = ["LOW", "MEDIUM", "HIGH"];

type Search = { q?: string; id?: string; account?: string };

/** Accepts a customer ID or an account ID. Accounts that only appear in transactions are not registered. */
async function lookup(sp: Search): Promise<{ data: CustomerWithAccounts | null; note: string | null }> {
  const raw = (sp.id ?? sp.account ?? sp.q ?? "").trim();
  if (!raw) return { data: null, note: null };
  const asCustomer = async (id: string) => api<CustomerWithAccounts>(`/api/v1/customers/${encodeURIComponent(id)}`);
  try {
    if (sp.id) return { data: await asCustomer(raw), note: null };
    try {
      const account = await api<AccountView>(`/api/v1/accounts/${encodeURIComponent(raw)}`);
      return { data: await asCustomer(account.customerId), note: null };
    } catch (e) {
      if (!(e instanceof ApiError) || e.status !== 404 || sp.account) throw e;
      return { data: await asCustomer(raw), note: null };
    }
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      return { data: null, note: `No registered customer or account “${raw}”. Accounts that only appear in transactions (e.g. other banks’ accounts) have no customer record.` };
    }
    return { data: null, note: errorMessage(e) };
  }
}

function RatingForm({ target, id, current }: { target: "customer" | "account"; id: string; current: RiskRating }) {
  return (
    <form action={changeRating} className="flex flex-wrap items-end gap-2">
      <input type="hidden" name="target" value={target} />
      <input type="hidden" name="id" value={id} />
      <div>
        <label className="label" htmlFor={`r-${id}`}>Rating</label>
        <select id={`r-${id}`} name="riskRating" defaultValue={current} className="input h-8 w-28">
          {RATINGS.map((r) => <option key={r} value={r}>{r.charAt(0) + r.slice(1).toLowerCase()}</option>)}
        </select>
      </div>
      <div className="min-w-40 flex-1">
        <label className="label" htmlFor={`why-${id}`}>Reason</label>
        <input id={`why-${id}`} name="reason" required maxLength={500} className="input h-8" placeholder="Recorded in the audit trail" />
      </div>
      <SubmitButton className="btn h-8">Update</SubmitButton>
    </form>
  );
}

export default async function CustomersPage({ searchParams }: { searchParams: Promise<Search> }) {
  const sp = await searchParams;
  const isSenior = hasRole(await getSession(), "SENIOR_ANALYST");
  const { data, note } = await lookup(sp);
  const query = sp.q ?? sp.account ?? sp.id ?? "";

  return (
    <>
      <PageHeader
        eyebrow="Investigation"
        title="Customers"
        description="Look up who is behind an account: risk ratings, accounts, and, for senior analysts, the full national ID with an audited reason."
      />

      <form role="search" className="card mb-5 flex flex-wrap items-end gap-3 p-4">
        <div className="min-w-64 flex-1">
          <label className="label" htmlFor="q">Customer ID or account ID</label>
          <div className="relative">
            <Icon name="search" size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
            <input id="q" name="q" defaultValue={query} className="input pl-8 font-mono" placeholder="e.g. ACC-RAPID-01 or a customer UUID" />
          </div>
        </div>
        <button className="btn btn-primary">Look up</button>
      </form>

      {note && <ErrorPanel message={note} />}
      {!data && !note && (
        <div className="card">
          <EmptyState icon="user" title="Search for a customer">Start from an alert’s account link, or paste an account or customer ID above.</EmptyState>
        </div>
      )}

      {data && (
        <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
          <section aria-labelledby="profile-h" className="card space-y-5 p-5">
            <div>
              <div className="eyebrow mb-1">{data.customer.type}</div>
              <h2 id="profile-h" className="text-[18px] font-semibold tracking-tight">{data.customer.fullName}</h2>
              <div className="mt-1 font-mono text-[12px] text-muted">{data.customer.id}</div>
            </div>
            <KeyValue
              items={[
                ["National ID", <RevealNationalId key="n" customerId={data.customer.id} masked={data.customer.idNumberMasked} canReveal={isSenior} />],
                ["Born", data.customer.dob],
                ["Nationality", data.customer.nationality],
                ["Risk", <RatingBadge key="r" rating={data.customer.riskRating} />],
                ["Onboarded", formatDateTime(data.customer.onboardedAt)],
              ]}
            />
            {isSenior && (
              <div className="border-t border-line pt-4">
                <div className="eyebrow mb-2">Customer risk rating</div>
                <RatingForm target="customer" id={data.customer.id} current={data.customer.riskRating} />
              </div>
            )}
          </section>

          <section aria-labelledby="accounts-h" className="card min-w-0 overflow-hidden">
            <div className="card-head">
              <h2 id="accounts-h" className="card-title">Accounts</h2>
              <span className="text-[12px] text-muted">Effective rating = the higher of customer and account</span>
            </div>
            {data.accounts.length === 0 ? (
              <EmptyState icon="folder" title="No accounts" />
            ) : (
              <ul>
                {data.accounts.map((a) => (
                  <li key={a.id} className="border-b border-line p-4 last:border-0">
                    <div className="flex flex-wrap items-center gap-3">
                      <Link href={`/alerts?accountId=${encodeURIComponent(a.id)}`} className="font-mono text-[13px] font-medium text-ink hover:text-brand" title="Alerts on this account">
                        {a.id}
                      </Link>
                      <span className="text-[12px] text-muted">{a.type} · {a.currency} · {a.status.toLowerCase()}</span>
                      <span className="ml-auto flex items-center gap-2 text-[12px] text-muted">
                        effective <RatingBadge rating={a.effectiveRiskRating} />
                      </span>
                    </div>
                    {isSenior && (
                      <div className="mt-3">
                        <RatingForm target="account" id={a.id} current={a.accountRiskRating} />
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
    </>
  );
}
