import { GradientButton } from '@/components/ui/gradient-button';
import { IconCirclePlus, IconTrash } from "@tabler/icons-react";
import { Input } from '@/components/ui/input';
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select';
import type { TurSNRankingExpression } from '@/models/sn/sn-ranking-expression.model';
import type { TurSNSiteField } from '@/models/sn/sn-site-field.model';
import { TurSNFieldService } from '@/services/sn/sn.field.service';
import { useEffect, useState } from 'react';
import { Controller, useFieldArray, type Control, type FieldErrors, type UseFormRegister, type FieldValues, type ArrayPath } from 'react-hook-form';
import { useTranslation } from 'react-i18next';

interface DynamicResultRankingFieldsProps<TFieldValues extends FieldValues = FieldValues> {
    control: Control<TFieldValues>;
    register: UseFormRegister<TFieldValues>;
    fieldName: keyof TurSNRankingExpression;
    snSiteId: string;
    errors?: FieldErrors;
}
const turSNFieldService = new TurSNFieldService();

export function DynamicResultRankingFields<TFieldValues extends FieldValues = FieldValues>({ control, register, fieldName, snSiteId, errors }: Readonly<DynamicResultRankingFieldsProps<TFieldValues>>) {
    const { t } = useTranslation();
    const { fields, append, remove } = useFieldArray({
        control,
        name: fieldName as ArrayPath<TFieldValues>,
        rules: {
            required: t("forms.resultRankingField.conditionMinimum"),
            minLength: { value: 1, message: t("forms.resultRankingField.conditionMinimum") },
        },
    });
    const [snFields, setSNFields] = useState<TurSNSiteField[]>([]);
    useEffect(() => {
        turSNFieldService.query(snSiteId).then(setSNFields)
    }, [snSiteId]);

    const handleAddField = () => {
        append({ attribute: '', condition: '', value: '' } as any);
    };

    return (
        <div className="flex flex-col gap-3 w-full">
            {fields.map((field, index) => {
                const fieldErrors = (errors?.[fieldName] as any)?.[index];
                return (
                    <div key={field.id} className="rounded-lg border bg-muted/30 p-3">
                        <div className="grid grid-cols-1 md:grid-cols-[200px_120px_1fr_auto] gap-2 items-start">
                            {/* Field select */}
                            <Controller
                                control={control}
                                name={`${fieldName}.${index}.attribute` as any}
                                rules={{ required: t("forms.resultRankingField.attributeRequired") }}
                                render={({ field: controllerField }) => (
                                    <div>
                                        <span className="text-xs text-muted-foreground mb-1 block md:hidden">{t("forms.common.field")}</span>
                                        <Select
                                            onValueChange={controllerField.onChange}
                                            value={controllerField.value === null ? '' : String(controllerField.value)}
                                        >
                                            <SelectTrigger className={fieldErrors?.attribute ? "border-destructive" : ""}>
                                                <SelectValue placeholder={t("forms.resultRankingField.chooseField")} />
                                            </SelectTrigger>
                                            <SelectContent>
                                                {snFields.map((option) => (
                                                    <SelectItem key={option.id} value={option.name}>
                                                        {option.name}
                                                    </SelectItem>
                                                ))}
                                            </SelectContent>
                                        </Select>
                                        {fieldErrors?.attribute && (
                                            <p className="text-xs text-destructive mt-1">{fieldErrors.attribute.message}</p>
                                        )}
                                    </div>
                                )}
                            />
                            {/* Condition select */}
                            <Controller
                                control={control}
                                name={`${fieldName}.${index}.condition` as any}
                                rules={{ required: t("forms.resultRankingField.conditionRequired") }}
                                render={({ field: controllerField }) => (
                                    <div>
                                        <span className="text-xs text-muted-foreground mb-1 block md:hidden">{t("forms.common.condition")}</span>
                                        <Select
                                            onValueChange={(value) => controllerField.onChange(Number(value))}
                                            value={controllerField.value === null ? '' : String(controllerField.value)}
                                        >
                                            <SelectTrigger className={fieldErrors?.condition ? "border-destructive" : ""}>
                                                <SelectValue placeholder={t("forms.common.condition")} />
                                            </SelectTrigger>
                                            <SelectContent>
                                                <SelectItem value="1">{t("forms.resultRankingField.is")}</SelectItem>
                                                <SelectItem value="2">{t("forms.resultRankingField.isNot")}</SelectItem>
                                            </SelectContent>
                                        </Select>
                                        {fieldErrors?.condition && (
                                            <p className="text-xs text-destructive mt-1">{fieldErrors.condition.message}</p>
                                        )}
                                    </div>
                                )}
                            />
                            {/* Value input */}
                            <div>
                                <span className="text-xs text-muted-foreground mb-1 block md:hidden">{t("forms.common.value")}</span>
                                <Input
                                    className={fieldErrors?.value ? "border-destructive" : ""}
                                    placeholder={t("forms.common.value")}
                                    {...register(`${fieldName}.${index}.value` as any, { required: t("forms.resultRankingField.valueRequired") })}
                                />
                                {fieldErrors?.value && (
                                    <p className="text-xs text-destructive mt-1">{fieldErrors.value.message}</p>
                                )}
                            </div>
                            {/* Delete button */}
                            <div className="flex md:items-start md:pt-0 justify-end">
                                <GradientButton
                                    variant="ghost"
                                    size="icon"
                                    onClick={() => remove(index)}
                                    aria-label={t("forms.resultRankingField.removeCondition")}
                                    title={t("forms.resultRankingField.removeCondition")}
                                    type="button"
                                    className="text-destructive hover:text-destructive"
                                >
                                    <IconTrash className="h-4 w-4" />
                                </GradientButton>
                            </div>
                        </div>
                    </div>
                );
            })}

            <div>
                <GradientButton variant="outline" onClick={handleAddField} type="button" size="sm">
                    <IconCirclePlus className="h-4 w-4 mr-1.5" />
                    {t("forms.resultRankingField.addCondition")}
                </GradientButton>
            </div>
            {errors?.[fieldName]?.root?.message && (
                <p className="text-sm text-destructive mt-1">
                    {errors[fieldName].root.message as string}
                </p>
            )}
        </div>
    );
}
