CREATE SCHEMA audit;

CREATE TABLE audit.audit_event
(
  decision_id    UUID PRIMARY KEY,
  outcome        VARCHAR(16) NOT NULL,
  schema_version INT         NOT NULL,
  written_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
