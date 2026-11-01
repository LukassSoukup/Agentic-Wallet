-- Invariant 8: the complete DecisionInput with the verdict, insert-only by trigger (ADR-0001 kept by
-- ADR-0009). The spike's audit_event of T-008 is replaced; the spike was never deployed.
DROP TABLE audit.audit_event;

CREATE TABLE audit.decision_record
(
  decision_id       UUID        PRIMARY KEY,
  cardholder_id     UUID        NOT NULL,
  agent_instance_id UUID        NOT NULL,
  outcome           VARCHAR(16) NOT NULL,
  reasons           TEXT        NOT NULL,
  schema_version    INT         NOT NULL,
  rule_set_version  VARCHAR(32) NOT NULL,
  decision_input    JSONB       NOT NULL,
  written_at        TIMESTAMPTZ NOT NULL
);

CREATE FUNCTION audit.refuse_change() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'audit.decision_record is insert-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER decision_record_insert_only
  BEFORE UPDATE OR DELETE ON audit.decision_record
  FOR EACH ROW EXECUTE FUNCTION audit.refuse_change();
