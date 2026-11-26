# ADR 1: Residual risk counts only the best tested control

Status: accepted

## Context
An exception lists one or more compensating controls. The register has to turn them into a residual risk, which then decides how long the exception may run and how many approvals it needs.

## Decision
- A control has one of three effectiveness levels. UNTESTED lowers likelihood by 0, PARTIAL by 1 and EFFECTIVE by 2.
- Only the single best control is applied. Its reduction is not added to the others.
- Controls lower likelihood, never impact. Likelihood never goes below 1.

## Why
- Controls usually overlap. "Isolated VLAN" and "IP allow-list" both reduce exposure to the network, and counting them twice would overstate the protection.
- Adding reductions up would let three untested-but-labelled-partial controls turn a CRITICAL risk into a MEDIUM one, the exact gaming a risk team wants to prevent.
- A compensating control makes an incident less likely. It rarely makes one less damaging, so impact stays the same.

## Consequences
- The model is simple enough to explain to a requester in one sentence.
- Some real combinations of controls are undervalued. A risk officer can still approve with a comment, but the system will not lower the score.
