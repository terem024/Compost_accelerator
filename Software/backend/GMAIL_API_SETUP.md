# Gmail API setup on Railway

The backend can send email through Gmail's HTTPS API, using an authorized Gmail
account. No SMTP connection, Gmail app password, or custom sender domain is needed
for this delivery method. Deployment alone does not authorize Gmail.

## 1. Prepare the sending account

1. Sign in to [Google Cloud Console](https://console.cloud.google.com/) with the
   system sender account and create a project, such as `Compost Accelerator Email`.
2. Enable **Gmail API** in that project.
3. Configure Google Auth Platform branding, contact details, and audience. For a
   personal Gmail account, use External; while Testing, add the sender as a test user.
4. Add only the `https://www.googleapis.com/auth/gmail.send` scope.
5. Create an OAuth client of type **Web application**, with this authorized redirect
   URI: `https://developers.google.com/oauthplayground`.

Only the sender authorizes Gmail. Website users receiving messages do not need to
authorize this OAuth client or become its test users.

## 2. Authorize the sender

1. Open [OAuth Playground](https://developers.google.com/oauthplayground).
2. In its settings, enable **Use your own OAuth credentials** and enter the client
   ID and client secret from your project. Use server-side flow and offline access.
3. Authorize the `https://www.googleapis.com/auth/gmail.send` scope as the exact
   Gmail account that will send the messages.
4. Exchange the authorization code for tokens. Keep the refresh token private.

Testing-mode refresh tokens for Gmail expire after seven days. Before relying on
unattended email, review Google's publishing and verification requirements, move
to an appropriate production setup, and authorize again. Tokens can still be
revoked; automatic access-token refresh cannot repair revoked authorization.

## 3. Configure the Railway backend

Add these variables to the **backend service only**:

| Variable | Value |
| --- | --- |
| `EMAIL_PROVIDER` | `gmail-api` |
| `GMAIL_USERNAME` | The Gmail address authorized above |
| `GMAIL_CLIENT_ID` | Your OAuth client ID |
| `GMAIL_CLIENT_SECRET` | Your OAuth client secret |
| `GMAIL_REFRESH_TOKEN` | Your refresh token |
| `APP_FRONTEND_URL` | Your public frontend HTTPS URL, without a trailing slash |
| `NOTIFICATION_EMAIL` | The intended actuator-alert recipient, or blank to disable alerts |

Do not put credentials in React, Git, screenshots, or chat. `.env.example` is a
reference; Spring Boot does not automatically load a local `.env` file.
`GMAIL_APP_PASSWORD` and Resend settings are not used with `EMAIL_PROVIDER=gmail-api`.
Deploy the updated backend and frontend, then apply the environment changes.

## 4. Verify delivery

- Request a signup OTP for an address you control, and complete verification.
- Request a password-reset link for an existing account. Confirm it opens the
  hosted frontend, not localhost.
- During a supervised actuator test, confirm the configured recipient receives
  an alert. Alerts run in a bounded, in-memory background queue; failed or skipped
  alerts are logged, not retried or saved across restarts. They cannot block the
  hardware response.
- Check spam folders and backend logs if delivery fails. Gmail quotas and the
  receiving organization's filtering still apply; API acceptance is not proof of
  inbox delivery. Frequent actuator alerts can consume the same sending allowance
  needed by OTPs and password resets.

## References

- [Gmail message sending](https://developers.google.com/workspace/gmail/api/guides/sending)
- [OAuth token expiration](https://developers.google.com/identity/protocols/oauth2#expiration)
- [Google OAuth production readiness](https://developers.google.com/identity/protocols/oauth2/production-readiness/overview)
- [Personal Gmail sending limits](https://support.google.com/mail/answer/22839)
