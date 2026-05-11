package com.cifertech.multitenant.resolver;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.inject.Instance;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomTenantConnectionResolverTest {

    @Mock
    Instance<AgroalDataSource> dataSources;

    @Mock
    Instance<AgroalDataSource> selectedDataSourceInstance;

    @Mock
    AgroalDataSource mockDataSource;

    @Mock
    Connection mockConnection;

    @InjectMocks
    CustomTenantConnectionResolver resolver;

    @BeforeEach
    void setUp() {
        // We use pure Mockito here instead of QuarkusTest because it's faster
        // and we want to control the Instance<AgroalDataSource> perfectly.
    }

    @Test
    void testResolveConnection_WithSchema() throws SQLException {
        // Arrange
        when(dataSources.select(any(DataSource.DataSourceLiteral.class))).thenReturn(selectedDataSourceInstance);
        when(selectedDataSourceInstance.get()).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);

        // Act
        ConnectionProvider provider = resolver.resolve("db1|tenant_schema");
        Connection conn = provider.getConnection();

        // Assert
        assertNotNull(conn);
        assertEquals(mockConnection, conn);
        verify(mockConnection, times(1)).setSchema("tenant_schema");

        // Test close
        provider.closeConnection(conn);
        verify(mockConnection, times(1)).close();

        assertTrue(provider.supportsAggressiveRelease());
        assertFalse(provider.isUnwrappableAs(Object.class));
        assertNull(provider.unwrap(Object.class));
    }

    @Test
    void testResolveConnection_WithoutSchema_DefaultsToPublic() throws SQLException {
        // Arrange
        when(dataSources.select(any(DataSource.DataSourceLiteral.class))).thenReturn(selectedDataSourceInstance);
        when(selectedDataSourceInstance.get()).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);

        // Act
        ConnectionProvider provider = resolver.resolve("db2");
        Connection conn = provider.getConnection();

        // Assert
        assertNotNull(conn);
        verify(mockConnection, times(1)).setSchema("public");
    }
}
