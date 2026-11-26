# ADR 3: Hash-chained, append-only audit trail

Status: accepted

## Context
Auditors and regulators expect to see who requested, approved, rejected, renewed or closed each exception, and they need to trust that nobody changed that history afterwards.

## Decision
- Every change writes one `audit_entry` in the same transaction as the change itself.
- Each entry stores the SHA-256 of its own fields plus the previous entry's hash. Fields are length-prefixed so their boundaries cannot be shifted.
- A PostgreSQL advisory lock serialises appends, so two transactions can never both extend the same "last entry" and fork the chain.
- A database trigger refuses `UPDATE` and `DELETE` on `audit_entry`.
- `GET /api/audit/verify` recomputes the chain and returns the first broken sequence number.

## What this protects against
- Editing an old entry: its hash no longer matches.
- Deleting an entry: the next entry's previous hash no longer matches.
- Rewriting one entry and recomputing its hash: the following entry still points to the old hash.

## What it does not protect against
Someone with owner rights on the database could drop the trigger and rebuild the entire chain. Real deployments close this by publishing the latest hash outside the database every day, for example to a write-once storage bucket or a signed report. That step is listed as a next step in the README.

## Consequences
- Appends are serialised. At a few writes per minute this costs nothing; a high-volume event log would need a different design (per-partition chains or a Merkle tree).
