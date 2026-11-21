"use server";

import { redirect } from "next/navigation";
import { ALERT_STATUSES, api, errorMessage, type AlertStatus, type CaseDetail } from "@/lib/api";
import { flashResult, setFlash } from "@/lib/flash-server";
import { STATUS_LABEL } from "@/lib/format";

/**
 * Moves an alert to the next status. Sends the version the analyst was looking at, so if someone else
 * changed the alert meanwhile the API answers 409 instead of silently overwriting their decision.
 * `status` is bound per button (changeStatus.bind(null, s)): the clicked button's name/value is not
 * reliably part of a server action's FormData.
 */
export async function changeStatus(status: AlertStatus, formData: FormData) {
  const id = String(formData.get("id"));
  const note = String(formData.get("note") ?? "").trim();
  const expectedVersion = Number(formData.get("expectedVersion"));
  if (!ALERT_STATUSES.includes(status)) return setFlash("danger", "Unknown status");

  let err: string | null = null;
  try {
    await api(`/api/v1/alerts/${encodeURIComponent(id)}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status, note: note || null, expectedVersion }),
    });
  } catch (e) {
    err = errorMessage(e);
  }
  // stays on the page: setting the flash cookie re-renders it with the new status
  await flashResult(err, `Moved to ${STATUS_LABEL[status]}.`);
}

/** Opens a new case containing this alert, then goes to the case. */
export async function openCase(formData: FormData) {
  const alertId = String(formData.get("alertId"));
  const title = String(formData.get("title") ?? "").trim();
  let created: CaseDetail | null = null;
  let err: string | null = null;
  try {
    created = await api<CaseDetail>("/api/v1/cases", { method: "POST", body: JSON.stringify({ title, alertIds: [alertId] }) });
  } catch (e) {
    err = errorMessage(e);
  }
  if (err || !created) return setFlash("danger", err ?? "Could not open the case");
  await setFlash("success", `${created.summary.caseNumber} opened.`);
  redirect(`/cases/${created.summary.id}`); // throws, so it stays outside any try
}

/** Adds this alert to an existing case, given as 42 or CASE-000042. */
export async function addToCase(formData: FormData) {
  const alertId = String(formData.get("alertId"));
  const raw = String(formData.get("caseNumber") ?? "").trim();
  const caseId = Number(raw.replace(/^CASE-0*/i, ""));
  if (!Number.isInteger(caseId) || caseId <= 0) return setFlash("danger", "Enter a case number like CASE-000001 or 1");
  let err: string | null = null;
  try {
    await api(`/api/v1/cases/${caseId}/alerts`, { method: "POST", body: JSON.stringify({ alertId }) });
  } catch (e) {
    err = errorMessage(e);
  }
  if (err) return setFlash("danger", err);
  await setFlash("success", "Alert added to the case.");
  redirect(`/cases/${caseId}`);
}
