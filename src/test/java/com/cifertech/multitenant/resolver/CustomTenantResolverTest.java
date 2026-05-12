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

import com.cifertech.exceptionhandler.exceptions._5xx.InternalServerError;
import com.cifertech.multitenant.services.MultitenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomTenantResolverTest {

    @Mock
    MultitenantContext multitenantContext;

    @InjectMocks
    CustomTenantResolver customTenantResolver;

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
    void testGetDefaultTenantId() {
        setPrivateField(customTenantResolver, "tenantDatabases", java.util.List.of("default_db"));
        String defaultTenant = customTenantResolver.getDefaultTenantId();
        assertEquals("default_db|public", defaultTenant);
    }

    @Test
    void testResolveTenantId_Success() {
        when(multitenantContext.getDataSource()).thenReturn("my-db_1!");
        when(multitenantContext.getTenant()).thenReturn("My-Tenant_A");

        String tenantId = customTenantResolver.resolveTenantId();

        // The method lowercases and replaces non [a-z0-9_] with _
        assertEquals("my_db_1_|my_tenant_a", tenantId);
    }

    @Test
    void testResolveTenantId_NullDataSource() {
        when(multitenantContext.getDataSource()).thenReturn(null);

        assertThrows(InternalServerError.class, () -> {
            customTenantResolver.resolveTenantId();
        });
    }

    @Test
    void testResolveTenantId_BlankDataSource() {
        when(multitenantContext.getDataSource()).thenReturn("   ");

        assertThrows(InternalServerError.class, () -> {
            customTenantResolver.resolveTenantId();
        });
    }

    @Test
    void testResolveTenantId_NullTenant() {
        when(multitenantContext.getDataSource()).thenReturn("valid_db");
        when(multitenantContext.getTenant()).thenReturn(null);

        assertThrows(InternalServerError.class, () -> {
            customTenantResolver.resolveTenantId();
        });
    }

    @Test
    void testResolveTenantId_BlankTenant() {
        when(multitenantContext.getDataSource()).thenReturn("valid_db");
        when(multitenantContext.getTenant()).thenReturn("   ");

        assertThrows(InternalServerError.class, () -> {
            customTenantResolver.resolveTenantId();
        });
    }
}
