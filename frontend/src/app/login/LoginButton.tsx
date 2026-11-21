"use client";

import { signIn } from "next-auth/react";

export function LoginButton({ callbackUrl }: { callbackUrl: string }) {
  return (
    <button className="btn btn-primary w-full py-2" onClick={() => signIn("keycloak", { callbackUrl })}>
      Sign in with Keycloak
    </button>
  );
}
