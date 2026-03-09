import { Badge } from "@/components/ui/badge";
import { Globe } from "lucide-react";
import React, { useEffect, useState } from "react";

interface BadgeLocaleProps {
    locale: string;
    className?: string;
}

const LANGUAGE_TO_COUNTRY: Record<string, string> = {
    AR: "SA",
    BN: "BD",
    CA: "ES",
    CS: "CZ",
    DA: "DK",
    DE: "DE",
    EL: "GR",
    EN: "US",
    EU: "ES",
    FA: "IR",
    FI: "FI",
    FIL: "PH",
    FR: "FR",
    GA: "IE",
    GL: "ES",
    GU: "IN",
    HE: "IL",
    HI: "IN",
    HU: "HU",
    ID: "ID",
    IT: "IT",
    JA: "JP",
    KN: "IN",
    KO: "KR",
    MR: "IN",
    MSA: "MY",
    NL: "NL",
    NO: "NO",
    PL: "PL",
    PT: "BR",
    RO: "RO",
    RU: "RU",
    ES: "ES",
    SV: "SE",
    TA: "IN",
    TH: "TH",
    TR: "TR",
    UK: "UA",
    UR: "PK",
    VI: "VN",
    ZH: "CN",
};

const COUNTRY_OVERRIDES: Record<string, string> = {
    UK: "GB",
};

export function getLocaleCountryCode(locale: string): string {
    if (!locale) return "";

    const normalizedLocale = locale.trim().replaceAll("-", "_").toUpperCase();
    const localeParts = normalizedLocale.split("_");

    if (localeParts.length > 1 && localeParts[1].length === 2) {
        const regionCode = localeParts[1];
        return (COUNTRY_OVERRIDES[regionCode] || regionCode).toLowerCase();
    }

    const languageCode = localeParts[0];
    const countryByLanguage = LANGUAGE_TO_COUNTRY[languageCode];
    if (countryByLanguage) {
        return countryByLanguage.toLowerCase();
    }

    return (COUNTRY_OVERRIDES[languageCode] || languageCode).toLowerCase();
}

export const BadgeLocale: React.FC<BadgeLocaleProps> = ({ locale, className }) => {
    const [imgError, setImgError] = useState(false);

    const countryCode = getLocaleCountryCode(locale);

    useEffect(() => {
        setImgError(false);
    }, [countryCode]);

    return (
        <Badge
            variant="secondary"
            className={`font-mono gap-2 py-1 pl-1 pr-2 w-fit ${className}`}
        >
            {!imgError && countryCode ? (
                <img
                    src={`https://flagcdn.com/w40/${countryCode}.png`}
                    alt={countryCode}
                    onError={() => setImgError(true)}
                    className="w-5 h-3.5 object-cover rounded-sm shadow-sm"
                />
            ) : (
                <Globe className="w-3.5 h-3.5 text-muted-foreground" />
            )}
            <span className="text-xs font-bold uppercase tracking-tight leading-none">
                {locale}
            </span>
        </Badge>
    );
};