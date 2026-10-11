import { z } from "zod";
import { woocommerceClient } from "../woocommerce-client.js";

export const createProductTool = {
    name: "create_product",
    description:
        "Create a new product in the WooCommerce store with a name, price, description, and stock status.",

    inputSchema: {
        name: z.string().min(1).describe("Product name"),
        regularPrice: z.string().regex(/^\d+(\.\d{1,2})?$/)
            .describe("Regular price, such as 299.99"),
        description: z.string().optional()
            .describe("Product description"),
        type: z.enum(["simple", "virtual", "downloadable"]).default("simple"),
    },

    async execute(input: {
        name: string;
        regularPrice: string;
        description?: string;
        type?: "simple" | "virtual" | "downloadable";
    }) {
        return woocommerceClient.createProduct(input);
    },
};