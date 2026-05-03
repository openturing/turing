"use client"
import { ROUTES } from "@/app/routes.const"
import {
    Form,
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form"
import { StickyPageHeader } from "@/components/sticky-page-header"
import { GradientButton } from "@/components/ui/gradient-button"
import { Input } from "@/components/ui/input"
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select"
import { SectionCard } from "@/components/ui/section-card"
import { SmartDescription } from "@/components/ui/smart-description"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table"
import type {
    TurSNSiteCustomSort,
    TurSNSiteCustomSortFieldOption,
    TurSNSiteCustomSortOrder,
} from "@/models/sn/sn-site-custom-sort.model"
import { TurSNSiteCustomSortService } from "@/services/sn/sn.site.custom.sort.service"
import { IconArrowDown, IconArrowUp, IconArrowsSort, IconCirclePlus, IconDeviceFloppy, IconFileText, IconTrash, IconX } from "@tabler/icons-react"
import { DialogDelete } from "@/components/dialog.delete"
import React, { useEffect, useState } from "react"
import { useFieldArray, useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"

const turSNSiteCustomSortService = new TurSNSiteCustomSortService();

interface Props {
    snSiteId: string;
    value: TurSNSiteCustomSort;
    isNew: boolean;
    onDelete?: () => void;
    open?: boolean;
    setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
}

export const SNSiteCustomSortForm: React.FC<Props> = ({ snSiteId, value, isNew, onDelete, open, setOpen }) => {
    const { t } = useTranslation();
    const form = useForm<TurSNSiteCustomSort>({
        defaultValues: value,
    });
    const navigate = useNavigate();
    const urlBase = `${ROUTES.SN_INSTANCE}/${snSiteId}/custom-sort`;
    const [fieldOptions, setFieldOptions] = useState<TurSNSiteCustomSortFieldOption[]>([]);

    const { fields, append, remove, swap, update } = useFieldArray({
        control: form.control,
        name: "items",
    });

    useEffect(() => {
        form.reset(value);
    }, [value]);

    useEffect(() => {
        turSNSiteCustomSortService.getFieldOptions(snSiteId).then(setFieldOptions).catch(console.error);
    }, [snSiteId]);

    function addSortLevel() {
        append({
            fieldName: fieldOptions.length > 0 ? fieldOptions[0].name : "",
            sortOrder: "ASC",
            position: fields.length,
        });
    }

    function removeSortLevel(index: number) {
        remove(index);
    }

    function updateItemField(index: number, fieldName: string) {
        const current = form.getValues(`items.${index}`);
        update(index, { ...current, fieldName });
    }

    function updateItemOrder(index: number, sortOrder: TurSNSiteCustomSortOrder) {
        const current = form.getValues(`items.${index}`);
        update(index, { ...current, sortOrder });
    }

    function moveItem(index: number, direction: -1 | 1) {
        const targetIndex = index + direction;
        if (targetIndex < 0 || targetIndex >= fields.length) return;
        swap(index, targetIndex);
    }

    async function onSubmit(data: TurSNSiteCustomSort) {
        // Re-number positions before saving
        const normalized = {
            ...data,
            items: (data.items || []).map((item, i) => ({ ...item, position: i })),
        };
        try {
            if (isNew) {
                const result = await turSNSiteCustomSortService.create(snSiteId, normalized);
                if (result) {
                    toast.success(t("sn.customSort.saved"));
                    navigate(urlBase);
                } else {
                    toast.error(t("sn.customSort.notSaved"));
                }
            } else {
                const result = await turSNSiteCustomSortService.update(snSiteId, normalized);
                if (result) {
                    toast.success(t("sn.customSort.updated"));
                } else {
                    toast.error(t("sn.customSort.notUpdated"));
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("forms.common.formSubmitFailed"));
        }
    }

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
                <StickyPageHeader>
                    <StickyPageHeader.Title
                        icon={IconArrowsSort}
                        feature={t("sn.customSort.title")}
                        description={t("sn.customSort.description")}
                    />
                    <StickyPageHeader.Actions>
                        {onDelete && open !== undefined && setOpen && <DialogDelete feature={t("sn.customSort.title")} name={value?.name || t("sn.customSort.newCustomSort")} onDelete={onDelete} open={open} setOpen={setOpen} />}
                        <GradientButton type="submit" size="sm">
                            <IconDeviceFloppy className="size-4" />
                            {t("forms.formActions.saveChanges")}
                        </GradientButton>
                        <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(urlBase)}>
                            <IconX className="size-4" />
                            {t("forms.formActions.cancel")}
                        </GradientButton>
                    </StickyPageHeader.Actions>
                </StickyPageHeader>
                {/* Details Section */}
                <SectionCard variant="blue">
                    <SectionCard.Header icon={IconArrowsSort} title={t("sn.customSort.details")} description={t("sn.customSort.detailsDesc")} />
                    <SectionCard.Content>
                        <FormField
                            control={form.control}
                            name="name"
                            rules={{ required: t("sn.customSort.nameRequired") }}
                            render={({ field }) => (
                                <FormItem>
                                    <FormLabel>{t("forms.common.name")}</FormLabel>
                                    <FormDescription>
                                        {t("sn.customSort.nameDesc")}
                                    </FormDescription>
                                    <FormControl>
                                        <Input {...field} placeholder="e.g. sort_date" type="text" />
                                    </FormControl>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />
                        <FormField
                            control={form.control}
                            name="description"
                            render={({ field }) => (
                                <FormItem>
                                    <FormControl>
                                        <SmartDescription
                                            value={field.value || ""}
                                            onChange={field.onChange}
                                            placeholder={t("sn.customSort.descriptionPlaceholder")}
                                            maxLength={500}
                                            entityType="Custom Sort"
                                            enableMetaPrompt
                                        >
                                            <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                                            <SmartDescription.Description>
                                                {t("sn.customSort.descriptionDesc")}
                                            </SmartDescription.Description>
                                        </SmartDescription>
                                    </FormControl>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />
                    </SectionCard.Content>
                </SectionCard>

                {/* Sort Levels Section */}
                <SectionCard variant="violet">
                    <SectionCard.Header icon={IconFileText} title={t("sn.customSort.sortLevels")} description={t("sn.customSort.sortLevelsDesc")} />
                    <SectionCard.Content>
                        <div className="flex items-center justify-between mb-4">
                            <GradientButton variant="outline" type="button" onClick={addSortLevel} disabled={fieldOptions.length === 0}>
                                <IconCirclePlus className="h-4 w-4 mr-2" />
                                {t("sn.customSort.addLevel")}
                            </GradientButton>
                        </div>
                        {fields.length > 0 && (
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead className="w-12">#</TableHead>
                                        <TableHead>{t("sn.customSort.field")}</TableHead>
                                        <TableHead className="w-32">{t("sn.customSort.direction")}</TableHead>
                                        <TableHead className="w-32 text-right">{t("forms.common.actions")}</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {fields.map((field, index) => {
                                        const watchedFieldName = form.watch(`items.${index}.fieldName`);
                                        const watchedSortOrder = form.watch(`items.${index}.sortOrder`);
                                        // If saved fieldName is not in fieldOptions, include it so Select can display it
                                        const fieldNameInOptions = !watchedFieldName || fieldOptions.some((o) => o.name === watchedFieldName);
                                        return (
                                        <TableRow key={field.id}>
                                            <TableCell className="font-medium">{index + 1}</TableCell>
                                            <TableCell>
                                                <Select
                                                    value={watchedFieldName || undefined}
                                                    onValueChange={(val) => updateItemField(index, val)}
                                                >
                                                    <SelectTrigger>
                                                        <SelectValue placeholder={t("sn.customSort.selectField")} />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        {!fieldNameInOptions && (
                                                            <SelectItem key={watchedFieldName} value={watchedFieldName}>
                                                                {watchedFieldName}
                                                            </SelectItem>
                                                        )}
                                                        {fieldOptions.map((opt) => (
                                                            <SelectItem key={opt.id} value={opt.name}>
                                                                {opt.name}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                            </TableCell>
                                            <TableCell>
                                                <Select
                                                    value={watchedSortOrder || "ASC"}
                                                    onValueChange={(val) => updateItemOrder(index, val as TurSNSiteCustomSortOrder)}
                                                >
                                                    <SelectTrigger>
                                                        <SelectValue />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value="ASC">ASC</SelectItem>
                                                        <SelectItem value="DESC">DESC</SelectItem>
                                                    </SelectContent>
                                                </Select>
                                            </TableCell>
                                            <TableCell className="text-right">
                                                <div className="flex justify-end gap-1">
                                                    <GradientButton
                                                        variant="ghost"
                                                        size="icon"
                                                        type="button"
                                                        onClick={() => moveItem(index, -1)}
                                                        disabled={index === 0}
                                                    >
                                                        <IconArrowUp className="h-4 w-4" />
                                                    </GradientButton>
                                                    <GradientButton
                                                        variant="ghost"
                                                        size="icon"
                                                        type="button"
                                                        onClick={() => moveItem(index, 1)}
                                                        disabled={index === fields.length - 1}
                                                    >
                                                        <IconArrowDown className="h-4 w-4" />
                                                    </GradientButton>
                                                    <GradientButton
                                                        variant="destructive"
                                                        size="icon"
                                                        type="button"
                                                        onClick={() => removeSortLevel(index)}
                                                    >
                                                        <IconTrash className="h-4 w-4" />
                                                    </GradientButton>
                                                </div>
                                            </TableCell>
                                        </TableRow>
                                    );
                                    })}
                                </TableBody>
                            </Table>
                        )}
                    </SectionCard.Content>
                </SectionCard>

                {/* Action Footer */}
            </form>
        </Form>
    );
};
