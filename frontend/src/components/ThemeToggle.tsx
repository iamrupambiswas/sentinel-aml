"use client";

import { useAppDispatch, useAppSelector } from "@/store/hooks";
import { selectTheme, themeChosen, type ThemeChoice } from "@/store/uiSlice";
import { Icon, type IconName } from "./Icon";

const OPTIONS: { value: ThemeChoice; icon: IconName; label: string }[] = [
  { value: "system", icon: "monitor", label: "System theme" },
  { value: "light", icon: "sun", label: "Light theme" },
  { value: "dark", icon: "moon", label: "Dark theme" },
];

/** System / light / dark. Applying it (data-theme + cookie) is a store listener: see src/store/store.ts. */
export function ThemeToggle() {
  const choice = useAppSelector(selectTheme);
  const dispatch = useAppDispatch();

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
            onClick={() => dispatch(themeChosen(o.value))}
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
