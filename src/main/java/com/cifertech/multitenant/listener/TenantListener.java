package com.cifertech.multitenant.listener;

import com.cifertech.exceptionhandler.exceptions._5xx.InternalServerError;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Slf4j
public class TenantListener {

    @Inject
    @Any
    Instance<AgroalDataSource> dataSources;

    @ConfigProperty(name = "cifertech.multitenant.databases")
    List<String> tenantDatabases;


    /**
     * Listener that automatically runs migrations on all existing tenant schemas
     * across all configured databases when the Quarkus application starts up.
     */
    void onStart(@Observes StartupEvent ev) {
        migrateAllTenantsInAllDatabases();
    }

    private void migrateAllTenantsInAllDatabases() {
        if (tenantDatabases == null || tenantDatabases.isEmpty()) {
            throw new InternalServerError("No databases configured");
        }
        for (String dbName : tenantDatabases) {
            log.info("Checking schemas for database: " + dbName);
            try {
                AgroalDataSource dataSource = getDataSource(dbName);
                List<String> schemas = getAllTenantSchemas(dataSource);
                for (String schema : schemas) {
                    log.info("Running migrations for schema: {} in db: {}", schema, dbName);
                    runMigrations(dataSource, schema);
                }
            } catch (Exception e) {
                log.error("Failed to migrate tenants for db: {}", dbName, e);
            }
        }
    }

    private AgroalDataSource getDataSource(String dataSource) {
        var dsInstance = dataSources.select(new DataSource.DataSourceLiteral(dataSource));
        if (dsInstance.isUnsatisfied()) {
            throw new InternalServerError("No datasource configured for: " + dataSource);
        }
        return dsInstance.get();
    }

    private List<String> getAllTenantSchemas(AgroalDataSource dataSource) {
        List<String> schemas = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%'")) {
            while (rs.next()) {
                schemas.add(rs.getString("schema_name"));
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new InternalServerError();
        }
        return schemas;
    }

    private void runMigrations(AgroalDataSource dataSource, String schemaName) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

}
