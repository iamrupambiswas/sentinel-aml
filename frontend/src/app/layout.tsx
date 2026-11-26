import type { Metadata } from "next";
import type { ReactNode } from "react";
import { IBM_Plex_Mono, IBM_Plex_Sans } from "next/font/google";
import { cookies } from "next/headers";
import "./globals.css";
import { Sidebar } from "@/components/Sidebar";
import { Topbar } from "@/components/Topbar";
import { LiveFeed } from "@/components/LiveAlerts";
import { FlashToasts, Toaster } from "@/components/Toaster";
import { StoreProvider } from "@/store/StoreProvider";
import type { ThemeChoice } from "@/store/uiSlice";
import { FLASH_COOKIE, decodeFlash } from "@/lib/flash";
import { getSession, keycloakLogoutUrl } from "@/lib/session";
import { hasRole } from "@/lib/tokens";

const plexSans = IBM_Plex_Sans({ subsets: ["latin"], weight: ["400", "500", "600", "700"], variable: "--font-plex-sans", display: "swap" });
const plexMono = IBM_Plex_Mono({ subsets: ["latin"], weight: ["400", "500"], variable: "--font-plex-mono", display: "swap" });

export const metadata: Metadata = {
  title: { default: "Sentinel · AML operations", template: "%s · Sentinel" },
  description: "Anti-money-laundering operations console: alerts, cases, customers, rules and audit trail",
};

function roleLabel(roles: string[]): string {
  if (roles.includes("ADMIN")) return "Administrator";
  if (roles.includes("SENIOR_ANALYST")) return "Senior analyst";
  if (roles.includes("ANALYST")) return "Analyst";
  return "No console role";
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  const session = await getSession(); // null on /login; every other page is behind middleware
  const jar = await cookies();
  const themeCookie = jar.get("sentinel-theme")?.value;
  const theme: ThemeChoice = themeCookie === "light" || themeCookie === "dark" ? themeCookie : "system";

  return (
    <html lang="en" data-theme={theme === "system" ? undefined : theme} className={`${plexSans.variable} ${plexMono.variable}`}>
      <body className="min-h-screen font-sans text-[14px]">
        {session ? (
          <StoreProvider theme={theme}>
            <LiveFeed />
            <Toaster />
            <FlashToasts flash={decodeFlash(jar.get(FLASH_COOKIE)?.value)} />
            <Sidebar
              username={session.username}
              roleLabel={roleLabel(session.roles)}
              isSenior={hasRole(session, "SENIOR_ANALYST")}
              isAdmin={hasRole(session, "ADMIN")}
              logoutUrl={keycloakLogoutUrl()}
              apiDocsUrl={`${process.env.GATEWAY_PUBLIC_URL ?? "http://localhost:8080"}/swagger-ui.html`}
            />
            <div className="canvas-grid min-h-screen lg:pl-64">
              <Topbar />
              <main className="page mx-auto max-w-[1280px] px-4 py-6 sm:px-8 sm:py-8">{children}</main>
            </div>
          </StoreProvider>
        ) : (
          children
        )}
      </body>
    </html>
  );
}
