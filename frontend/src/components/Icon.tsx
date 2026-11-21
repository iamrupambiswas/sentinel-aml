import type { SVGProps } from "react";

/** A small, consistent icon set (1.6px strokes on a 20px grid), drawn inline: no icon library needed. */
const PATHS = {
  overview: "M3 3h6v6H3zM11 3h6v4h-6zM11 9h6v8h-6zM3 11h6v6H3z",
  alert: "M10 3 2.5 16h15L10 3zM10 8v3.5M10 13.6v.1",
  folder: "M2.5 5.5a1.5 1.5 0 0 1 1.5-1.5h3.6l1.6 2H16a1.5 1.5 0 0 1 1.5 1.5v7A1.5 1.5 0 0 1 16 16H4a1.5 1.5 0 0 1-1.5-1.5z",
  user: "M10 10a3.2 3.2 0 1 0 0-6.4A3.2 3.2 0 0 0 10 10zM3.5 17c.8-3 3.4-4.6 6.5-4.6s5.7 1.6 6.5 4.6",
  ledger: "M5 2.5h8.5L16 5v12.5H5zM8 7h5M8 10h5M8 13h3",
  sliders: "M4 5h7M14 5h2M4 10h2M9 10h7M4 15h9M16 15h0M11 3.5v3M6 8.5v3M13 13.5v3",
  inbox: "M2.5 11 5 4h10l2.5 7v5h-15zM2.5 11H7l1 2h4l1-2h4.5",
  book: "M4 3.5h8.5A1.5 1.5 0 0 1 14 5v12H5.5A1.5 1.5 0 0 1 4 15.5zM14 6h2v11h-2M7 7h4",
  sun: "M10 13.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM10 1.5v2M10 16.5v2M3.6 3.6 5 5M15 15l1.4 1.4M1.5 10h2M16.5 10h2M3.6 16.4 5 15M15 5l1.4-1.4",
  moon: "M16.5 12.2A7 7 0 0 1 7.8 3.5a7 7 0 1 0 8.7 8.7z",
  monitor: "M2.5 4h15v9.5h-15zM7 17h6M10 13.5V17",
  logout: "M12.5 6V4H4v12h8.5v-2M8.5 10H17M14 7l3 3-3 3",
  chevron: "M8 5l5 5-5 5",
  menu: "M3 5.5h14M3 10h14M3 14.5h14",
  close: "M5 5l10 10M15 5 5 15",
  search: "M8.8 15a6.2 6.2 0 1 0 0-12.4 6.2 6.2 0 0 0 0 12.4zM13.3 13.3 17.5 17.5",
  plus: "M10 4v12M4 10h12",
  check: "M4 10.5 8 14l8-8.5",
  external: "M11 3.5h5.5V9M16.5 3.5 9 11M14 12v4.5H3.5V6H8",
  shield: "M10 2 3.5 4.5v5c0 4 2.8 7.1 6.5 8.5 3.7-1.4 6.5-4.5 6.5-8.5v-5z",
  eye: "M1.8 10S4.8 4.5 10 4.5 18.2 10 18.2 10 15.2 15.5 10 15.5 1.8 10 1.8 10zM10 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z",
  lock: "M5 9h10v8H5zM7 9V6.5a3 3 0 0 1 6 0V9",
  link: "M8.5 11.5a3.5 3.5 0 0 0 5 0l2.5-2.5a3.5 3.5 0 0 0-5-5L10 5M11.5 8.5a3.5 3.5 0 0 0-5 0L4 11a3.5 3.5 0 0 0 5 5l1-1",
  activity: "M2 10h3.5l2-5 3.5 10 2-5H18",
} as const;

export type IconName = keyof typeof PATHS;

export function Icon({ name, size = 16, ...props }: { name: IconName; size?: number } & SVGProps<SVGSVGElement>) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      {...props}
    >
      <path d={PATHS[name]} />
    </svg>
  );
}

/** The Sentinel mark: a watch tower's beam over a baseline. */
export function Logo({ size = 22 }: { size?: number }) {
  return (
    // Same mark as brand/svg/sentinel-mark.svg: shield + an "S" drawn as a transaction flow, middle node flagged
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden>
      <defs>
        <linearGradient id="sentinel-g" x1="0" y1="0" x2="0.35" y2="1">
          <stop offset="0" stopColor="#16897d" />
          <stop offset="1" stopColor="#0a4f48" />
        </linearGradient>
      </defs>
      <path d="M32 3.5C39 7 46 8.6 54 9.2V29C54 43.6 45 54.6 32 60.5 19 54.6 10 43.6 10 29V9.2C18 8.6 25 7 32 3.5Z" fill="url(#sentinel-g)" />
      <path
        d="M42 20.5C39.5 17.6 36 16 32 16 26.5 16 22.5 19 22.5 23.6 22.5 28.6 27 30 32 31.5 37 33 41.5 34.5 41.5 39.5 41.5 44 37.5 47 32 47 28 47 24.5 45.5 22 42.5"
        fill="none"
        stroke="#fff"
        strokeWidth="5"
        strokeLinecap="round"
      />
      <circle cx="42" cy="20.5" r="3.6" fill="#fff" />
      <circle cx="22" cy="42.5" r="3.6" fill="#fff" />
      <circle cx="32" cy="31.5" r="5.6" fill="#0f6b62" />
      <circle cx="32" cy="31.5" r="3.9" fill="#f5b942" />
    </svg>
  );
}
