import { AsideSkeleton, HeaderSkeleton, LoadingFrame, TableSkeleton } from "@/components/Skeleton";

export default function Loading() {
  return (
    <LoadingFrame label="Loading the rule">
      <HeaderSkeleton back badges={1} />
      <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
        <AsideSkeleton actions={1} />
        <TableSkeleton cols={7} rows={5} />
      </div>
    </LoadingFrame>
  );
}
