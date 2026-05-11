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
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@PersistenceUnitExtension
@RequestScoped
public class CustomTenantResolver implements TenantResolver {

    private static final String DEFAULT_TENANT = "default_db";
    private static final String DEFAULT_TENANT_ID = DEFAULT_TENANT + "|public";

    @Inject
    private MultitenantContext multitenantContext;

    @Override
    public String getDefaultTenantId() {
        return DEFAULT_TENANT_ID;
    }

    @Override
    public String resolveTenantId() {
        String dbName = multitenantContext.getDataSource();
        if (dbName == null || dbName.isBlank()) {
            throw new InternalServerError("No datasource configured");
        } else {
            dbName = dbName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        }

        String tenant = multitenantContext.getTenant();
        if (tenant == null || tenant.isBlank()) {
            throw new InternalServerError("No tenant configured");
        } else {
            tenant = tenant.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        }

        return dbName + "|" + tenant;
    }
}
