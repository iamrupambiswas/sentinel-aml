import Link from "next/link";
import { Icon } from "@/components/Icon";
import { StatusPage } from "@/components/StatusPage";

export default function CaseNotFound() {
  return (
    <StatusPage
      code="404"
      title="Case not found"
      actions={
        <Link href="/cases?view=all" className="btn btn-primary btn-lg">
          <Icon name="back" size={15} />
          All cases
        </Link>
      }
    >
      No case has this number. Case numbers look like CASE-000042; the list shows every case you can see.
    </StatusPage>
  );
}
