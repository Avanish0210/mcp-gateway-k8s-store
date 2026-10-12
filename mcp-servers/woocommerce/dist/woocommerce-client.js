import { config } from "./config.js";
import { createHmac, randomBytes } from "node:crypto";
export class WooCommerceClient {
    baseUrl;
    constructor() {
        this.baseUrl = `${config.woocommerceUrl}/wp-json/wc/v3`;
    }
    encode(value) {
        return encodeURIComponent(value).replace(/[!'()*]/g, (character) => `%${character.charCodeAt(0).toString(16).toUpperCase()}`);
    }
    async request(endpoint, options = {}) {
        const url = new URL(`${this.baseUrl}/${endpoint.replace(/^\/+/, "")}`);
        const method = (options.method ?? "GET").toUpperCase();
        const headers = new Headers(options.headers);
        headers.set("Accept", "application/json");
        if (url.protocol === "http:") {
            // OAuth 1.0a for local HTTP WooCommerce.
            const oauthParams = {
                oauth_consumer_key: config.consumerKey,
                oauth_nonce: randomBytes(16).toString("hex"),
                oauth_signature_method: "HMAC-SHA1",
                oauth_timestamp: Math.floor(Date.now() / 1000).toString(),
                oauth_version: "1.0",
            };
            const signatureParams = [];
            url.searchParams.forEach((value, key) => {
                signatureParams.push([key, value]);
            });
            for (const [key, value] of Object.entries(oauthParams)) {
                signatureParams.push([key, value]);
            }
            signatureParams.sort((a, b) => {
                const keyComparison = this.encode(a[0]).localeCompare(this.encode(b[0]));
                if (keyComparison !== 0) {
                    return keyComparison;
                }
                return this.encode(a[1]).localeCompare(this.encode(b[1]));
            });
            const normalizedParams = signatureParams
                .map(([key, value]) => `${this.encode(key)}=${this.encode(value)}`)
                .join("&");
            const signatureBaseString = [
                method,
                this.encode(`${url.origin}${url.pathname}`),
                this.encode(normalizedParams),
            ].join("&");
            const signingKey = `${this.encode(config.consumerSecret)}&`;
            oauthParams.oauth_signature = createHmac("sha1", signingKey)
                .update(signatureBaseString)
                .digest("base64");
            for (const [key, value] of Object.entries(oauthParams)) {
                url.searchParams.set(key, value);
            }
        }
        else if (url.protocol === "https:") {
            // HTTPS: use Basic Authentication.
            const credentials = Buffer.from(`${config.consumerKey}:${config.consumerSecret}`).toString("base64");
            headers.set("Authorization", `Basic ${credentials}`);
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
            throw new Error(`WooCommerce API error (${response.status}): ${responseText}`);
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
        return this.request("products", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                name: input.name,
                regular_price: input.regularPrice,
                description: input.description ?? "",
                type: input.type ?? "simple",
                status: "publish",
            }),
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
