-- 06-consistency §4: the pre-approval written with an APPROVE; the hook responder reads the
-- published view only and never this table.
CREATE SCHEMA authorisation;

CREATE TABLE authorisation.pre_approval
(
  decision_id        UUID          PRIMARY KEY,
  amount             NUMERIC(19,2) NOT NULL,
  currency           VARCHAR(3)    NOT NULL,
  merchant_reference VARCHAR(64)   NOT NULL,
  valid_from         TIMESTAMPTZ   NOT NULL,
  valid_until        TIMESTAMPTZ   NOT NULL,
  state              VARCHAR(16)   NOT NULL CHECK (state IN ('ACTIVE', 'CANCELLED'))
);

CREATE VIEW authorisation.pre_approval_published AS
  SELECT decision_id, amount, currency, merchant_reference, valid_from, valid_until
  FROM authorisation.pre_approval
  WHERE state = 'ACTIVE';
