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
package com.cifertech.multitenant.listener;

import com.cifertech.exceptionhandler.exceptions._5xx.InternalServerError;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.inject.Instance;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantListenerTest {

    @Mock
    Instance<AgroalDataSource> dataSources;

    @Mock
    Instance<AgroalDataSource> selectedDataSourceInstance;

    @Mock
    AgroalDataSource mockDataSource;

    @Mock
    Connection mockConnection;

    @Mock
    Statement mockStatement;

    @Mock
    ResultSet mockResultSet;

    @Mock
    StartupEvent mockStartupEvent;

    @InjectMocks
    TenantListener tenantListener;

    @BeforeEach
    void setUp() {
    }

    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testOnStart_Success() throws SQLException {
        // Arrange
        setPrivateField(tenantListener, "tenantDatabases", Arrays.asList("db1"));

        when(dataSources.select(any(DataSource.DataSourceLiteral.class))).thenReturn(selectedDataSourceInstance);
        when(selectedDataSourceInstance.isUnsatisfied()).thenReturn(false);
        when(selectedDataSourceInstance.get()).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);

        // Simulate returning two schemas: 'tenant_a' and 'tenant_b'
        when(mockResultSet.next()).thenReturn(true, true, false);
        when(mockResultSet.getString("schema_name")).thenReturn("tenant_a", "tenant_b");

        // Mock static Flyway
        try (MockedStatic<Flyway> flywayMockedStatic = mockStatic(Flyway.class)) {
            FluentConfiguration mockFluentConfig = mock(FluentConfiguration.class);
            Flyway mockFlyway = mock(Flyway.class);

            flywayMockedStatic.when(Flyway::configure).thenReturn(mockFluentConfig);
            when(mockFluentConfig.dataSource(mockDataSource)).thenReturn(mockFluentConfig);
            // It should be called for both schemas
            when(mockFluentConfig.schemas(anyString())).thenReturn(mockFluentConfig);
            when(mockFluentConfig.locations("classpath:db/migration")).thenReturn(mockFluentConfig);
            when(mockFluentConfig.load()).thenReturn(mockFlyway);

            // Act
            tenantListener.onStart(mockStartupEvent);

            // Assert
            verify(mockFluentConfig).schemas("tenant_a");
            verify(mockFluentConfig).schemas("tenant_b");
            verify(mockFlyway, times(2)).migrate();
        }
    }

    @Test
    void testOnStart_NoDatabasesConfigured() {
        // Arrange
        setPrivateField(tenantListener, "tenantDatabases", Collections.emptyList());

        // Act & Assert
        assertThrows(InternalServerError.class, () -> {
            tenantListener.onStart(mockStartupEvent);
        });
    }

    @Test
    void testOnStart_SQLExceptionWhenFetchingSchemas() throws SQLException {
        // Arrange
        setPrivateField(tenantListener, "tenantDatabases", Arrays.asList("db1"));

        when(dataSources.select(any(DataSource.DataSourceLiteral.class))).thenReturn(selectedDataSourceInstance);
        when(selectedDataSourceInstance.isUnsatisfied()).thenReturn(false);
        when(selectedDataSourceInstance.get()).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenThrow(new SQLException("Query Failed"));

        // Act & Assert
        // In the listener, the exception is caught and logged, or rethrown?
        // Let's check TenantListener.java code. 
        // Inside getAllTenantSchemas it catches SQLException and throws InternalServerError.
        // Inside migrateAllTenantsInAllDatabases, the whole block is inside a try-catch Exception and logged, so it does not propagate!
        // Wait, if it doesn't propagate, we just verify it doesn't throw out of onStart.
        tenantListener.onStart(mockStartupEvent);
        
        // Ensure no exception is thrown out of onStart since it is caught in the loop
        verify(mockStatement, times(1)).executeQuery(anyString());
    }
}
