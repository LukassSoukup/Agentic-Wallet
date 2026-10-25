# VIC open questions

Part of [visa-intelligent-commerce](visa-intelligent-commerce.md). Each item is closed by a live call (logged in [vic-sandbox-log](vic-sandbox-log.md)) or an answer from Cybersource/Visa, then moved to the page it belongs to. Updated 2026-10-24.

## Open
| # | Question | Why it matters | How to close it | Status |
|---|---|---|---|---|
| Q1 | `/acp/v1` or `/icc/v1`? | Wrong path or schema = rejected calls | First authenticated call on both; ask support | narrowed: the sandbox routes both prefixes (400 without auth, 404 for unknown paths); the SDK moved to `/icc` in 0.0.95, the guide is older |
| Q2 | Are the TRID and relationship ID needed in the sandbox? | Likely blocker; not self-serve | Ask support (see [vic-sandbox-access](vic-sandbox-access.md) step 3) | narrowed: entered in the vault's network tokenization settings; network-token enablement is a manual support request |
| Q3 | Is Intelligent Commerce enabled on a new sandbox account? | Calls fail otherwise | First call after signup; ask support | narrowed, likely no: TMS and network tokens need manual enablement; ICC-specific enablement isn't documented |
| Q4 | How do we pass cardholder verification in the sandbox? | Enroll and initiate stay `PENDING` | Try the OTP route; try self-declared `assuranceData` | narrowed: OTP route exists under `/tms/v2/tokenized-cards/{id}/authentication-options`; Visa's sandbox OTP is `456789`; passkeys need a browser and the VTS iframe |
| Q5 | Which test cards work for enrollment? | Need a card that tokenizes | Try the network-token test PANs | narrowed: TMS network-token test PANs, each usable once |
| Q8 | Who runs payment (step 4) and confirm (step 6) in our product? | Same question as TASK-047 | User decision on TASK-047 | open. Facts: the signed payload "is used by the merchant's payment processor"; confirm needs the processor's result (approval code, RRN) |
| Q10 | Does the sandbox enforce the guide schema (`tokenizedCard`, `transactionData` on confirm) or the SDK schema (`confirmationData`/`processorInformation`)? | Decides the adapter's request shapes | First authenticated calls; ask support | open |
| Q11 | Is a Visa network token (`tokenReferenceId`) needed before enroll, or is an instrument identifier enough? | If needed, Q2/Q3 enablement blocks everything | Enroll with only an instrument identifier | open |
| Q12 | What's inside `signedPayload` (card token, expiry, cryptogram?), which key verifies it, and what is `authorization` for? | What we hand to execution | First successful credentials call | open |
| Q13 | How does the Java SDK expose a 202 with `pendingEvents` when the methods return the 200 model? | Step-up handling | Read the SDK source or a live 202 | open |
| Q14 | Is `fingerprintSessionId` (required) checked, or can it be any value without the iframe? | Headless use | Live call | open |
| Q15 | Is the ICC response-MLE key the same Business Center MLE key as the TMS one? | Key setup | Business Center UI; first call | open |
| Q16 | Does OTP `456789` work on Cybersource apitest? | Headless step-up in tests | Live call | open |

## Closed
| # | Answer | Evidence |
|---|---|---|
| Q6 | "No authorization required" in the SDK docs is a generator artifact | The same text is on `PaymentsApi`; the sandbox rejects unauthenticated calls (2026-10-24 probe) |
| Q7 | `declineThreshold` = `{amount, currencyCode}`, both strings; samples `"100.00"`, `"USD"`. Response models in [vic-api-surface](vic-api-surface.md) | SDK 0.0.95 model docs; guide samples |
| Q9 | Cybersource is the only self-serve route; Visa's own VIC sandbox is for acquirers and issuers with credentials from a Visa representative | developer.visa.com VIC overview; Visa's reference agent README |
