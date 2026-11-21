"use server";

import { redirect } from "next/navigation";
import { ALERT_STATUSES, api, errorMessage, type AlertStatus } from "@/lib/api";
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
  if (!ALERT_STATUSES.includes(status)) redirect(`/alerts/${id}?err=${encodeURIComponent("Unknown status")}`);

  let err: string | null = null;
  try {
    await api(`/api/v1/alerts/${encodeURIComponent(id)}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status, note: note || null, expectedVersion }),
    });
  } catch (e) {
    err = errorMessage(e);
  }
  // redirect() works by throwing, so it stays outside the try
  const q = err ? `err=${encodeURIComponent(err)}` : `msg=${encodeURIComponent(`Moved to ${STATUS_LABEL[status]}.`)}`;
  redirect(`/alerts/${id}?${q}`);
}
