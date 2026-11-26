import type { ReactNode } from "react";

/**
 * Full-page state for 404s and errors, in the product's own language: a transaction signal being scanned.
 * "missing" draws a gap where the record should be; "error" draws a flagged spike (like the login page).
 */
export function StatusPage({
  code,
  title,
  children,
  actions,
  variant = "missing",
}: {
  code: string;
  title: string;
  children: ReactNode;
  actions: ReactNode;
  variant?: "missing" | "error";
}) {
  const accent = variant === "error" ? "var(--danger)" : "var(--brand)";
  return (
    <div className="mx-auto flex min-h-[70vh] max-w-xl flex-col items-center justify-center px-4 py-12 text-center">
      <div className="float relative mb-8 w-full max-w-md overflow-hidden" aria-hidden>
        <svg viewBox="0 0 480 140" className="w-full">
          {variant === "missing" ? (
            <>
              <path d="M0 100 H60 L74 90 L88 104 L102 94 H180" {...line("var(--muted)", 0.1)} />
              <path d="M300 100 H340 L354 88 L368 104 L382 94 H480" {...line("var(--muted)", 0.5)} />
              <path d="M180 100 H300" fill="none" stroke={accent} strokeWidth="1.6" strokeDasharray="4 6" opacity="0" style={{ animation: "rise 0.4s ease-out 1.1s forwards" }} />
              <g opacity="0" style={{ animation: "rise 0.45s ease-out 1.3s forwards" }}>
                <circle cx="240" cy="58" r="20" fill="var(--surface)" stroke={accent} strokeWidth="1.6" />
                <text x="240" y="65" textAnchor="middle" fontFamily="var(--font-plex-mono), monospace" fontSize="20" fill={accent}>?</text>
                <line x1="240" y1="80" x2="240" y2="96" stroke={accent} strokeWidth="1" strokeDasharray="3 3" />
              </g>
            </>
          ) : (
            <>
              <path d="M0 100 H70 L84 92 L98 104 L112 94 H200 L216 28 L232 118 L248 82 H330 L344 94 L358 88 L372 100 H480" {...line("var(--muted)", 0.1)} />
              <g opacity="0" style={{ animation: "rise 0.45s ease-out 1.3s forwards" }}>
                <circle cx="216" cy="28" r="7" fill="none" stroke={accent} strokeWidth="1.8" />
                <circle cx="216" cy="28" r="2.5" fill={accent} />
                <line x1="216" y1="38" x2="216" y2="132" stroke={accent} strokeWidth="1" strokeDasharray="3 4" />
              </g>
            </>
          )}
        </svg>
        {/* scanning beam */}
        <div
          className="sweep pointer-events-none absolute inset-y-0 left-0 w-1/5"
          style={{ background: `linear-gradient(90deg, transparent, color-mix(in srgb, ${accent} 18%, transparent), transparent)` }}
        />
      </div>

      <div
        className="mb-3 bg-clip-text font-mono text-[56px] font-semibold leading-none tracking-tight text-transparent sm:text-[64px]"
        style={{ backgroundImage: `linear-gradient(180deg, var(--ink) 20%, ${accent})` }}
      >
        {code}
      </div>
      <h1 className="text-[20px] font-semibold tracking-tight text-ink">{title}</h1>
      <div className="mt-2 max-w-md text-[13.5px] leading-relaxed text-muted">{children}</div>
      <div className="mt-7 flex flex-wrap items-center justify-center gap-2">{actions}</div>
    </div>
  );
}

/** A signal path that draws itself once. */
function line(stroke: string, delay: number) {
  return {
    fill: "none",
    stroke,
    strokeWidth: 1.8,
    strokeLinejoin: "round" as const,
    pathLength: 1,
    strokeDasharray: 1,
    strokeDashoffset: 1,
    style: { animation: `draw 1.2s ease-out ${delay}s forwards` },
  };
}
