import { Input } from "@/components/ui/input";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

type TimeUnit = "ms" | "s" | "min" | "h" | "d";

interface UnitConfig {
    labelKey: string;
    factor: number;
}

const UNITS: Record<TimeUnit, UnitConfig> = {
    ms: { labelKey: "forms.duration.milliseconds", factor: 1 },
    s: { labelKey: "forms.duration.seconds", factor: 1_000 },
    min: { labelKey: "forms.duration.minutes", factor: 60_000 },
    h: { labelKey: "forms.duration.hours", factor: 3_600_000 },
    d: { labelKey: "forms.duration.days", factor: 86_400_000 },
};

function detectBestUnit(ms: number): TimeUnit {
    if (ms <= 0) return "min";
    if (ms % 86_400_000 === 0) return "d";
    if (ms % 3_600_000 === 0) return "h";
    if (ms % 60_000 === 0) return "min";
    if (ms % 1_000 === 0) return "s";
    return "ms";
}

interface DurationInputProps {
    value: number;
    onChange: (ms: number) => void;
    disabled?: boolean;
}

export function DurationInput({ value, onChange, disabled }: Readonly<DurationInputProps>) {
    const { t } = useTranslation();
    const [unit, setUnit] = useState<TimeUnit>(() => detectBestUnit(value ?? 0));
    const [displayValue, setDisplayValue] = useState<string>(() => {
        const ms = value ?? 0;
        return ms <= 0 ? "" : String(ms / UNITS[detectBestUnit(ms)].factor);
    });

    useEffect(() => {
        const ms = value ?? 0;
        const bestUnit = detectBestUnit(ms);
        setUnit(bestUnit);
        setDisplayValue(ms <= 0 ? "" : String(ms / UNITS[bestUnit].factor));
    }, [value]);

    const handleValueChange = useCallback(
        (raw: string) => {
            setDisplayValue(raw);
            const num = parseFloat(raw);
            if (!isNaN(num) && num >= 0) {
                onChange(Math.round(num * UNITS[unit].factor));
            } else if (raw === "") {
                onChange(0);
            }
        },
        [onChange, unit],
    );

    const handleUnitChange = useCallback(
        (newUnit: TimeUnit) => {
            const currentMs = value ?? 0;
            setUnit(newUnit);
            setDisplayValue(
                currentMs <= 0 ? "" : String(currentMs / UNITS[newUnit].factor),
            );
        },
        [value],
    );

    return (
        <div className="flex items-center gap-2 max-w-xs">
            <Input
                type="number"
                min={0}
                className="flex-1 font-mono text-sm"
                placeholder="0"
                disabled={disabled}
                value={displayValue}
                onChange={(e) => handleValueChange(e.target.value)}
            />
            <Select value={unit} onValueChange={(v) => handleUnitChange(v as TimeUnit)} disabled={disabled}>
                <SelectTrigger className="w-[140px]">
                    <SelectValue />
                </SelectTrigger>
                <SelectContent>
                    {(Object.entries(UNITS) as [TimeUnit, UnitConfig][]).map(([key, cfg]) => (
                        <SelectItem key={key} value={key}>
                            {t(cfg.labelKey)}
                        </SelectItem>
                    ))}
                </SelectContent>
            </Select>
        </div>
    );
}
