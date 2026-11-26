"use client";

import { useRouter } from "next/navigation";
import { useTransition } from "react";
import { Icon } from "./Icon";
import { Spinner } from "./SubmitButton";

/**
 * Re-runs the current page's server components (fresh fetch, same URL). `onRetry` lets an error boundary
 * reset itself in the same transition, so the page only re-renders once the new data is there.
 */
export function RetryButton({ onRetry, className = "btn" }: { onRetry?: () => void; className?: string }) {
  const router = useRouter();
  const [pending, start] = useTransition();
  return (
    <button
      className={className}
      disabled={pending}
      aria-busy={pending || undefined}
      onClick={() =>
        start(() => {
          router.refresh();
          onRetry?.();
        })
      }
    >
      {pending ? <Spinner /> : <Icon name="refresh" size={14} />}
      {pending ? "Retrying…" : "Try again"}
    </button>
  );
}
