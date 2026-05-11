package com.cifertech.multitenant.resolver;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.hibernate.orm.runtime.tenant.TenantConnectionResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;

@ApplicationScoped
public class CustomTenantConnectionResolver implements TenantConnectionResolver {

    @Inject
    @Any
    Instance<AgroalDataSource> dataSources;

    @Override
    public ConnectionProvider resolve(String tenantId) {
        String[] parts = tenantId.split("\\|");
        String dbName = parts[0];
        String schemaName = parts.length > 1 ? parts[1] : "public";

        // Select the specific DataSource based on the requested dbName
        AgroalDataSource dataSource = dataSources.select(new DataSource.DataSourceLiteral(dbName)).get();

        return new ConnectionProvider() {
            @Override
            public Connection getConnection() throws SQLException {
                Connection conn = dataSource.getConnection();
                conn.setSchema(schemaName); // Route to specific tenant schema within that DB
                return conn;
            }

            @Override
            public void closeConnection(Connection conn) throws SQLException {
                conn.close();
            }

            @Override
            public boolean supportsAggressiveRelease() {
                return true;
            }

            @Override
            public boolean isUnwrappableAs(Class unwrapType) {
                return false;
            }

            @Override
            public <T> T unwrap(Class<T> unwrapType) {
                return null;
            }
        };
    }
}
