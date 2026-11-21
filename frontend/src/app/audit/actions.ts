"use server";

import { api, errorMessage, type VerifyResult } from "@/lib/api";
import { setFlash } from "@/lib/flash-server";

/** Recomputes every hash in the chain (ADMIN). The result comes back as a notification. */
export async function verifyChain() {
  let msg: string | null = null;
  let err: string | null = null;
  try {
    const r = await api<VerifyResult>("/api/v1/audit/verify");
    if (r.valid) msg = `Chain intact: all ${r.checked} entries verified. Head hash ${r.headHash?.slice(0, 16)}…`;
    else err = `Chain broken at entry #${r.firstBrokenSeq}: ${r.problem}. Entries before it (${r.checked}) are intact.`;
  } catch (e) {
    err = errorMessage(e);
  }
  await setFlash(err ? "danger" : "success", err ?? msg!);
}
