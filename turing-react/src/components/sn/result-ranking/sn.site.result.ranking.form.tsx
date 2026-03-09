"use client"
import { ROUTES } from "@/app/routes.const"
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion"
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { GradientButton } from "@/components/ui/gradient-button"
import {
  Input
} from "@/components/ui/input"
import {
  Textarea
} from "@/components/ui/textarea"
import type { TurSNRankingExpression } from "@/models/sn/sn-ranking-expression.model"
import { TurSNRankingExpressionService } from "@/services/sn/sn.site.result.ranking.service"
import React, { useEffect } from "react"
import {
  useForm
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { Slider } from "../../ui/slider"
import { DynamicResultRankingFields } from "./dynamic-result-ranking-field"
const turSNRankingExpressionService = new TurSNRankingExpressionService();
interface Props {
  snSiteId: string
  value: TurSNRankingExpression;
  isNew: boolean;
}

export const SNSiteResultRankingForm: React.FC<Props> = ({ snSiteId, value, isNew }) => {
  const form = useForm<TurSNRankingExpression>({
    defaultValues: value
  });
  const { control, register, formState: { errors } } = form;
  const [slideValue, setSlideValue] = React.useState([4]);
  const urlBase = `${ROUTES.SN_INSTANCE}/${snSiteId}/result-ranking`;;
  const navigate = useNavigate()
  useEffect(() => {
    const nextValue = isNew
      ? { ...value, weight: value.weight ?? 4 }
      : value;

    form.reset(nextValue);
    setSlideValue([nextValue.weight ?? 4]);
  }, [value, isNew]);


  async function onSubmit(snRankingExpression: TurSNRankingExpression) {
    try {
      if (isNew) {
        const result = await turSNRankingExpressionService.create(snSiteId, snRankingExpression);
        if (result) {
          toast.success(`The ${snRankingExpression.name} Ranking Expression was created`);
          navigate(urlBase);
        } else {
          toast.error("Failed to create the Ranking Expression. Please try again.");
        }
      }
      else {
        const result = await turSNRankingExpressionService.update(snSiteId, snRankingExpression);
        if (result) {
          toast.success(`The ${snRankingExpression.name} Ranking Expression was updated`);
        } else {
          toast.error("Failed to update the Ranking Expression. Please try again.");
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error("Failed to submit the form. Please try again.");
    }
  }

  return (
    <div className="px-6 py-8">
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <Accordion
            type="multiple"
            defaultValue={["general", "conditions", "weight"]}
            className="w-full space-y-4"
          >
            {/* General Information Section */}
            <AccordionItem value="general" className="border rounded-lg px-6">
              <AccordionTrigger className="hover:no-underline">
                <div className="flex items-center gap-2">
                  <span className="text-lg font-semibold">General Information</span>
                </div>
              </AccordionTrigger>
              <AccordionContent className="flex flex-col gap-6 pt-4">
                <FormField
                  control={form.control}
                  name="name"
                  rules={{ required: "Name is required." }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Name</FormLabel>
                      <FormDescription>
                        Enter a clear, descriptive name for this ranking expression.
                      </FormDescription>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder="Ranking expression name"
                          type="text"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="description"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Description</FormLabel>
                      <FormDescription>
                        Briefly summarize the purpose of this ranking expression.
                      </FormDescription>
                      <FormControl>
                        <Textarea
                          placeholder="Purpose of this ranking expression"
                          className="resize-none"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </AccordionContent>
            </AccordionItem>

            {/* Ranking Conditions Section */}
            <AccordionItem value="conditions" className="border rounded-lg px-6">
              <AccordionTrigger className="hover:no-underline">
                <div className="flex items-center gap-2">
                  <span className="text-lg font-semibold">Ranking Conditions</span>
                </div>
              </AccordionTrigger>
              <AccordionContent className="flex flex-col gap-6 pt-4">
                <FormItem>
                  <FormLabel>
                    Content Filter <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormDescription>
                    Specify filter expressions to target content for ranking.
                  </FormDescription>
                  <FormControl>
                    <DynamicResultRankingFields
                      fieldName="turSNRankingConditions"
                      control={control}
                      register={register}
                      snSiteId={snSiteId}
                      errors={errors}
                    />
                  </FormControl>
                </FormItem>
              </AccordionContent>
            </AccordionItem>

            {/* Ranking Weight Section */}
            <AccordionItem value="weight" className="border rounded-lg px-6">
              <AccordionTrigger className="hover:no-underline">
                <div className="flex items-center gap-2">
                  <span className="text-lg font-semibold">Ranking Weight</span>
                </div>
              </AccordionTrigger>
              <AccordionContent className="flex flex-col gap-6 pt-4">
                <FormField
                  control={form.control}
                  name="weight"
                  render={({ field }) => (
                    <FormItem>
                      <div className="flex flex-row justify-between items-center w-full">
                        <div className="flex flex-col">
                          <FormLabel>Weight Adjustment</FormLabel>
                          <FormDescription>
                            Adjust the influence of this ranking expression on search results.
                          </FormDescription>
                        </div>
                        <div className="flex items-center gap-4 min-w-45">
                          <Slider
                            id="weight-slider"
                            value={[field.value ?? slideValue[0]]}
                            onValueChange={(value) => {
                              setSlideValue(value)
                              field.onChange(value[0])
                            }}
                            max={10}
                            min={0}
                            step={1}
                          />
                          <span className="w-10 text-right font-medium">
                            +{slideValue[0]}
                          </span>
                        </div>
                      </div>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </AccordionContent>
            </AccordionItem>
          </Accordion>
          {/* Action Footer */}
          <div className="flex justify-end gap-4 mt-8">
            <GradientButton
              type="button"
              variant="outline"
              onClick={() => navigate(urlBase)}
            >
              Cancel
            </GradientButton>
            <GradientButton type="submit">
              Save Changes
            </GradientButton>
          </div>
        </form>
      </Form>
    </div>
  )
}

