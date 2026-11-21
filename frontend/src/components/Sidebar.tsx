"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect } from "react";
import { signOut } from "next-auth/react";
import { Icon, Logo, type IconName } from "./Icon";
import { useAppDispatch, useAppSelector } from "@/store/hooks";
import { navClosed, navOpened, selectNavOpen } from "@/store/uiSlice";
import { LiveStatus } from "./LiveAlerts";
import { ThemeToggle } from "./ThemeToggle";

interface NavItem {
  href: string;
  label: string;
  icon: IconName;
  show: boolean;
}

interface Props {
  username: string;
  roleLabel: string;
  isSenior: boolean;
  isAdmin: boolean;
  logoutUrl: string;
  apiDocsUrl: string;
}

export function Sidebar({ username, roleLabel, isSenior, isAdmin, logoutUrl, apiDocsUrl }: Props) {
  const path = usePathname();
  const open = useAppSelector(selectNavOpen);
  const dispatch = useAppDispatch();
  const setOpen = (o: boolean) => dispatch(o ? navOpened() : navClosed());
  useEffect(() => {
    dispatch(navClosed()); // close the mobile drawer after navigating
  }, [path, dispatch]);

  const groups: { title: string; items: NavItem[] }[] = [
    {
      title: "Monitoring",
      items: [
        { href: "/", label: "Overview", icon: "overview", show: true },
        { href: "/alerts", label: "Alerts", icon: "alert", show: true },
        { href: "/cases", label: "Cases", icon: "folder", show: true },
      ],
    },
    {
      title: "Investigation",
      items: [
        { href: "/customers", label: "Customers", icon: "user", show: true },
        { href: "/audit", label: "Audit trail", icon: "ledger", show: isSenior },
      ],
    },
    {
      title: "Configuration",
      items: [
        { href: "/rules", label: "Detection rules", icon: "sliders", show: true },
        { href: "/ops", label: "Dead letters", icon: "inbox", show: isAdmin },
      ],
    },
    {
      title: "Demo",
      items: [{ href: "/simulate", label: "Simulation", icon: "flask", show: isAdmin }],
    },
  ];

  const isActive = (href: string) => (href === "/" ? path === "/" : path.startsWith(href));
  const initials = username.slice(0, 2).toUpperCase();

  return (
    <>
      {/* Mobile top bar */}
      <div className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-[var(--sidebar-line)] bg-[var(--sidebar)] px-4 text-[var(--sidebar-ink)] lg:hidden">
        <button className="btn btn-ghost h-9 w-9 px-0 text-[var(--sidebar-ink)] hover:bg-[var(--sidebar-active)]" aria-label="Open menu" onClick={() => setOpen(true)}>
          <Icon name="menu" size={18} />
        </button>
        <Logo size={22} />
        <span className="text-sm font-semibold text-white">Sentinel</span>
        <span className="ml-auto">
          <LiveStatus />
        </span>
      </div>

      {open && <div className="fixed inset-0 z-40 bg-black/40 lg:hidden" onClick={() => setOpen(false)} aria-hidden />}

      <aside
        aria-label="Main navigation"
        className={`fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-[var(--sidebar)] text-[var(--sidebar-ink)] transition-transform duration-200 lg:translate-x-0 ${
          open ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        <div className="flex h-16 items-center gap-2.5 border-b border-[var(--sidebar-line)] px-5">
          <Logo size={26} />
          <div className="leading-tight">
            <div className="text-[15px] font-semibold tracking-tight text-white">Sentinel</div>
            <div className="text-[11px] text-[var(--sidebar-muted)]">AML operations</div>
          </div>
          <button className="ml-auto text-[var(--sidebar-muted)] lg:hidden" aria-label="Close menu" onClick={() => setOpen(false)}>
            <Icon name="close" />
          </button>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-4">
          {groups.map((g) => {
            const items = g.items.filter((i) => i.show);
            if (!items.length) return null;
            return (
              <div key={g.title} className="mb-5">
                <div className="mb-1.5 px-2.5 text-[10.5px] font-medium uppercase tracking-[0.1em] text-[var(--sidebar-muted)]">{g.title}</div>
                <ul className="space-y-0.5">
                  {items.map((i) => {
                    const active = isActive(i.href);
                    return (
                      <li key={i.href}>
                        <Link
                          href={i.href}
                          aria-current={active ? "page" : undefined}
                          className={`group relative flex h-9 items-center gap-2.5 rounded-lg px-2.5 text-[13px] transition-colors duration-150 ${
                            active ? "bg-[var(--sidebar-active)] font-medium text-white" : "hover:bg-[var(--sidebar-active)] hover:text-white"
                          }`}
                        >
                          {active && <span className="absolute -left-3 top-2 h-5 w-[3px] rounded-r bg-[var(--brand)]" aria-hidden />}
                          <Icon name={i.icon} className={active ? "text-[var(--brand)]" : "text-[var(--sidebar-muted)] group-hover:text-[var(--sidebar-ink)]"} />
                          {i.label}
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              </div>
            );
          })}
          <div className="mb-5">
            <div className="mb-1.5 px-2.5 text-[10.5px] font-medium uppercase tracking-[0.1em] text-[var(--sidebar-muted)]">Developers</div>
            <a
              href={apiDocsUrl}
              target="_blank"
              rel="noreferrer"
              className="group flex h-9 items-center gap-2.5 rounded-lg px-2.5 text-[13px] hover:bg-[var(--sidebar-active)] hover:text-white"
            >
              <Icon name="book" className="text-[var(--sidebar-muted)] group-hover:text-[var(--sidebar-ink)]" />
              API reference
              <Icon name="external" size={13} className="ml-auto text-[var(--sidebar-muted)]" />
            </a>
          </div>
        </nav>

        <div className="space-y-3 border-t border-[var(--sidebar-line)] p-4">
          <ThemeToggle />
          <div className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--sidebar-active)] text-[11px] font-semibold text-white" aria-hidden>
              {initials}
            </div>
            <div className="min-w-0 flex-1 leading-tight">
              <div className="truncate text-[13px] font-medium text-white">{username}</div>
              <div className="truncate text-[11px] text-[var(--sidebar-muted)]">{roleLabel}</div>
            </div>
            <button
              onClick={() => signOut({ callbackUrl: logoutUrl })}
              className="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--sidebar-muted)] transition-colors hover:bg-[var(--sidebar-active)] hover:text-white"
              aria-label="Sign out"
              title="Sign out"
            >
              <Icon name="logout" />
            </button>
          </div>
        </div>
      </aside>
    </>
  );
}
