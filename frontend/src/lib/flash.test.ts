import { describe, expect, it } from "vitest";
import { decodeFlash, encodeFlash } from "./flash";

describe("flash cookie", () => {
  it("round-trips a message, including non-ASCII text", () => {
    const f = { id: "a1", tone: "success" as const, text: "Saved · ₹10 lakh threshold" };
    expect(decodeFlash(encodeFlash(f))).toEqual(f);
  });

  it("ignores missing, malformed or tampered values", () => {
    expect(decodeFlash(undefined)).toBeNull();
    expect(decodeFlash("not base64 json")).toBeNull();
    expect(decodeFlash(Buffer.from(JSON.stringify({ id: "x", tone: "evil", text: "hi" })).toString("base64url"))).toBeNull();
    expect(decodeFlash(Buffer.from(JSON.stringify({ tone: "danger", text: "no id" })).toString("base64url"))).toBeNull();
  });

  it("caps the text length", () => {
    const long = Buffer.from(JSON.stringify({ id: "x", tone: "danger", text: "a".repeat(2000) })).toString("base64url");
    expect(decodeFlash(long)?.text).toHaveLength(500);
  });
});
