"use server";

import { api, errorMessage, type RiskRating } from "@/lib/api";
import { flashResult } from "@/lib/flash-server";

export interface RevealState {
  idNumber?: string;
  error?: string;
}

/**
 * Asks customer-service for the full national ID (SENIOR_ANALYST+, audited as PII_REVEALED with the reason).
 * Returned to the component only: never put in a URL, a redirect or a log.
 */
export async function revealNationalId(_prev: RevealState, formData: FormData): Promise<RevealState> {
  const customerId = String(formData.get("customerId"));
  const reason = String(formData.get("reason") ?? "").trim();
  if (!reason) return { error: "Give a reason: it is recorded in the audit trail." };
  try {
    const r = await api<{ idNumber: string }>(`/api/v1/customers/${encodeURIComponent(customerId)}/national-id/reveal`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    });
    return { idNumber: r.idNumber };
  } catch (e) {
    return { error: errorMessage(e) };
  }
}

/** Changes a customer's or an account's risk rating (SENIOR_ANALYST+). Detection uses it within seconds. */
export async function changeRating(formData: FormData) {
  const target = String(formData.get("target")); // "customer" | "account"
  const id = String(formData.get("id"));
  const riskRating = String(formData.get("riskRating")) as RiskRating;
  const reason = String(formData.get("reason") ?? "").trim();
  const path = target === "account" ? `/api/v1/accounts/${encodeURIComponent(id)}/risk-rating` : `/api/v1/customers/${encodeURIComponent(id)}/risk-rating`;
  let err: string | null = reason ? null : "Give a reason for the rating change.";
  if (!err) {
    try {
      await api(path, { method: "PUT", body: JSON.stringify({ riskRating, reason }) });
    } catch (e) {
      err = errorMessage(e);
    }
  }
  await flashResult(err, "Rating updated; detection uses it within seconds.");
}
