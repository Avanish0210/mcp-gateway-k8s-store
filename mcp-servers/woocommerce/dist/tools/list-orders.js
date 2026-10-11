import { z } from "zod";
import { woocommerceClient } from "../woocommerce-client.js";
export const listOrdersTool = {
    name: "list_orders",
    description: "List orders from the WooCommerce store.",
    inputSchema: {
        page: z.number().int().min(1).default(1),
        perPage: z.number().int().min(1).max(100).default(10),
        status: z.string().optional(),
    },
    async execute(input) {
        return woocommerceClient.listOrders(input);
    },
};
