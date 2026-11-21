import { redirect } from "next/navigation";
import { getSession } from "./session";

/** Server-side only. Every call goes through the gateway with the signed-in user's own token. */
const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const session = await getSession();
  if (!session) redirect("/login");

  const res = await fetch(`${GATEWAY}${path}`, {
    ...init,
    cache: "no-store",
    headers: {
      Accept: "application/json",
      // JSON unless it's a file upload: fetch sets the multipart boundary for FormData itself
      ...(init.body && !(init.body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
      Authorization: `Bearer ${session.accessToken}`,
    },
  });
  const text = await res.text();
  let body: { data?: T; message?: string } | null = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = null;
  }
  if (!res.ok) {
    throw new ApiError(res.status, body?.message ?? messageFor(res.status));
  }
  return (body?.data ?? body) as T;
}

function messageFor(status: number): string {
  switch (status) {
    case 401: return "Your session has expired. Sign in again.";
    case 403: return "Your role does not allow this.";
    case 404: return "Not found.";
    case 429: return "Too many requests. Wait a moment and try again.";
    default: return `The server answered ${status}.`;
  }
}

/** For server actions: turn any failure into a message for the page. */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  return "Could not reach the API. Is the gateway running?";
}

// ---- Shapes returned by the gateway (see each service's DTOs) ----

export type AlertStatus = "NEW" | "UNDER_REVIEW" | "ESCALATED" | "CLOSED_FALSE_POSITIVE" | "CLOSED_CONFIRMED";
export const ALERT_STATUSES: AlertStatus[] = ["NEW", "UNDER_REVIEW", "ESCALATED", "CLOSED_FALSE_POSITIVE", "CLOSED_CONFIRMED"];

export interface AlertSummary {
  id: string;
  accountId: string;
  ruleCode: string;
  windowBucket: string;
  status: AlertStatus;
  riskScore: number;
  hitCount: number;
  latestExplanation: string;
  firstTxnAt: string;
  lastTxnAt: string;
  lastDetectedAt: string;
  version: number;
}

export interface HitView {
  eventId: string;
  transactionId: string;
  ruleCode: string;
  weight: number;
  riskScore: number;
  explanation: string;
  evidenceTxnIds: string[];
  txnTimestamp: string;
  detectedAt: string;
}

export interface AlertDetail {
  alert: AlertSummary;
  dedupKey: string;
  statusNote: string | null;
  statusChangedAt: string | null;
  allowedNextStatuses: AlertStatus[];
  hits: HitView[];
  /** Investigation case this alert belongs to, if any */
  caseId: number | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface AlertStats {
  alerts: number;
  hits: number;
  alertsByStatus: Partial<Record<AlertStatus, number>>;
  hitsPerAlert: number;
}

export interface RuleView {
  ruleCode: string;
  ruleName: string;
  description: string;
  thresholdAmount: number | null;
  windowMinutes: number;
  weight: number;
  active: boolean;
  params: Record<string, unknown>;
  version: number;
  loadedVersion: number | null;
  supportedParams: string[];
}

export interface RuleVersionView {
  version: number;
  thresholdAmount: number | null;
  windowMinutes: number;
  weight: number;
  active: boolean;
  params: Record<string, unknown>;
  changedBy: string;
  reason: string;
  changedAt: string;
}

export interface DeadLetterQueueInfo {
  sourceQueue: string;
  deadLetterQueue: string;
  depth: number;
}

export interface DeadLetter {
  messageId: string;
  type: string | null;
  originalQueue: string | null;
  firstSentAt: string | null;
  body: string;
}

// ---- Phase 7b: cases, audit, customers ----

export type CaseStatus = "OPEN" | "INVESTIGATING" | "CLOSED_NO_ACTION" | "CLOSED_REPORTED";
export type Priority = "LOW" | "MEDIUM" | "HIGH";

export interface CaseSummary {
  id: number;
  caseNumber: string;
  title: string;
  status: CaseStatus;
  priority: Priority;
  assignee: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  alertCount: number;
  maxRisk: number | null;
  version: number;
}

export interface AuditEntry {
  seq: number;
  eventId: string;
  occurredAt: string;
  recordedAt: string;
  service: string;
  actor: string;
  action: string;
  entityType: string;
  entityId: string;
  /** Canonical JSON text */
  details: string;
  hash: string;
}

export interface CaseDetail {
  summary: CaseSummary;
  resolution: string | null;
  closedAt: string | null;
  allowedNextStatuses: CaseStatus[];
  alerts: AlertSummary[];
  notes: { id: number; author: string; body: string; createdAt: string }[];
  history: AuditEntry[];
}

export interface VerifyResult {
  valid: boolean;
  checked: number;
  firstBrokenSeq: number | null;
  problem: string | null;
  headHash: string | null;
}

export type RiskRating = "LOW" | "MEDIUM" | "HIGH";

export interface CustomerView {
  id: string;
  type: string;
  fullName: string;
  idNumberMasked: string;
  dob: string;
  nationality: string;
  riskRating: RiskRating;
  onboardedAt: string;
}

export interface AccountView {
  id: string;
  customerId: string;
  type: string;
  currency: string;
  status: string;
  accountRiskRating: RiskRating;
  customerRiskRating: RiskRating;
  effectiveRiskRating: RiskRating;
  openedAt: string;
}

export interface CustomerWithAccounts {
  customer: CustomerView;
  accounts: AccountView[];
}

/** GET /api/v1/ingestion/batches/{id}: a CSV upload's progress and its rejected rows. */
export interface BatchStatus {
  batchId: string;
  status: "PROCESSING" | "COMPLETED" | "FAILED";
  totalRecords: number | null;
  acceptedRecords: number | null;
  failedRecords: number | null;
  createdAt: string;
  completedAt: string | null;
  errors: { lineNumber: number | null; reason: string; rawData: string | null }[];
}
