import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconCpu2 } from "@tabler/icons-react";
import { useEffect } from "react";

export default function LLMInstanceRootPage() {
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    pushItem({ label: "Language Model", href: `${ROUTES.LLM_INSTANCE}` });
    return () => popItem();
  }, []);
  return (
    <Page turIcon={IconCpu2} title="Language Model" urlBase={ROUTES.LLM_INSTANCE} urlNew={`${ROUTES.LLM_INSTANCE}/new`} />
  )
}


