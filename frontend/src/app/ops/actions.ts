"use server";

import { redirect } from "next/navigation";
import { api, errorMessage } from "@/lib/api";

const back = (queue: string, q: string) => `/ops?queue=${encodeURIComponent(queue)}&${q}`;

/** Moves dead letters back to their source queue: one message, or all of them. ADMIN only (enforced by the API). */
export async function replay(formData: FormData) {
  const queue = String(formData.get("queue"));
  const messageId = String(formData.get("messageId") ?? "");
  const q = new URLSearchParams(messageId ? { messageId } : {});
  let err: string | null = null;
  let replayed = 0;
  try {
    replayed = (await api<{ replayed: number }>(`/api/v1/ops/dlq/${encodeURIComponent(queue)}/replay?${q}`, { method: "POST" })).replayed;
  } catch (e) {
    err = errorMessage(e);
  }
  redirect(back(queue, err ? `err=${encodeURIComponent(err)}` : `msg=${encodeURIComponent(`Replayed ${replayed} message(s) to ${queue}.`)}`));
}

/** Deletes one dead letter for good (after the cause is understood and the message is not needed). */
export async function remove(formData: FormData) {
  const queue = String(formData.get("queue"));
  const messageId = String(formData.get("messageId"));
  let err: string | null = null;
  try {
    await api(`/api/v1/ops/dlq/${encodeURIComponent(queue)}/messages/${encodeURIComponent(messageId)}`, { method: "DELETE" });
  } catch (e) {
    err = errorMessage(e);
  }
  redirect(back(queue, err ? `err=${encodeURIComponent(err)}` : `msg=${encodeURIComponent("Dead letter deleted.")}`));
}
