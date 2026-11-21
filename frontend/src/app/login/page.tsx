import type { Metadata } from "next";
import { Logo } from "@/components/Icon";
import { LoginButton } from "./LoginButton";

export const metadata: Metadata = { title: "Sign in" };

const POINTS = [
  ["Detect", "Six AML typologies scored in under a second, with the evidence behind every hit."],
  ["Investigate", "One alert per pattern, grouped into cases with notes and a clear owner."],
  ["Prove", "Every decision lands in a hash-chained audit trail that shows any tampering."],
];

export default async function LoginPage({ searchParams }: { searchParams: Promise<{ callbackUrl?: string; error?: string }> }) {
  const { callbackUrl, error } = await searchParams;
  // Only same-site paths, never an absolute URL from the query string
  const target = callbackUrl?.startsWith("/") && !callbackUrl.startsWith("//") ? callbackUrl : "/";

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.15fr_1fr]">
      {/* Brand panel */}
      <section className="relative hidden overflow-hidden bg-[#101415] text-[#c9cfcc] lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.55]"
          style={{
            backgroundImage:
              "linear-gradient(rgb(255 255 255 / 0.045) 1px, transparent 1px), linear-gradient(90deg, rgb(255 255 255 / 0.045) 1px, transparent 1px)",
            backgroundSize: "44px 44px",
          }}
          aria-hidden
        />
        <div className="relative flex items-center gap-3">
          <Logo size={30} />
          <div className="leading-tight">
            <div className="text-[17px] font-semibold tracking-tight text-white">Sentinel</div>
            <div className="text-[12px] text-[#7b8580]">AML operations</div>
          </div>
        </div>

        <div className="relative max-w-lg">
          {/* A transaction signal with one spike flagged: what the product does, drawn once on load */}
          <svg viewBox="0 0 480 120" className="mb-10 w-full" aria-hidden>
            <path
              d="M0 86 H70 L84 78 L98 90 L112 80 H170 L184 84 L198 76 L212 88 H252 L266 22 L280 98 L294 70 H360 L374 82 L388 74 L402 86 H480"
              fill="none"
              stroke="#3fb5a5"
              strokeWidth="1.8"
              strokeLinejoin="round"
              strokeDasharray="900"
              strokeDashoffset="900"
              style={{ animation: "draw 1.8s ease-out 0.2s forwards" }}
            />
            <circle cx="266" cy="22" r="5" fill="none" stroke="#f07c70" strokeWidth="1.6" opacity="0" style={{ animation: "rise 0.4s ease-out 1.6s forwards" }} />
            <line x1="266" y1="30" x2="266" y2="112" stroke="#f07c70" strokeWidth="1" strokeDasharray="3 4" opacity="0" style={{ animation: "rise 0.4s ease-out 1.7s forwards" }} />
          </svg>
          <h2 className="text-[30px] font-semibold leading-tight tracking-tight text-white">
            From transaction to decision, with every step on record.
          </h2>
          <dl className="mt-8 space-y-5">
            {POINTS.map(([k, v]) => (
              <div key={k} className="flex gap-4">
                <dt className="w-24 shrink-0 text-[13px] font-medium text-[#3fb5a5]">{k}</dt>
                <dd className="text-[14px] leading-relaxed text-[#aab3af]">{v}</dd>
              </div>
            ))}
          </dl>
        </div>

        <div className="relative text-[12px] text-[#5f6966]">Internal use only. Access is logged.</div>
      </section>

      {/* Sign-in */}
      <section className="canvas-grid flex items-center justify-center px-6 py-16">
        <div className="w-full max-w-[380px] animate-rise">
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <Logo size={28} />
            <span className="text-[17px] font-semibold tracking-tight">Sentinel</span>
          </div>
          <h1 className="text-[24px] font-semibold tracking-tight">Sign in</h1>
          <p className="mt-2 text-[14px] leading-relaxed text-muted">
            Use your organisation account. You’ll be sent to the identity provider and brought straight back.
          </p>

          {error && (
            <p role="alert" className="mt-6 rounded-xl border border-danger/30 bg-danger-soft px-4 py-3 text-[13px] text-danger">
              Sign-in didn’t complete ({error}). Please try again.
            </p>
          )}

          <div className="mt-8">
            <LoginButton callbackUrl={target} />
          </div>

          <div className="mt-8 rounded-xl border border-line bg-surface p-4 text-[12.5px] text-muted">
            <div className="mb-1.5 font-medium text-ink-2">Local test accounts</div>
            <code className="font-mono text-ink-2">analyst1</code>, <code className="font-mono text-ink-2">senior1</code>,{" "}
            <code className="font-mono text-ink-2">admin1</code> — the password is the same as the username.
          </div>
        </div>
      </section>
    </div>
  );
}
