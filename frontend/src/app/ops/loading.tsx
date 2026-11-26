import { HeaderSkeleton, ListCardSkeleton, LoadingFrame, Sk } from "@/components/Skeleton";

export default function Loading() {
  return (
    <LoadingFrame label="Loading dead letters">
      <HeaderSkeleton />
      <div className="mb-5 grid gap-4 md:grid-cols-3">
        {[0, 1, 2].map((i) => (
          <div key={i} className="card p-5">
            <Sk className="mb-3 h-3 w-40" />
            <Sk className="h-6 w-10" />
          </div>
        ))}
      </div>
      <ListCardSkeleton rows={4} />
    </LoadingFrame>
  );
}
