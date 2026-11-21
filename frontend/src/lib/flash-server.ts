import { randomUUID } from "node:crypto";
import { cookies } from "next/headers";
import { FLASH_COOKIE, encodeFlash, type Flash } from "./flash";

/**
 * For server actions: report the outcome as a notification. Call it before redirect() (which throws).
 * Without a redirect, the page re-renders in place because the cookie changed.
 */
export async function setFlash(tone: Flash["tone"], text: string): Promise<void> {
  (await cookies()).set(FLASH_COOKIE, encodeFlash({ id: randomUUID(), tone, text }), {
    path: "/",
    maxAge: 60, // only has to survive until the next render; the console deletes it once shown
    sameSite: "lax",
  });
}

/** The usual ending of an action: success text, or the error from the API. */
export async function flashResult(err: string | null, success: string): Promise<void> {
  await setFlash(err ? "danger" : "success", err ?? success);
}
