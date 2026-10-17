# Team invitations and member management

Email delivery is not configured in this project. Invitation creation therefore returns HTTP 503 unless the explicit development-link flow is enabled with the `dev` or `local` Spring profile. It never reports an email as sent. Keep this setting disabled in production until a real mail provider is integrated.

For local testing, from the repository root:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:TEAM_INVITATION_DEV_LINKS_ENABLED = 'true'
$env:TEAM_INVITATION_EXPIRATION_HOURS = '72'
$env:INVITATION_FRONTEND_BASE_URL = 'http://localhost:5173'
docker compose -f backend/compose.yaml up -d --build
```

Sign into the dashboard as an administrator and open **Team**. The development invitation URL is returned only in the immediate create response and is shown once by the dashboard. It is not stored in the database or returned when listing invitations. Send the link to the invitee through an appropriate local testing channel. The invitee chooses a username and password on the acceptance page; the server always assigns `MEMBER`.

For HTTP-level testing, use an ADMIN JWT from the existing login endpoint:

```powershell
$adminSession = Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/auth/login `
  -ContentType 'application/json' `
  -Body '{"username":"admin","password":"<your-admin-password>"}'
$headers = @{ Authorization = "Bearer $($adminSession.accessToken)" }
$invitation = Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/team/invitations `
  -Headers $headers -ContentType 'application/json' -Body '{"email":"member@example.com"}'
$invitation.inviteUrl # Share/copy this development link; it is not returned again.
Invoke-RestMethod -Method Get -Uri http://localhost:8081/api/team/invitations -Headers $headers
Invoke-RestMethod -Method Get -Uri http://localhost:8081/api/team/members -Headers $headers
```

The public acceptance endpoint is `POST /api/auth/accept-invitation` with JSON fields `token`, `username`, and `password`; it does not accept a role. The dashboard acceptance page posts this request. Invitation management endpoints are `POST /api/team/invitations`, `GET /api/team/invitations`, `DELETE /api/team/invitations/{id}`, and `GET /api/team/members`.

`TEAM_INVITATION_EXPIRATION_HOURS` defaults to 72 (maximum 720 hours). `TEAM_INVITATION_DEV_LINKS_ENABLED` defaults to false. `INVITATION_FRONTEND_BASE_URL` defaults to `http://localhost:5173`. A future mail-provider integration should send the one-time URL directly without logging or persisting the plaintext token; it should replace the development-link response path rather than enabling that path in production.

The public acceptance endpoint applies a per-process limit of 10 attempts per client address per minute. Deployments behind trusted proxies should configure the servlet container's remote-address handling appropriately; the limiter does not use arbitrary forwarding headers.
