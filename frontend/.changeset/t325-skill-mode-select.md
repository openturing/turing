---
"@viglet/turing-sdk": minor
"@viglet/turing-react-sdk": minor
---

Add an optional skill-mode pin to the agent chat surface (T325). A new
`selectedSkillId` on `PostAgentChatOptions` (vanilla SDK) and
`UseTuringChatOptions` (`useTuringChat`, React SDK) selects a single skill —
by id or case-insensitive name — for the conversation, so the agent operates
that one skill as a distinct "mode/flow" instead of offering the whole enabled
skill set for progressive disclosure. The value rides the agent chat request
body alongside `flowId`/`forcedVariant`; leaving it unset keeps the existing
all-skills behaviour, so consumers are unchanged.
