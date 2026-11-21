import Link from "next/link";
import { EmptyState } from "@/components/ui";

export default function NotFound() {
  return (
    <div className="card mx-auto mt-10 max-w-lg">
      <EmptyState icon="search" title="Page not found">
        The page you asked for doesn’t exist.{" "}
        <Link href="/" className="text-brand hover:underline">Back to the overview</Link>
      </EmptyState>
    </div>
  );
}
