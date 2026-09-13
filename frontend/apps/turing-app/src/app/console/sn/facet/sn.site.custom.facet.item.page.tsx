import { ROUTES } from "@/app/routes.const";
import { useSnSiteAvailableLocales } from "@/api/queries/locale.queries";
import { LoadProvider } from "@/components/loading-provider";
import { CustomFacetItemFormFields } from "@/components/sn/facet/sn.site.custom.facet.item-form-fields";
import {
  DEFAULT_OPERATOR,
  isBetweenOperator,
  isDateFieldType,
  normalizeItems,
  parseIsoDateValue,
  parseRangeValue,
} from "@/components/sn/facet/sn.site.custom.facet.form.utils";
import { BentoFormHero } from "@/components/bento";
import { SectionCard } from "@/components/ui/section-card";
import { GradientButton } from "@/components/ui/gradient-button";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type {
  TurSNSiteCustomFacet,
  TurSNSiteCustomFacetFieldOption,
  TurSNSiteCustomFacetItem,
  TurSNSiteCustomFacetOperator,
} from "@/models/sn/sn-site-custom-facet.model";
import { TurSNSiteCustomFacetService } from "@/services/sn/sn.site.custom.facet.service";
import { IconDeviceFloppy, IconListDetails, IconX } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const customFacetService = new TurSNSiteCustomFacetService();

const EMPTY_ITEM: TurSNSiteCustomFacetItem = {
  label: "",
  labels: {},
  operator: DEFAULT_OPERATOR,
  rangeStart: null,
  rangeEnd: null,
  rangeStartDate: null,
  rangeEndDate: null,
};

/**
 * Standalone page for adding/editing a single Custom Facet item. Replaces the
 * legacy modal. Loads the parent facet by id, edits one item, and on save
 * issues a full facet update so the items array is persisted atomically.
 *
 * Route: `facet/custom/:customFacetId/item/:itemIndex` where `itemIndex` is
 * either a 0-based number (existing item) or the string "new".
 *
 * @since 2026.2.7
 */
/**
 * @param baseRoute SN instance base route for back/breadcrumb navigation.
 *   Defaults to the console; Bento passes `ROUTES.BENTO_SN_INSTANCE` (T576).
 * @param chrome Which shell chrome to render. `console` (default) uses the
 *   sidebar-coupled {@link StickyPageHeader}; `bento` renders the canonical
 *   {@link BentoFormHero} (T738) so the surface stays clear of `useSidebar` and
 *   gets the standard hero → sticky-save-bar morph.
 */
export default function SNSiteCustomFacetItemPage({ baseRoute = ROUTES.BENTO_SN_INSTANCE }: Readonly<{ baseRoute?: string }> = {}) {
  const navigate = useNavigate();
  const { id, customFacetId, itemIndex } = useParams() as {
    id: string;
    customFacetId: string;
    itemIndex: string;
  };
  const { t } = useTranslation();
  const { data: availableLocales } = useSnSiteAvailableLocales(id);

  const [customFacet, setCustomFacet] = useState<TurSNSiteCustomFacet>();
  const [fieldOptions, setFieldOptions] = useState<TurSNSiteCustomFacetFieldOption[]>([]);
  const [editingItem, setEditingItem] = useState<TurSNSiteCustomFacetItem>(EMPTY_ITEM);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();

  const isNewItem = itemIndex === "new";
  const editingIndex = isNewItem ? null : Number(itemIndex);

  useEffect(() => {
    customFacetService
      .get(id, customFacetId)
      .then((facet) => {
        setCustomFacet(facet);
        if (!isNewItem && editingIndex !== null && facet.items?.[editingIndex]) {
          setEditingItem({ ...facet.items[editingIndex] });
        } else {
          setEditingItem(EMPTY_ITEM);
        }
        setBreadcrumb([
          { label: t("sn.facets.title"), href: `${ROUTES.BENTO_SN_INSTANCE}/${id}/facet` },
          {
            label: facet.name || customFacetId,
            href: `${ROUTES.BENTO_SN_INSTANCE}/${id}/facet/custom/${customFacetId}`,
          },
          {
            label: isNewItem
              ? t("forms.snCustomFacet.addItemTitle")
              : t("forms.snCustomFacet.editItem"),
          },
        ]);
      })
      .catch(() => setError("Connection error or timeout while fetching custom facet."));

    customFacetService.getFieldOptions(id).then(setFieldOptions);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, customFacetId, itemIndex]);

  useSubPageBreadcrumb(breadcrumb);

  const selectedFieldOption = useMemo(
    () => fieldOptions.find((field) => field.id === customFacet?.fieldExtId),
    [fieldOptions, customFacet?.fieldExtId],
  );
  const selectedFieldType = selectedFieldOption?.type ?? customFacet?.fieldExtType;
  const selectedFieldName =
    selectedFieldOption?.name ?? customFacet?.fieldExtName ?? "";
  const dateField = isDateFieldType(selectedFieldType);

  function onUpdate(key: keyof TurSNSiteCustomFacetItem, rawValue: string) {
    setEditingItem((prev) => {
      if (key === "rangeStart" || key === "rangeEnd") {
        return { ...prev, [key]: parseRangeValue(rawValue) };
      }
      if (key === "rangeStartDate" || key === "rangeEndDate") {
        return { ...prev, [key]: parseIsoDateValue(rawValue) };
      }
      if (key === "operator") {
        return { ...prev, operator: rawValue as TurSNSiteCustomFacetOperator };
      }
      return { ...prev, [key]: rawValue };
    });
  }

  function onUpdateLabels(labels: Record<string, string>) {
    setEditingItem((prev) => ({ ...prev, labels }));
  }

  function backToFacet() {
    navigate(`${baseRoute}/${id}/facet/custom/${customFacetId}`);
  }

  async function onSave() {
    if (!customFacet) return;

    if (isBetweenOperator(editingItem.operator ?? DEFAULT_OPERATOR)) {
      if (dateField) {
        const start = parseIsoDateValue(editingItem.rangeStartDate);
        const end = parseIsoDateValue(editingItem.rangeEndDate);
        if (start && end && new Date(end).getTime() < new Date(start).getTime()) {
          toast.error(
            t("forms.snCustomFacet.invalidRange", {
              label: editingItem.label || "(without label)",
            }),
          );
          return;
        }
      } else {
        const start = parseRangeValue(editingItem.rangeStart);
        const end = parseRangeValue(editingItem.rangeEnd);
        if (start !== null && end !== null && end < start) {
          toast.error(
            t("forms.snCustomFacet.invalidRange", {
              label: editingItem.label || "(without label)",
            }),
          );
          return;
        }
      }
    }

    const items = [...(customFacet.items ?? [])];
    if (editingIndex !== null) {
      items[editingIndex] = editingItem;
    } else {
      items.push(editingItem);
    }
    const payload: TurSNSiteCustomFacet = {
      ...customFacet,
      items: normalizeItems(items, selectedFieldType),
    };

    try {
      const result = await customFacetService.update(id, payload);
      if (result) {
        toast.success(
          t("forms.common.updated", {
            name: customFacet.name,
            feature: "Custom Facet",
          }),
        );
        backToFacet();
      } else {
        toast.error(
          t("forms.common.notUpdated", {
            name: customFacet.name,
            feature: "Custom Facet",
          }),
        );
      }
    } catch (submitError) {
      console.error("Failed to save facet item", submitError);
      const message =
        submitError instanceof Error ? submitError.message : "Unknown error occurred";
      toast.error(`Failed to save the item: ${message}`);
    }
  }

  // Shared by the hero-anchored (fade-out) and the scroll-in sticky save bar.
  const facetItemActions = (
    <>
      <GradientButton type="button" size="sm" onClick={onSave}>
        <IconDeviceFloppy className="size-4" />
        {t("forms.formActions.saveChanges")}
      </GradientButton>
      <GradientButton type="button" variant="outline" size="sm" onClick={backToFacet}>
        <IconX className="size-4" />
        {t("forms.formActions.cancel")}
      </GradientButton>
    </>
  );

  return (
    <LoadProvider
      checkIsNotUndefined={customFacet && availableLocales}
      error={error}
      tryAgainUrl={`${baseRoute}/${id}/facet/custom/${customFacetId}/item/${itemIndex}`}
    >
      {customFacet && (
        <div className="flex flex-col gap-5 pb-8">
          /* T738: the canonical BentoFormHero owns both halves of the morph.
             This page saves imperatively (onClick, no form submit), so it
             passes its own `actions` — the escape hatch that replaces the
             default `type="submit"` Save/Cancel in the hero *and* the
             sticky bar. */
          <BentoFormHero
            backTo={`${baseRoute}/${id}/facet/custom/${customFacetId}`}
            backLabel={t("sn.facets.title")}
            leading={
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-amber-500 to-orange-500 text-white shadow-md">
                <IconListDetails size={24} />
              </span>
            }
            title={t("forms.snCustomFacet.facetItems")}
            subtitle={t("forms.snCustomFacet.itemDesc")}
            actions={facetItemActions}
            onCancel={backToFacet}
          />

          <SectionCard variant="amber">
            <SectionCard.Header
              icon={IconListDetails}
              title={t("forms.snCustomFacet.facetItems")}
              description={t("forms.snCustomFacet.itemDesc")}
            />
            <SectionCard.Content>
              <CustomFacetItemFormFields
                key={`${customFacetId}-${itemIndex}`}
                editingItem={editingItem}
                isDateField={dateField}
                fieldName={selectedFieldName}
                availableLocales={availableLocales ?? []}
                onUpdate={onUpdate}
                onUpdateLabels={onUpdateLabels}
              />
            </SectionCard.Content>
          </SectionCard>
        </div>
      )}
    </LoadProvider>
  );
}
