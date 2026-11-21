"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { signOut } from "next-auth/react";
import { LiveAlerts } from "./LiveAlerts";

interface Props {
  username: string;
  roles: string[];
  isAdmin: boolean;
  logoutUrl: string;
}

const LINKS = [
  { href: "/", label: "Dashboard", adminOnly: false },
  { href: "/alerts", label: "Alert queue", adminOnly: false },
  { href: "/rules", label: "Rules", adminOnly: false },
  { href: "/ops", label: "Dead letters", adminOnly: true },
];

const SHOWN_ROLES = ["ADMIN", "SENIOR_ANALYST", "ANALYST", "INGEST"];

export function Nav({ username, roles, isAdmin, logoutUrl }: Props) {
  const path = usePathname();
  const topRole = SHOWN_ROLES.find((r) => roles.includes(r)) ?? "no role";

  return (
    <header className="border-b border-line bg-surface">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
        <Link href="/" className="flex items-center gap-2 font-semibold tracking-tight">
          <span aria-hidden className="inline-block h-5 w-1.5 rounded-sm bg-accent" />
          Sentinel AML
        </Link>
        <nav aria-label="Main" className="flex flex-wrap gap-1 text-sm">
          {LINKS.filter((l) => !l.adminOnly || isAdmin).map((l) => {
            const active = l.href === "/" ? path === "/" : path.startsWith(l.href);
            return (
              <Link
                key={l.href}
                href={l.href}
                aria-current={active ? "page" : undefined}
                className={`rounded-md px-2.5 py-1.5 ${active ? "bg-accent-soft font-medium text-accent" : "text-muted hover:text-ink"}`}
              >
                {l.label}
              </Link>
            );
          })}
        </nav>
        <div className="ml-auto flex items-center gap-3 text-sm">
          <LiveAlerts />
          <span className="text-muted">
            <span className="font-medium text-ink">{username}</span> · {topRole.replace("_", " ").toLowerCase()}
          </span>
          {/* Clears the console session, then ends the Keycloak session too */}
          <button className="btn" onClick={() => signOut({ callbackUrl: logoutUrl })}>
            Sign out
          </button>
        </div>
      </div>
    </header>
  );
}
