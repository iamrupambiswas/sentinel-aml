"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Fragment } from "react";
import { Icon } from "./Icon";
import { LiveStatus } from "./LiveAlerts";

const SECTION: Record<string, string> = {
  alerts: "Alerts",
  cases: "Cases",
  customers: "Customers",
  audit: "Audit trail",
  rules: "Detection rules",
  ops: "Dead letters",
  simulate: "Simulation",
};

/** Breadcrumbs from the URL (first segment named, the rest shown as-is) plus the live status. */
export function Topbar() {
  const path = usePathname();
  const parts = path.split("/").filter(Boolean);
  const crumbs = [{ href: "/", label: "Overview" }];
  if (parts[0]) crumbs.push({ href: `/${parts[0]}`, label: SECTION[parts[0]] ?? parts[0] });
  if (parts[1]) {
    const raw = decodeURIComponent(parts[1]);
    crumbs.push({ href: path, label: parts[0] === "cases" ? `CASE-${raw.padStart(6, "0")}` : raw.length > 14 ? `${raw.slice(0, 8)}…` : raw });
  }

  return (
    <header className="sticky top-0 z-20 hidden h-14 items-center border-b border-line bg-canvas/85 px-8 backdrop-blur lg:flex">
      <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-1.5 text-[13px]">
        {crumbs.map((c, i) => (
          <Fragment key={c.href + i}>
            {i > 0 && <Icon name="chevron" size={12} className="shrink-0 text-line-strong" />}
            {i === crumbs.length - 1 ? (
              <span className="truncate font-medium text-ink" aria-current="page">{c.label}</span>
            ) : (
              <Link href={c.href} className="truncate text-muted hover:text-ink">{c.label}</Link>
            )}
          </Fragment>
        ))}
      </nav>
      <div className="ml-auto flex items-center gap-3">
        <LiveStatus />
      </div>
    </header>
  );
}
