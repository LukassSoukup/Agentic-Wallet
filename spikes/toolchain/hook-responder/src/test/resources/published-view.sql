-- The backend's published contract as the hook sees it (a copy of control-backend's V7 for the
-- hook's own tests; in a deployment both run against one PostgreSQL instance, 02-system).
CREATE SCHEMA IF NOT EXISTS authorisation;

CREATE TABLE IF NOT EXISTS authorisation.pre_approval
(
  decision_id        UUID          PRIMARY KEY,
  amount             NUMERIC(19,2) NOT NULL,
  currency           VARCHAR(3)    NOT NULL,
  merchant_reference VARCHAR(64)   NOT NULL,
  valid_from         TIMESTAMPTZ   NOT NULL,
  valid_until        TIMESTAMPTZ   NOT NULL,
  state              VARCHAR(16)   NOT NULL CHECK (state IN ('ACTIVE', 'CANCELLED'))
);

CREATE OR REPLACE VIEW authorisation.pre_approval_published AS
  SELECT decision_id, amount, currency, merchant_reference, valid_from, valid_until
  FROM authorisation.pre_approval
  WHERE state = 'ACTIVE';
