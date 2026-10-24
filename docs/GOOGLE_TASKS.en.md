# Google Tasks setup and availability

[简体中文](GOOGLE_TASKS.md)

## Current status

The code includes Credential Manager sign-in, AuthorizationClient consent, task requests, two-way merge, an offline queue and conflict UI. The public Cloud project, client ID, review and live account testing are not complete. Unconfigured builds disable connection explicitly and never simulate success. Installed users do not create developer projects: the publisher must configure and verify the integration before a connectable release.

## Using the integration

Settings → Google Tasks → Connect → preview seven days or all goals → choose an account and authorize. Only the third answer and completion sync to a dedicated `Daily 3 Minutes` list. The other answers stay local. A source-date marker in task notes supports retries and relinking; it contains neither of the other answers.

Google's `tasks` permission allows management of all tasks, as its consent screen states. This app processes only its own marked or linked tasks. It does not integrate Drive or request Drive permissions.

Disconnection keeps local and cloud content. Revoke Google access by removing the app from your Google Account's third-party apps and services. Cloud deletion keeps the journal. Clearing or deleting locally first detaches the task; cloud deletion requires separate confirmation.

Concurrent goal edits retain both versions for a choice. After JSON restore, reconnect and check date links. Task sync is not a complete journal backup. Goals over 1,024 characters are neither uploaded nor truncated; changing task dates does not move diary dates.

## Publisher configuration

1. Create a separate project under the user's own Google Cloud account and enable the Google Tasks API.
2. Set the app name, support contact, homepage, privacy policy, scope purpose and external audience. Provide real accessible pages with ownership verification as required by Google.
3. Register `io.github.daily3minutes` and the actual APK's SHA-1 certificates. A rotated APK uses the new signer on Android 13+ and the old signer on older systems; register both.
4. Create a Web client for Credential Manager's app identification. This app has no backend and embeds no client secret or refresh token.
5. Build with `-PGOOGLE_WEB_CLIENT_ID=<public-client-id>`. This ID is not a password. Never commit OAuth secrets, account tokens or signing keys.
6. Test with dedicated synthetic tasks, prepare review materials, and keep a beta label until required review and live verification pass.

People building their own version need their own signing and client registration. Missing configuration does not disable local features.

## References

[Android authorization](https://developer.android.com/identity/authorization), [Tasks scopes](https://developers.google.com/workspace/tasks/auth), [task field limits](https://developers.google.com/workspace/tasks/reference/rest/v1/tasks), [public authorization review](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification).
