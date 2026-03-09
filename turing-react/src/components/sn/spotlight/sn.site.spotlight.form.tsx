"use client"
import { ROUTES } from "@/app/routes.const"
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion"
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
import type { TurSNSearchDocument } from "@/search/models/sn-search-document.model"
import type { TurSNSearch } from "@/search/models/sn-search.model"
import { TurSNSearchService } from "@/search/services/sn-search.service"
import { TurSNSiteLocaleService } from "@/services/sn/sn.site.locale.service"
import { TurSNSiteSpotlightService } from "@/services/sn/sn.site.spotlight.service"
import { PlusCircle, Search, Trash2 } from "lucide-react"
import React, { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"

const turSNSiteSpotlightService = new TurSNSiteSpotlightService();
const turSNSiteLocaleService = new TurSNSiteLocaleService();

interface Props {
    snSiteId: string;
    value: TurSNSiteSpotlight;
    isNew: boolean;
}

export const SNSiteSpotlightForm: React.FC<Props> = ({ snSiteId, value, isNew }) => {
    const form = useForm<TurSNSiteSpotlight>({
        defaultValues: value,
    });
    const navigate = useNavigate();
    const urlBase = `${ROUTES.SN_INSTANCE}/${snSiteId}/spotlight`;
    const [locales, setLocales] = useState<TurSNSiteLocale[]>([]);
    const [searchDialogOpen, setSearchDialogOpen] = useState(false);
    const [searchQuery, setSearchQuery] = useState("");
    const [searchResult, setSearchResult] = useState<TurSNSearch | null>(null);
    const [termsError, setTermsError] = useState<string | null>(null);
    const [documentsError, setDocumentsError] = useState<string | null>(null);

    useEffect(() => {
        turSNSiteLocaleService.query(snSiteId).then(setLocales);
    }, [snSiteId]);

    useEffect(() => {
        form.reset(value);
    }, [value]);

    async function onSubmit(data: TurSNSiteSpotlight) {
        let hasError = false;
        if (!data.turSNSiteSpotlightTerms || data.turSNSiteSpotlightTerms.length === 0) {
            setTermsError("At least one term is required.");
            hasError = true;
        } else {
            setTermsError(null);
        }
        if (!data.turSNSiteSpotlightDocuments || data.turSNSiteSpotlightDocuments.length === 0) {
            setDocumentsError("At least one document is required.");
            hasError = true;
        } else {
            setDocumentsError(null);
        }
        if (hasError) return;
        try {
            if (isNew) {
                const result = await turSNSiteSpotlightService.create(data);
                if (result) {
                    toast.success(`The ${data.name} spotlight was created`);
                    navigate(urlBase);
                } else {
                    toast.error("Failed to create the spotlight. Please try again.");
                }
            } else {
                const result = await turSNSiteSpotlightService.update(data);
                if (result) {
                    toast.success(`The ${data.name} spotlight was updated`);
                } else {
                    toast.error("Failed to update the spotlight. Please try again.");
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error("Failed to submit the form. Please try again.");
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
        const language = form.getValues("language");
        if (!siteName || !searchQuery) return;
        try {
            const result = await TurSNSearchService.query(
                siteName,
                {
                    q: searchQuery,
                    p: page.toString(),
                    _setlocale: language || "en-US",
                    sort: "title:desc",
                    fq: [],
                    tr: [],
                    nfpr: "",
                }
            );
            setSearchResult(result);
        } catch (error) {
            console.error("Search error", error);
            toast.error("Failed to search documents.");
        }
    }

    function addDocument(searchDoc: TurSNSearchDocument) {
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

    function handleFormSubmit(e: React.BaseSyntheticEvent) {
        const currentTerms = form.getValues("turSNSiteSpotlightTerms") || [];
        if (currentTerms.length === 0) {
            setTermsError("At least one term is required.");
        } else {
            setTermsError(null);
        }
        if (selectedLanguage) {
            const currentDocs = form.getValues("turSNSiteSpotlightDocuments") || [];
            if (currentDocs.length === 0) {
                setDocumentsError("At least one document is required.");
            } else {
                setDocumentsError(null);
            }
        }
        form.handleSubmit(onSubmit)(e);
    }

    return (

        <Form {...form}>
            <form onSubmit={handleFormSubmit}>
                <Accordion
                    type="multiple"
                    defaultValue={["general", "terms", "documents"]}
                    className="space-y-6 py-8 px-6"
                >
                    {/* General Section */}
                    <AccordionItem value="general" className="border rounded-lg px-6">
                        <AccordionTrigger className="hover:no-underline">
                            <div className="flex items-center gap-2">
                                <span className="text-lg font-semibold text-foreground">Spotlight Details</span>
                            </div>
                        </AccordionTrigger>
                        <AccordionContent className="flex flex-col gap-8 pt-4">
                            {/* Name */}
                            <FormField
                                control={form.control}
                                name="name"
                                rules={{ required: "Please give your spotlight a name." }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Name</FormLabel>
                                        <FormDescription>
                                            Choose a name to help you recognize this spotlight later.
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder="e.g. AI Best Practices" type="text" />
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
                                        <FormLabel>Description</FormLabel>
                                        <FormDescription>
                                            Add a short description so others know what this spotlight is about.
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} placeholder="e.g. Curated resources for AI adoption" type="text" />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                            {/* Language (50/50 row) */}
                            <FormField
                                control={form.control}
                                name="language"
                                rules={{ required: "Please select a language." }}
                                render={({ field }) => (
                                    <FormItemTwoColumns>
                                        <FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Label>Language</FormItemTwoColumns.Label>
                                            <FormItemTwoColumns.Description>
                                                Pick the language for this spotlight. This helps show the right content to your users.
                                            </FormItemTwoColumns.Description>
                                        </FormItemTwoColumns.Left>
                                        <FormItemTwoColumns.Right>
                                            <FormControl>
                                                <Select onValueChange={field.onChange} value={field.value || ""}>
                                                    <SelectTrigger className="w-full">
                                                        <SelectValue placeholder="Choose language" />
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
                        </AccordionContent>
                    </AccordionItem>

                    {/* Terms Section */}
                    <AccordionItem value="terms" className="border rounded-lg px-6">
                        <AccordionTrigger className="hover:no-underline">
                            <div className="flex items-center gap-2">
                                <span className="text-lg font-semibold text-foreground">Trigger Terms</span>
                            </div>
                        </AccordionTrigger>
                        <AccordionContent className="flex flex-col gap-8 pt-4">
                            <div>
                                <div className="mb-2 font-medium">When someone searches for these terms...</div>
                                <div className="text-sm text-muted-foreground mb-4">
                                    Add keywords or phrases. If a user searches for any of these, this spotlight will appear.
                                </div>
                                <div className="space-y-2">
                                    {terms.map((term, index) => (
                                        <div key={term.id || `term-${index}`} className="flex items-center gap-1.5">
                                            <Input
                                                value={term.name}
                                                onChange={(e) => updateTermName(index, e.target.value)}
                                                placeholder="Type a search term"
                                                className="grow"
                                            />
                                            <GradientButton
                                                variant="ghost"
                                                size="icon"
                                                type="button"
                                                onClick={() => removeTerm(index)}
                                                aria-label="Remove term"
                                            >
                                                <Trash2 className="h-4 w-4 text-red-500" />
                                            </GradientButton>
                                        </div>
                                    ))}
                                </div>
                                <div className="mt-4">
                                    <GradientButton variant="outline" type="button" onClick={addTerm}>
                                        <PlusCircle className="h-4 w-4 mr-2" />
                                        Add Term
                                    </GradientButton>
                                </div>
                                {termsError && (
                                    <p className="text-sm font-medium text-destructive mt-2">{termsError}</p>
                                )}
                            </div>
                        </AccordionContent>
                    </AccordionItem>

                    {/* Documents Section */}
                    <AccordionItem value="documents" className="border rounded-lg px-6">
                        <AccordionTrigger className="hover:no-underline">
                            <div className="flex items-center gap-2">
                                <span className="text-lg font-semibold text-foreground">Spotlight Documents</span>
                            </div>
                        </AccordionTrigger>
                        <AccordionContent className="flex flex-col gap-8 pt-4">
                            <div>
                                <div className="mb-2 font-medium">...show these documents as spotlights</div>
                                <div className="text-sm text-muted-foreground mb-4">
                                    Choose which documents will be highlighted when the above terms are searched.
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
                                                <PlusCircle className="h-4 w-4 mr-2" />
                                                Add Document
                                            </GradientButton>
                                        </DialogTrigger>
                                        <DialogContent className="sm:max-w-2xl">
                                            <DialogHeader>
                                                <DialogTitle>Find a Document</DialogTitle>
                                                <DialogDescription>
                                                    Search and select a document to feature in this spotlight.
                                                </DialogDescription>
                                            </DialogHeader>
                                            <div className="flex items-center gap-2">
                                                <Input
                                                    placeholder="Type to search..."
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
                                                    <Search className="h-4 w-4" />
                                                </GradientButton>
                                            </div>
                                            {searchResult?.results?.document && searchResult.results.document.length > 0 && (
                                                <div className="max-h-80 overflow-y-auto">
                                                    <Table>
                                                        <TableHeader>
                                                            <TableRow>
                                                                <TableHead>Title</TableHead>
                                                                <TableHead>Type</TableHead>
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
                                                <TableHead className="w-24">Order</TableHead>
                                                <TableHead>Title</TableHead>
                                                <TableHead>Type</TableHead>
                                                <TableHead className="w-24 text-right">Actions</TableHead>
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
                                                            <Trash2 className="h-4 w-4 mr-1" />
                                                            Remove
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
                        </AccordionContent>
                    </AccordionItem>
                </Accordion>
                {/* Action Footer */}
                <div className="flex justify-end gap-4 mt-8">
                    <GradientButton variant="outline" type="button" onClick={() => navigate(urlBase)}>
                        Cancel
                    </GradientButton>
                    <GradientButton type="submit">
                        {isNew ? "Create Spotlight" : "Save Changes"}
                    </GradientButton>
                </div>
            </form>
        </Form>
    );
};
