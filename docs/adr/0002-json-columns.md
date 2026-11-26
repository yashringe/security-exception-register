# ADR 2: Controls and approvals as JSONB columns

Status: accepted

## Context
Each exception has a short list of compensating controls and a list of approvals (one or two entries).

## Decision
Store both lists as `JSONB` columns on `exception_request`, and map them with Jackson in the repository.

## Why
- They are always loaded and saved together with their request. Nothing queries a control on its own.
- One row per request keeps optimistic locking simple: one `version` column protects the whole aggregate.
- The audit trail, not these columns, is the record of who approved what and when.

## Consequences
- A query like "all exceptions that rely on network segmentation" needs a JSON operator (`controls @> ...`) instead of a join. That is acceptable at the size of a security exception register (hundreds to a few thousand rows).
- If controls later get their own lifecycle (testing dates, owners), they should move to their own table.
