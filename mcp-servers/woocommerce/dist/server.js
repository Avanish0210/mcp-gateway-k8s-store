import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { listProductsTool } from "./tools/list-products.js";
import { getProductTool } from "./tools/get_product.js";
import { listOrdersTool } from "./tools/list-orders.js";
import { getOrderTool } from "./tools/get-order.js";
import { listCustomersTool } from "./tools/list_customers.js";
import { getCustomerTool } from "./tools/get_customer.js";
import { createProductTool } from "./tools/create-product.js";
import { updateOrderStatusTool } from "./tools/update-order-status.js";
const server = new McpServer({
    name: "woocommerce-mcp-server",
    version: "1.0.0",
});
// Register an MCP tool using its metadata and execution function.
function registerTool(tool) {
    server.registerTool(tool.name, {
        description: tool.description,
        inputSchema: tool.inputSchema,
    }, async (input) => {
        try {
            const result = await tool.execute(input);
            return {
                content: [
                    {
                        type: "text",
                        text: JSON.stringify(result, null, 2),
                    },
                ],
            };
        }
        catch (error) {
            return {
                isError: true,
                content: [
                    {
                        type: "text",
                        text: error instanceof Error
                            ? error.message
                            : "An unexpected error occurred.",
                    },
                ],
            };
        }
    });
}
registerTool(listProductsTool);
registerTool(getProductTool);
registerTool(listOrdersTool);
registerTool(getOrderTool);
registerTool(listCustomersTool);
registerTool(getCustomerTool);
registerTool(listProductsTool);
registerTool(getProductTool);
registerTool(createProductTool);
registerTool(updateOrderStatusTool);
async function main() {
    const transport = new StdioServerTransport();
    await server.connect(transport);
    console.error("WooCommerce MCP server started.");
}
main().catch((error) => {
    console.error("Failed to start WooCommerce MCP server:", error);
    process.exitCode = 1;
});
