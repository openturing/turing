import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { LoggingGrid } from "@/components/logging/logging.grid";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurLoggingGeneral } from "@/models/logging/logging-general.model";
import { TurLoggingInstanceService } from "@/services/logging/logging.service";
import { useEffect, useState } from "react";

const turLoggingInstanceService = new TurLoggingInstanceService();

export default function LoggingServerPage() {
  const [loggingInstances, setLoggingInstances] = useState<TurLoggingGeneral[]>();
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    let added = false;
    turLoggingInstanceService.server().then((loggingInstances) => {
      setLoggingInstances(loggingInstances);
      pushItem({ label: "Turing ES Server" });
      added = true;
    }).catch(() => setError("Connection error or timeout while fetching server logging."));
    return () => {
      if (added) popItem();
    };
  }, [])

  return (
    <LoadProvider checkIsNotUndefined={loggingInstances} error={error} tryAgainUrl={`${ROUTES.LOGGING_INSTANCE}/server`}>
      <LoggingGrid gridItemList={loggingInstances || []} />
    </LoadProvider>
  )
}