# VIC sandbox log

Part of [visa-intelligent-commerce](visa-intelligent-commerce.md). One entry per real call or session against the sandbox, newest first. Record the endpoint, what we sent (shape only, no secrets, no card numbers), the status and the response shape, and which open question it closes ([vic-open-questions](vic-open-questions.md)). Recorded responses that become test fixtures are scrubbed first. Network-token test cards can be provisioned only once per account: record each one used (last 4 digits only).

Template:

```
## YYYY-MM-DD: <what we tried>
- Task: T-NNN
- Call: METHOD /path
- Result: HTTP status, status field, notable response fields
- Learned: … (closes Qn / contradicts <page>)
```

## 2026-10-24: unauthenticated route probe
- Task: none (research agent)
- Calls: POST without credentials or body to `apitest.cybersource.com` on `/icc/v1/instructions`, `/acp/v1/instructions`, `/icc/v1/tokens`, `/acp/v1/tokens`, `/icc/v1/agents`, `/{icc,acp}/v1/instructions/x/credentials`; controls `/icc/v1/nonexistent`, `/acp/v1/nonexistent`, `/foo/v1/tokens`, `/pts/v2/payments`, `/tms/v1/instrumentidentifiers`.
- Result: every real path → 400 `{"response":{"rmsg":"Bad Request"}}`; unknown paths → 404.
- Learned: the gateway routes both `/icc` and `/acp` (narrows Q1); unauthenticated calls are rejected (closes Q6). Says nothing about which schema each enforces.
- Card numbers used: none.
