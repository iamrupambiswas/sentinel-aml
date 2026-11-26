/**
 * One-shot result message from a server action ("Moved to Under review.", or why it failed). The action
 * stores it in a short-lived cookie; setting a cookie makes Next.js re-render the page and the root layout,
 * which passes it to <FlashToasts>, which shows it as a notification and deletes the cookie.
 *
 * Why not ?msg= in the URL: redirecting a page to itself with new search params re-mounts its loading.tsx
 * boundary, and that redirect never commits (Next 15.5 / React 19.3), leaving the action stuck "pending".
 * A cookie keeps the URL unchanged, so the action just re-renders the page in place.
 *
 * Shared by server and client code: no server-only imports here (setFlash lives in flash-server.ts).
 */
export const FLASH_COOKIE = "sentinel-flash";

export interface Flash {
  /** unique per message, so the same text twice shows twice and a re-render never repeats one */
  id: string;
  tone: "success" | "danger";
  text: string;
}

export function encodeFlash(f: Flash): string {
  return Buffer.from(JSON.stringify(f)).toString("base64url");
}

/** Anything malformed (or tampered with) is ignored: it's only a UI message. */
export function decodeFlash(raw: string | undefined): Flash | null {
  if (!raw) return null;
  try {
    const f = JSON.parse(Buffer.from(raw, "base64url").toString("utf8")) as Partial<Flash>;
    if (typeof f.id !== "string" || typeof f.text !== "string" || (f.tone !== "success" && f.tone !== "danger")) return null;
    return { id: f.id, tone: f.tone, text: f.text.slice(0, 500) };
  } catch {
    return null;
  }
}
