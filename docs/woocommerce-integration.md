# WooCommerce integration

## Request flow

The dashboard calls Spring Boot on port 8081. Spring AI's synchronous MCP client launches `mcp-servers/woocommerce/dist/server.js` with STDIO. The existing TypeScript tools make the WooCommerce REST API calls; Java does not call the WooCommerce API directly.

## Local setup

Build the MCP server and backend from the repository root:

```powershell
cd mcp-servers/woocommerce
npm ci
npm run typecheck
npm run build

cd ../../backend
mvn clean compile
```

The MCP entry path defaults to `../mcp-servers/woocommerce/dist/server.js`, relative to the backend working directory. Override it with `MCP_SERVER_PATH` if the backend is launched from another directory. Override the Node executable with `MCP_NODE_COMMAND`.

Set `WOOCOMMERCE_URL`, `WOOCOMMERCE_CONSUMER_KEY`, and `WOOCOMMERCE_CONSUMER_SECRET` for the MCP process, or keep them in `mcp-servers/woocommerce/.env` for local development. Do not place these values in React environment variables or commit them. Configure the backend database with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; the checked-in configuration no longer contains a database password. `DASHBOARD_ALLOWED_ORIGIN` defaults to `http://localhost:5173`.

Start with `mvn spring-boot:run` from `backend`. Spring Security uses HTTP Basic; for local development the default username is `user` and Spring prints a generated password at startup. Set `SPRING_SECURITY_USER_NAME` and `SPRING_SECURITY_USER_PASSWORD` from a secret in deployed environments.

The dashboard reads `VITE_API_BASE_URL`, defaulting to `http://localhost:8081`. Copy `dashboard/.env.example` to `dashboard/.env.local` and adjust the URL when needed. Enter backend Basic credentials in the dashboard; they remain in page memory and are sent only to Spring Boot.

## API routes

All routes except `GET /api/health` require backend authentication.

| Method | Route | MCP tool |
|---|---|---|
| GET | `/api/woocommerce/tools` | Server tool discovery |
| GET | `/api/woocommerce/status` | MCP client status |
| GET | `/api/woocommerce/products?page=1&perPage=10&search=` | `list_products` |
| GET | `/api/woocommerce/products/{productId}` | `get_product` |
| POST | `/api/woocommerce/products` | `create_product` |
| GET | `/api/woocommerce/orders?page=1&perPage=10&status=` | `list_orders` |
| GET | `/api/woocommerce/orders/{orderId}` | `get_order` |
| PUT | `/api/woocommerce/orders/{orderId}/status` | `update_order_status` |
| GET | `/api/woocommerce/customers?page=1&perPage=10` | `list_customers` |
| GET | `/api/woocommerce/customers/{customerId}` | `get_customer` |

Product creation accepts `name`, `regularPrice`, `description`, and `type` (`simple`, `virtual`, or `downloadable`). Order status updates accept `pending`, `processing`, `on-hold`, `completed`, `cancelled`, `refunded`, or `failed`. List sizes are limited to 100 per page. MCP failures are returned as HTTP 502 Problem Details.

`GET /api/health` reports Spring's application status and MCP process initialization separately. `GET /api/woocommerce/status` exposes the configured and initialized MCP client counts. Neither endpoint checks whether WordPress is answering; a read-only product request is required to verify WooCommerce itself.

## Kubernetes transport

The current TypeScript server only starts with STDIO. Local Spring Boot launches it as a child process. Kubernetes does not turn STDIO into a network service: either deploy Spring Boot and Node in one pod with a deliberately managed process lifecycle, or add the MCP SDK's Streamable HTTP transport and point Spring AI at that internal service. Keep any MCP HTTP service cluster-internal and protect it with service-level access controls. Inject WooCommerce credentials from Kubernetes Secrets into the MCP process environment, and inject database and Spring Security credentials from Secrets as well.

The current Helm chart files and the WooCommerce Dockerfile are empty placeholders, and no backend Kubernetes deployment manifest currently configures this connection. A production chart, image, health probes, secret references, and independent MCP HTTP transport remain deployment work; no Kubernetes resources were changed as part of the local integration.
