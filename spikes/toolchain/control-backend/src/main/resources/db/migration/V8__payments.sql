-- 06-consistency §3 and ADR-0009 B6: one checkout attempt per approved decision; every transition is
-- a compare-and-set on the expected state; the deadline of the announced call is stored on the row.
CREATE SCHEMA payments;

CREATE TABLE payments.checkout_attempt
(
  decision_id         UUID          PRIMARY KEY,
  id                  UUID          NOT NULL UNIQUE,
  mandate_id          UUID          NOT NULL,
  state               VARCHAR(24)   NOT NULL
    CHECK (state IN ('CREATED', 'CREDENTIAL_REQUESTED', 'COMPLETING', 'COMPLETED', 'FAILED', 'UNKNOWN', 'CANCELLED')),
  checkout_session_id VARCHAR(64)   NOT NULL,
  session_expires_at  TIMESTAMPTZ   NOT NULL,
  merchant_reference  VARCHAR(64)   NOT NULL,
  amount              NUMERIC(19,2) NOT NULL,
  currency            VARCHAR(3)    NOT NULL,
  call_deadline       TIMESTAMPTZ,
  failure_reason      VARCHAR(64),
  last_transition_at  TIMESTAMPTZ   NOT NULL
);
