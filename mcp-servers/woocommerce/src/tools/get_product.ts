import { z } from "zod";
import { woocommerceClient } from "../woocommerce-client.js";

export const getProductTool = {
    name: "get_product",
    description: "Retrieve a WooCommerce product by its ID.",

    inputSchema: {
        productId: z.number().int().positive(),
    },

    async execute(input: {
        productId: number;
    }) {
        return woocommerceClient.getProduct(input.productId);
    },
};