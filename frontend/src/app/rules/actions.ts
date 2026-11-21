"use server";

import { api, errorMessage, type RuleView } from "@/lib/api";
import { flashResult } from "@/lib/flash-server";

function optionalNumber(v: FormDataEntryValue | null): number | undefined {
  const s = String(v ?? "").trim();
  if (s === "") return undefined;
  const n = Number(s);
  return Number.isFinite(n) ? n : undefined;
}

/**
 * Saves a rule change. Who changed it is NOT sent: detection-service records the token's username.
 * expectedVersion = the version shown on screen, so a concurrent edit gives 409 instead of an overwrite.
 * Only changed fields are sent; the API rejects a request that changes nothing.
 */
export async function updateRule(formData: FormData) {
  const code = String(formData.get("ruleCode"));
  const path = `/api/v1/rules/${encodeURIComponent(code)}`;
  const current = {
    thresholdAmount: optionalNumber(formData.get("currentThreshold")),
    windowMinutes: optionalNumber(formData.get("currentWindow")),
    weight: optionalNumber(formData.get("currentWeight")),
    active: formData.get("currentActive") === "true",
  };
  const next = {
    thresholdAmount: optionalNumber(formData.get("thresholdAmount")),
    windowMinutes: optionalNumber(formData.get("windowMinutes")),
    weight: optionalNumber(formData.get("weight")),
    active: formData.get("active") === "on",
  };
  const body: Record<string, unknown> = {
    expectedVersion: Number(formData.get("expectedVersion")),
    reason: String(formData.get("reason") ?? "").trim(),
  };
  for (const k of Object.keys(next) as (keyof typeof next)[]) {
    if (next[k] !== undefined && next[k] !== current[k]) body[k] = next[k];
  }

  let err: string | null = null;
  let saved: RuleView | null = null;
  if (!body.reason) err = "Give a reason: it goes into the rule's audit history.";
  else {
    try {
      saved = await api<RuleView>(path, { method: "PUT", body: JSON.stringify(body) });
    } catch (e) {
      err = errorMessage(e);
    }
  }
  await flashResult(err, `Saved as version ${saved?.version}. Live on every detection pod in seconds.`);
}
