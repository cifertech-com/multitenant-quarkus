# Multitenant Quarkus Core Library

A Quarkus library designed to manage **Multitenant** environments with high flexibility. This library dynamically routes database connections to **Multiple Databases** and **Multiple Schemas** within each database (supporting Database-per-Tenant or Schema-per-Tenant models).

It is built on top of the Hibernate ORM, Agroal, and Flyway ecosystem for Quarkus.

---

## Key Features

1. **Dynamic Routing**: Allows routing requests to the correct database and the specific tenant schema at runtime.
2. **Automatic Tenant Creation**: Provides a service (`TenantService`) that dynamically creates new schemas in specific databases and runs Flyway migrations to provision them.
3. **Automatic Migrations on Startup**: Listens to the Quarkus startup event (`StartupEvent`) and automatically iterates through all registered tenants (schemas starting with `tenant_`) across the configured databases, applying any pending Flyway migrations.
4. **Validation and Sanitization**: Prevents injections by ensuring that schema names are kept as clean alphanumeric characters (`[a-z0-9_]`).
5. **Auto-configuration (Plug & Play)**: Generates its own Jandex index at compile time. Applications using this library will automatically recognize and inject all its beans.

---

## Installation

Add the following dependency in the `pom.xml` file of your consumer Quarkus microservice:

```xml
<dependency>
    <groupId>io.github.yacson3287</groupId>
    <artifactId>mutitenant-quarkus-ct</artifactId>
    <version>1.0-SNAPSHOT</version> <!-- Replace with the final version -->
</dependency>
```

---

## Consumer Project Configuration

For the library to work, the consumer microservice must configure the databases and specify which ones the library should observe when the project starts.

Add this to your `application.properties`:

```properties
# 1. Configure which databases the library will manage on startup (comma-separated)
cifertech.multitenant.databases=db1,db2

# 2. Configure Quarkus data sources (Agroal)
# Default DB (Required for Hibernate)
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=postgres
quarkus.datasource.password=postgres
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/default_db

# Database 1 (Named 'db1')
quarkus.datasource.db1.db-kind=postgresql
quarkus.datasource.db1.username=admin1
quarkus.datasource.db1.password=admin1
quarkus.datasource.db1.jdbc.url=jdbc:postgresql://localhost:5432/tenant_db_1

# Database 2 (Named 'db2')
quarkus.datasource.db2.db-kind=postgresql
quarkus.datasource.db2.username=admin2
quarkus.datasource.db2.password=admin2
quarkus.datasource.db2.jdbc.url=jdbc:postgresql://localhost:5432/tenant_db_2

# 3. Hibernate Multitenancy configuration
quarkus.hibernate-orm.multitenant=SCHEMA
```

### Migration Location
The library will look for tenant migrations in the default path: `src/main/resources/db/migration`. Make sure to place your `.sql` files there (e.g., `V1.0.0__Init_Tenant.sql`).

---

## Usage and Examples

### 1. Routing requests to a specific Tenant

The library uses the `MutitenantContext` class (Request Scoped) to keep track of which database and tenant schema to point to during an HTTP request. Typically, you will want to set this up in a Filter or Interceptor that reads a JWT token or an HTTP Header to extract the tenant's name.

**Example using a JAX-RS Filter:**

```java
import com.cifertech.multitenant.services.MultitenantContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TenantFilter implements ContainerRequestFilter {

    @Inject
    MultitenantContext multitenantContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Read the tenant and the DB (e.g., from a Header or Token)
        String dbName = requestContext.getHeaderString("X-Tenant-DB"); // e.g., "db1"
        String tenantName = requestContext.getHeaderString("X-Tenant-ID"); // e.g., "tenant_acme"

        // Assign the context to the current request
        if (dbName != null && tenantName != null) {
            mutitenantContext.setDataSource(dbName);
            mutitenantContext.setTenant(tenantName);
        }
    }
}
```
Once the context is set, any Hibernate ORM and Panache query will automatically execute over that specific connection and schema.

### 2. Provisioning a New Tenant

To create a new customer or tenant in your system, you can use the service provided by the library: `TenantService`. This service will create the schema and run the Flyway scripts to prepare the tables.

**Example from a REST Endpoint:**
```java
import com.cifertech.multitenant.services.TenantService;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/admin/tenants")
public class TenantController {

    @Inject
    TenantService tenantService;

    @POST
    public void createNewCustomer(NewCustomerDto request) {
        // In which database it will live ("db1" or "db2") and the tenant name ("tenant_acme")
        tenantService.createTenant(request.getDatabaseName(), "tenant_" + request.getCustomerName());
    }
}
```

---

## Internal Library Architecture

- **`MutitenantContext`**: Temporarily stores (RequestScoped) which database and schema we are currently working on per request.
- **`CustomTenantResolver`**: Extracts the information from `MutitenantContext` and informs Hibernate that the tenant identifier follows the `dbName|tenantSchema` format.
- **`CustomTenantConnectionResolver`**: Hibernate invokes this resolver when it needs a connection. The library extracts the `dbName` part, looks up the correct Agroal Data Source, gets a connection, and uses `conn.setSchema(tenantSchema)` to route the PostgreSQL context to the correct client.
- **`TenantListener`**: A startup event that uses `information_schema.schemata` to find previously created schemas and executes `flyway.migrate()` to ensure the database is always updated against new software versions.
