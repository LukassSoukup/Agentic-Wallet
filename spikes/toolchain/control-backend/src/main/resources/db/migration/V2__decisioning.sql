CREATE SCHEMA decisioning;

CREATE TABLE decisioning.decision
(
  id         UUID PRIMARY KEY,
  decided_at TIMESTAMPTZ   NOT NULL,
  amount     NUMERIC(19,2) NOT NULL,
  currency   VARCHAR(3)    NOT NULL,
  outcome    VARCHAR(16)   NOT NULL CHECK (outcome IN ('APPROVE', 'STEP_UP', 'DECLINE')),
  reasons    TEXT          NOT NULL
);
