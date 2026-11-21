"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ruleLabel } from "@/lib/format";

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

const CHANGE_TEXT = {
  CREATED: "New alert",
  UPDATED: "New hit on alert",
  STATUS_CHANGED: "Status changed",
};

/**
 * Listens to /api/live (SSE, proxied by the Next.js server to the gateway with the user's token).
 * Each change shows a short notice and re-renders the current page's server data.
 * EventSource reconnects by itself if the connection drops (gateway restart, 30-min server timeout).
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
      // A burst of hits (e.g. a CSV batch) causes one refresh, not dozens
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
      <span className="flex items-center gap-1.5 rounded-full border border-line px-2.5 py-1 text-xs text-muted" title="Live alert updates">
        <span aria-hidden className={`h-2 w-2 rounded-full ${connected ? "bg-ok" : "bg-muted"}`} />
        {connected ? "Live" : "Connecting…"}
      </span>
      <div aria-live="polite" className="fixed right-3 top-16 z-20 flex w-80 max-w-[calc(100vw-1.5rem)] flex-col gap-2">
        {toasts.map((t) => (
          <Link
            key={t.key}
            href={`/alerts/${t.alertId}`}
            className="card block p-3 text-sm shadow-md hover:border-accent"
          >
            <div className="font-medium">{CHANGE_TEXT[t.change]}</div>
            <div className="text-muted">
              {t.accountId} · {ruleLabel(t.ruleCode)}
              {t.change === "STATUS_CHANGED" && t.status ? ` → ${t.status.replaceAll("_", " ").toLowerCase()}` : ""}
            </div>
          </Link>
        ))}
      </div>
    </>
  );
}
