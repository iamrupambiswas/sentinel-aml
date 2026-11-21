import { HeaderSkeleton, LoadingFrame, Sk, TableSkeleton } from "@/components/Skeleton";

export default function Loading() {
  return (
    <LoadingFrame label="Loading cases">
      <HeaderSkeleton />
      <Sk className="mb-5 h-9 w-72 rounded-lg" />
      <TableSkeleton cols={7} />
    </LoadingFrame>
  );
}
