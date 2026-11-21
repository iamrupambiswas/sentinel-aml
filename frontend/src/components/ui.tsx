import type { ReactNode } from "react";
import type { AlertStatus } from "@/lib/api";
import { STATUS_LABEL, severity } from "@/lib/format";

const STATUS_STYLE: Record<AlertStatus, string> = {
  NEW: "bg-accent-soft text-accent",
  UNDER_REVIEW: "bg-warn-soft text-warn",
  ESCALATED: "bg-danger-soft text-danger",
  CLOSED_CONFIRMED: "bg-danger-soft text-danger opacity-80",
  CLOSED_FALSE_POSITIVE: "bg-canvas text-muted",
};

export function StatusBadge({ status }: { status: AlertStatus }) {
  return (
    <span className={`inline-flex whitespace-nowrap rounded px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[status]}`}>
      {STATUS_LABEL[status]}
    </span>
  );
}

const SEVERITY_STYLE = {
  high: "bg-danger-soft text-danger",
  medium: "bg-warn-soft text-warn",
  low: "bg-canvas text-muted",
};

export function RiskScore({ score }: { score: number }) {
  const s = severity(score);
  return (
    <span
      className={`inline-flex min-w-12 justify-center rounded px-1.5 py-0.5 font-mono text-xs font-semibold tabular-nums ${SEVERITY_STYLE[s]}`}
      title={`Risk score ${score} (${s})`}
    >
      {Number(score).toFixed(1)}
    </span>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
      </div>
      {actions}
    </div>
  );
}

/** Success / error banner driven by ?msg= and ?err= after a server action redirects back. */
export function Flash({ msg, err }: { msg?: string; err?: string }) {
  if (!msg && !err) return null;
  return (
    <div
      role={err ? "alert" : "status"}
      className={`mb-4 rounded-md border px-3 py-2 text-sm ${
        err ? "border-danger bg-danger-soft text-danger" : "border-ok bg-ok-soft text-ok"
      }`}
    >
      {err ?? msg}
    </div>
  );
}

export function ErrorPanel({ message }: { message: string }) {
  return (
    <div role="alert" className="card border-danger p-4 text-sm text-danger">
      {message}
    </div>
  );
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="card p-4">
      <div className="label">{label}</div>
      <div className="text-2xl font-semibold tabular-nums">{value}</div>
      {hint && <div className="mt-1 text-xs text-muted">{hint}</div>}
    </div>
  );
}
