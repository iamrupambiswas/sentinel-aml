import type { AlertStatus, CaseStatus, Priority } from "./api";

const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
const dateTime = new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Kolkata" });

export function formatInr(amount: number | null | undefined): string {
  return amount == null ? "—" : inr.format(amount);
}

/** Service timestamps are UTC LocalDateTime without a zone, e.g. 2026-11-21T06:10:15.73 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  const d = new Date(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`);
  return Number.isNaN(d.getTime()) ? value : dateTime.format(d);
}

export function formatWindow(minutes: number): string {
  if (!minutes) return "—";
  if (minutes % 1440 === 0) return `${minutes / 1440} d`;
  if (minutes % 60 === 0) return `${minutes / 60} h`;
  return `${minutes} min`;
}

export const STATUS_LABEL: Record<AlertStatus, string> = {
  NEW: "New",
  UNDER_REVIEW: "Under review",
  ESCALATED: "Escalated",
  CLOSED_FALSE_POSITIVE: "Closed · false positive",
  CLOSED_CONFIRMED: "Closed · confirmed",
};

export const RULE_LABEL: Record<string, string> = {
  CTR_THRESHOLD: "CTR threshold",
  STRUCTURING: "Structuring",
  RAPID_MOVEMENT: "Rapid movement",
  HIGH_RISK_JURISDICTION: "High-risk jurisdiction",
  BEHAVIORAL_DEVIATION: "Behavioral deviation",
  ROUND_AMOUNT: "Round amounts",
};

export function ruleLabel(code: string): string {
  return RULE_LABEL[code] ?? code;
}

export type Severity = "high" | "medium" | "low";

export function severity(score: number): Severity {
  if (score >= 70) return "high";
  if (score >= 40) return "medium";
  return "low";
}

/** aml_rule.threshold_amount means different things per rule (ADR-004): INR, a count, a percentage or a multiplier. */
type Unit = "inr" | "count" | "percent" | "multiplier";
const THRESHOLD_UNIT: Record<string, Unit> = {
  CTR_THRESHOLD: "inr",
  HIGH_RISK_JURISDICTION: "inr",
  "24H_VELOCITY_LIMIT": "inr",
  STRUCTURING: "count",
  ROUND_AMOUNT: "count",
  RAPID_MOVEMENT: "percent",
  BEHAVIORAL_DEVIATION: "multiplier",
};

export function thresholdLabel(code: string): string {
  return { inr: "Threshold (INR)", count: "Threshold (transactions)", percent: "Threshold (% moved out)", multiplier: "Threshold (× 90-day average)" }[
    THRESHOLD_UNIT[code] ?? "inr"
  ];
}

export function formatThreshold(code: string, value: number | null | undefined): string {
  if (value == null) return "—";
  switch (THRESHOLD_UNIT[code] ?? "inr") {
    case "count": return `${value} transactions`;
    case "percent": return `${value} %`;
    case "multiplier": return `${value}× average`;
    default: return value === 0 ? "any amount" : formatInr(value);
  }
}

export const CASE_STATUS_LABEL: Record<CaseStatus, string> = {
  OPEN: "Open",
  INVESTIGATING: "Investigating",
  CLOSED_NO_ACTION: "Closed · no action",
  CLOSED_REPORTED: "Closed · reported",
};

export const PRIORITY_LABEL: Record<Priority, string> = { HIGH: "High", MEDIUM: "Medium", LOW: "Low" };

/** "CUSTOMER_RISK_CHANGED" -> "Customer risk changed" */
export function actionLabel(action: string): string {
  const s = action.toLowerCase().replaceAll("_", " ").replace("pii", "PII").replace("dlq", "DLQ");
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** Relative time for recent events ("4 min ago"), absolute date beyond a week. */
export function timeAgo(value: string | null | undefined, now = Date.now()): string {
  if (!value) return "—";
  const t = new Date(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`).getTime();
  if (Number.isNaN(t)) return value;
  const s = Math.round((now - t) / 1000);
  if (s < 45) return "just now";
  if (s < 3600) return `${Math.round(s / 60)} min ago`;
  if (s < 86400) return `${Math.round(s / 3600)} h ago`;
  if (s < 7 * 86400) return `${Math.round(s / 86400)} d ago`;
  return formatDateTime(value);
}
