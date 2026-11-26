"use client";

import Link from "next/link";
import { useEffect, useRef, type CSSProperties } from "react";
import { FLASH_COOKIE, type Flash } from "@/lib/flash";
import { useAppDispatch, useAppSelector } from "@/store/hooks";
import { selectToasts, toastDismissed, toastRemoved, toastShown, type Toast, type ToastTone } from "@/store/toastSlice";
import { Icon, type IconName } from "./Icon";

const TONE: Record<ToastTone, { icon: IconName; badge: string; bar: string }> = {
  success: { icon: "check", badge: "bg-ok-soft text-ok", bar: "bg-ok" },
  danger: { icon: "alert", badge: "bg-danger-soft text-danger", bar: "bg-danger" },
  warn: { icon: "alert", badge: "bg-warn-soft text-warn", bar: "bg-warn" },
  info: { icon: "activity", badge: "bg-info-soft text-info", bar: "bg-info" },
  brand: { icon: "shield", badge: "bg-brand-soft text-brand", bar: "bg-brand" },
};

/**
 * Every notice in the console: action results, errors and live alert changes. Top right on desktop,
 * a snackbar stack at the bottom on phones. The countdown is a CSS animation, so hovering or focusing a
 * notice pauses it; when it ends the notice plays its exit animation and is removed.
 */
export function Toaster() {
  const toasts = useAppSelector(selectToasts);
  return (
    <section
      aria-label="Notifications"
      aria-live="polite"
      className="pointer-events-none fixed inset-x-3 bottom-3 z-[60] flex flex-col-reverse gap-2 sm:inset-x-auto sm:bottom-auto sm:right-4 sm:top-[4.25rem] sm:w-[360px] sm:flex-col"
    >
      {toasts.map((t) => (
        <ToastCard key={t.id} toast={t} />
      ))}
    </section>
  );
}

function ToastCard({ toast: t }: { toast: Toast }) {
  const dispatch = useAppDispatch();
  const tone = TONE[t.tone];
  const close = () => dispatch(toastDismissed(t.id));

  return (
    <div
      role={t.tone === "danger" ? "alert" : "status"}
      className={`toast pointer-events-auto relative overflow-hidden rounded-xl border border-line bg-raised shadow-pop ${t.leaving ? "toast-leave" : "toast-enter"}`}
      onAnimationEnd={(e) => {
        if (t.leaving && e.target === e.currentTarget) dispatch(toastRemoved(t.id));
      }}
    >
      <div className="flex gap-3 p-3 pr-10">
        <span className={`mt-px flex h-6 w-6 shrink-0 items-center justify-center rounded-full ${tone.badge}`}>
          <Icon name={tone.icon} size={13} />
        </span>
        <div className="min-w-0 text-[13px] leading-snug">
          <div className="font-medium text-ink">{t.title}</div>
          {t.message && <div className="mt-0.5 line-clamp-3 break-words text-muted">{t.message}</div>}
        </div>
      </div>
      {t.href && <Link href={t.href} onClick={close} className="absolute inset-0 rounded-xl" aria-label={`${t.title}. Open`} />}
      <button
        onClick={close}
        aria-label="Dismiss notification"
        className="absolute right-2 top-2 z-10 flex h-6 w-6 items-center justify-center rounded-md text-muted transition-colors hover:bg-sunken hover:text-ink"
      >
        <Icon name="close" size={13} />
      </button>
      {t.duration > 0 && !t.leaving && (
        <div
          className={`toast-timer absolute bottom-0 left-0 h-[2px] w-full ${tone.bar}`}
          style={{ "--toast-ms": `${t.duration}ms` } as CSSProperties}
          onAnimationEnd={close}
          aria-hidden
        />
      )}
    </div>
  );
}

/**
 * Shows a server action's result (lib/flash.ts): the root layout passes the flash cookie's message here after
 * the action re-renders the page. Each message has an id, so it shows once; the cookie is then deleted.
 */
export function FlashToasts({ flash }: { flash: Flash | null }) {
  const dispatch = useAppDispatch();
  const shown = useRef<string | null>(null); // the prop stays set until the next render; React also runs effects twice in dev

  useEffect(() => {
    if (!flash || shown.current === flash.id) return;
    shown.current = flash.id;
    dispatch(toastShown(flash.tone === "danger" ? { tone: "danger", title: "That didn’t go through", message: flash.text } : { tone: "success", title: flash.text }));
    document.cookie = `${FLASH_COOKIE}=; Max-Age=0; path=/; samesite=lax`;
  }, [flash, dispatch]);

  return null;
}
