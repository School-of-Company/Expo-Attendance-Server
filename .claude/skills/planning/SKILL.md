---
name: planning
argument-hint: [instructions]
description: Conduct an in-depth structured interview with the user to uncover non-obvious requirements, tradeoffs, and constraints, then produce a detailed implementation spec file.
allowed-tools: AskUserQuestion, Write
---

Interview me relentlessly about every aspect of this plan until we reach a shared understanding.

1. Walk the design tree one branch at a time, asking a single question per turn.
2. Before asking a question the codebase can answer, explore the codebase instead of asking.
3. After each answer, state your evaluation and a recommended answer, then move to the next question.

## Output

Write the finished spec to `<scratchpad>/<topic>-spec.md` with sections: Goal, Constraints, Decisions
(Q&A log), Open Questions, Implementation Steps.
