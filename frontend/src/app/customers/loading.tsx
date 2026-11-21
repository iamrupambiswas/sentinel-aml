import { FilterBarSkeleton, HeaderSkeleton, ListCardSkeleton, LoadingFrame, TextCardSkeleton } from "@/components/Skeleton";

export default function Loading() {
  return (
    <LoadingFrame label="Loading the customer">
      <HeaderSkeleton />
      <FilterBarSkeleton fields={1} />
      <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
        <TextCardSkeleton lines={6} />
        <ListCardSkeleton rows={3} />
      </div>
    </LoadingFrame>
  );
}
