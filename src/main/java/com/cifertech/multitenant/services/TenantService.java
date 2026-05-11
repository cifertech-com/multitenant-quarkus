package com.cifertech.multitenant.services;

public interface TenantService {

     void createTenant(String dataSourceName, String tenantName);
}
