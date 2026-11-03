-- 06-consistency §5: a proposal is unique on (agent instance, purchaseKey); the constraint is what
-- turns two concurrent deliveries into one decision. Binding fields are compared in the application.
CREATE TABLE decisioning.proposal
(
  id                 UUID        PRIMARY KEY,
  agent_instance_id  UUID        NOT NULL,
  purchase_key       VARCHAR(64) NOT NULL,
  mandate_id         UUID        NOT NULL,
  merchant_reference VARCHAR(64) NOT NULL,
  lines              JSONB       NOT NULL,
  description        TEXT        NOT NULL,
  decision_id        UUID        NOT NULL,
  received_at        TIMESTAMPTZ NOT NULL,
  UNIQUE (agent_instance_id, purchase_key)
);

ALTER TABLE decisioning.decision
  ADD COLUMN mandate_id          UUID        NOT NULL,
  ADD COLUMN merchant_reference  VARCHAR(64) NOT NULL,
  ADD COLUMN checkout_session_id VARCHAR(64) NOT NULL;
