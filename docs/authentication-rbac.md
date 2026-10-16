# User authentication and roles

## Local configuration

From the repository root, create a private environment file and edit its placeholders:

```powershell
Copy-Item .env.example .env
notepad .env
```

Set `POSTGRES_PASSWORD` to the password for the existing PostgreSQL volume (or use a new unique value for a fresh volume). Generate the JWT secret in PowerShell with:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Put the generated value in `JWT_SECRET`. Set a private `INITIAL_ADMIN_USERNAME` and `INITIAL_ADMIN_PASSWORD` (8–72 UTF-8 bytes). `INITIAL_MEMBER_USERNAME` and `INITIAL_MEMBER_PASSWORD` are optional; set both or leave both blank. Keep `.env` private and out of source control.

Build and start the local stack:

```powershell
docker compose -f backend/compose.yaml up -d --build
```

The backend creates each configured initial account only if that username does not already exist. It does not reset existing passwords or roles on restart. Hibernate creates the `app_users` table in the configured existing PostgreSQL database. For direct local Spring Boot runs, provide the same `JWT_SECRET`, `INITIAL_ADMIN_*`, and database variables in the process environment.

The React dashboard signs in through `POST /api/auth/login`, keeps the access token in memory, and sends it as a Bearer token. Access tokens use HS256, default to 15 minutes, and are not refreshed automatically; sign in again when one expires.

## Accounts and authorization

Users live in `app_users`: username, BCrypt password hash, `ADMIN` or `MEMBER` role, enabled flag, and creation timestamp. The login and profile response DTOs never serialize the password hash. There is no public registration or role-changing endpoint. The optional initial member is provisioned only by environment configuration; changing a user's role or enabled flag is an operator/database administration action.

| Access | Endpoints |
| --- | --- |
| Public | `POST /api/auth/login`, `GET /api/health`, `GET /actuator/health` |
| Any authenticated role | `GET /api/auth/me`; read-only WooCommerce, GitHub, and Weather MCP status, discovery, search, and data endpoints |
| `ADMIN` only | All `/api/stores/**` Kubernetes/store lifecycle endpoints; `POST /api/github/tools/**`; `POST /api/woocommerce/products`; `PUT /api/woocommerce/orders/**` |

Missing, invalid, or expired bearer tokens receive HTTP 401. Authenticated users without the required role receive HTTP 403. JWT requests reload the account from PostgreSQL, so a disabled account is rejected even if its token has not expired.

## Required environment variables

- `JWT_SECRET`: Base64-encoded random secret of at least 32 bytes.
- `INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_PASSWORD`: required by the Docker Compose setup for first account bootstrap.
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`: database connection and volume credentials.
- `GITHUB_PERSONAL_ACCESS_TOKEN`: existing GitHub MCP configuration required by Compose.
- `JWT_EXPIRATION_SECONDS`: optional, defaults to 900 (allowed range 60–86400).
- `INITIAL_MEMBER_USERNAME`, `INITIAL_MEMBER_PASSWORD`: optional one-time member bootstrap pair.
