# AI prediction on Railway

The Spring Boot backend calls Gemini over HTTPS. Gmail OAuth credentials are only
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

The backend waits at most 60 seconds for Gemini; the browser allows 90 seconds
for the entire prediction request, including loading and saving batch data.

References: [Gemini API](https://ai.google.dev/api),
[Gemini 2.5 Flash](https://ai.google.dev/gemini-api/docs/models/gemini-2.5-flash).
