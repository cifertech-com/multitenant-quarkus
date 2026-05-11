package com.cifertech.multitenant.services.impl;

import com.cifertech.exceptionhandler.exceptions._5xx.InternalServerError;
import com.cifertech.multitenant.services.TenantService;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@ApplicationScoped
@Slf4j
public class TenantServiceImpl implements TenantService {

    @Inject
    @Any
    Instance<AgroalDataSource> dataSources;

    @Override
    public void createTenant(String dataSourceName, String tenantName) {
        String schemaName = sanitizeSchemaName(tenantName);
        var dataSource = getDataSource(dataSourceName);
        createSchema(dataSource, schemaName);
        runMigrations(dataSource, schemaName);
    }

    private AgroalDataSource getDataSource(String dataSource) {
        var dsInstance = dataSources.select(new DataSource.DataSourceLiteral(dataSource));
        if (dsInstance.isUnsatisfied()) {
            throw new InternalServerError("No datasource configured for: " + dataSource);
        }
        return dsInstance.get();
    }

    private void createSchema(AgroalDataSource dataSource, String schemaName) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new InternalServerError(e.getMessage());
        }
    }

    private void runMigrations(AgroalDataSource dataSource, String schemaName) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

    /**
     * Sanitizes the tenant name to be used as a PostgreSQL schema name.
     * Removes any characters that are not alphanumeric or underscores,
     * and converts to lowercase.
     */
    private String sanitizeSchemaName(String tenantName) {
        return tenantName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }


}
