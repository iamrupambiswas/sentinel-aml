"use client";

import { useEffect, useState, type ReactNode } from "react";
import { useFormStatus } from "react-dom";

/**
 * A form's submit button with a pending state. While the server action runs, every submit button in the
 * form is disabled (no double submits) and the one that was clicked shows a spinner.
 */
export function SubmitButton({
  children,
  icon,
  className = "btn",
  formAction,
  disabled,
  title,
}: {
  children: ReactNode;
  /** leading icon; the spinner takes its place while busy */
  icon?: ReactNode;
  className?: string;
  formAction?: (formData: FormData) => void | Promise<void>;
  disabled?: boolean;
  title?: string;
}) {
  const { pending } = useFormStatus();
  const [clicked, setClicked] = useState(false);
  useEffect(() => {
    if (!pending) setClicked(false);
  }, [pending]);
  const busy = pending && clicked;

  return (
    <button
      type="submit"
      formAction={formAction}
      disabled={disabled || pending}
      aria-busy={busy || undefined}
      title={title}
      onClick={() => setClicked(true)}
      className={`${className} ${busy ? "is-busy" : ""}`}
    >
      {busy ? <Spinner /> : icon}
      {children}
    </button>
  );
}

export function Spinner({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" className="spin shrink-0" aria-hidden>
      <circle cx="8" cy="8" r="6.5" fill="none" stroke="currentColor" strokeOpacity="0.25" strokeWidth="2" />
      <path d="M14.5 8A6.5 6.5 0 0 0 8 1.5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}
