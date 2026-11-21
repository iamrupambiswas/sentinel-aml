"use client";

import { useActionState, useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { Spinner } from "@/components/SubmitButton";
import { revealNationalId, type RevealState } from "./actions";

/** Shows the masked value; a senior can reveal the full one with a reason. It hides itself again after 60 s. */
export function RevealNationalId({ customerId, masked, canReveal }: { customerId: string; masked: string; canReveal: boolean }) {
  const [state, action, pending] = useActionState<RevealState, FormData>(revealNationalId, {});
  const [open, setOpen] = useState(false);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (!state.idNumber) return;
    setVisible(true);
    setOpen(false);
    const t = setTimeout(() => setVisible(false), 60_000);
    return () => clearTimeout(t);
  }, [state]);

  return (
    <div>
      <div className="flex items-center gap-2">
        <span className="font-mono text-[13.5px] tracking-wide text-ink">{visible && state.idNumber ? state.idNumber : masked}</span>
        {visible && state.idNumber ? (
          <button className="btn btn-ghost h-7 px-2 text-[12px]" onClick={() => setVisible(false)}>Hide</button>
        ) : canReveal ? (
          <button className="btn btn-ghost h-7 px-2 text-[12px]" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
            <Icon name="eye" size={14} />
            Reveal
          </button>
        ) : (
          <span className="flex items-center gap-1 text-[12px] text-muted" title="Only senior analysts can reveal national IDs">
            <Icon name="lock" size={13} />
            Masked
          </span>
        )}
      </div>
      {open && (
        <form action={action} className="mt-3 space-y-2 rounded-lg border border-line bg-sunken/50 p-3 animate-rise">
          <input type="hidden" name="customerId" value={customerId} />
          <label className="label" htmlFor="reveal-reason">Why do you need the full ID?</label>
          <input id="reveal-reason" name="reason" required maxLength={300} className="input" placeholder="e.g. KYC refresh for CASE-000012" />
          <p className="text-[12px] text-muted">Your name, the time and this reason are written to the audit trail.</p>
          <div className="flex gap-2">
            <button className="btn btn-primary" disabled={pending} aria-busy={pending || undefined}>
              {pending && <Spinner />}
              {pending ? "Revealing…" : "Reveal"}
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => setOpen(false)}>Cancel</button>
          </div>
        </form>
      )}
      {state.error && <p role="alert" className="mt-2 text-[12.5px] text-danger">{state.error}</p>}
    </div>
  );
}
