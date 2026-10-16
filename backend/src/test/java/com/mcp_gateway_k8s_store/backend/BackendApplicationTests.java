package com.mcp_gateway_k8s_store.backend;

import com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "spring.security.user.password=test-only-password",
        "spring.ai.mcp.client.enabled=false",
        "stores.provisioning.enabled=false"
})
class BackendApplicationTests {

    @Autowired
    private WooCommerceMcpService wooCommerceMcpService;

    @Test
    void contextLoadsWithoutRequiringExternalMcpServers() {
        assertNotNull(wooCommerceMcpService);
    }
}
