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
import { FormActions } from "@/components/ui/form-actions"
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
    TurSNSiteCustomSortItem,
    TurSNSiteCustomSortOrder,
} from "@/models/sn/sn-site-custom-sort.model"
import { TurSNSiteCustomSortService } from "@/services/sn/sn.site.custom.sort.service"
import { IconArrowsSort, IconFileText } from "@tabler/icons-react"
import { ArrowDown, ArrowUp, PlusCircle, Trash2 } from "lucide-react"
import React, { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"

const turSNSiteCustomSortService = new TurSNSiteCustomSortService();

interface Props {
    snSiteId: string;
    value: TurSNSiteCustomSort;
    isNew: boolean;
}

export const SNSiteCustomSortForm: React.FC<Props> = ({ snSiteId, value, isNew }) => {
    const { t } = useTranslation();
    const form = useForm<TurSNSiteCustomSort>({
        defaultValues: value,
    });
    const navigate = useNavigate();
    const urlBase = `${ROUTES.SN_INSTANCE}/${snSiteId}/custom-sort`;
    const [fieldOptions, setFieldOptions] = useState<TurSNSiteCustomSortFieldOption[]>([]);

    useEffect(() => {
        form.reset(value);
    }, [value]);

    useEffect(() => {
        turSNSiteCustomSortService.getFieldOptions(snSiteId).then(setFieldOptions).catch(console.error);
    }, [snSiteId]);

    const items = form.watch("items") || [];

    function addSortLevel() {
        const currentItems = form.getValues("items") || [];
        const newItem: TurSNSiteCustomSortItem = {
            fieldName: fieldOptions.length > 0 ? fieldOptions[0].name : "",
            sortOrder: "ASC",
            position: currentItems.length,
        };
        form.setValue("items", [...currentItems, newItem]);
    }

    function removeSortLevel(index: number) {
        const currentItems = form.getValues("items") || [];
        const updated = currentItems
            .filter((_, i) => i !== index)
            .map((item, i) => ({ ...item, position: i }));
        form.setValue("items", updated);
    }

    function updateItemField(index: number, fieldName: string) {
        const currentItems = [...(form.getValues("items") || [])];
        currentItems[index] = { ...currentItems[index], fieldName };
        form.setValue("items", currentItems);
    }

    function updateItemOrder(index: number, sortOrder: TurSNSiteCustomSortOrder) {
        const currentItems = [...(form.getValues("items") || [])];
        currentItems[index] = { ...currentItems[index], sortOrder };
        form.setValue("items", currentItems);
    }

    function moveItem(index: number, direction: -1 | 1) {
        const currentItems = [...(form.getValues("items") || [])];
        const targetIndex = index + direction;
        if (targetIndex < 0 || targetIndex >= currentItems.length) return;
        const temp = currentItems[index];
        currentItems[index] = currentItems[targetIndex];
        currentItems[targetIndex] = temp;
        const reordered = currentItems.map((item, i) => ({ ...item, position: i }));
        form.setValue("items", reordered);
    }

    async function onSubmit(data: TurSNSiteCustomSort) {
        try {
            if (isNew) {
                const result = await turSNSiteCustomSortService.create(snSiteId, data);
                if (result) {
                    toast.success(t("sn.customSort.saved"));
                    navigate(urlBase);
                } else {
                    toast.error(t("sn.customSort.notSaved"));
                }
            } else {
                const result = await turSNSiteCustomSortService.update(snSiteId, data);
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
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8 px-6">
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
                                <PlusCircle className="h-4 w-4 mr-2" />
                                {t("sn.customSort.addLevel")}
                            </GradientButton>
                        </div>
                        {items.length > 0 && (
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
                                    {items.map((item, index) => (
                                        <TableRow key={item.id ?? `new-${index}`}>
                                            <TableCell className="font-medium">{index + 1}</TableCell>
                                            <TableCell>
                                                <Select
                                                    value={item.fieldName}
                                                    onValueChange={(val) => updateItemField(index, val)}
                                                >
                                                    <SelectTrigger>
                                                        <SelectValue />
                                                    </SelectTrigger>
                                                    <SelectContent>
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
                                                    value={item.sortOrder}
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
                                                        <ArrowUp className="h-4 w-4" />
                                                    </GradientButton>
                                                    <GradientButton
                                                        variant="ghost"
                                                        size="icon"
                                                        type="button"
                                                        onClick={() => moveItem(index, 1)}
                                                        disabled={index === items.length - 1}
                                                    >
                                                        <ArrowDown className="h-4 w-4" />
                                                    </GradientButton>
                                                    <GradientButton
                                                        variant="destructive"
                                                        size="icon"
                                                        type="button"
                                                        onClick={() => removeSortLevel(index)}
                                                    >
                                                        <Trash2 className="h-4 w-4" />
                                                    </GradientButton>
                                                </div>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        )}
                    </SectionCard.Content>
                </SectionCard>

                {/* Action Footer */}
                <FormActions>
                    <FormActions.Cancel onClick={() => navigate(urlBase)} />
                    <FormActions.Submit />
                </FormActions>
            </form>
        </Form>
    );
};
