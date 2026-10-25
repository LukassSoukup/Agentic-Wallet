# VIC sources

Part of [visa-intelligent-commerce](visa-intelligent-commerce.md). Checked 2026-10-23, extended 2026-10-24.

| Source | Access | What it gives | Gaps |
|---|---|---|---|
| `cybersource-developer-mcp` (MCP server in Claude Code) | Yes, no login | Every SDK endpoint (221 in Java SDK v0.0.95), API class docs, model classes, code and build templates, per-endpoint MLE flags, SDK setup guide | Generated from the SDK, so descriptions can be thin. Says "No authorization required" on the agentic endpoints, which is a generator artifact (see [vic-open-questions](vic-open-questions.md)). |
| [Intelligent Commerce developer guide](https://developer.cybersource.com/docs/cybs/en-us/intelligent-commerce/developer/all/rest/intelligent-commerce.html) | Public | Prerequisites, Passkey Service setup (pilot), order of API requests, purchase workflow, one page per endpoint | Uses `/acp/v1` paths; the SDK uses `/icc/v1`. No sandbox test data. |
| Developer guide as Markdown: swap `.html` for `.md` on any guide URL, e.g. [intelligent-commerce.md](https://developer.cybersource.com/docs/cybs/en-us/intelligent-commerce/developer/all/rest/intelligent-commerce.md) | Public | The whole guide in one file (revision 25.11.01), easiest to read and grep | Older than SDK 0.0.95 |
| [TMS developer guide](https://developer.cybersource.com/docs/cybs/en-us/tms/developer/all/rest/tms.md) | Public | Instrument identifiers, network-token enablement, network-token test cards, MLE key setup | |
| [Visa VIC overview](https://developer.visa.com/capabilities/visa-intelligent-commerce/overview), `github.com/visa/vic-reference-agent`, `github.com/visa/ai`, `github.com/visa/trusted-agent-protocol` | Public (the overview blocks plain fetches; curl works) | Visa's own route: gated to acquirers and issuers; reference agent, env layout, sandbox OTP, Trusted Agent Protocol | Not self-serve |
| [API reference](https://developer.cybersource.com/api-reference-assets/index.html) | Public | Live console for the REST API | Landing page only through a plain fetch; the agentic section needs a browser. |
| [Documentation hub](https://developer.cybersource.com/docs.html) | Public | REST getting started, auth (JWT, HTTP Signature), Token Management, Payer Authentication | |
| [Intelligent Commerce sandbox signup](https://developer.cybersource.com/hello-world/agentic-sandbox.html) | Public | The signup form; see [vic-sandbox-access](vic-sandbox-access.md) | |
| [Testing guide](https://developer.cybersource.com/hello-world/testing-guide-v1.html) | Public | Processor test trigger values | Nothing agentic-specific found. |
| [cybersource-rest-client-java](https://github.com/CyberSource/cybersource-rest-client-java) | Public | SDK source; `docs/<Api>.md` per class (e.g. `docs/PurchaseIntentApi.md`), `MLE.md` | Same content as the MCP. Release tags show the API history (`/acp` in 0.0.85–0.0.90, `/icc` from 0.0.95). |
| [cybersource-rest-samples-java](https://github.com/CyberSource/cybersource-rest-samples-java) | Public | Runnable samples: Payments, TokenManagement, networkTokenization, MLEFeature, JwtSharedSecretAuth… | **No Intelligent Commerce samples** (checked the `src/main/java/samples` folders). |
| Business Center (test) `businesscentertest.cybersource.com/ebc2` | Needs our sandbox login | Keys, MLE certificate, account settings | Not reachable until we sign up. |

## Querying the MCP
The tools are `list_available_apis`, `get_api_class_details`, `get_model_class_details`, `get_code_template`, `get_sdk_overview`; all take `language` (default `java`) and `sdk_version` (default `latest`).
- `list_available_apis` returns about 85 KB. Its endpoint map is one JSON object; filter it for paths starting with `/icc/` instead of reading it whole.
- Model classes nest (`Iccv1instructionsMandates` → `Iccv1instructionsDeclineThreshold` …). Follow each level with another `get_model_class_details` call rather than guessing a field.
- For MLE questions, `list_available_apis` is the source of truth (`apiMleCapabilities`), not the class docs.
