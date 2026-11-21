import type { AlertStatus } from "./api";

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
