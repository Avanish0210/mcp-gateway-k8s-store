package com.mcp_gateway_k8s_store.backend;

import com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "spring.security.user.password=test-only-password"
})
class BackendApplicationTests {

    @Autowired
    private WooCommerceMcpService wooCommerceMcpService;

    @Test
    void contextLoadsAndDiscoversWooCommerceTools() {
        assertTrue(wooCommerceMcpService.getAvailableTools().containsAll(List.of(
                "list_products", "get_product", "create_product", "list_orders", "get_order",
                "list_customers", "get_customer", "update_order_status")));
    }
}
