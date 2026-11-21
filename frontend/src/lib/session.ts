import { cookies } from "next/headers";
import { decode } from "next-auth/jwt";
import { readSessionCookie, type SessionToken } from "./tokens";

/** Server-side only: the signed-in user's tokens, from the encrypted session cookie (already refreshed by middleware). */
export async function getSession(): Promise<SessionToken | null> {
  const raw = readSessionCookie((await cookies()).getAll());
  if (!raw) return null;
  try {
    const token = (await decode({ token: raw, secret: process.env.NEXTAUTH_SECRET! })) as unknown as SessionToken | null;
    return token?.accessToken ? token : null;
  } catch {
    // Encrypted with another secret (NEXTAUTH_SECRET rotated, or a cookie from another setup): not signed in
    return null;
  }
}

export function keycloakLogoutUrl(): string {
  const params = new URLSearchParams({
    client_id: process.env.KEYCLOAK_CLIENT_ID!,
    post_logout_redirect_uri: `${process.env.NEXTAUTH_URL}/login`,
  });
  return `${process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/logout?${params}`;
}
