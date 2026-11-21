// Keycloak token handling shared by the middleware (edge runtime) and server code.
// Pure functions only: no Node APIs, so it runs in both runtimes and is unit-tested in tokens.test.ts.

export type Role = "ANALYST" | "SENIOR_ANALYST" | "ADMIN" | "INGEST";

/** What the encrypted session cookie holds. Kept small: browsers cap a cookie at ~4 KB. */
export interface SessionToken {
  accessToken: string;
  refreshToken: string;
  /** Access-token expiry, epoch seconds */
  expiresAt: number;
  username: string;
  roles: string[];
  error?: "RefreshFailed";
}

export interface KeycloakTokenResponse {
  access_token: string;
  refresh_token?: string;
  expires_in: number;
}

export interface KeycloakConfig {
  issuer: string;
  clientId: string;
  clientSecret: string;
}

/** Payload of a JWT. Only for reading claims we already trust (the token came straight from Keycloak). */
export function jwtClaims(jwt: string): Record<string, unknown> {
  const part = jwt.split(".")[1];
  if (!part) return {};
  const b64 = part.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(part.length / 4) * 4, "=");
  const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  try {
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return {};
  }
}

export function rolesOf(accessToken: string): string[] {
  const realmAccess = jwtClaims(accessToken).realm_access as { roles?: unknown } | undefined;
  return Array.isArray(realmAccess?.roles) ? realmAccess.roles.filter((r): r is string => typeof r === "string") : [];
}

export function usernameOf(accessToken: string): string {
  const c = jwtClaims(accessToken);
  return String(c.preferred_username ?? c.sub ?? "unknown");
}

/** Composite roles in Keycloak already expand ADMIN to every role, so a plain membership check is enough. */
export function hasRole(token: Pick<SessionToken, "roles"> | null | undefined, role: Role): boolean {
  return !!token?.roles.includes(role);
}

/** Refresh a little early so a token never expires between this check and the gateway call. */
export function needsRefresh(token: Pick<SessionToken, "expiresAt">, nowMs = Date.now(), skewSeconds = 30): boolean {
  return nowMs / 1000 >= token.expiresAt - skewSeconds;
}

export function toSessionToken(res: KeycloakTokenResponse, previous?: SessionToken, nowMs = Date.now()): SessionToken {
  return {
    accessToken: res.access_token,
    // Keycloak may not rotate the refresh token; keep the old one if none came back
    refreshToken: res.refresh_token ?? previous?.refreshToken ?? "",
    expiresAt: Math.floor(nowMs / 1000) + res.expires_in,
    username: usernameOf(res.access_token),
    roles: rolesOf(res.access_token),
  };
}

/** Uses the refresh token at Keycloak's token endpoint. Returns null when the Keycloak session is over. */
export async function refreshSession(
  token: SessionToken,
  cfg: KeycloakConfig,
  fetchImpl: typeof fetch = fetch,
): Promise<SessionToken | null> {
  if (!token.refreshToken) return null;
  const res = await fetchImpl(`${cfg.issuer}/protocol/openid-connect/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "refresh_token",
      refresh_token: token.refreshToken,
      client_id: cfg.clientId,
      client_secret: cfg.clientSecret,
    }),
    cache: "no-store",
  });
  if (!res.ok) return null;
  return toSessionToken((await res.json()) as KeycloakTokenResponse, token);
}

export function keycloakConfig(): KeycloakConfig {
  const issuer = process.env.KEYCLOAK_ISSUER;
  const clientId = process.env.KEYCLOAK_CLIENT_ID;
  const clientSecret = process.env.KEYCLOAK_CLIENT_SECRET;
  if (!issuer || !clientId || !clientSecret) {
    throw new Error("KEYCLOAK_ISSUER, KEYCLOAK_CLIENT_ID and KEYCLOAK_CLIENT_SECRET must be set (see .env.example)");
  }
  return { issuer, clientId, clientSecret };
}

/** next-auth's session cookie name; the __Secure- prefix only applies when the app runs on https. */
export function sessionCookieName(): string {
  return process.env.NEXTAUTH_URL?.startsWith("https://")
    ? "__Secure-next-auth.session-token"
    : "next-auth.session-token";
}

/**
 * next-auth splits a session cookie over 4 KB into name.0, name.1, ... Join them back.
 * `all` is every cookie on the request as { name, value }.
 */
export function readSessionCookie(all: { name: string; value: string }[], base = sessionCookieName()): string | undefined {
  const whole = all.find((c) => c.name === base)?.value;
  if (whole) return whole;
  const chunks = all
    .filter((c) => c.name.startsWith(`${base}.`))
    .sort((a, b) => Number(a.name.slice(base.length + 1)) - Number(b.name.slice(base.length + 1)));
  return chunks.length ? chunks.map((c) => c.value).join("") : undefined;
}
