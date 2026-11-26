"use server";

import { flashResult } from "@/lib/flash-server";
import { api, errorMessage, type CaseStatus, type Priority } from "@/lib/api";
import { CASE_STATUS_LABEL } from "@/lib/format";
import { getSession } from "@/lib/session";

async function patch(id: string, body: Record<string, unknown>): Promise<string | null> {
  try {
    await api(`/api/v1/cases/${encodeURIComponent(id)}`, { method: "PATCH", body: JSON.stringify(body) });
    return null;
  } catch (e) {
    return errorMessage(e);
  }
}

/** Moves the case on. Closing needs SENIOR_ANALYST and a resolution: the API enforces both. */
export async function changeCaseStatus(status: CaseStatus, formData: FormData) {
  const id = String(formData.get("id"));
  const resolution = String(formData.get("resolution") ?? "").trim();
  const err = await patch(id, { status, resolution: resolution || null, expectedVersion: Number(formData.get("expectedVersion")) });
  await flashResult(err, `Moved to ${CASE_STATUS_LABEL[status]}.`);
}

export async function assignToMe(formData: FormData) {
  const id = String(formData.get("id"));
  const me = (await getSession())?.username ?? "";
  const err = await patch(id, { assignee: me, expectedVersion: Number(formData.get("expectedVersion")) });
  await flashResult(err, "Assigned to you.");
}

export async function updateOwnerAndPriority(formData: FormData) {
  const id = String(formData.get("id"));
  const assignee = String(formData.get("assignee") ?? "").trim();
  const priority = String(formData.get("priority")) as Priority;
  const body: Record<string, unknown> = { expectedVersion: Number(formData.get("expectedVersion")) };
  if (assignee !== String(formData.get("currentAssignee") ?? "")) body.assignee = assignee; // "" unassigns
  if (priority !== formData.get("currentPriority")) body.priority = priority;
  const err = Object.keys(body).length === 1 ? "Nothing changed." : await patch(id, body);
  await flashResult(err, "Case updated.");
}

export async function addNote(formData: FormData) {
  const id = String(formData.get("id"));
  const body = String(formData.get("body") ?? "").trim();
  let err: string | null = body ? null : "Write something first.";
  if (!err) {
    try {
      await api(`/api/v1/cases/${encodeURIComponent(id)}/notes`, { method: "POST", body: JSON.stringify({ body }) });
    } catch (e) {
      err = errorMessage(e);
    }
  }
  await flashResult(err, "Note added.");
}

export async function addAlert(formData: FormData) {
  const id = String(formData.get("id"));
  const alertId = String(formData.get("alertId") ?? "").trim();
  let err: string | null = alertId ? null : "Paste an alert ID.";
  if (!err) {
    try {
      await api(`/api/v1/cases/${encodeURIComponent(id)}/alerts`, { method: "POST", body: JSON.stringify({ alertId }) });
    } catch (e) {
      err = errorMessage(e);
    }
  }
  await flashResult(err, "Alert added.");
}
