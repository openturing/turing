import { GradientButton } from '@/components/ui/gradient-button';
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
import { PlusCircle, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Controller, useFieldArray, type Control, type FieldErrors, type UseFormRegister } from 'react-hook-form';

interface DynamicResultRankingFieldsProps {
    control: Control<any>;
    register: UseFormRegister<any>;
    fieldName: keyof TurSNRankingExpression;
    snSiteId: string;
    errors?: FieldErrors;
}
const turSNFieldService = new TurSNFieldService();

export function DynamicResultRankingFields({ control, register, fieldName, snSiteId, errors }: Readonly<DynamicResultRankingFieldsProps>) {
    const { fields, append, remove } = useFieldArray({
        control,
        name: fieldName,
        rules: {
            required: "At least one condition is required.",
            minLength: { value: 1, message: "At least one condition is required." },
        },
    });
    const [snFields, setSNFields] = useState<TurSNSiteField[]>([]);
    useEffect(() => {
        turSNFieldService.query(snSiteId).then(setSNFields)
    }, [snSiteId]);

    const handleAddField = () => {
        append({ attribute: '', condition: '', value: '' });
    };

    return (
        <div className="flex flex-col gap-2 w-full">
            {fields.map((field, index) => {
                const fieldErrors = (errors?.[fieldName] as any)?.[index];
                return (
                    <div key={field.id} className="flex flex-col">
                        <div className="flex items-start gap-2">
                            <Controller
                                control={control}
                                name={`${fieldName}.${index}.attribute`}
                                rules={{ required: "Attribute is required." }}
                                render={({ field: controllerField }) => (
                                    <div className="w-48 shrink-0">
                                        <Select
                                            onValueChange={controllerField.onChange}
                                            value={controllerField.value === null ? '' : String(controllerField.value)}
                                        >
                                            <SelectTrigger className={`w-full ${fieldErrors?.attribute ? "border-destructive" : ""}`}>
                                                <SelectValue placeholder="Choose the field" />
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
                            <Controller
                                control={control}
                                name={`${fieldName}.${index}.condition`}
                                rules={{ required: "Condition is required." }}
                                render={({ field: controllerField }) => (
                                    <div className="w-32 shrink-0">
                                        <Select
                                            onValueChange={(value) => controllerField.onChange(Number(value))}
                                            value={controllerField.value === null ? '' : String(controllerField.value)}
                                        >
                                            <SelectTrigger className={`w-full ${fieldErrors?.condition ? "border-destructive" : ""}`}>
                                                <SelectValue placeholder="Choose..." />
                                            </SelectTrigger>
                                            <SelectContent>
                                                <SelectItem key="1" value="1">Is</SelectItem>
                                                <SelectItem key="2" value="2">Is not</SelectItem>
                                            </SelectContent>
                                        </Select>
                                        {fieldErrors?.condition && (
                                            <p className="text-xs text-destructive mt-1">{fieldErrors.condition.message}</p>
                                        )}
                                    </div>
                                )}
                            />
                            <div className="grow">
                                <Input
                                    className={`grow ${fieldErrors?.value ? "border-destructive" : ""}`}
                                    placeholder="Value"
                                    {...register(`${fieldName}.${index}.value`, { required: "Value is required." })}
                                />
                                {fieldErrors?.value && (
                                    <p className="text-xs text-destructive mt-1">{fieldErrors.value.message}</p>
                                )}
                            </div>
                            <GradientButton
                                variant="ghost"
                                size="icon"
                                onClick={() => remove(index)}
                                aria-label="Remove the field"
                                type="button"
                            >
                                <Trash2 className="h-4 w-4 text-red-500" />
                            </GradientButton>
                        </div>
                    </div>
                );
            })}

            <div className="mt-2">
                <GradientButton variant="outline" onClick={handleAddField} type="button">
                    <PlusCircle className="h-4 w-4 mr-2" />
                    Add
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