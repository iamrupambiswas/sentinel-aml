"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ruleLabel } from "@/lib/format";
import { Icon } from "./Icon";

interface AlertChanged {
  alertId: string;
  accountId: string;
  ruleCode: string;
  change: "CREATED" | "UPDATED" | "STATUS_CHANGED";
  status: string | null;
}

interface Toast extends AlertChanged {
  key: number;
}

const CHANGE = {
  CREATED: { text: "New alert", tone: "border-l-danger" },
  UPDATED: { text: "New evidence on alert", tone: "border-l-warn" },
  STATUS_CHANGED: { text: "Alert status changed", tone: "border-l-brand" },
};

/**
 * Listens to /api/live (SSE, piped by the Next.js server from the gateway with the user's token).
 * Each change shows a notice and re-renders the current page's server data, debounced so a CSV batch
 * causes one refresh, not dozens. EventSource reconnects by itself after a drop.
 */
export function LiveAlerts() {
  const router = useRouter();
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [connected, setConnected] = useState(false);
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const counter = useRef(0);

  useEffect(() => {
    const source = new EventSource("/api/live");
    source.addEventListener("connected", () => setConnected(true));
    source.onerror = () => setConnected(false);
    source.addEventListener("alert", (e) => {
      const data = JSON.parse((e as MessageEvent).data) as AlertChanged;
      const key = ++counter.current;
      setToasts((t) => [{ ...data, key }, ...t].slice(0, 4));
      setTimeout(() => setToasts((t) => t.filter((x) => x.key !== key)), 8000);
      if (refreshTimer.current) clearTimeout(refreshTimer.current);
      refreshTimer.current = setTimeout(() => router.refresh(), 500);
    });
    return () => {
      source.close();
      if (refreshTimer.current) clearTimeout(refreshTimer.current);
    };
  }, [router]);

  return (
    <>
      <span
        className="inline-flex h-7 items-center gap-2 rounded-full border border-line bg-surface px-2.5 text-[12px] text-muted"
        title={connected ? "Receiving alert changes as they happen" : "Connecting to the live alert stream"}
      >
        <span className="relative flex h-2 w-2" aria-hidden>
          {connected && <span className="absolute inline-flex h-full w-full rounded-full bg-ok animate-ping-slow" />}
          <span className={`relative inline-flex h-2 w-2 rounded-full ${connected ? "bg-ok" : "bg-line-strong"}`} />
        </span>
        {connected ? "Live" : "Connecting"}
      </span>
      <div aria-live="polite" className="pointer-events-none fixed right-4 top-4 z-[60] flex w-[340px] max-w-[calc(100vw-2rem)] flex-col gap-2">
        {toasts.map((t) => (
          <Link
            key={t.key}
            href={`/alerts/${t.alertId}`}
            className={`pointer-events-auto flex gap-3 rounded-xl border border-line border-l-[3px] ${CHANGE[t.change].tone} bg-raised p-3 shadow-pop animate-slide-in hover:border-line-strong`}
          >
            <Icon name="alert" className="mt-0.5 shrink-0 text-muted" />
            <div className="min-w-0 text-[13px]">
              <div className="font-medium text-ink">{CHANGE[t.change].text}</div>
              <div className="truncate text-muted">
                <span className="font-mono">{t.accountId}</span> · {ruleLabel(t.ruleCode)}
                {t.change === "STATUS_CHANGED" && t.status ? ` → ${t.status.replaceAll("_", " ").toLowerCase()}` : ""}
              </div>
            </div>
          </Link>
        ))}
      </div>
    </>
  );
}
