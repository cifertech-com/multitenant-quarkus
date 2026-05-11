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
package com.cifertech.multitenant.services.impl;

import com.cifertech.exceptionhandler.exceptions._5xx.InternalServerError;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
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
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

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

    @InjectMocks
    TenantServiceImpl tenantService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testCreateTenant_Success() throws SQLException {
        // Arrange
        when(dataSources.select(any(DataSource.DataSourceLiteral.class))).thenReturn(selectedDataSourceInstance);
        when(selectedDataSourceInstance.isUnsatisfied()).thenReturn(false);
        when(selectedDataSourceInstance.get()).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        // Mock static Flyway methods
        try (MockedStatic<Flyway> flywayMockedStatic = mockStatic(Flyway.class)) {
            FluentConfiguration mockFluentConfig = mock(FluentConfiguration.class);
            Flyway mockFlyway = mock(Flyway.class);

            flywayMockedStatic.when(Flyway::configure).thenReturn(mockFluentConfig);
            when(mockFluentConfig.dataSource(mockDataSource)).thenReturn(mockFluentConfig);
            when(mockFluentConfig.schemas("my_company_")).thenReturn(mockFluentConfig);
            when(mockFluentConfig.locations("classpath:db/migration")).thenReturn(mockFluentConfig);
            when(mockFluentConfig.load()).thenReturn(mockFlyway);

            // Act
            tenantService.createTenant("db_main", "My Company!");

            // Assert
            verify(mockStatement, times(1)).execute("CREATE SCHEMA IF NOT EXISTS \"my_company_\"");
            verify(mockFlyway, times(1)).migrate();
        }
    }

    @Test
    void testCreateTenant_MissingDataSource() {
        // Arrange
        when(dataSources.select(any(DataSource.DataSourceLiteral.class))).thenReturn(selectedDataSourceInstance);
        when(selectedDataSourceInstance.isUnsatisfied()).thenReturn(true);

        // Act & Assert
        assertThrows(InternalServerError.class, () -> {
            tenantService.createTenant("db_invalid", "TenantA");
        });
    }

    @Test
    void testCreateTenant_SQLExceptionWhenCreatingSchema() throws SQLException {
        // Arrange
        when(dataSources.select(any(DataSource.DataSourceLiteral.class))).thenReturn(selectedDataSourceInstance);
        when(selectedDataSourceInstance.isUnsatisfied()).thenReturn(false);
        when(selectedDataSourceInstance.get()).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute(anyString())).thenThrow(new SQLException("DB Error"));

        // Act & Assert
        assertThrows(InternalServerError.class, () -> {
            tenantService.createTenant("db_main", "TenantA");
        });
    }
}
