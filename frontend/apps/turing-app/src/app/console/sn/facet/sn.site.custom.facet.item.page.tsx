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
import { BentoSaveBar, BentoScrollSaveBar } from "@/components/bento";
import { StickyPageHeader } from "@/components/sticky-page-header";
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
import { type ReactNode, useEffect, useMemo, useState } from "react";
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
 * @param header Optional header override. The console renders its own
 *   sidebar-coupled {@link StickyPageHeader}; Bento passes a `BentoHero` (T576)
 *   so the surface stays clear of `useSidebar` and gets a sticky BentoSaveBar.
 */
export default function SNSiteCustomFacetItemPage({ baseRoute = ROUTES.SN_INSTANCE, header }: Readonly<{ baseRoute?: string; header?: ReactNode }> = {}) {
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
  const isBento = header !== undefined;

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
          { label: t("sn.facets.title"), href: `${ROUTES.SN_INSTANCE}/${id}/facet` },
          {
            label: facet.name || customFacetId,
            href: `${ROUTES.SN_INSTANCE}/${id}/facet/custom/${customFacetId}`,
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
        <div className={isBento ? "flex flex-col gap-5 pb-8" : "space-y-4 px-4 lg:px-6 pb-8"}>
          {isBento ? (
            <>
              {header}
              {/* Save/Cancel are onClick handlers (this page saves
                  imperatively, not via a form submit), so override the bar's
                  default submit buttons via `actions` in both the hero-anchored
                  (fade-out) copy and the scroll-in sticky copy. */}
              <div className="bento-fade-out">
                <BentoSaveBar actions={facetItemActions} />
              </div>
              <BentoScrollSaveBar actions={facetItemActions} />
            </>
          ) : (
            <StickyPageHeader>
              <StickyPageHeader.Title
                icon={IconListDetails}
                feature={
                  isNewItem
                    ? t("forms.snCustomFacet.addItemTitle")
                    : t("forms.snCustomFacet.editItem")
                }
                description={customFacet.defaultLabel ?? customFacet.name ?? ""}
              />
              <StickyPageHeader.Actions>
                <GradientButton type="button" size="sm" onClick={onSave}>
                  <IconDeviceFloppy className="size-4" />
                  {t("forms.formActions.saveChanges")}
                </GradientButton>
                <GradientButton
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={backToFacet}
                >
                  <IconX className="size-4" />
                  {t("forms.formActions.cancel")}
                </GradientButton>
              </StickyPageHeader.Actions>
            </StickyPageHeader>
          )}

          <SectionCard variant="amber">
            <SectionCard.Header
              icon={IconListDetails}
              title={t("forms.snCustomFacet.facetItems")}
              description={t("forms.snCustomFacet.itemDesc")}
            />
            <SectionCard.Content>
              <CustomFacetItemFormFields
                editingItem={editingItem}
                isDateField={dateField}
                fieldName={selectedFieldName}
                availableLocales={availableLocales ?? []}
                resetKey={`${customFacetId}-${itemIndex}`}
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
