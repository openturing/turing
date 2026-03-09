import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurTokenInstance } from "@/models/token/token-instance.model.ts";
import { TurTokenInstanceService } from "@/services/token/token.service";
import { IconCode } from "@tabler/icons-react";
import { useEffect, useState } from "react";

const turTokenInstanceService = new TurTokenInstanceService();

export default function TokenInstanceListPage() {
  const [tokenInstances, setTokenInstances] = useState<TurTokenInstance[]>();
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    turTokenInstanceService.query().then(setTokenInstances).catch(() => setError("Connection error or timeout while fetching api tokens."));
  }, [])
  const gridItemList = useGridAdapter(tokenInstances, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.TOKEN_INSTANCE}/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={tokenInstances} error={error} tryAgainUrl={`${ROUTES.TOKEN_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList} />
      ) : (
        <BlankSlate
          icon={IconCode}
          title="You don’t seem to have any API token."
          description="Create a new token to allow access to the API."
          buttonText="New API token"
          urlNew={`${ROUTES.TOKEN_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}


