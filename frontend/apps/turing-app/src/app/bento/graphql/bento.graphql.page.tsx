import { BentoHero } from "@/components/bento";
import GraphiQLPage from "@/app/console/graphql/graphql.page";
import { IconBrandGraphql } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento GraphQL explorer — T565. GraphiQL is an embedded iframe tool, not a
 * form CRUD, so per §XXXI.8 it sits under a {@link BentoHero} inside the shell;
 * the reused {@link GraphiQLPage} keeps its theme-sync + iframe logic unchanged.
 */
export default function BentoGraphqlPage() {
  const { t } = useTranslation();
  return (
    <>
      <BentoHero
        eyebrow={t("home.sections.enterpriseSearch.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-rose-500 to-pink-600 text-white shadow-md">
            <IconBrandGraphql size={24} />
          </span>
        }
        title={t("home.features.graphqlExplorer.title")}
        subtitle={t("home.features.graphqlExplorer.description")}
      />
      <div className="bento-glass overflow-hidden rounded-3xl">
        <GraphiQLPage />
      </div>
    </>
  );
}
