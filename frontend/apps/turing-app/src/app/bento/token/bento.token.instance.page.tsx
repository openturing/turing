import { useDeleteTokenInstance, useTokenInstance, useUpdateTokenInstance } from "@/api/queries/token-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import type { TurTokenInstance } from "@/models/token/token-instance.model.ts";
import { IconCode } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BENTO_TOKEN_FORM_ID, BentoTokenInstanceForm } from "./bento.token.instance.form";

/**
 * Bento API Token detail — T553. Tokens have only title + description as
 * identity (no icon / status / tenant), so the shell runs with `hideIcon`
 * and no status pill. The read-only secret + copy lives in the form.
 */
export default function BentoTokenInstancePage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: token, isError } = useTokenInstance(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("apiToken.title") }) : null;

  const updateMutation = useUpdateTokenInstance();
  const deleteMutation = useDeleteTokenInstance();

  function shell(entity: TurTokenInstance, headlineFallback: string) {
    return (
      <BentoEntityShell
        entity={entity}
        isNew={isNew}
        headlineFallback={headlineFallback}
        eyebrow={t("apiToken.title")}
        listRoute={ROUTES.BENTO_TOKEN_INSTANCE}
        icon={IconCode}
        tone="slate"
        formId={BENTO_TOKEN_FORM_ID}
        feature={t("apiToken.title")}
        hideIcon
        onUpdate={isNew ? undefined : (next) => updateMutation.mutateAsync(next)}
        onDelete={isNew ? undefined : () => deleteMutation.mutateAsync(entity)}
      >
        {({ staged, onStateChange }) => (
          <BentoTokenInstanceForm value={entity} isNew={isNew} staged={staged} onStateChange={onStateChange} />
        )}
      </BentoEntityShell>
    );
  }

  if (isNew) {
    return shell({} as TurTokenInstance, t("apiToken.newInstance"));
  }

  return (
    <LoadProvider checkIsNotUndefined={token} error={error} tryAgainUrl={`${ROUTES.BENTO_TOKEN_INSTANCE}/${id}`}>
      {token && shell(token, token.title)}
    </LoadProvider>
  );
}
