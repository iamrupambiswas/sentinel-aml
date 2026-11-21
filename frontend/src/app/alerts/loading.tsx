import { FilterBarSkeleton, HeaderSkeleton, LoadingFrame, TableSkeleton } from "@/components/Skeleton";

export default function Loading() {
  return (
    <LoadingFrame label="Loading alerts">
      <HeaderSkeleton />
      <FilterBarSkeleton fields={3} />
      <TableSkeleton cols={7} />
    </LoadingFrame>
  );
}
