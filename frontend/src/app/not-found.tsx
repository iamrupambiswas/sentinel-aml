import type { Metadata } from "next";
import Link from "next/link";
import { Icon } from "@/components/Icon";
import { StatusPage } from "@/components/StatusPage";

export const metadata: Metadata = { title: "Page not found" };

export default function NotFound() {
  return (
    <StatusPage
      code="404"
      title="No record of this page"
      actions={
        <>
          <Link href="/" className="btn btn-primary btn-lg">
            <Icon name="home" size={15} />
            Back to overview
          </Link>
          <Link href="/alerts" className="btn btn-lg">
            Alert queue
          </Link>
        </>
      }
    >
      The address may be mistyped, or the page has moved. Nothing else is affected: your alerts and cases are where you left them.
    </StatusPage>
  );
}
