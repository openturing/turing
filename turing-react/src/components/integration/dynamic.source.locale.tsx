import { LanguageSelect } from '@/components/language-select';
import { Input } from '@/components/ui/input';
import type { TurLocale } from '@/models/locale/locale.model';
import { TurLocaleService } from '@/services/locale/locale.service';
import { PlusCircle, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Controller, useFieldArray, type Control, type UseFormRegister, type FieldValues, type ArrayPath } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { GradientButton } from '../ui/gradient-button';

interface DynamicSourceLocalesProps<TFieldValues extends FieldValues = FieldValues> {
    control: Control<TFieldValues>;
    register: UseFormRegister<TFieldValues>;
    fieldName: string;
}
const turLocaleService = new TurLocaleService();
export function DynamicSourceLocales<TFieldValues extends FieldValues = FieldValues>({
    control,
    register,
    fieldName
}: Readonly<DynamicSourceLocalesProps<TFieldValues>>) {
    const { t } = useTranslation();
    const { fields, append, remove } = useFieldArray({
        control,
        name: fieldName as ArrayPath<TFieldValues>,
    });
    const [locales, setLocales] = useState<TurLocale[]>([]);
    useEffect(() => {
        turLocaleService.query().then(setLocales)
    }, []);
    const handleAddField = () => {
        append({ locale: '', path: '' } as any);
    };

    return (
        <div className="flex flex-col gap-4 w-full">
            {fields.map((field, index) => (
                <div key={field.id} className="flex items-center gap-2">
                    <Controller
                        control={control}
                        name={`${fieldName}.${index}.locale` as any}
                        render={({ field: controllerField }) => (
                            <LanguageSelect
                                value={controllerField.value}
                                onValueChange={controllerField.onChange}
                                locales={locales}
                                extraLocaleValues={controllerField.value ? [controllerField.value] : []}
                                className="w-full"
                            />
                        )}
                    />
                    <Input
                        className="grow"
                        placeholder={t("forms.integrationSource.rootPath")}
                        {...register(`${fieldName}.${index}.path` as any)}
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