import { HeaderSkeleton, LoadingFrame, TableSkeleton } from "@/components/Skeleton";

export default function Loading() {
  return (
    <LoadingFrame label="Loading detection rules">
      <HeaderSkeleton />
      <TableSkeleton cols={6} rows={6} />
    </LoadingFrame>
  );
}
