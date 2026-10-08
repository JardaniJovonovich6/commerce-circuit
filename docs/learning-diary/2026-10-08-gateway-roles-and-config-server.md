# Learning record — 8 October 2026

This records today's learning from the conversation and the current project source. OpenFeign was already implemented before today; it is revisited in the companion guide, not counted as a new implementation today.

## 1. Custom Gateway filter factories

I built `RequiredRoleGatewayFilterFactory` using:

```java
AbstractGatewayFilterFactory<RequiredRoleGatewayFilterFactory.Config>
```

The generic type says which configuration object `apply(Config)` receives. `super(Config.class)` provides that type at runtime so Gateway can bind route arguments to it.

```yaml
- name: RequiredRole
  args:
    requiredRole: ADMIN
```

`Config.requiredRole`, its getter, and its setter receive the required permission for that particular route. `static` lets the nested configuration class exist without an enclosing factory instance.

Memory cue: **the factory creates a filter; Config supplies that route's settings.**

Source: [RequiredRoleGatewayFilterFactory.java](../../api-gateway/api-gateway/src/main/java/com/aks/ecommerce/api_gateway/filters/RequiredRoleGatewayFilterFactory.java).

## 2. Authorization header and JWT roles

The request carries `Authorization: Bearer <token>`. The string `Bearer ` is seven characters long, so `substring(7)` extracts the token after checking the prefix.

Roles are in the token payload, not at character index seven:

```json
{"sub":"123","roles":["admin","client","freeUser"]}
```

`JwtService` verifies/parses signed claims and reads:

```java
claims.get("roles", List.class);
```

The first tokens had an `admin` boolean but no `roles` key, so the method returned `null`. After adding a roles array to the test token, the logs showed `[admin, client, freeUser]` and a Gateway response of `200 OK`.

`requiredRole: ADMIN` describes route policy. It does not modify the token or grant a role.

The current comparison lowercases the configured role, so `ADMIN` becomes `admin`. Token role values remain unchanged: consistently naming roles still matters.

Memory cue: **token roles say what the user has; requiredRole says what the route needs.**

Source: [JwtService.java](../../api-gateway/api-gateway/src/main/java/com/aks/ecommerce/api_gateway/filters/service/JwtService.java).

## 3. Authentication versus authorization

- Authentication: verify the token and derive the user ID.
- Authorization: check whether that user has the required role.
- `401`: authentication is missing or invalid.
- `403`: authenticated user lacks permission.

The existing authentication filter forwards the JWT subject as `X-User-Id` in a mutated exchange. Order's hello endpoint reads it with `@RequestHeader`.

I added `/orders/adminPanel` and `/orders/devPanel` as learning endpoints. Their current controller methods return text; they do not perform their own permission checks.

### Current gap to revisit

The role filter's denial branch sets `403` and then calls `chain.filter(exchange)`. The remaining filters and downstream work can still execute. The intended terminal branch is:

```java
exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
return exchange.getResponse().setComplete();
```

This is guidance, not a source-code correction made by this documentation task. Before the comparison, also handle missing/malformed headers, absent roles, missing route configuration, and JWT parsing failures. Remove full bearer-token logging.

Memory cue: **continue with chain.filter; reject with setComplete.**

## 4. Route-specific and global filters

`OrdersLoggingFilter` runs when the selected route lists it under `filters`. Its class name does not automatically restrict it to Order requests.

`GlobalLoggingFilter` participates in all matched Gateway routes. A request with no matching route does not enter the Gateway route-filter chain.

For normal authentication followed by role checking, attach filters in this order, unless explicit filter ordering overrides it:

```yaml
filters:
  - name: OrdersLoggingFilter
  - name: Authentication
    args:
      enable: true
  - name: RequiredRole
    args:
      requiredRole: ADMIN
```

A role filter on `/api/v1/orders/**` affects every matching Order endpoint. Restricting only an admin panel needs a separate, more specific route.

## 5. Environment variables through IntelliJ

Spring could not create `JwtService` when `JWT_SECRET_KEY` was missing. I used the IntelliJ Run Configuration to load the local `.env` file into the process environment.

```text
Local .env -> IntelliJ -> process environment -> Spring placeholders
```

This approach needs no dotenv dependency. Each application's launch configuration must provide the variables it needs. The local `.env` remains ignored by Git.

## 6. Git-backed Config Server

I created a separate configuration repository and a `config-server` Spring Boot application with `@EnableConfigServer`.

Its current local settings are:

- Port `8888`.
- Git branch `main`.
- Git URI/username/password supplied through environment placeholders.
- Eureka registration using `localhost:8761`.

In the configuration Git repository, `application.yml` is shared configuration. Files named `order-service.yml`, `inventory-service.yml`, and `api-gateway.yml` hold client-specific settings.

Memory cue: **Config Server supplies settings; Eureka supplies service addresses.**

## 7. Config Client migration

Order and Inventory now contain only their application name and remote config import locally. Gateway has subsequently been configured the same way:

```yaml
spring:
  application:
    name: inventory-service
  config:
    import: configserver:http://localhost:8888
```

All three have `spring-cloud-starter-config` in their POM. Without `optional:`, unavailable remote configuration prevents normal startup.

I reported Order migration working. Config Server logs proved it could fetch `main` from Git for Inventory, but could not parse the YAML.

The Inventory error was `found duplicate key defaultZone`, around lines 29–31. The solution is to keep one `defaultZone` per mapping, commit/push the remote file, and retry `/inventory-service/default/main`. Trying `master` afterwards was fallback behavior; creating a `master` branch would not fix the duplicate key.

Remote configuration secrets can stay as placeholders such as `${INVENTORY_DB_PASSWORD}`. Inventory's own environment provides the real value when consuming the configuration. Config Server Git credentials do not automatically become client secrets.

## 8. Evidence and next practice

| Item | Evidence/status |
| --- | --- |
| JWT roles extraction | Request logs showed `[admin, client, freeUser]`. |
| Config Server Git access | Logs showed a successful fetch of `main`. |
| Config Server + Order/Inventory migration | Local commit `67fae0f` contains the module and client migration. |
| Config Server packaging | Passed in the earlier commit task; not rerun for documentation. |
| Gateway Config Client | Present in the current POM and local YAML. |
| Inventory remote YAML correction | Diagnosed; final remote contents were not inspected here. |
| Role rejection prevents downstream work | Not established: current denial branch continues the chain. |

Next practice: stop denied requests, test missing/non-admin/admin roles, and confirm configuration responses and service startup after each migration. OpenFeign's existing Inventory-to-Order hello experiment needs attention because the target endpoint now requires `X-User-Id`.

The project remains a learning exercise. The documentation does not claim complete authorization or verified remote configuration contents.

Continue with the [reusable build-and-connect guide](../guides/microservices-build-and-connect.md).
