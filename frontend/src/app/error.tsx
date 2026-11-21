"use client";

import Link from "next/link";
import { useEffect } from "react";
import { Icon } from "@/components/Icon";
import { RetryButton } from "@/components/RetryButton";
import { StatusPage } from "@/components/StatusPage";

/**
 * Any page that throws while rendering lands here, inside the normal layout (sidebar and top bar stay).
 * Expected API failures don't: pages catch those and show an inline ErrorPanel instead.
 */
export default function PageError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <StatusPage
      code="Error"
      variant="error"
      title="This page ran into a problem"
      actions={
        <>
          <RetryButton onRetry={reset} className="btn btn-primary btn-lg" />
          <Link href="/" className="btn btn-lg">
            <Icon name="home" size={15} />
            Overview
          </Link>
        </>
      }
    >
      Something unexpected happened while loading it. Nothing was changed. Try again, and if it keeps happening, send this
      reference to your administrator.
      {error.digest && (
        <span className="mt-3 block">
          <span className="kbd select-all">{error.digest}</span>
        </span>
      )}
    </StatusPage>
  );
}
