import { HeaderSkeleton, LoadingFrame, Sk, TextCardSkeleton } from "@/components/Skeleton";

export default function Loading() {
  return (
    <LoadingFrame label="Loading the simulation page">
      <HeaderSkeleton />
      <Sk className="mb-6 h-12 w-full rounded-xl" />
      <div className="mb-8 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <div key={i} className="card space-y-3 p-5">
            <Sk className="h-4 w-24 rounded-md" />
            <Sk className="h-4 w-2/3" />
            <Sk className="h-3 w-full" />
            <Sk className="h-3 w-5/6" />
            <Sk className="h-8 w-full rounded-lg" />
          </div>
        ))}
      </div>
      <div className="grid gap-5 xl:grid-cols-[1.15fr_1fr]">
        <TextCardSkeleton lines={4} />
        <TextCardSkeleton lines={6} />
      </div>
    </LoadingFrame>
  );
}
