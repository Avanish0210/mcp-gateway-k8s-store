///This should handle communication with the WooCommerce REST API.
import { config } from "./config.js";
import { createHmac, randomBytes } from "node:crypto";
export class WooCommerceClient {
    baseUrl;
    constructor() {
        this.baseUrl = `${config.woocommerceUrl}/wp-json/wc/v3`;
    }
    async request(endpoint, options = {}) {
        const url = new URL(`${this.baseUrl}/${endpoint.replace(/^\/+/, "")}`);
        const method = (options.method ?? "GET").toUpperCase();
        const headers = new Headers(options.headers);
        headers.set("Accept", "application/json");
        if (url.protocol === "https:") {
            // HTTPS: use HTTP Basic Authentication.
            const credentials = Buffer.from(`${config.consumerKey}:${config.consumerSecret}`).toString("base64");
            headers.set("Authorization", `Basic ${credentials}`);
        }
        else if (url.protocol === "http:") {
            // HTTP: use WooCommerce OAuth 1.0a authentication.
            const oauthParams = {
                oauth_consumer_key: config.consumerKey,
                oauth_nonce: randomBytes(16).toString("hex"),
                oauth_signature_method: "HMAC-SHA1",
                oauth_timestamp: Math.floor(Date.now() / 1000).toString(),
            };
            // RFC 3986-compatible percent encoding.
            const encode = (value) => encodeURIComponent(value).replace(/[!'()*]/g, (character) => `%${character
                .charCodeAt(0)
                .toString(16)
                .toUpperCase()}`);
            // Include existing endpoint query parameters in the signature.
            const signatureParams = [];
            url.searchParams.forEach((value, key) => {
                signatureParams.push([key, value]);
            });
            for (const [key, value] of Object.entries(oauthParams)) {
                signatureParams.push([key, value]);
            }
            // Encode and sort parameters by encoded key, then encoded value.
            signatureParams.sort((a, b) => {
                const keyA = encode(a[0]);
                const keyB = encode(b[0]);
                if (keyA !== keyB) {
                    return keyA < keyB ? -1 : 1;
                }
                const valueA = encode(a[1]);
                const valueB = encode(b[1]);
                return valueA === valueB ? 0 : valueA < valueB ? -1 : 1;
            });
            const normalizedParams = signatureParams
                .map(([key, value]) => `${encode(key)}=${encode(value)}`)
                .join("&");
            // The query string is excluded from the base URL.
            const baseUrl = `${url.origin}${url.pathname}`;
            const signatureBaseString = [
                method,
                encode(baseUrl),
                encode(normalizedParams),
            ].join("&");
            const signingKey = `${encode(config.consumerSecret)}&`;
            oauthParams.oauth_signature = createHmac("sha1", signingKey)
                .update(signatureBaseString)
                .digest("base64");
            // Add OAuth parameters to the request URL.
            for (const [key, value] of Object.entries(oauthParams)) {
                url.searchParams.set(key, value);
            }
        }
        else {
            throw new Error(`Unsupported WooCommerce URL protocol: ${url.protocol}`);
        }
        const response = await fetch(url, {
            ...options,
            method,
            headers,
        });
        const responseText = await response.text();
        if (!response.ok) {
            let errorDetails = responseText;
            try {
                const errorBody = JSON.parse(responseText);
                if (errorBody.code === "woocommerce_rest_cannot_view") {
                    errorDetails +=
                        " Check the API key permissions and the capabilities " +
                            "of the WordPress user associated with that key.";
                }
            }
            catch {
                // Preserve the original response if it is not JSON.
            }
            throw new Error(`WooCommerce API error (${response.status}): ${errorDetails}`);
        }
        if (!responseText) {
            return undefined;
        }
        return JSON.parse(responseText);
    }
    async listProducts(params = {}) {
        const query = new URLSearchParams();
        query.set("page", String(params.page ?? 1));
        query.set("per_page", String(params.perPage ?? 10));
        if (params.search) {
            query.set("search", params.search);
        }
        return this.request(`products?${query.toString()}`);
    }
    async getProduct(productId) {
        return this.request(`products/${productId}`);
    }
    async createProduct(input) {
        const body = {
            name: input.name,
            regular_price: input.regularPrice,
            description: input.description ?? "",
            type: input.type ?? "simple",
            status: "publish",
        };
        return this.request("products", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(body),
        });
    }
    async listOrders(params = {}) {
        const query = new URLSearchParams();
        query.set("page", String(params.page ?? 1));
        query.set("per_page", String(params.perPage ?? 10));
        if (params.status) {
            query.set("status", params.status);
        }
        return this.request(`orders?${query.toString()}`);
    }
    async getOrder(orderId) {
        return this.request(`orders/${orderId}`);
    }
    async updateOrderStatus(orderId, status) {
        return this.request(`orders/${orderId}`, {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({ status }),
        });
    }
    async listCustomers(params = {}) {
        const query = new URLSearchParams();
        query.set("page", String(params.page ?? 1));
        query.set("per_page", String(params.perPage ?? 10));
        return this.request(`customers?${query.toString()}`);
    }
    async getCustomer(customerId) {
        return this.request(`customers/${customerId}`);
    }
}
export const woocommerceClient = new WooCommerceClient();
