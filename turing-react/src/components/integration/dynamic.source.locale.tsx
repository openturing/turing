import { LanguageSelect } from '@/components/language-select';
import { Input } from '@/components/ui/input';
import type { TurLocale } from '@/models/locale/locale.model';
import { TurLocaleService } from '@/services/locale/locale.service';
import { PlusCircle, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Controller, useFieldArray, type Control, type UseFormRegister } from 'react-hook-form';
import { GradientButton } from '../ui/gradient-button';

interface DynamicSourceLocalesProps {
    control: Control<any>;
    register: UseFormRegister<any>;
    fieldName: string;
}
const turLocaleService = new TurLocaleService();
export function DynamicSourceLocales({
    control,
    register,
    fieldName
}: Readonly<DynamicSourceLocalesProps>) {
    const { fields, append, remove } = useFieldArray({
        control,
        name: fieldName,
    });
    const [locales, setLocales] = useState<TurLocale[]>([]);
    useEffect(() => {
        turLocaleService.query().then(setLocales)
    }, []);
    const handleAddField = () => {
        append({ locale: '', path: '' });
    };

    return (
        <div className="flex flex-col gap-4 w-full">
            {fields.map((field, index) => (
                <div key={field.id} className="flex items-center gap-2">
                    <Controller
                        control={control}
                        name={`${fieldName}.${index}.locale`}
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
                        placeholder="Path"
                        {...register(`${fieldName}.${index}.path`)}
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