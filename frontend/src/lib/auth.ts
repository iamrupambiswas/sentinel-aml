import type { NextAuthOptions } from "next-auth";
import KeycloakProvider from "next-auth/providers/keycloak";
import { keycloakConfig, toSessionToken, type SessionToken } from "./tokens";

/** Session lifetime of the console cookie; the Keycloak session (refresh token) usually ends sooner. */
export const SESSION_MAX_AGE = 10 * 60 * 60;

const kc = keycloakConfig();

/**
 * Login runs on the Next.js server: authorization code + PKCE against Keycloak (client sentinel-console).
 * The tokens are stored only in next-auth's encrypted, httpOnly session cookie; the session object that
 * reaches the browser carries the username and roles, never a token. Token refresh happens in middleware.ts.
 */
export const authOptions: NextAuthOptions = {
  providers: [
    KeycloakProvider({
      clientId: kc.clientId,
      clientSecret: kc.clientSecret,
      issuer: kc.issuer,
      // Discovery via the internal URL: Keycloak answers with the public authorization URL (for the browser)
      // and back-channel token/JWKS URLs this server can reach. Same URL outside Docker.
      wellKnown: `${kc.internalIssuer}/.well-known/openid-configuration`,
      checks: ["pkce", "state"],
    }),
  ],
  session: { strategy: "jwt", maxAge: SESSION_MAX_AGE },
  pages: { signIn: "/login", error: "/login" },
  callbacks: {
    async jwt({ token, account }) {
      if (account?.access_token) {
        // First sign-in: keep only what the console needs, so the cookie stays under the 4 KB limit
        const session = toSessionToken({
          access_token: account.access_token,
          refresh_token: account.refresh_token,
          expires_in: (account.expires_at ?? 0) - Math.floor(Date.now() / 1000),
        });
        return session as unknown as typeof token;
      }
      return token;
    },
    async session({ session, token }) {
      const t = token as unknown as SessionToken;
      return { ...session, user: { name: t.username }, roles: t.roles };
    },
    async redirect({ url, baseUrl }) {
      // Allow the Keycloak logout endpoint as a post-sign-out destination, otherwise stay on this site
      if (url.startsWith(baseUrl) || url.startsWith(kc.issuer)) return url;
      if (url.startsWith("/")) return `${baseUrl}${url}`;
      return baseUrl;
    },
  },
};
