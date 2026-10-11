import { z } from "zod";
import { woocommerceClient } from "../woocommerce-client.js";

export const listCustomersTool = {
    name: "list_customers",
    description: "List customers registered in WooCommerce.",

    inputSchema: {
        page: z.number().int().min(1).default(1),
        perPage: z.number().int().min(1).max(100).default(10),
    },

    async execute(input: {
        page?: number;
        perPage?: number;
    }) {
        return woocommerceClient.listCustomers(input);
    },
};