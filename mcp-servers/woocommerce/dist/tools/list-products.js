import { z } from "zod";
import { woocommerceClient } from "../woocommerce-client.js";
export const listProductsTool = {
    name: "list_products",
    description: "List products available in the WooCommerce store.",
    inputSchema: {
        page: z.number().int().min(1).default(1),
        perPage: z.number().int().min(1).max(100).default(10),
        search: z.string().optional(),
    },
    async execute(input) {
        return woocommerceClient.listProducts(input);
    },
};
