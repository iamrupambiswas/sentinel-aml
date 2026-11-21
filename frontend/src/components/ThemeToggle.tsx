"use client";

import { useState } from "react";
import { Icon, type IconName } from "./Icon";

export type ThemeChoice = "system" | "light" | "dark";

const OPTIONS: { value: ThemeChoice; icon: IconName; label: string }[] = [
  { value: "system", icon: "monitor", label: "System theme" },
  { value: "light", icon: "sun", label: "Light theme" },
  { value: "dark", icon: "moon", label: "Dark theme" },
];

/**
 * System / light / dark. The choice is a cookie, so the server renders <html data-theme> correctly on the
 * next request and the page never flashes the wrong theme.
 */
export function ThemeToggle({ initial }: { initial: ThemeChoice }) {
  const [choice, setChoice] = useState<ThemeChoice>(initial);

  function apply(next: ThemeChoice) {
    setChoice(next);
    const root = document.documentElement;
    if (next === "system") root.removeAttribute("data-theme");
    else root.setAttribute("data-theme", next);
    document.cookie = `sentinel-theme=${next}; path=/; max-age=31536000; samesite=lax`;
  }

  return (
    <div role="radiogroup" aria-label="Colour theme" className="flex rounded-lg border border-[var(--sidebar-line)] p-0.5">
      {OPTIONS.map((o) => {
        const active = choice === o.value;
        return (
          <button
            key={o.value}
            role="radio"
            aria-checked={active}
            aria-label={o.label}
            title={o.label}
            onClick={() => apply(o.value)}
            className={`flex h-7 flex-1 items-center justify-center rounded-md transition-colors duration-150 ${
              active ? "bg-[var(--sidebar-active)] text-white" : "text-[var(--sidebar-muted)] hover:text-[var(--sidebar-ink)]"
            }`}
          >
            <Icon name={o.icon} size={15} />
          </button>
        );
      })}
    </div>
  );
}
