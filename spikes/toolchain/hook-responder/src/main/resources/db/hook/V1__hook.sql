-- 06-consistency §4: the hook responder owns the fact that a pre-approval was used. One consumption
-- per pre-approval (the unique key), one call-log row per processor authorisation id (deduplication).
CREATE TABLE hook.pre_approval_consumption
(
  pre_approval_id            UUID        PRIMARY KEY,
  processor_authorisation_id VARCHAR(64) NOT NULL,
  consumed_at                TIMESTAMPTZ NOT NULL
);

CREATE TABLE hook.call_log
(
  processor_authorisation_id VARCHAR(64)   PRIMARY KEY,
  -- PENDING while the call that claimed the id is still deciding; a twin delivery waits on this key
  answer                     VARCHAR(16)   NOT NULL CHECK (answer IN ('PENDING', 'MATCH', 'NO_MATCH')),
  pre_approval_id            UUID,
  amount                     NUMERIC(19,2) NOT NULL,
  currency                   VARCHAR(3)    NOT NULL,
  merchant_reference         VARCHAR(64)   NOT NULL,
  mode                       VARCHAR(16)   NOT NULL,
  received_at                TIMESTAMPTZ   NOT NULL
);
