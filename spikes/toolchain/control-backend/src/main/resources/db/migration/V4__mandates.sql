-- T-037 walking skeleton. One mandate, fixed terms, no confirm flow yet (T-003 writes the real model).
CREATE SCHEMA mandates;

CREATE TABLE mandates.mandate
(
  id                 UUID          PRIMARY KEY,
  version            INT           NOT NULL,
  status             VARCHAR(16)   NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
  limit_per_purchase NUMERIC(19,2) NOT NULL,
  budget             NUMERIC(19,2) NOT NULL,
  currency           VARCHAR(3)    NOT NULL
);

-- Completed purchases per mandate, one row per attempt so a redelivered CheckoutCompleted counts once.
CREATE TABLE mandates.completed_purchase
(
  attempt_id   UUID        PRIMARY KEY,
  mandate_id   UUID        NOT NULL REFERENCES mandates.mandate (id),
  decision_id  UUID        NOT NULL,
  completed_at TIMESTAMPTZ NOT NULL
);

-- SYNTHETIC seed for the skeleton: a 100.00 EUR limit per purchase and a 150.00 EUR budget, so two
-- 80.00 proposals racing for the budget can both pass the limit while only one fits the budget.
INSERT INTO mandates.mandate (id, version, status, limit_per_purchase, budget, currency)
VALUES ('00000000-0000-0000-0000-00000000a001', 1, 'ACTIVE', 100.00, 150.00, 'EUR');
