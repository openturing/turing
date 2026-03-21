import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntent } from "@/models/intent/intent.model";
import { TurIntentService } from "@/services/intent/intent.service";
import { IconBulb } from "@tabler/icons-react";
import { useEffect, useState } from "react";

const turIntentService = new TurIntentService();

export default function IntentListPage() {
  const [intents, setIntents] = useState<TurIntent[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    turIntentService.query().then(setIntents).catch(() => setError("Connection error or timeout while fetching intents."));
  }, [])

  const gridItemList = useGridAdapter(intents, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.INTENT_INSTANCE}/${item.id}`,
    icon: "icon",
  });

  return (
    <LoadProvider checkIsNotUndefined={intents} error={error} tryAgainUrl={ROUTES.INTENT_INSTANCE}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.INTENT_INSTANCE}/new`} label="Intent" />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconBulb}
          title="You don't have any intents yet."
          description="Create an intent to group suggested prompts for the chat."
          buttonText="New Intent"
          urlNew={`${ROUTES.INTENT_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}
