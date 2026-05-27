/**
 * Canonical DSL example: a 4-question lead-capture flow with email
 * validation and a free-text career-objective slot. Mirrors the
 * {@code lead-capture-b2c} recipe shipped with the backend so a reader
 * can diff this file against
 * {@code turing-app/src/main/resources/flow-recipes/lead-capture-b2c.json}
 * to see the same flow expressed in both forms.
 *
 * <p>Run {@code tsc} on this file to produce {@code lead-capture.flow.mjs},
 * then {@code turing-flow-dsl build examples/ --out out/} writes
 * {@code out/lead-capture.chat-flow.json} which the editor accepts as-is.
 *
 * @since 2026.3.1
 */

import { defineFlow } from "@viglet/turing-flow-dsl";

export default defineFlow({
  name: "Lead capture (DSL)",
  description: "Collects name, email, area of interest, free-form objective.",
  enabled: 1,
  guardrailMethod: "LLM_JUDGE",
  triggerMode: "ONCE",
  triggerLanguage: "PT",
  triggerDescription:
    "Ativar quando o visitante demonstrar interesse em conhecer cursos ou programas.",
  slots: [
    { name: "name", description: "Visitor first name", type: "STRING" },
    // email is a STRING slot validated by the ask-email node's validationRule.
    { name: "email", description: "Visitor primary email", type: "STRING" },
    { name: "area", description: "Area of interest", type: "STRING" },
    { name: "objetivo", description: "Free-form career objective", type: "TEXT" },
  ],
  nodes: [
    { id: "start", type: "start" },
    {
      id: "ask-name",
      type: "aiQuestion",
      label: "Pergunta nome",
      aiInstruction:
        "Cumprimente e pergunte o nome do visitante. Tom acolhedor, primeira pessoa, uma frase.",
      outputVariable: "name",
    },
    {
      id: "ask-email",
      type: "aiQuestion",
      label: "Pergunta email",
      aiInstruction:
        "Use o nome {{name}}. Peça o melhor email para contato, explicando que será usado apenas para enviar materiais.",
      outputVariable: "email",
      validationRule: "email",
    },
    {
      id: "ask-area",
      type: "aiQuestion",
      label: "Pergunta área",
      aiInstruction:
        "Pergunte qual área de interesse do {{name}}: liderança, tecnologia, vendas, ou outra.",
      outputVariable: "area",
      inlineOptions: ["Liderança", "Tecnologia", "Vendas", "Outra"],
    },
    {
      id: "ask-objective",
      type: "aiQuestion",
      label: "Pergunta objetivo",
      aiInstruction:
        "Pergunte ao {{name}} qual é o principal objetivo de carreira nos próximos 6-12 meses.",
      outputVariable: "objetivo",
      onJudgeReject: "advance_with_literal",
    },
    { id: "end", type: "end" },
  ],
  edges: [
    { source: "start", target: "ask-name" },
    { source: "ask-name", target: "ask-email" },
    { source: "ask-email", target: "ask-area" },
    { source: "ask-area", target: "ask-objective" },
    { source: "ask-objective", target: "end" },
  ],
});
