import { ArrowRight } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Container, SectionHeader } from "@/components/brand";
import { STEPS } from "@/lib/site-content";

export function HowItWorks() {
  return (
    <section id="how" className="bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="The agent loop"
          title="How Turing answers a question"
          description="Every conversation runs an agentic loop — retrieve, reason, act with tools, and respond with grounded, cited answers."
        />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {STEPS.map((step, i) => (
            <div key={step.title} className="relative">
              <Card className="h-full gap-0 p-6">
                <span className="mb-4 flex size-9 items-center justify-center rounded-lg bg-gradient-to-br from-[#1a3a9e] to-[#4f46e5] text-sm font-extrabold text-white">
                  {i + 1}
                </span>
                <h3 className="text-base font-bold">{step.title}</h3>
                <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                  {step.description}
                </p>
              </Card>
              {i < STEPS.length - 1 && (
                <ArrowRight className="absolute top-1/2 -right-3 hidden size-5 -translate-y-1/2 text-primary/40 lg:block" />
              )}
            </div>
          ))}
        </div>
      </Container>
    </section>
  );
}
