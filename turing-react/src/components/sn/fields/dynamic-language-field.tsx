import { LanguageSelect } from '@/components/language-select';
import { GradientButton } from '@/components/ui/gradient-button';
import { Input } from '@/components/ui/input';
import type { TurLocale } from '@/models/locale/locale.model';
import type { TurSNSiteField } from '@/models/sn/sn-site-field.model';
import { TurLocaleService } from '@/services/locale/locale.service';
import { PlusCircle, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Controller, useFieldArray, type Control, type UseFormRegister } from 'react-hook-form';

interface DynamicLanguageFieldsProps {
    control: Control<any>;
    register: UseFormRegister<any>;
    fieldName: keyof TurSNSiteField;
}
const turLocaleService = new TurLocaleService();
export function DynamicLanguageFields({ control, register, fieldName }: Readonly<DynamicLanguageFieldsProps>) {
    const { fields, append, remove } = useFieldArray({
        control,
        name: fieldName,
    });
    const [locales, setLocales] = useState<TurLocale[]>([]);
    useEffect(() => {
        turLocaleService.query().then(setLocales)
    }, []);

    const handleAddField = () => {
        append({ id: '', locale: '', label: '' });
    };

    return (
        <div className="flex flex-col gap-4 w-full">
            {fields.map((field, index) => (
                <div key={field.id} className="flex items-center gap-4">
                    <Controller
                        control={control}
                        name={`${fieldName}.${index}.locale`}
                        render={({ field: controllerField }) => (
                            <LanguageSelect
                                value={controllerField.value}
                                onValueChange={controllerField.onChange}
                                locales={locales}
                                className="w-full"
                            />
                        )}
                    />

                    <Input
                        className="grow w-full"
                        placeholder="Facet Name..."
                        {...register(`${fieldName}.${index}.label`)}
                    />
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
            ))}

            <div className="mt-2">
                <GradientButton variant="outline" onClick={handleAddField} type="button">
                    <PlusCircle className="h-4 w-4 mr-2" />
                    Add
                </GradientButton>
            </div>
        </div>
    );
}