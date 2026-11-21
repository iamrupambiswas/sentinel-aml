import { FilterBarSkeleton, HeaderSkeleton, LoadingFrame, TableSkeleton } from "@/components/Skeleton";

export default function Loading() {
  return (
    <LoadingFrame label="Loading the audit trail">
      <HeaderSkeleton badges={1} />
      <FilterBarSkeleton fields={3} />
      <TableSkeleton cols={6} rows={10} />
    </LoadingFrame>
  );
}
