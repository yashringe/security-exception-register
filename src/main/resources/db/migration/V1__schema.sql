-- Security exception register: requests and their append-only audit trail.

CREATE TABLE exception_request (
    id                 UUID PRIMARY KEY,
    title              TEXT        NOT NULL,
    asset_id           TEXT        NOT NULL,
    policy_reference   TEXT        NOT NULL,
    justification      TEXT        NOT NULL,
    requester          TEXT        NOT NULL,
    owner              TEXT        NOT NULL,
    inherent_likelihood SMALLINT   NOT NULL CHECK (inherent_likelihood BETWEEN 1 AND 5),
    inherent_impact    SMALLINT    NOT NULL CHECK (inherent_impact BETWEEN 1 AND 5),
    -- Controls and approvals are always read and written together with their request,
    -- so they are stored as JSON next to it (see docs/adr/0002-json-columns.md).
    controls           JSONB       NOT NULL DEFAULT '[]',
    approvals          JSONB       NOT NULL DEFAULT '[]',
    requested_expiry   DATE        NOT NULL,
    status             TEXT        NOT NULL
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'EXPIRED', 'CLOSED')),
    created_at         TIMESTAMPTZ NOT NULL,
    submitted_at       TIMESTAMPTZ,
    decided_at         TIMESTAMPTZ,
    decision_note      TEXT,
    renewal_count      INTEGER     NOT NULL DEFAULT 0,
    version            BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX exception_request_status_idx ON exception_request (status);
CREATE INDEX exception_request_expiry_idx ON exception_request (requested_expiry) WHERE status = 'APPROVED';

CREATE TABLE audit_entry (
    sequence      BIGINT PRIMARY KEY,
    exception_id  UUID        NOT NULL REFERENCES exception_request (id),
    action        TEXT        NOT NULL,
    actor         TEXT        NOT NULL,
    at            TIMESTAMPTZ NOT NULL,
    details       TEXT        NOT NULL,
    previous_hash CHAR(64)    NOT NULL,
    hash          CHAR(64)    NOT NULL UNIQUE
);

CREATE INDEX audit_entry_exception_idx ON audit_entry (exception_id);

-- The application only ever inserts audit rows. Refuse updates and deletes at database level too,
-- so a bug or a careless script cannot rewrite history without first dropping this trigger.
CREATE FUNCTION audit_entry_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_entry is append-only (% refused)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_entry_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit_entry
    FOR EACH ROW EXECUTE FUNCTION audit_entry_is_append_only();
