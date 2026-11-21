"use client";

import { signIn } from "next-auth/react";
import { useState } from "react";
import { Icon } from "@/components/Icon";

export function LoginButton({ callbackUrl }: { callbackUrl: string }) {
  const [busy, setBusy] = useState(false);
  return (
    <button
      className="btn btn-primary btn-lg w-full"
      disabled={busy}
      onClick={() => {
        setBusy(true);
        signIn("keycloak", { callbackUrl });
      }}
    >
      <Icon name="lock" />
      {busy ? "Redirecting…" : "Continue with Keycloak"}
    </button>
  );
}
