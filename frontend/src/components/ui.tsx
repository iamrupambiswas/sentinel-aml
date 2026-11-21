import Link from "next/link";
import type { ReactNode } from "react";
import type { AlertStatus, CaseStatus, Priority, RiskRating } from "@/lib/api";
import { CASE_STATUS_LABEL, PRIORITY_LABEL, STATUS_LABEL, severity } from "@/lib/format";
import { Icon, type IconName } from "./Icon";

// ---------- badges ----------

const ALERT_TONE: Record<AlertStatus, string> = {
  NEW: "bg-info-soft text-info",
  UNDER_REVIEW: "bg-warn-soft text-warn",
  ESCALATED: "bg-danger-soft text-danger",
  CLOSED_CONFIRMED: "bg-danger-soft text-danger",
  CLOSED_FALSE_POSITIVE: "bg-sunken text-muted",
};

function Dot({ className = "" }: { className?: string }) {
  return <span className={`h-1.5 w-1.5 rounded-full bg-current ${className}`} aria-hidden />;
}

export function StatusBadge({ status }: { status: AlertStatus }) {
  return (
    <span className={`chip ${ALERT_TONE[status]}`}>
      <Dot />
      {STATUS_LABEL[status]}
    </span>
  );
}

const CASE_TONE: Record<CaseStatus, string> = {
  OPEN: "bg-info-soft text-info",
  INVESTIGATING: "bg-warn-soft text-warn",
  CLOSED_NO_ACTION: "bg-sunken text-muted",
  CLOSED_REPORTED: "bg-ok-soft text-ok",
};

export function CaseStatusBadge({ status }: { status: CaseStatus }) {
  return (
    <span className={`chip ${CASE_TONE[status]}`}>
      <Dot />
      {CASE_STATUS_LABEL[status]}
    </span>
  );
}

const PRIORITY_TONE: Record<Priority, string> = {
  HIGH: "text-danger",
  MEDIUM: "text-warn",
  LOW: "text-muted",
};

/** Three bars, filled by level: readable without relying on colour alone. */
export function PriorityBadge({ priority }: { priority: Priority }) {
  const level = priority === "HIGH" ? 3 : priority === "MEDIUM" ? 2 : 1;
  return (
    <span className={`inline-flex items-center gap-1.5 text-[12px] font-medium ${PRIORITY_TONE[priority]}`}>
      <span className="flex items-end gap-[2px]" aria-hidden>
        {[1, 2, 3].map((b) => (
          <span key={b} className={`w-[3px] rounded-sm ${b <= level ? "bg-current" : "bg-line-strong"}`} style={{ height: 4 + b * 3 }} />
        ))}
      </span>
      {PRIORITY_LABEL[priority]}
    </span>
  );
}

const RATING_TONE: Record<RiskRating, string> = {
  HIGH: "bg-danger-soft text-danger",
  MEDIUM: "bg-warn-soft text-warn",
  LOW: "bg-sunken text-muted",
};

export function RatingBadge({ rating }: { rating: RiskRating }) {
  return <span className={`chip ${RATING_TONE[rating]}`}>{rating.charAt(0) + rating.slice(1).toLowerCase()}</span>;
}

const SEVERITY_TONE = {
  high: "bg-danger text-white",
  medium: "bg-warn-soft text-warn ring-1 ring-inset ring-warn/30",
  low: "bg-sunken text-ink-2 ring-1 ring-inset ring-line",
};

export function RiskScore({ score, size = "md" }: { score: number; size?: "md" | "lg" }) {
  const s = severity(score);
  return (
    <span
      className={`inline-flex items-center justify-center rounded-md font-mono font-semibold tabular-nums ${SEVERITY_TONE[s]} ${
        size === "lg" ? "h-8 min-w-14 px-2 text-[15px]" : "h-6 min-w-11 px-1.5 text-[12px]"
      }`}
      title={`Risk score ${Number(score).toFixed(1)} of 100 (${s})`}
    >
      {Number(score).toFixed(1)}
    </span>
  );
}

// ---------- page structure ----------

export function PageHeader({
  title,
  description,
  actions,
  eyebrow,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  eyebrow?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        {eyebrow && <div className="eyebrow mb-1.5">{eyebrow}</div>}
        <h1 className="text-[22px] font-semibold tracking-tight text-ink">{title}</h1>
        {description && <p className="mt-1.5 max-w-2xl text-[13.5px] leading-relaxed text-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function BackLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link href={href} className="mb-4 inline-flex items-center gap-1 text-[13px] text-muted transition-colors hover:text-ink">
      <Icon name="chevron" size={12} className="rotate-180" />
      {children}
    </Link>
  );
}

/** Success / error banner driven by ?msg= and ?err= after a server action redirects back. */
export function Flash({ msg, err }: { msg?: string; err?: string }) {
  if (!msg && !err) return null;
  return (
    <div
      role={err ? "alert" : "status"}
      className={`mb-5 flex items-start gap-2.5 rounded-xl border px-4 py-3 text-[13px] animate-rise ${
        err ? "border-danger/30 bg-danger-soft text-danger" : "border-ok/30 bg-ok-soft text-ok"
      }`}
    >
      <Icon name={err ? "alert" : "check"} className="mt-0.5 shrink-0" />
      <span>{err ?? msg}</span>
    </div>
  );
}

export function ErrorPanel({ message }: { message: string }) {
  return (
    <div role="alert" className="card flex items-start gap-3 border-danger/30 p-5 text-[13px]">
      <Icon name="alert" className="mt-0.5 shrink-0 text-danger" />
      <div>
        <div className="font-medium text-ink">Couldn’t load this</div>
        <div className="mt-0.5 text-muted">{message}</div>
      </div>
    </div>
  );
}

export function EmptyState({ icon, title, children }: { icon: IconName; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center">
      <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-xl border border-line bg-sunken text-muted">
        <Icon name={icon} size={18} />
      </div>
      <div className="text-[14px] font-medium text-ink">{title}</div>
      {children && <div className="mt-1 max-w-sm text-[13px] text-muted">{children}</div>}
    </div>
  );
}

export function StatTile({
  label,
  value,
  hint,
  tone = "default",
  href,
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  tone?: "default" | "danger" | "ok";
  href?: string;
}) {
  const body = (
    <>
      <div className="eyebrow">{label}</div>
      <div
        className={`mt-2 text-[28px] font-semibold leading-none tracking-tight tabular-nums ${
          tone === "danger" ? "text-danger" : tone === "ok" ? "text-ok" : "text-ink"
        }`}
      >
        {value}
      </div>
      {hint && <div className="mt-2 text-[12px] text-muted">{hint}</div>}
    </>
  );
  return href ? (
    <Link href={href} className="card block p-5 transition-[border-color,transform] duration-150 hover:-translate-y-0.5 hover:border-line-strong">
      {body}
    </Link>
  ) : (
    <div className="card p-5">{body}</div>
  );
}

export function KeyValue({ items }: { items: [ReactNode, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-[minmax(96px,auto)_1fr] gap-x-4 gap-y-2.5 text-[13px]">
      {items.map(([k, v], i) => (
        <div key={i} className="contents">
          <dt className="text-muted">{k}</dt>
          <dd className="min-w-0 break-words text-ink">{v}</dd>
        </div>
      ))}
    </dl>
  );
}

export function Pager({
  page,
  totalPages,
  total,
  noun,
  plural = `${noun}s`,
  link,
}: {
  page: number;
  totalPages: number;
  total: number;
  noun: string;
  plural?: string;
  link: (p: number) => string;
}) {
  return (
    <div className="flex items-center justify-between px-4 py-3 text-[12.5px] text-muted">
      <span>
        {total} {total === 1 ? noun : plural}
      </span>
      <span className="flex items-center gap-2">
        {page > 0 && (
          <Link className="btn h-7" href={link(page - 1)}>
            Previous
          </Link>
        )}
        <span>
          Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
        </span>
        {page + 1 < totalPages && (
          <Link className="btn h-7" href={link(page + 1)}>
            Next
          </Link>
        )}
      </span>
    </div>
  );
}
