import type { TurSNSiteSpotlight } from "@/models/sn/sn-site-spotlight.model";
import { TurSNSiteSpotlightService } from "@/services/sn/sn.site.spotlight.service";
import { IconSpeakerphone } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoSnSectionList } from "./bento.sn.section-list";

const service = new TurSNSiteSpotlightService();

/** Bento SN spotlight list — T576. */
export default function BentoSNSpotlightListPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [items, setItems] = useState<TurSNSiteSpotlight[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    service.query(id).then(setItems).catch(() => setError(t("common.connectionError", { resource: t("sn.spotlight.title") })));
  }, [id, t]);

  return (
    <BentoSnSectionList
      siteId={id}
      section="spotlight"
      icon={IconSpeakerphone}
      title={t("sn.spotlight.title")}
      subtitle={t("sn.spotlight.description")}
      items={items}
      error={error}
      newLabel={t("sn.spotlight.newSpotlight")}
      emptyTitle={t("sn.spotlight.blankTitle")}
      emptyDescription={t("sn.spotlight.blankDescription")}
      itemKey={(item) => item.id ?? ""}
      itemTitle={(item) => item.name}
      itemDescription={(item) => item.description}
    />
  );
}
