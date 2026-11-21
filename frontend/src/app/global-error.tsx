"use client";

import "./globals.css";
import { RetryButton } from "@/components/RetryButton";
import { StatusPage } from "@/components/StatusPage";

/** Last resort: the root layout itself failed, so this renders its own <html>, without sidebar or fonts. */
export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <html lang="en">
      <body className="min-h-screen font-sans text-[14px]">
        <StatusPage
          code="Error"
          variant="error"
          title="Sentinel couldn’t start this page"
          actions={
            <>
              <RetryButton onRetry={reset} className="btn btn-primary btn-lg" />
              <a href="/" className="btn btn-lg">Overview</a>
            </>
          }
        >
          The console hit an unexpected error before the page could load. Nothing was changed.
          {error.digest && (
            <span className="mt-3 block">
              Reference <span className="kbd select-all">{error.digest}</span>
            </span>
          )}
        </StatusPage>
      </body>
    </html>
  );
}
