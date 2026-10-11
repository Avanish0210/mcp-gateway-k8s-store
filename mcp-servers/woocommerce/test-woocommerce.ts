import { WooCommerceClient } from "./src/woocommerce-client.js";

const client = new WooCommerceClient();

try {
    const products = (await client.listProducts({
        page: 1,
        perPage: 5,
    })) as Array<{ id: number; name: string }>;

    console.log("WooCommerce API connection successful!");
    console.log("Products returned:", products.length);

    console.log(
        products.map((product) => ({
            id: product.id,
            name: product.name,
        }))
    );
} catch (error) {
    console.error(
        "WooCommerce API test failed:",
        error instanceof Error ? error.message : error
    );

    process.exitCode = 1;
}