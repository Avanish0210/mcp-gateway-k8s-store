# Audit logging

Audit events are persisted in the existing PostgreSQL database as `audit_events`. The project currently manages schema changes with Hibernate `ddl-auto=update`; the entity supplies indexes for event time, actor/time, event type/time, server, store, and tool. No parallel Flyway migration was added.

## Recorded events

- All 16 annotated MCP tools: success and runtime failure, elapsed milliseconds, authenticated user (JWT or API key), server/tool, timestamp, and request correlation ID.
- Store create/delete: accepted or rejected API operation; retry acceptance.
- API key create/revoke and team invitation create/revoke/accept.

MCP arguments and responses are never copied into audit records. Failure descriptions contain only a sanitized exception class/category, not exception messages or stack traces. A valid `X-Request-ID` value is retained; otherwise a UUID is generated. User IDs come from the authenticated principal and the user database record.

## API

- `GET /api/audit` — `ADMIN` only; supports `actorId`, `eventType`, `outcome`, `serverId`, `toolName`, `storeId`, `from`, `to`, `page`, and `size` (1–100).
- `GET /api/audit/me` — authenticated users; actor filtering is always forced to the current principal, regardless of caller-supplied parameters. Supports the same filters except actor ID.

Events cannot be changed or deleted through the HTTP API. The dashboard provides global admin history or current-user history, filters, and pagination.

## Reliability notes

MCP tool audit writes are synchronous and flushed before the result is returned. If persistence fails, the MCP request fails rather than silently reporting an unaudited success. For a remote mutation that already completed, a subsequent database failure can still mean the external effect occurred without a stored event; this cannot be made atomic with the upstream service. Store provisioning/deletion runs asynchronously, so these audit rows describe whether the API accepted/rejected the lifecycle request. The final Kubernetes state and worker errors remain available from the managed-store status.

The project has no MCP server administration or user-role management endpoints today, so no such events are emitted. Unknown MCP tool names rejected by the protocol before an annotated tool method executes are also outside the current instrumentation point.

## Local validation (PowerShell)

```powershell
cd backend
mvn test
cd ..\dashboard
npm run build
```

After starting the backend and dashboard, sign in and open **Audit log**. An administrator sees the global log; a member sees only their own events. To query directly, use the existing bearer token:

```powershell
$token = '<JWT>'
Invoke-RestMethod 'http://localhost:8081/api/audit?page=0&size=25' -Headers @{ Authorization = "Bearer $token" }
Invoke-RestMethod 'http://localhost:8081/api/audit/me?page=0&size=25' -Headers @{ Authorization = "Bearer $token" }
```
