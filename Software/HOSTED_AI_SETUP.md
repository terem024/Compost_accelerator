# AI prediction on Railway

The Spring Boot backend calls Gemini over HTTPS, with optional automatic Groq failover. Gmail OAuth credentials are only
for email; they do not enable Gemini. The ESP32 firmware does not need changing.

## Configure

1. In Railway, open **Backend > Variables**.
2. Add `GEMINI_API_KEY` using your Gemini API key from
   [Google AI Studio](https://aistudio.google.com/apikey).
3. Optionally add `GEMINI_MODEL=gemini-2.5-flash` (the code's default).
4. Deploy the backend changes and the frontend changes from GitHub.
5. Open AI Prediction, select a batch with sensor readings, and generate once.

Keep the key in backend variables only. Never use a `VITE_` variable for it,
commit it to GitHub, or share it in screenshots. Local Eclipse environment
variables and `.env` files are not automatically uploaded to Railway.

## Enable the backup AI

1. Create a key in [GroqCloud](https://console.groq.com/keys).
2. Add `GROQ_API_KEY` to **Railway > Backend > Variables**, not Frontend.
3. Keep `GROQ_MODEL=openai/gpt-oss-120b` (the default), or select another model
   that supports strict JSON schema output. Do not choose a chat-only model.
4. `AI_FALLBACK_ENABLED=true` is the default. Set it to `false` to disable sending
   data to the backup provider. Redeploy Backend after changing these variables.

Gemini is always tried first for a new prediction. A provider error, rate limit,
timeout, incomplete response, or invalid prediction JSON triggers one Groq attempt.
With no Groq key, the system keeps its Gemini-only behavior. Cancellation does not
start a backup request. Saved daily results do not call either provider again.
Both providers receive the same batch and monitoring summary; account credentials
are not included. The privacy notice identifies both providers.

Groq currently offers a rate-limited free plan for GPT-OSS 120B. Free availability,
account quotas, and model availability can change; check your Groq account limits.
A backup reduces dependence on Gemini but does not guarantee availability or accuracy.
There is no fake or rule-generated prediction if both providers fail, and provider
failures do not consume the daily allowance. Sensor monitoring and threshold-based
actuator control do not depend on either AI provider.

No schema migration or firmware change is required. The existing `model_provider`
and `model_name` columns record the actual provider and model, including Groq results.

## Verify

- The result should be saved in `ai_predictions` for the selected batch.
- Reopening that batch on the same Manila calendar day returns the saved result.
- A failed provider request does not consume the daily prediction allowance.
- A successful result can still have no ready date when evidence is insufficient.
  No mock sensor readings or fabricated fallback predictions are generated.

## If it fails

Check **Backend > Deployment Logs** immediately after one attempt. Never paste keys.

| Log | Action |
| --- | --- |
| `Gemini is not configured` | Add `GEMINI_API_KEY` to Backend, then deploy. |
| `Gemini request failed: HTTP 400/401/403` | Check the key's project, API restrictions, and Gemini access. |
| `Gemini request failed: HTTP 404` | Select a supported model using `GEMINI_MODEL`. |
| `Gemini request failed: HTTP 429` | Check the project's Gemini quota and rate limits in AI Studio. |
| `Gemini request timed out` or HTTP 5xx | Try again later; check provider availability. |
| `AI prediction database operation failed` | Share the SQL error below this log, with credentials removed. |

The backend allows 30 seconds per provider (at most two provider attempts);
the browser allows 90 seconds
for the entire prediction request, including loading and saving batch data.

References: [Gemini API](https://ai.google.dev/api),
[Gemini 2.5 Flash](https://ai.google.dev/gemini-api/docs/models/gemini-2.5-flash).
Backup references: [Groq structured outputs](https://console.groq.com/docs/structured-outputs),
[Groq free-plan limits](https://console.groq.com/docs/rate-limits).
