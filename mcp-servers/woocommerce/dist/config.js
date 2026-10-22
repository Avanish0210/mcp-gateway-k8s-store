///This should load your store URL and API credentials.
import process from "node:process";
import { fileURLToPath } from "node:url";
import { config as loadEnv } from "dotenv";
// stdout belongs exclusively to MCP JSON-RPC; dotenv banners corrupt stdio.
loadEnv({ path: fileURLToPath(new URL("../.env", import.meta.url)), quiet: true });
function getRequiredEnv(name) {
    const value = process.env[name];
    if (!value) {
        throw new Error(`Missing required environment variable: ${name}`);
    }
    return value;
}
const rawUrl = getRequiredEnv("WOOCOMMERCE_URL");
export const config = {
    woocommerceUrl: rawUrl.replace(/\/+$/, ""),
    consumerKey: getRequiredEnv("WOOCOMMERCE_CONSUMER_KEY"),
    consumerSecret: getRequiredEnv("WOOCOMMERCE_CONSUMER_SECRET"),
};
