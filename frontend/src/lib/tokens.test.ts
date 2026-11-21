import { describe, expect, it, vi } from "vitest";
import { hasRole, jwtClaims, needsRefresh, readSessionCookie, refreshSession, rolesOf, toSessionToken, usernameOf, type SessionToken } from "./tokens";

/** Unsigned test JWT with the given payload (signature is never checked client-side). */
function jwt(payload: object): string {
  const b64url = (s: string) => btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `${b64url('{"alg":"none"}')}.${b64url(JSON.stringify(payload))}.sig`;
}

const analystToken = jwt({ preferred_username: "analyst1", sub: "u-1", realm_access: { roles: ["ANALYST", "default-roles-sentinel"] } });
const cfg = { issuer: "http://kc/realms/sentinel", clientId: "sentinel-console", clientSecret: "s" };

describe("claims", () => {
  it("reads username and realm roles from a Keycloak access token", () => {
    expect(usernameOf(analystToken)).toBe("analyst1");
    expect(rolesOf(analystToken)).toEqual(["ANALYST", "default-roles-sentinel"]);
  });

  it("falls back to sub, and to no roles, when claims are missing", () => {
    const t = jwt({ sub: "u-2" });
    expect(usernameOf(t)).toBe("u-2");
    expect(rolesOf(t)).toEqual([]);
    expect(jwtClaims("not-a-jwt")).toEqual({});
  });

  it("checks a role by membership (composites are already expanded by Keycloak)", () => {
    expect(hasRole({ roles: ["ADMIN", "ANALYST"] }, "ADMIN")).toBe(true);
    expect(hasRole({ roles: ["ANALYST"] }, "ADMIN")).toBe(false);
    expect(hasRole(null, "ANALYST")).toBe(false);
  });
});

describe("refresh timing", () => {
  it("refreshes 30 s before expiry, not after", () => {
    const now = 1_000_000_000_000;
    expect(needsRefresh({ expiresAt: now / 1000 + 60 }, now)).toBe(false);
    expect(needsRefresh({ expiresAt: now / 1000 + 20 }, now)).toBe(true);
    expect(needsRefresh({ expiresAt: now / 1000 - 1 }, now)).toBe(true);
  });
});

describe("refreshSession", () => {
  const current: SessionToken = { accessToken: "old", refreshToken: "r-1", expiresAt: 0, username: "analyst1", roles: ["ANALYST"] };

  it("posts the refresh token and keeps the old one if Keycloak does not rotate it", async () => {
    const fetchImpl = vi.fn(async () => new Response(JSON.stringify({ access_token: analystToken, expires_in: 900 }), { status: 200 }));
    const next = await refreshSession(current, cfg, fetchImpl as unknown as typeof fetch);

    expect(next?.accessToken).toBe(analystToken);
    expect(next?.refreshToken).toBe("r-1");
    expect(next?.roles).toContain("ANALYST");
    const [url, init] = fetchImpl.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://kc/realms/sentinel/protocol/openid-connect/token");
    expect(String(init.body)).toContain("grant_type=refresh_token");
    expect(String(init.body)).toContain("refresh_token=r-1");
  });

  it("returns null when the Keycloak session has ended", async () => {
    const fetchImpl = vi.fn(async () => new Response('{"error":"invalid_grant"}', { status: 400 }));
    expect(await refreshSession(current, cfg, fetchImpl as unknown as typeof fetch)).toBeNull();
  });

  it("builds the session from a token response", () => {
    const s = toSessionToken({ access_token: analystToken, refresh_token: "r-2", expires_in: 900 }, undefined, 1_000_000);
    expect(s).toEqual({ accessToken: analystToken, refreshToken: "r-2", expiresAt: 1000 + 900, username: "analyst1", roles: ["ANALYST", "default-roles-sentinel"] });
  });
});

describe("readSessionCookie", () => {
  it("reads a single cookie or joins next-auth's numbered chunks in order", () => {
    expect(readSessionCookie([{ name: "s", value: "abc" }], "s")).toBe("abc");
    expect(readSessionCookie([{ name: "s.1", value: "def" }, { name: "s.0", value: "abc" }, { name: "x", value: "z" }], "s")).toBe("abcdef");
    expect(readSessionCookie([{ name: "x", value: "z" }], "s")).toBeUndefined();
  });
});
