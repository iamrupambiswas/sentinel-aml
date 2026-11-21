import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import { Nav } from "@/components/Nav";
import { getSession, keycloakLogoutUrl } from "@/lib/session";
import { hasRole } from "@/lib/tokens";

export const metadata: Metadata = {
  title: "Sentinel AML console",
  description: "Analyst console: alert queue, rules and operations",
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  // Null on /login; every other page is behind middleware, so a session exists there
  const session = await getSession();

  return (
    <html lang="en">
      <body className="min-h-screen font-sans antialiased">
        {session && (
          <>
            <Nav
              username={session.username}
              roles={session.roles}
              isAdmin={hasRole(session, "ADMIN")}
              logoutUrl={keycloakLogoutUrl()}
            />
          </>
        )}
        <main className="mx-auto max-w-7xl px-4 py-6">{children}</main>
      </body>
    </html>
  );
}
