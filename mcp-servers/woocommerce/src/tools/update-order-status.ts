import { z } from "zod";
import { woocommerceClient } from "../woocommerce-client.js";

const orderStatusSchema = z.enum([
    "pending",
    "processing",
    "on-hold",
    "completed",
    "cancelled",
    "refunded",
    "failed",
]);

export const updateOrderStatusTool = {
    name: "update_order_status",

    description:
        "Update the status of an existing WooCommerce order.",

    inputSchema: {
        orderId: z.number().int().positive()
            .describe("ID of the WooCommerce order"),
        status: orderStatusSchema
            .describe("New status to assign to the order"),
    },

    async execute(input: {
        orderId: number;
        status:
            | "pending"
            | "processing"
            | "on-hold"
            | "completed"
            | "cancelled"
            | "refunded"
            | "failed";
    }) {
        return woocommerceClient.updateOrderStatus(
            input.orderId,
            input.status
        );
    },
};