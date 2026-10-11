import { z } from "zod";
import { woocommerceClient } from "../woocommerce-client.js";

export const getCustomerTool = {
    name: "get_customer",
    description: "Retrieve a WooCommerce customer by their ID.",

    inputSchema: {
        customerId: z.number().int().positive(),
    },

    async execute(input: {
        customerId: number;
    }) {
        return woocommerceClient.getCustomer(input.customerId);
    },
};