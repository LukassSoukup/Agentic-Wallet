# VIC API surface

Part of [visa-intelligent-commerce](visa-intelligent-commerce.md). From the Cybersource developer MCP and the SDK docs (Java SDK v0.0.95, released 2026-10-07) and the developer guide (revision 25.11.01), checked 2026-10-23 and 2026-10-24. Not yet confirmed by an authenticated call.

## Environments
- Sandbox: `https://apitest.cybersource.com`. Production: `https://api.cybersource.com` (out of scope).
- Paths: the SDK uses `/icc/v1/...`; the guide uses `/acp/v1/...`. The sandbox routes both (unauthenticated probe, [vic-sandbox-log](vic-sandbox-log.md)). SDK history: 0.0.85–0.0.90 (2026-01 to 06) used `/acp/v1`, 0.0.91–0.0.94 had no agentic classes, 0.0.95 brought them back as `/icc/v1`, alongside agent registration and the ACP/UCP checkout APIs. The guide predates 0.0.95, so treat the SDK as current until a live call says otherwise.

## Endpoints (SDK paths)
Core purchase flow, the part TASK-034 needs:

| Step | Method and path | SDK class / method |
|---|---|---|
| 0. Card on file | `POST /tms/v1/instrumentidentifiers` (card number in, instrument identifier out) | `InstrumentIdentifierApi` |
| 1. Enroll a card | `POST /icc/v1/tokens` | `EnrollmentApi` |
| 2. Initiate a purchase intent | `POST /icc/v1/instructions` | `PurchaseIntentApi.initiatePurchaseIntent` |
| 3. Update it (optional) | `PUT /icc/v1/instructions/{instructionId}` | `PurchaseIntentApi.updatePurchaseIntent` |
| 4. Cancel it (optional) | `PUT /icc/v1/instructions/{instructionId}/cancel` | `PurchaseIntentApi.cancelPurchaseIntent` |
| 5. Retrieve payment credentials | `POST /icc/v1/instructions/{instructionId}/credentials` | `PaymentEventsApi` |
| 6. Confirm transaction events | `POST /icc/v1/instructions/{instructionId}/confirmations` | `PaymentEventsApi` |

Also in the SDK, not needed for the flow:
- `AgentRegistrationApi` (`/icc/v1/agents`, keys): registers an agent in the Visa Agent Registry Service and uploads its public signing keys (rsa-pss, ecdsa, ed25519; keys expire after 14 days by default) so merchants can verify agent signatures. Needs a `tokenRequestorId` "assigned by Visa". No purchase-flow model carries an agent ID, so it is the Trusted Agent Protocol side, not a prerequisite (inferred).
- `MerchantRegistrationApi`, `AcpCheckoutApi` (`/icc/v1/checkout_sessions`), `UcpCheckoutApi` (`/icc/v1/checkout-sessions`), `ProductFeedApi`, `CredentialsApi` (`/icc/v1/mpp/credentials`).
- Passkey Service (pilot) under `/tms/v2/tokenized-cards/{id}/authentication-options...`; see [vic-sandbox-access](vic-sandbox-access.md).

## Call flow (developer guide)
1. The customer enrolls a card. Answer: `ACTIVE` (200) or `PENDING` with `pendingEvents: ["PENDING_CARDHOLDER_AUTHENTICATION"]` (202); the customer verifies identity to activate.
2. The customer states a purchase and verifies identity. Initiate returns an `instructionId` (200), or `PENDING` + `pendingEvents` (202).
3. The customer picks an option; the agent retrieves credentials with the `instructionId`.
4. The merchant's payment processor completes the payment with them.
5. The agent confirms the transaction events.

## Auth and encryption
- Auth: JWT. `jwtKeyType=SHARED_SECRET` (HS256: `merchantKeyId`, `merchantsecretKey`) is recommended; `P12` (RS256) is the default. HTTP Signature is being deprecated. The SDK docs' "No authorization required" is a generator artifact: it appears on every API (e.g. `PaymentsApi`), and the sandbox rejects unauthenticated calls.
- **MLE is mandatory** on every `/icc/v1` endpoint for the request body (except GETs) and for the response, and MLE works only with JWT. Java SDK properties (`MLE.md`):
  - request: `mleForRequestPublicCertPath` (required with a shared secret; the cert comes from the test Business Center), or with P12 auto-extracted under `requestMleKeyAlias` (default `CyberSource_SJC_US`); `enableRequestMLEForOptionalApisGlobally`, `disableRequestMLEForMandatoryApisGlobally`;
  - response: `enableResponseMleGlobally`, `responseMlePrivateKeyFilePath` (.p12/.pfx/.pem/.key/.p8) or `responseMlePrivateKey`, `responseMlePrivateKeyFilePassword`, `responseMleKID` ("provided in merchant portal"; required for PEM);
  - per API: `mapToControlMLEonAPI`.

## Models (SDK 0.0.95)
"req" = not marked optional in the SDK docs. Scalars are strings unless noted.

**Enroll request** (`AgenticCardEnrollmentRequest`): req `clientCorrelationId`, `deviceInformation`, `buyerInformation`, `billTo`, `consumerIdentity`, `paymentInformation`; optional `enrollmentReferenceData{enrollmentReferenceType req, enrollmentReferenceProvider}`, `assuranceData[]`, `consentData[]{id, type=PERSONALIZATION, source, acceptedTime, effectiveUntil (epoch)}`. Responses: 200 `{clientCorrelationId, status}`; 202 `{clientCorrelationId, status, pendingEvents[]}`; 400 `{error{status, reason, message, detail{reason, source, sourceType, message}}}`.

**The card goes in as a TMS reference, never a card number**: `Iccv1tokensPaymentInformation` = `{customer{id}, paymentInstrument{id}, instrumentIdentifier{id} req}`.

Shared nested models:
- `Iccv1tokensDeviceInformation`: req `applicationName`, `fingerprintSessionId`, `deviceData{type req, brand req, manufacturer, model}`, `ipAddress`; optional `userAgent` (Base64 without padding, required for browsers), `country`, `clientDeviceId`.
- `Iccv1tokensAssuranceData`: `verificationType` CARDHOLDER (default) | DEVICE; `verificationEntity` "10" = Visa; `verificationEvents[]` 01 payment, 02 enrollment, 03 profile, 04 account verification; `verificationMethod` (required) 02 app-based, 04 one-time passcode, 21 VTS device binding, 22 VTS cardholder verification, 23 FIDO2; `verificationResults` (required) 01 verified, 02 not verified, 03 not performed, 04 not required, 21 not allowed; `verificationTimestamp` (required, epoch); optional `authenticationContext{action}`, `authenticatedIdentities{data, provider, id req}`, `additionalData`.
- `Iccv1tokensBuyerInformation`: `merchantCustomerId`, `personalIdentification[]{type, id, issuedBy}`, `language` (ISO 639-1).
- `Iccv1tokensBillTo`: req `countryCallingCode`, `phoneNumber`, `country`; optional names, email, `numberIsVoiceOnly` (Boolean).
- `Iccv1tokensConsumerIdentity`: req `identityType` EMAIL_ADDRESS | MOBILE_PHONE_NUMBER, req `identityValue`; `identityProvider` VISA | PARTNER (default), `identityProviderUrl`.

**Purchase intent** (`AgenticCreatePurchaseIntentRequest`; update has the same shape): `clientCorrelationId`, `paymentInformation`, `deviceInformation`, `assuranceData[]`, `mandates[]`; optional `buyerInformation`, `isRecurring` (Boolean, default false), `consumerPrompt` (recap of the user's prompts).

Mandate (`Iccv1instructionsMandates`): req `mandateId` (ours; never reused after an update or delete), `description`, `declineThreshold`, `effectiveUntilTime` (UTC epoch); optional `preferredMerchantName`, `merchantCategory`, `merchantCategoryCode` (must be a valid MCC, e.g. `5311`), `quantity`, `recurringPaymentInformation{occurrence: WEEKLY | MONTHLY | YEARLY}`.
- `declineThreshold` = `{amount, currencyCode}`, both strings. Samples: `"100.00"`, `"USD"` (alpha ISO 4217). Optional in the SDK model, required by the guide.

Responses: 200 `AgenticCreatePurchaseIntentResponse200{clientCorrelationId, status, instructionId}`; 202 `AgenticPendingPurchaseIntentResponse202{clientCorrelationId, instructionId, status, pendingEvents[], propertyName}`. The SDK types initiate, update and cancel as returning the 200 model only, so the adapter likely has to read 202 bodies itself (inferred).

**Cancel**: `{clientCorrelationId, paymentInformation (optional), deviceInformation, assuranceData[]}` → `CANCELLED` (200) or `PENDING` (202).

**Retrieve credentials** (`AgenticRetrievePaymentCredentialsRequest`): `{clientCorrelationId, paymentInformation, transactionData[]}`. Each `transactionData`:
- req `clientReferenceInformation{code}`, `orderInformation{amountDetail req, shipTo, lineItems[], deliveryMethod}`, `merchantInformation{merchantName req, merchantDescriptor{country, url} req, merchantId, categoryCode, domainName, language}`;
- `mandateReferenceData[]{mandateId}`, `type` PURCHASE (default) | BILL_PAYMENT | MONEY_TRANSFER | DISBURSEMENT | P2P, `paymentOptions{dpaDynamicDataTtlMinutes, dynamicDataType: TAVV | DAVV | NONE}`, `paymentServiceProviderUrl/Name`, `merchantOrderId`, `attachments[]`.
- `IccAmountDetail`: req `totalAmount`; `currency` (ISO 4217); `subTotalAmount`, `discountAmount`, `shippingAmount`, `handlingAmount`. All strings.

Response 200 is one of: `transactionResponseComplete{clientCorrelationId, instructionId, status, authorization, signedPayload}` or `transactionResponseWithPendingEvents{…, pendingEvents[]}`. SDK: "Returns COMPLETED (HTTP 200) with a signed payload containing encrypted payment credentials (authorization token and JWS-signed payload)… used by the merchant's payment processor to complete the transaction." No card number, cryptogram or expiry fields are exposed; what the JWS holds is open. For comparison, TMS `POST /tms/v2/tokens/{tokenId}/payment-credentials` returns `tokenizedCard{number, expirationMonth/Year, cryptogram, eci, requestorId}`.

**Confirm**: `{clientCorrelationId, paymentInformation, confirmationData[] req}`. Each entry: req `clientReferenceInformation{code}`, req `processorInformation{transactionType req (PURCHASE, AUTHORIZATION, CAPTURE, REFUND, REVERSAL, VERIFICATION, CHARGEBACK, FRAUD), transactionStatus req (APPROVED, DECLINED, PENDING, ERROR, CANCELLED), responseCode, approvalCode, retrievalReferenceNumber, systemTraceAuditNumber, acquirerReferenceNumber, dynamicDataId, amountDetail, entryMode, …}`; `orderInformation{orderId, orderStatus, orderDate, expectedDeliveryDate (epoch), amountDetail, shipTo, shippingDetails, trackingId, carrier, lineItems}`; `merchantInformation{merchantId, merchantName, categoryCode}`. Response 202 `{clientCorrelationId}`.

## Guide vs SDK: confirmed contradictions
Which one the sandbox enforces is open ([vic-open-questions](vic-open-questions.md) Q10).
- The guide requires `tokenizedCard.number` on initiate, update and credentials, and sends `tokenizedCard.tokenReferenceId` on enroll (`enrollmentReferenceType: "TOKEN_REFERENCE_ID"`, provider `VTS`). No SDK model has `tokenizedCard`.
- Confirmations: the guide sends `transactionData[]` and answers `{transactionId, status: "COMPLETED", signedPayload}`; the SDK sends `confirmationData[]` with `processorInformation` and answers a bare 202.
- Credentials response: the guide shows `{clientCorrelationId, transactionId, status: "COMPLETED"}` with no payload.
- Recurrence: the SDK nests `recurringPaymentInformation` in the mandate; a guide example puts it at the top level.
- The guide marks `customer.id` and `paymentInstrument.id` required on enroll but its samples send `""`.

## Money formats
- ICC amounts: decimal strings in major units (`"100.00"`), alpha currency (`"USD"`).
- Passkey Service samples: numeric currency (`"978"`).
- `/icc/v1/mpp/credentials` challenge: `amount` in **minor units** (`"4999"` = 49.99) with a lowercase currency.
- So the adapter formats per endpoint from our exact decimal (invariant 6): half-even to 2 dp, then the endpoint's string form.

## How it maps to our domain (observations, not decisions)
- A VIC mandate is a network-side ceiling: amount, merchant, MCC, expiry, description. Our control layer decides first; a mandate can at most repeat what we already allow (invariants 1 and 4).
- Mandates have no line items; credentials requests can carry them, but basket checks stay ours.
- `PENDING` + `pendingEvents` is the network's own step-up; how it relates to our `STEP_UP` is for the architecture task.
- `consumerPrompt`, `description` and merchant text are untrusted data (invariant 3).
- `effectiveUntilTime`, `verificationTimestamp`: epoch values from the clock port, never a system clock (invariant 9).
- The confirm call reports the processor's result (approval code, RRN), which is also our reconciliation evidence (invariant 8).
