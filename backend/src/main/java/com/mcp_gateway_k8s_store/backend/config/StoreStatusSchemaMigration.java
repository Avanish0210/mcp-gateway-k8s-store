package com.mcp_gateway_k8s_store.backend.config;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Hibernate schema update does not extend existing PostgreSQL enum check constraints. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StoreStatusSchemaMigration implements ApplicationRunner {
    private final org.springframework.beans.factory.ObjectProvider<DataSource> dataSources;
    public StoreStatusSchemaMigration(org.springframework.beans.factory.ObjectProvider<DataSource> dataSources) { this.dataSources = dataSources; }

    @Override
    public void run(ApplicationArguments args) {
        DataSource source = dataSources.getIfAvailable();
        if (source == null) return;
        JdbcTemplate jdbc = new JdbcTemplate(source);
        Boolean postgres = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().equals("PostgreSQL"));
        if (!Boolean.TRUE.equals(postgres)) return;
        String definition = jdbc.query("select pg_get_constraintdef(oid) from pg_constraint where conname = 'managed_stores_status_check' and conrelid = 'managed_stores'::regclass",
                result -> result.next() ? result.getString(1) : "");
        if (definition.contains("RECOVERING")) return;
        new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source)).executeWithoutResult(transaction -> {
            jdbc.execute("alter table managed_stores drop constraint if exists managed_stores_status_check");
            jdbc.execute("alter table managed_stores add constraint managed_stores_status_check check (status in ('PROVISIONING','READY','RECOVERING','FAILED','DELETING'))");
        });
    }
}
