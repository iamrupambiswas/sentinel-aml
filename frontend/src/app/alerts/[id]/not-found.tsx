import Link from "next/link";
import { Icon } from "@/components/Icon";
import { StatusPage } from "@/components/StatusPage";

export default function AlertNotFound() {
  return (
    <StatusPage
      code="404"
      title="Alert not found"
      actions={
        <Link href="/alerts" className="btn btn-primary btn-lg">
          <Icon name="back" size={15} />
          Back to alerts
        </Link>
      }
    >
      No alert has this ID. Check the link, or find the alert in the queue by account or rule.
    </StatusPage>
  );
}
