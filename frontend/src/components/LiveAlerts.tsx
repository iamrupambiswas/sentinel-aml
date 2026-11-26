"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { ruleLabel } from "@/lib/format";
import { useAppDispatch, useAppSelector } from "@/store/hooks";
import { selectConnected, streamLost, streamOpened, type AlertChanged } from "@/store/liveSlice";
import { toastShown, type ToastTone } from "@/store/toastSlice";

const CHANGE: Record<AlertChanged["change"], { title: string; tone: ToastTone }> = {
  CREATED: { title: "New alert", tone: "danger" },
  UPDATED: { title: "New evidence on alert", tone: "warn" },
  STATUS_CHANGED: { title: "Alert status changed", tone: "brand" },
};

/**
 * Mounted once in the root layout; renders nothing. Listens to /api/live (SSE, piped by the Next.js server
 * from the gateway with the user's token), tracks the connection in the store and shows each change as a
 * notice. Also re-renders the current page's server data, debounced so a CSV batch causes one refresh, not
 * dozens. EventSource reconnects by itself after a drop.
 */
export function LiveFeed() {
  const router = useRouter();
  const dispatch = useAppDispatch();

  useEffect(() => {
    let refresh: ReturnType<typeof setTimeout> | undefined;
    const source = new EventSource("/api/live");
    source.addEventListener("connected", () => dispatch(streamOpened()));
    source.onerror = () => dispatch(streamLost());
    source.addEventListener("alert", (e) => {
      const c = JSON.parse((e as MessageEvent).data) as AlertChanged;
      const status = c.change === "STATUS_CHANGED" && c.status ? ` → ${c.status.replaceAll("_", " ").toLowerCase()}` : "";
      dispatch(
        toastShown({
          ...CHANGE[c.change],
          message: `${c.accountId} · ${ruleLabel(c.ruleCode)}${status}`,
          href: `/alerts/${c.alertId}`,
          duration: 8000,
        }),
      );
      clearTimeout(refresh);
      refresh = setTimeout(() => router.refresh(), 500);
    });
    return () => {
      source.close();
      clearTimeout(refresh);
    };
  }, [dispatch, router]);

  return null;
}

/** The "Live / Connecting" pill. Reads the store, so it can sit in the top bar and the mobile bar at once. */
export function LiveStatus() {
  const connected = useAppSelector(selectConnected);
  return (
    <span
      className="inline-flex h-7 items-center gap-2 rounded-full border border-line bg-surface px-2.5 text-[12px] text-muted transition-colors"
      title={connected ? "Receiving alert changes as they happen" : "Connecting to the live alert stream"}
    >
      <span className="relative flex h-2 w-2" aria-hidden>
        {connected && <span className="absolute inline-flex h-full w-full rounded-full bg-ok animate-ping-slow" />}
        <span className={`relative inline-flex h-2 w-2 rounded-full transition-colors duration-300 ${connected ? "bg-ok" : "bg-line-strong"}`} />
      </span>
      {connected ? "Live" : "Connecting"}
    </span>
  );
}
