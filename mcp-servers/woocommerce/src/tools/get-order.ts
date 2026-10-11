import { z } from "zod";
import { woocommerceClient } from "../woocommerce-client.js";

export const getOrderTool = {
    name: "get_order",
    description: "Retrieve a WooCommerce order by its ID.",

    inputSchema: {
        orderId: z.number().int().positive(),
    },

    async execute(input: {
        orderId: number;
    }) {
        return woocommerceClient.getOrder(input.orderId);
    },
};