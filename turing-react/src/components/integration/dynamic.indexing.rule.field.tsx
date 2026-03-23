import { Input } from '@/components/ui/input';
import { PlusCircle, Trash2 } from 'lucide-react';
import { useFieldArray, type Control, type UseFormRegister, type FieldValues, type ArrayPath, type FieldArray, type Path } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { GradientButton } from '../ui/gradient-button';


interface DynamicIndexingRuleFieldsProps<TFieldValues extends FieldValues = FieldValues> {
    control: Control<TFieldValues>;
    register: UseFormRegister<TFieldValues>;
    fieldName: string;
}


export function DynamicIndexingRuleFields<TFieldValues extends FieldValues = FieldValues>({
    control,
    register,
    fieldName
}: Readonly<DynamicIndexingRuleFieldsProps<TFieldValues>>) {
    const { t } = useTranslation();
    const { fields, append, remove } = useFieldArray({
        control,
        name: fieldName as ArrayPath<TFieldValues>,
    });

    const handleAddField = () => {
        append({ value: '' } as FieldArray<TFieldValues, ArrayPath<TFieldValues>>);
    };

    return (
        <div className="flex flex-col gap-4 w-full">
            {fields.map((field, index) => (
                <div key={field.id} className="flex items-center gap-2">
                    <Input
                        className="grow"
                        placeholder={t("forms.common.value")}
                        {...register(`${fieldName}.${index}.value` as Path<TFieldValues>)}
                    />
                    <GradientButton
                        variant="ghost"
                        size="icon"
                        onClick={() => remove(index)}
                        aria-label={t("forms.dynamicField.removeField")}
                        type="button"
                    >
                        <Trash2 className="h-4 w-4 text-red-500" />
                    </GradientButton>
                </div>
            ))}

            <div className="mt-2">
                <GradientButton variant="outline" onClick={handleAddField} type="button">
                    <PlusCircle className="h-4 w-4 mr-2" />
                    {t("forms.common.add")}
                </GradientButton>
            </div>
        </div>
    );
}