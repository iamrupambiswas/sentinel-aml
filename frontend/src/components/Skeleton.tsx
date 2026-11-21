import type { ReactNode } from "react";

/**
 * Loading placeholders shaped like the real pages, used by each route's loading.tsx. They fade in after a
 * short delay (.loading-in), so a fast page never flashes them. Widths vary by row so they read as content.
 */

const WIDTHS = ["w-3/4", "w-1/2", "w-2/3", "w-5/6", "w-2/5", "w-3/5"];
const w = (i: number) => WIDTHS[i % WIDTHS.length];

export function Sk({ className = "" }: { className?: string }) {
  return <div className={`skeleton ${className}`} />;
}

export function LoadingFrame({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div role="status" aria-busy="true" aria-label={label} className="loading-in">
      <span className="sr-only">{label}</span>
      {children}
    </div>
  );
}

export function HeaderSkeleton({ back = false, badges = 0 }: { back?: boolean; badges?: number }) {
  return (
    <>
      {back && <Sk className="mb-5 h-3 w-16" />}
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <Sk className="mb-2.5 h-2.5 w-20" />
          <Sk className="mb-3 h-6 w-72 max-w-[70vw]" />
          <Sk className="h-3 w-96 max-w-[80vw]" />
        </div>
        {badges > 0 && (
          <div className="flex gap-2">
            {Array.from({ length: badges }, (_, i) => <Sk key={i} className="h-6 w-20 rounded-full" />)}
          </div>
        )}
      </div>
    </>
  );
}

export function FilterBarSkeleton({ fields = 3 }: { fields?: number }) {
  return (
    <div className="card mb-5 flex flex-wrap items-end gap-3 p-4">
      {Array.from({ length: fields }, (_, i) => (
        <div key={i} className="min-w-[140px] flex-1">
          <Sk className="mb-2 h-2.5 w-14" />
          <Sk className="h-9 w-full rounded-lg" />
        </div>
      ))}
      <Sk className="h-8 w-20 rounded-lg" />
    </div>
  );
}

export function TableSkeleton({ cols, rows = 8 }: { cols: number; rows?: number }) {
  return (
    <div className="card overflow-hidden">
      <div className="flex gap-6 border-b border-line bg-sunken/60 px-4 py-3">
        {Array.from({ length: cols }, (_, c) => <Sk key={c} className="h-2.5 flex-1" />)}
      </div>
      {Array.from({ length: rows }, (_, r) => (
        <div key={r} className="flex items-center gap-6 border-b border-line px-4 py-3.5 last:border-0">
          {Array.from({ length: cols }, (_, c) => (
            <div key={c} className="flex-1">
              <Sk className={`h-3 ${c === 0 ? "w-11 h-6" : w(r + c)}`} />
            </div>
          ))}
        </div>
      ))}
      <div className="flex justify-between border-t border-line px-4 py-3">
        <Sk className="h-3 w-16" />
        <Sk className="h-3 w-24" />
      </div>
    </div>
  );
}

export function StatTilesSkeleton() {
  return (
    <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="card p-5">
          <Sk className="mb-3 h-2.5 w-20" />
          <Sk className="mb-3 h-7 w-16" />
          <Sk className="h-2.5 w-32" />
        </div>
      ))}
    </div>
  );
}

/** A card with a title and rows of "badge + two lines", like Needs attention or Open cases. */
export function ListCardSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="card overflow-hidden">
      <div className="flex justify-between border-b border-line px-4 py-3.5">
        <Sk className="h-3 w-28" />
        <Sk className="h-3 w-16" />
      </div>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex items-center gap-4 border-b border-line px-4 py-3 last:border-0">
          <Sk className="h-6 w-11 rounded-md" />
          <div className="flex-1">
            <Sk className={`mb-2 h-3 ${w(i + 2)}`} />
            <Sk className={`h-2.5 ${w(i)}`} />
          </div>
        </div>
      ))}
    </div>
  );
}

export function TextCardSkeleton({ lines = 3 }: { lines?: number }) {
  return (
    <div className="card p-5">
      <Sk className="mb-4 h-2.5 w-28" />
      {Array.from({ length: lines }, (_, i) => <Sk key={i} className={`mb-2.5 h-3 last:mb-0 ${i === lines - 1 ? "w-2/3" : "w-full"}`} />)}
    </div>
  );
}

/** Side panel: key/value rows, then a block of action buttons. */
export function AsideSkeleton({ actions = 3 }: { actions?: number }) {
  return (
    <div className="space-y-5">
      <div className="card space-y-3 p-5">
        {[0, 1, 2, 3, 4].map((i) => (
          <div key={i} className="flex gap-4">
            <Sk className="h-3 w-20" />
            <Sk className={`h-3 flex-1 ${w(i)}`} />
          </div>
        ))}
      </div>
      <div className="card space-y-3 p-5">
        <Sk className="h-3 w-20" />
        <Sk className="h-16 w-full rounded-lg" />
        {Array.from({ length: actions }, (_, i) => <Sk key={i} className="h-8 w-full rounded-lg" />)}
      </div>
    </div>
  );
}

/** Alert / case detail: main column with cards, side panel. */
export function DetailSkeleton({ label }: { label: string }) {
  return (
    <LoadingFrame label={label}>
      <HeaderSkeleton back badges={2} />
      <div className="grid gap-5 xl:grid-cols-[1fr_340px]">
        <div className="min-w-0 space-y-5">
          <TextCardSkeleton lines={2} />
          <ListCardSkeleton rows={3} />
        </div>
        <AsideSkeleton />
      </div>
    </LoadingFrame>
  );
}
