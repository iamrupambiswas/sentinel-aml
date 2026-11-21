import { NextResponse, type NextRequest } from "next/server";
import { decode, encode } from "next-auth/jwt";
import { keycloakConfig, needsRefresh, readSessionCookie, refreshSession, sessionCookieName, type SessionToken } from "@/lib/tokens";

// Same value as SESSION_MAX_AGE in lib/auth.ts (not imported: auth.ts pulls in the provider, which middleware doesn't need)
const SESSION_MAX_AGE = 10 * 60 * 60;

/**
 * Runs before every page and /api/live request:
 *  - no session      -> pages redirect to /login, API calls get 401
 *  - token expiring  -> refresh it at Keycloak and rewrite the cookie, both on the response (browser) and on
 *                       the request (so server components in THIS request already use the new token)
 *  - refresh refused -> the Keycloak session is over: clear the cookie and sign in again
 */
export async function middleware(req: NextRequest) {
  const secret = process.env.NEXTAUTH_SECRET!;
  const name = sessionCookieName();
  const raw = readSessionCookie(req.cookies.getAll(), name);
  let token: SessionToken | null = null;
  try {
    token = raw ? ((await decode({ token: raw, secret })) as unknown as SessionToken | null) : null;
  } catch {
    // A cookie this server can't decrypt (secret rotated, or from another setup): drop it and sign in again
    const res = unauthenticated(req);
    clearSession(req, res, name);
    return res;
  }

  if (!token?.accessToken) return unauthenticated(req);
  if (!needsRefresh(token)) return NextResponse.next();

  const refreshed = await refreshSession(token, keycloakConfig());
  if (!refreshed) {
    const res = unauthenticated(req);
    clearSession(req, res, name);
    return res;
  }

  const value = await encode({ token: refreshed as never, secret, maxAge: SESSION_MAX_AGE });
  clearSession(req, null, name);
  req.cookies.set(name, value);
  const res = NextResponse.next({ request: { headers: req.headers } });
  clearSession(req, res, name);
  res.cookies.set(name, value, {
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    secure: name.startsWith("__Secure-"),
    maxAge: SESSION_MAX_AGE,
  });
  return res;
}

/** Removes the session cookie and any numbered chunks of it. */
function clearSession(req: NextRequest, res: NextResponse | null, name: string) {
  for (const c of req.cookies.getAll()) {
    if (c.name === name || c.name.startsWith(`${name}.`)) {
      if (res) res.cookies.delete(c.name);
      else req.cookies.delete(c.name);
    }
  }
}

function unauthenticated(req: NextRequest): NextResponse {
  if (req.nextUrl.pathname.startsWith("/api/")) {
    return NextResponse.json({ success: false, message: "Not signed in" }, { status: 401 });
  }
  const login = new URL("/login", req.url);
  login.searchParams.set("callbackUrl", req.nextUrl.pathname + req.nextUrl.search);
  return NextResponse.redirect(login);
}

export const config = {
  // Everything except next-auth's own routes, the login page and static assets
  matcher: ["/((?!api/auth|login|_next/static|_next/image|favicon.ico).*)"],
};
