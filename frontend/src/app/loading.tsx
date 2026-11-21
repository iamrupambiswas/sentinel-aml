import { HeaderSkeleton, ListCardSkeleton, LoadingFrame, Sk, StatTilesSkeleton } from "@/components/Skeleton";

/** Overview, and the fallback for any route without its own loading.tsx. */
export default function Loading() {
  return (
    <LoadingFrame label="Loading the overview">
      <HeaderSkeleton badges={1} />
      <StatTilesSkeleton />
      <div className="grid gap-5 xl:grid-cols-[1fr_340px]">
        <ListCardSkeleton rows={6} />
        <div className="card space-y-4 p-5">
          <Sk className="mb-2 h-3 w-28" />
          {[0, 1, 2, 3, 4].map((i) => (
            <div key={i}>
              <Sk className="mb-2 h-2.5 w-24" />
              <Sk className="h-1.5 w-full rounded-full" />
            </div>
          ))}
        </div>
      </div>
      <div className="mt-5 grid gap-5 xl:grid-cols-2">
        <ListCardSkeleton rows={4} />
        <ListCardSkeleton rows={4} />
      </div>
    </LoadingFrame>
  );
}
