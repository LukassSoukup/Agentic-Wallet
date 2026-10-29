# VIC sandbox access

Part of [visa-intelligent-commerce](visa-intelligent-commerce.md). From the signup page, the Intelligent Commerce guide, the TMS guide and the SDK, 2026-10-23 and 2026-10-24.

## Which route
- **Cybersource** (this page) is the only self-serve route.
- **Visa's own VIC sandbox** (developer.visa.com, `sandbox.api.visa.com`, X-Pay auth) is gated: "Available for use by: Acquirer Banks, Issuer Banks … Access to this product is limited to authorized users". Visa's reference agent says to "contact your Visa representative" for VTS, VIC and token requestor credentials and MLE keys. Not for us now. Visa also runs a docs MCP server (`https://sandbox.mcp.visa.com/mcp/doc`) and publishes the Trusted Agent Protocol sample (`github.com/visa/trusted-agent-protocol`).

## Step 1: signup (the user, once)
Form: [Intelligent Commerce Sandbox Sign up](https://developer.cybersource.com/hello-world/agentic-sandbox.html).
1. **Organization ID**: the account's name. Sandbox-specific (e.g. ending in `_sandbox`); it can't easily be renamed and must not be the one we'd go live with.
2. Company, name, address, email, phone. "Payment Technology Provider with own POS terminals?": no.
3. Accept the terms, reCAPTCHA, Create Account.
4. An email follows to create a user in the **test Business Center** (`businesscentertest.cybersource.com/ebc2`).

Terms: simulated test data only, no load testing, no production use; Visa can approve, decline or revoke the account. No approval time stated.

## Step 2: keys in the test Business Center (the user)
Payment Configuration → Key Management:
- **REST shared secret** (key id + secret) for JWT auth.
- **Request MLE certificate** (Cybersource's public cert): download it.
- **Response MLE key**: generate a key pair locally and register the public half: `openssl genrsa -out private.pem 2048 && openssl rsa -in private.pem -pubout -out public.pem`, then + Generate Key → Message-Level Encryption → paste `public.pem`. Note the KID it shows. Keys expire after 3 years; test and production need separate keys. (This is the TMS MLE procedure; that ICC uses the same key is inferred.)

## Step 3: ask Cybersource support (the user; the likely blocker)
- **Enable the Token Management Service vault with network tokens.** "Network token enablement is currently a manual process and requires a request to be sent to Cybersource support"; for evaluation accounts: "Send your merchantID to TMS representative … to create a vault and enable Token Management Service with network tokens."
- **Token requester ID (TRID) and relationship ID.** Intelligent Commerce prerequisites: "To obtain these IDs, contact your Cybersource account manager." They are entered at Token Management → Vault Management → Network Tokenization → VISA → Enroll to Visa Token Services (the guide gives Acquirer ID `40010052242` for this step, and Acquirer merchant ID = our organization ID).
- **Enable Intelligent Commerce (ICC) on the account**, and confirm whether the sandbox enforces the `/icc` (SDK) or `/acp` (guide) schema.

Suggested message: "Sandbox org `<org id>`: please create a TMS vault with Visa network tokenization, provide a token requester ID and relationship ID for the sandbox, and enable Intelligent Commerce (ICC) APIs. Which request schema does apitest enforce for /icc/v1 vs /acp/v1?"

## Test data
- **Network-token test cards** (TMS guide, updated 26.07.01): Visa PANs `46229431231XX639`, `…647`, `…654`, `…662`, `…67X`, `…688`, `…696` with each X replaced by 4, expiry 12/26, CVV per the guide's table. "Once a network token has been successfully provisioned for one of the above test PANs there is no way to delete the network token": each card works once per account, so keep a record of which we've used in [vic-sandbox-log](vic-sandbox-log.md).
- Failure triggers, e.g. `4000000011XXXXXX` → CARD_VERIFICATION_FAILED, `4010057022XXXXXX` → DECLINED.
- Whether these work for ICC enroll is untested.
- **OTP**: Visa's reference agent uses `456789` in Visa's sandbox; the Cybersource guide uses the same value in its example without calling it a test value. Untested on apitest.
- **Passkeys** need the VTS iframe and browser WebAuthn over HTTPS ("For information about iframes and the Visa Token Service SDK, contact your Cybersource account manager"). Not possible headless on the Pi; the OTP route is the candidate for tests.

## Credentials we hold
| Item | From | Secret? |
|---|---|---|
| Organization / merchant ID | Signup | No, but kept out of the repo |
| REST shared secret: key id + secret | Business Center | **Yes** |
| Request MLE public certificate | Business Center | No |
| Response MLE private key + KID | Generated locally, public half registered | **Yes** (the private key) |
| TRID, relationship ID | Cybersource support | Treat as secret |

## Where they live
- Values only in the local `.env`; key files outside git (`.env`, `*.pem`, `*.key` are git-ignored). Never in docs, logs, fixtures or the audit log.
- The setting names and how the adapter loads them are decided in T-028; its gate is "Visa developer sandbox credentials are in the local .env".
