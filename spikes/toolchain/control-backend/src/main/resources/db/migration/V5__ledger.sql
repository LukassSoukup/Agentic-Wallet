-- 06-consistency §1: one budget-period row per (mandate budget, period), locked FOR UPDATE at the start
-- of every decision that may reserve; reservations turn into counted on COMPLETED and are released on
-- FAILED or CANCELLED.
CREATE SCHEMA ledger;

CREATE TABLE ledger.budget_period
(
  mandate_id   UUID          NOT NULL,
  period_start DATE          NOT NULL,
  currency     VARCHAR(3)    NOT NULL,
  budget       NUMERIC(19,2) NOT NULL,
  reserved     NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (reserved >= 0),
  counted      NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (counted >= 0),
  PRIMARY KEY (mandate_id, period_start)
);

CREATE TABLE ledger.reservation
(
  decision_id  UUID          PRIMARY KEY,
  mandate_id   UUID          NOT NULL,
  period_start DATE          NOT NULL,
  amount       NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  currency     VARCHAR(3)    NOT NULL,
  state        VARCHAR(16)   NOT NULL CHECK (state IN ('RESERVED', 'COUNTED', 'RELEASED')),
  changed_at   TIMESTAMPTZ   NOT NULL,
  FOREIGN KEY (mandate_id, period_start) REFERENCES ledger.budget_period (mandate_id, period_start)
);
