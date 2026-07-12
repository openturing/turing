"use client"
import { ROUTES } from "@/app/routes.const"
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog"
import {
    Form,
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { GradientButton } from "@/components/ui/gradient-button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { SmartDescription } from "@/components/ui/smart-description"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table"
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model"
import type { TurSNSiteSpotlightDocument } from "@/models/sn/sn-site-spotlight-document.model"
import type { TurSNSiteSpotlightTerm } from "@/models/sn/sn-site-spotlight-term.model"
import type { TurSNSiteSpotlight } from "@/models/sn/sn-site-spotlight.model"
import { TurSNSiteLocaleService } from "@/services/sn/sn.site.locale.service"
import { TurSNSiteSpotlightService } from "@/services/sn/sn.site.spotlight.service"
import { fetchSearch, type TurDocument, type TurSearchResponse } from "@viglet/turing-react-sdk"
import { isAxiosError } from "axios"
import { toast } from "@viglet/viglet-design-system"
import { IconCirclePlus, IconDeviceFloppy, IconFileText, IconHelp, IconSearch, IconSpeakerphone, IconTags, IconTrash, IconX } from "@tabler/icons-react"
import { DialogDelete } from "@/components/dialog.delete"
import React, { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Link, useNavigate } from "react-router-dom"
import { StickyPageHeader } from "../../sticky-page-header"
import { BentoHero, BentoScrollSaveBar } from "@/components/bento"
import { SNFormSection, type SNFormChrome } from "@/components/sn/sn-form-section"

const turSNSiteSpotlightService = new TurSNSiteSpotlightService();
const turSNSiteLocaleService = new TurSNSiteLocaleService();

interface Props {
    snSiteId: string;
    value: TurSNSiteSpotlight;
    isNew: boolean;
    onDelete?: () => void;
    open?: boolean;
    setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
    /** SN instance base route for save/cancel navigation. Defaults to the
     *  console; the Bento surface passes `ROUTES.BENTO_SN_INSTANCE` (T576). */
    baseRoute?: string;
    /** Render chrome. console = StickyPageHeader + SectionCards; bento = BentoHero + frosted BentoFormSection cards (T576). Defaults to console. */
    chrome?: SNFormChrome;
}

export const SNSiteSpotlightForm: React.FC<Props> = ({ snSiteId, value, isNew, onDelete, open, setOpen, baseRoute = ROUTES.SN_INSTANCE, chrome = "console" }) => {
    const { t } = useTranslation();
    const form = useForm<TurSNSiteSpotlight>({
        defaultValues: value,
    });
    const navigate = useNavigate();
    const urlBase = `${baseRoute}/${snSiteId}/spotlight`;
    const [locales, setLocales] = useState<TurSNSiteLocale[]>([]);
    const [searchDialogOpen, setSearchDialogOpen] = useState(false);
    const [searchQuery, setSearchQuery] = useState("");
    const [searchResult, setSearchResult] = useState<TurSearchResponse | null>(null);
    const [termsError, setTermsError] = useState<string | null>(null);
    const [documentsError, setDocumentsError] = useState<string | null>(null);

    useEffect(() => {
        turSNSiteLocaleService.query(snSiteId).then(setLocales);
    }, [snSiteId]);

    useEffect(() => {
        form.reset(value);
    }, [value]);

    // Default the language to the site's first available locale when none is set
    // yet (new spotlight). The document search is scoped by locale, and a site is
    // rarely indexed in "en-US" — firing that hardcoded fallback returns HTTP 404
    // ("site+language not indexed"), which used to surface as a search error.
    useEffect(() => {
        if (locales.length > 0 && !form.getValues("language")) {
            form.setValue("language", locales[0].language);
        }
    }, [locales, value]);

    async function onSubmit(data: TurSNSiteSpotlight) {
        let hasError = false;
        if (!data.turSNSiteSpotlightTerms || data.turSNSiteSpotlightTerms.length === 0) {
            setTermsError(t("forms.snSpotlight.termRequired"));
            hasError = true;
        } else {
            setTermsError(null);
        }
        if (!data.turSNSiteSpotlightDocuments || data.turSNSiteSpotlightDocuments.length === 0) {
            setDocumentsError(t("forms.snSpotlight.documentRequired"));
            hasError = true;
        } else {
            setDocumentsError(null);
        }
        if (hasError) return;
        try {
            if (isNew) {
                const result = await turSNSiteSpotlightService.create(data);
                if (result) {
                    toast.success(t("forms.snSpotlight.created", { name: data.name }));
                    navigate(urlBase);
                } else {
                    toast.error(t("forms.snSpotlight.createFailed"));
                }
            } else {
                const result = await turSNSiteSpotlightService.update(data);
                if (result) {
                    toast.success(t("forms.snSpotlight.spotlightUpdated", { name: data.name }));
                } else {
                    toast.error(t("forms.snSpotlight.updateFailed"));
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("forms.common.formSubmitFailed"));
        }
    }

    function addTerm() {
        const currentTerms = form.getValues("turSNSiteSpotlightTerms") || [];
        const newTerm: TurSNSiteSpotlightTerm = { name: "" };
        form.setValue("turSNSiteSpotlightTerms", [...currentTerms, newTerm]);
        setTermsError(null);
    }

    function removeTerm(index: number) {
        const currentTerms = form.getValues("turSNSiteSpotlightTerms") || [];
        form.setValue(
            "turSNSiteSpotlightTerms",
            currentTerms.filter((_, i) => i !== index)
        );
    }

    function removeDocument(index: number) {
        const currentDocs = form.getValues("turSNSiteSpotlightDocuments") || [];
        form.setValue(
            "turSNSiteSpotlightDocuments",
            currentDocs.filter((_, i) => i !== index)
        );
    }

    function updateTermName(index: number, name: string) {
        const currentTerms = form.getValues("turSNSiteSpotlightTerms") || [];
        const updated = [...currentTerms];
        updated[index] = { ...updated[index], name };
        form.setValue("turSNSiteSpotlightTerms", updated);
    }

    function updateDocumentPosition(index: number, position: number) {
        const currentDocs = form.getValues("turSNSiteSpotlightDocuments") || [];
        const updated = [...currentDocs];
        updated[index] = { ...updated[index], position };
        form.setValue("turSNSiteSpotlightDocuments", updated);
    }

    async function searchDocument(page: number) {
        const siteName = value.turSNSite?.name;
        const language = form.getValues("language") || locales[0]?.language;
        if (!siteName || !searchQuery) return;
        if (!language) {
            toast.error(t("forms.snSpotlight.selectLanguageFirst"));
            return;
        }
        try {
            const result = await fetchSearch(siteName, {
                q: searchQuery,
                p: page.toString(),
                _setlocale: language,
                sort: "title:desc",
            });
            setSearchResult(result);
        } catch (error) {
            // A 404 means this site isn't indexed in the selected locale (or has no
            // matching content) — that's an empty result, not a failure. Only real
            // errors (5xx / network) get an error toast.
            if (isAxiosError(error) && error.response?.status === 404) {
                setSearchResult({ results: { document: [] } } as unknown as TurSearchResponse);
                return;
            }
            console.error("Document search error", error);
            toast.error(t("forms.snSpotlight.searchFailed"));
        }
    }

    function addDocument(searchDoc: TurDocument) {
        const currentDocs = form.getValues("turSNSiteSpotlightDocuments") || [];
        const newDoc: TurSNSiteSpotlightDocument = {
            title: searchDoc.fields.title || "",
            type: searchDoc.fields.type || "",
            position: 1,
            link: searchDoc.fields.url || "",
            referenceId: searchDoc.fields.id || "",
        };
        form.setValue("turSNSiteSpotlightDocuments", [...currentDocs, newDoc]);
        setDocumentsError(null);
        setSearchDialogOpen(false);
        setSearchQuery("");
        setSearchResult(null);
    }

    const terms = form.watch("turSNSiteSpotlightTerms") || [];
    const documents = form.watch("turSNSiteSpotlightDocuments") || [];
    const selectedLanguage = form.watch("language");
    const [syntaxHelpOpen, setSyntaxHelpOpen] = useState(false);

    function handleFormSubmit(e: React.BaseSyntheticEvent) {
        const currentTerms = form.getValues("turSNSiteSpotlightTerms") || [];
        if (currentTerms.length === 0) {
            setTermsError(t("forms.snSpotlight.termRequired"));
        } else {
            setTermsError(null);
        }
        if (selectedLanguage) {
            const currentDocs = form.getValues("turSNSiteSpotlightDocuments") || [];
            if (currentDocs.length === 0) {
                setDocumentsError(t("forms.snSpotlight.documentRequired"));
            } else {
                setDocumentsError(null);
            }
        }
        form.handleSubmit(onSubmit)(e);
    }

    const isBento = chrome === "bento";

    const actions = (
        <>
            {onDelete && open !== undefined && setOpen && <DialogDelete feature={t("sn.spotlight.title")} name={value?.name || t("sn.spotlight.newSpotlight")} onDelete={onDelete} open={open} setOpen={setOpen} />}
            <GradientButton type="submit" size="sm">
                <IconDeviceFloppy className="size-4" />
                {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(urlBase)}>
                <IconX className="size-4" />
                {t("forms.formActions.cancel")}
            </GradientButton>
        </>
    );

    return (

        <Form {...form}>
            <form onSubmit={handleFormSubmit} className={isBento ? "space-y-5 pb-8" : "space-y-4 px-4 lg:px-6 pb-8"}>
                {isBento ? (
                    <>
                    <BentoHero
                        eyebrow={<Link to={urlBase} className="hover:text-foreground">{t("sn.spotlight.title")}</Link>}
                        leading={
                            <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
                                <IconSpeakerphone size={24} />
                            </span>
                        }
                        title={value?.name || t("sn.spotlight.newSpotlight")}
                        subtitle={t("sn.spotlight.description")}
                        trailing={<div className="bento-fade-out flex shrink-0 items-center gap-2">{actions}</div>}
                    />
                    <BentoScrollSaveBar onCancel={() => navigate(urlBase)} />
                    </>
                ) : (
                    <StickyPageHeader>
                        <StickyPageHeader.Title
                            icon={IconSpeakerphone}
                            feature={t("sn.spotlight.title")}
                            description={t("sn.spotlight.description")}
                        />
                        <StickyPageHeader.Actions>{actions}</StickyPageHeader.Actions>
                    </StickyPageHeader>
                )}
                {/* General Section */}
                <SNFormSection chrome={chrome} icon={IconSpeakerphone} tone="blue" title={t("forms.snSpotlight.details")} description={t("forms.snSpotlight.detailsDesc")}>
                        {/* Name */}
                        <FormField
                            control={form.control}
                            name="name"
                            rules={{ required: t("forms.snSpotlight.nameRequired") }}
                            render={({ field }) => (
                                <FormItem>
                                    <FormLabel>{t("forms.common.name")}</FormLabel>
                                    <FormDescription>
                                        {t("forms.snSpotlight.nameDesc")}
                                    </FormDescription>
                                    <FormControl>
                                        <Input {...field} placeholder={t("forms.snSpotlight.namePlaceholder")} type="text" />
                                    </FormControl>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />
                        {/* Description */}
                        <FormField
                            control={form.control}
                            name="description"
                            render={({ field }) => (
                                <FormItem>
                                    <FormControl>
                                        <SmartDescription
                                            value={field.value}
                                            onChange={field.onChange}
                                            placeholder={t("forms.snSpotlight.descPlaceholder")}
                                            maxLength={500}
                                            title={form.watch("name")}
                                            entityType="Spotlight"
                                            enableMetaPrompt
                                        >
                                            <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                                            <SmartDescription.Description>
                                                {t("forms.snSpotlight.descDesc")}
                                            </SmartDescription.Description>
                                        </SmartDescription>
                                    </FormControl>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />
                        {/* Language (50/50 row) */}
                        <FormField
                            control={form.control}
                            name="language"
                            rules={{ required: t("forms.snSpotlight.langRequired") }}
                            render={({ field }) => (
                                <FormItemTwoColumns>
                                    <FormItemTwoColumns.Left>
                                        <FormItemTwoColumns.Label>{t("forms.snSpotlight.language")}</FormItemTwoColumns.Label>
                                        <FormItemTwoColumns.Description>
                                            {t("forms.snSpotlight.languageDesc")}
                                        </FormItemTwoColumns.Description>
                                    </FormItemTwoColumns.Left>
                                    <FormItemTwoColumns.Right>
                                        <FormControl>
                                            <Select onValueChange={field.onChange} value={field.value || ""}>
                                                <SelectTrigger className="w-full">
                                                    <SelectValue placeholder={t("forms.snSpotlight.chooseLang")} />
                                                </SelectTrigger>
                                                <SelectContent>
                                                    {locales.map((locale) => (
                                                        <SelectItem key={locale.id} value={locale.language}>
                                                            {locale.language}
                                                        </SelectItem>
                                                    ))}
                                                </SelectContent>
                                            </Select>
                                        </FormControl>
                                    </FormItemTwoColumns.Right>
                                    <FormMessage />
                                </FormItemTwoColumns>
                            )}
                        />
                </SNFormSection>

                {/* Terms Section */}
                <SNFormSection chrome={chrome} icon={IconTags} tone="violet" title={t("forms.snSpotlight.triggerTerms")} description={t("forms.snSpotlight.triggerTermsDesc")}>
                        <div>
                            <div className="mb-2 font-medium">{t("forms.snSpotlight.whenSearched")}</div>
                            <div className="text-sm text-muted-foreground mb-2">
                                {t("forms.snSpotlight.addKeywords")}
                            </div>
                            {/* T21 / §III.4 V2 — collapsible Lucene syntax help.
                                Opt-in (closed by default) so simple-term admins
                                aren't overwhelmed; the link reveals examples
                                for power users who want phrase / fuzzy / boolean. */}
                            <div className="mb-4">
                                <button
                                    type="button"
                                    onClick={() => setSyntaxHelpOpen((open) => !open)}
                                    className="inline-flex items-center gap-1.5 text-xs font-medium text-primary hover:underline"
                                    aria-expanded={syntaxHelpOpen ? "true" : "false"}
                                >
                                    <IconHelp className="h-3.5 w-3.5" />
                                    {t("forms.snSpotlight.syntaxHelp")}
                                </button>
                                {syntaxHelpOpen && (
                                    <div className="mt-2 rounded-md border bg-muted/40 p-3 text-xs text-muted-foreground space-y-2">
                                        <p>{t("forms.snSpotlight.syntaxHelpDesc")}</p>
                                        <ul className="space-y-1.5 ml-1">
                                            <li>
                                                <span className="font-medium text-foreground">
                                                    {t("forms.snSpotlight.syntaxPhrase")}:
                                                </span>{" "}
                                                <code className="bg-background px-1.5 py-0.5 rounded text-[11px]">
                                                    {t("forms.snSpotlight.syntaxPhraseDesc")}
                                                </code>
                                            </li>
                                            <li>
                                                <span className="font-medium text-foreground">
                                                    {t("forms.snSpotlight.syntaxPhraseSlop")}:
                                                </span>{" "}
                                                <code className="bg-background px-1.5 py-0.5 rounded text-[11px]">
                                                    {t("forms.snSpotlight.syntaxPhraseSlopDesc")}
                                                </code>
                                            </li>
                                            <li>
                                                <span className="font-medium text-foreground">
                                                    {t("forms.snSpotlight.syntaxFuzzy")}:
                                                </span>{" "}
                                                <code className="bg-background px-1.5 py-0.5 rounded text-[11px]">
                                                    {t("forms.snSpotlight.syntaxFuzzyDesc")}
                                                </code>
                                            </li>
                                            <li>
                                                <span className="font-medium text-foreground">
                                                    {t("forms.snSpotlight.syntaxBoolean")}:
                                                </span>{" "}
                                                <code className="bg-background px-1.5 py-0.5 rounded text-[11px]">
                                                    {t("forms.snSpotlight.syntaxBooleanDesc")}
                                                </code>
                                            </li>
                                        </ul>
                                    </div>
                                )}
                            </div>
                            <div className="space-y-2">
                                {terms.map((term, index) => (
                                    <div key={term.id || `term-${index}`} className="flex items-center gap-1.5">
                                        <Input
                                            value={term.name}
                                            onChange={(e) => updateTermName(index, e.target.value)}
                                            placeholder={t("forms.snSpotlight.typeSearchTerm")}
                                            className="grow"
                                        />
                                        <GradientButton
                                            variant="ghost"
                                            size="icon"
                                            type="button"
                                            onClick={() => removeTerm(index)}
                                            aria-label="Remove term"
                                        >
                                            <IconTrash className="h-4 w-4 text-red-500" />
                                        </GradientButton>
                                    </div>
                                ))}
                            </div>
                            <div className="mt-4">
                                <GradientButton variant="outline" type="button" onClick={addTerm}>
                                    <IconCirclePlus className="h-4 w-4 mr-2" />
                                    {t("forms.snSpotlight.addTerm")}
                                </GradientButton>
                            </div>
                            {termsError && (
                                <p className="text-sm font-medium text-destructive mt-2">{termsError}</p>
                            )}
                        </div>
                </SNFormSection>

                {/* Documents Section */}
                <SNFormSection chrome={chrome} icon={IconFileText} tone="emerald" title={t("forms.snSpotlight.documents")} description={t("forms.snSpotlight.documentsDesc")}>
                        <div>
                            <div className="mb-2 font-medium">{t("forms.snSpotlight.showDocuments")}</div>
                            <div className="text-sm text-muted-foreground mb-4">
                                {t("forms.snSpotlight.chooseDocuments")}
                            </div>
                            <div className="mb-4">
                                <Dialog open={searchDialogOpen} onOpenChange={(open) => {
                                    setSearchDialogOpen(open);
                                    if (!open) {
                                        setSearchQuery("");
                                        setSearchResult(null);
                                    }
                                }}>
                                    <DialogTrigger asChild>
                                        <GradientButton variant="outline" type="button">
                                            <IconCirclePlus className="h-4 w-4 mr-2" />
                                            {t("forms.snSpotlight.addDocument")}
                                        </GradientButton>
                                    </DialogTrigger>
                                    <DialogContent className="sm:max-w-2xl">
                                        <DialogHeader>
                                            <DialogTitle>{t("forms.snSpotlight.findDocument")}</DialogTitle>
                                            <DialogDescription>
                                                {t("forms.snSpotlight.findDocumentDesc")}
                                            </DialogDescription>
                                        </DialogHeader>
                                        <div className="flex items-center gap-2">
                                            <Input
                                                placeholder={t("forms.snSpotlight.typeToSearch")}
                                                value={searchQuery}
                                                onChange={(e) => setSearchQuery(e.target.value)}
                                                onKeyDown={(e) => {
                                                    if (e.key === "Enter") {
                                                        e.preventDefault();
                                                        searchDocument(1);
                                                    }
                                                }}
                                                className="grow"
                                            />
                                            <GradientButton
                                                type="button"
                                                variant="outline"
                                                size="icon"
                                                onClick={() => searchDocument(1)}
                                            >
                                                <IconSearch className="h-4 w-4" />
                                            </GradientButton>
                                        </div>
                                        {searchResult?.results?.document && searchResult.results.document.length > 0 && (
                                            <div className="max-h-80 overflow-y-auto">
                                                <Table>
                                                    <TableHeader>
                                                        <TableRow>
                                                            <TableHead>{t("forms.common.title")}</TableHead>
                                                            <TableHead>{t("forms.common.field")}</TableHead>
                                                        </TableRow>
                                                    </TableHeader>
                                                    <TableBody>
                                                        {searchResult.results.document.map((searchDoc, index) => (
                                                            <TableRow
                                                                key={searchDoc.fields.id || `search-${index}`}
                                                                className="cursor-pointer"
                                                                onClick={() => addDocument(searchDoc)}
                                                            >
                                                                <TableCell>{searchDoc.fields.title}</TableCell>
                                                                <TableCell>{searchDoc.fields.type}</TableCell>
                                                            </TableRow>
                                                        ))}
                                                    </TableBody>
                                                </Table>
                                            </div>
                                        )}
                                        {searchResult && (searchResult.results?.document?.length ?? 0) === 0 && (
                                            <p className="py-4 text-center text-sm text-muted-foreground">
                                                {t("forms.snSpotlight.noResults")}
                                            </p>
                                        )}
                                        {searchResult?.pagination && searchResult.pagination.length > 0 && (
                                            <div className="flex items-center gap-1 pt-2">
                                                {searchResult.pagination.map((page) => (
                                                    <GradientButton
                                                        key={page.page}
                                                        type="button"
                                                        variant={page.type === "current" ? "default" : "outline"}
                                                        size="sm"
                                                        onClick={() => searchDocument(page.page)}
                                                    >
                                                        {page.text}
                                                    </GradientButton>
                                                ))}
                                            </div>
                                        )}
                                    </DialogContent>
                                </Dialog>
                            </div>
                            {documents.length > 0 && (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead className="w-24">{t("forms.snSpotlight.order")}</TableHead>
                                            <TableHead>{t("forms.common.title")}</TableHead>
                                            <TableHead>{t("forms.common.field")}</TableHead>
                                            <TableHead className="w-24 text-right">{t("forms.common.edit")}</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {documents.map((doc, index) => (
                                            <TableRow key={doc.id || `doc-${index}`}>
                                                <TableCell>
                                                    <Input
                                                        type="number"
                                                        value={doc.position}
                                                        onChange={(e) => updateDocumentPosition(index, Number(e.target.value))}
                                                        className="w-20"
                                                    />
                                                </TableCell>
                                                <TableCell>{doc.title}</TableCell>
                                                <TableCell>{doc.type}</TableCell>
                                                <TableCell className="text-right">
                                                    <GradientButton
                                                        variant="destructive"
                                                        size="sm"
                                                        type="button"
                                                        onClick={() => removeDocument(index)}
                                                    >
                                                        <IconTrash className="h-4 w-4 mr-1" />
                                                        {t("forms.common.remove")}
                                                    </GradientButton>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                            {documentsError && (
                                <p className="text-sm font-medium text-destructive mt-2">{documentsError}</p>
                            )}
                        </div>
                </SNFormSection>
            </form>
        </Form>
    );
};
