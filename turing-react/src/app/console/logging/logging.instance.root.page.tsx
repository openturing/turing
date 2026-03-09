import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconReceiptRupee } from "@tabler/icons-react";
import { useEffect } from "react";

export default function LoggingInstanceRootPage() {
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    pushItem({ label: "Logging", href: `${ROUTES.LOGGING_INSTANCE}` });
    return () => popItem();
  }, []);
  return (
    <Page turIcon={IconReceiptRupee} title="Logging" urlBase={ROUTES.LOGGING_INSTANCE} />
  )
}


