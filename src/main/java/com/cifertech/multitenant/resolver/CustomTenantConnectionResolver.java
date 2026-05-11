/*
 * Copyright © 2026 CiferTech (yacson.ramirez@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
