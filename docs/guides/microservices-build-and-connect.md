# Microservices build-and-connect guide

A reusable reference based on this ECommerce learning project. Work through one section at a time: build the endpoint, test it directly, connect the caller, then add Gateway/configuration behavior.

Examples marked **recipe** are instructions for future changes, not claims that those exact changes are already in the source. Never copy real secrets into this document.

## 1. Remember what each component does

| Component | Question it answers |
| --- | --- |
| Controller | Which HTTP request invokes this operation? |
| Service class | What work should happen? |
| Repository | How do I read/write this service's database? |
| DTO | What data crosses the HTTP boundary? |
| Eureka | Where is a service instance running? |
| OpenFeign | How can this Java method call another service over HTTP? |
| Gateway | Which route and filters handle an incoming request? |
| Config Server | Where does an application fetch its settings? |

Current environment: Java 25, Boot 4.1.1, Spring Cloud BOM 2025.1.3. These are the repository's versions at the time of writing; recheck compatibility when starting a newer project.

Each service is an independent Maven application. There is no root reactor POM: run Maven against that service's `pom.xml`.

## 2. Build a service before connecting it

1. Create the Spring Boot application with Web MVC and the dependencies it needs. For persistence here, use Data JPA and PostgreSQL.
2. Give it a stable `spring.application.name`, such as `inventory-service`.
3. Keep HTTP mapping in a controller, stock/order logic in a service, and database access in a repository.
4. Define DTO fields that match the JSON body.
5. Configure its database, port, and context path, locally first or through Config Server once available.
6. Start the database/service and test the endpoint directly before adding Feign.

Inventory's existing stock endpoint is:

```java
@RequestMapping("/products") // on ProductController
```

```java
@PutMapping("/reduce-stocks")
public ResponseEntity<Double> reduceStocks(@RequestBody OrderRequestDto request) {
    return ResponseEntity.ok(productService.reduceStocks(request));
}
```

With the configured servlet context `/api/v1`, its full path is:

```text
/api/v1 + /products + /reduce-stocks
= PUT /api/v1/products/reduce-stocks
```

Memory cue: **context path + controller path + method path = complete endpoint.**

## 3. Register services with Eureka

The Discovery application already runs on `8761`. Add this dependency to a service that needs registration/discovery:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

Its effective configuration needs:

```yaml
spring:
  application:
    name: inventory-service
eureka:
  instance:
    hostname: localhost
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka
```

`localhost` works for these apps running on the same machine. For containers or other machines, advertise an address the caller can reach.

Open `http://localhost:8761` and check registration, then test the actual endpoint. An `UP` registration proves discovery information exists; it does not prove the requested controller path works.

## 4. Create an OpenFeign client: Order -> Inventory

### Step 1 — Add dependencies in the calling application

Add OpenFeign to **Order**, because Order makes the HTTP call. For discovery-based calls, include LoadBalancer explicitly in a reusable setup:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

Keep the Eureka client dependency too. Use the project's Spring Cloud BOM for dependency versions. The existing services declare OpenFeign/Eureka; when troubleshooting discovery-based Feign, check that LoadBalancer is on the effective classpath.

### Step 2 — Enable Feign interface scanning

The current Order application already has `@EnableFeignClients`. For a new setup, explicit package scanning is useful:

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "com.aks.ecommerce.order_service.clients")
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

Spring discovers the interfaces and creates the objects that make HTTP calls. You do not implement the interface yourself.

### Step 3 — Describe the target API

The existing [InventoryOpenFeignClient](../../order-service/order-service/src/main/java/com/aks/ecommerce/order_service/clients/InventoryOpenFeignClient.java) describes both stock operations:

```java
@FeignClient(name = "inventory-service", path = "/api/v1")
public interface InventoryOpenFeignClient {
    @PutMapping("/products/reduce-stocks")
    Double reduceStocks(@RequestBody OrderRequestDto request);

    @PutMapping("/products/addStocks")
    String addStocks(@RequestBody OrderRequestDto request);
}
```

| Part | Meaning |
| --- | --- |
| `name` | Target service ID resolved through discovery when no fixed URL is configured. |
| `path` | Shared prefix for this client's endpoint mappings. |
| `@PutMapping` | HTTP method and remaining path; must match the target controller. |
| `@RequestBody` | Serialize the DTO into the JSON request body. |
| `Double` / `String` | Decode the successful response body into this type. |

The target returns `ResponseEntity<Double>`; Feign may receive just `Double` because that is the response body type.

For a fixed-address experiment, use `url = "http://localhost:9010"`. That mode bypasses discovery/load balancing for this client. Choose intentionally; do not add a fixed URL while expecting Eureka to choose the instance.

### Step 4 — Match the JSON contract

The request contains `items`; each item contains `productId` and `quantity`:

```json
{
  "items": [
    {"productId": 1, "quantity": 1}
  ]
}
```

Use a product ID that actually exists and has stock. The services have their own DTO classes; their Java package names can differ, but JSON field names and types must agree.

`@RequestBody` handles JSON. `@RequestHeader` handles headers. Both can be used on GET/POST/PUT methods as appropriate; header binding is not GET-only.

### Step 5 — Inject and invoke the client

Recipe with an explicit constructor:

```java
@Service
public class InventoryCaller {
    private final InventoryOpenFeignClient inventoryClient;

    public InventoryCaller(InventoryOpenFeignClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    public Double reduceStocks(OrderRequestDto request) {
        return inventoryClient.reduceStocks(request);
    }
}
```

The project's `OrdersService` uses Lombok's `@RequiredArgsConstructor` for the same constructor-injection idea. It calls `reduceStocks`, gets the total, sets order-item back references, and saves a `CONFIRMED` order.

```text
Order controller -> OrdersService -> Feign proxy
  -> discovery/load balancer -> Inventory controller
  -> ProductService -> Inventory database
  -> total price returns -> Order saves its order
```

Memory cue: **Feign interface = remote HTTP contract; injected proxy = caller.**

### Step 6 — Verify the connection once

1. Start both databases, Eureka, Config Server, Inventory, and Order.
2. Read `GET http://localhost:9010/api/v1/products/getproducts` to find a valid product ID and stock.
3. Send the JSON above once to `POST http://localhost:9020/api/v1/orders/create`.
4. Check the returned order/total and read Inventory stock again.

This is a stock-changing test. Calling `reduce-stocks` directly and then creating an order would reduce stock twice. A failed Order save after Inventory succeeds also does not automatically restore stock: the two databases are separate.

### Step 7 — Understand the reverse Feign experiment

Inventory has `OrdersFeignClient`, called by `ProductController` at `/products/fetchOrdersUsingFeignClient`.

Its target `/api/v1/orders/helloOrders` now requires `X-User-Id`. The current Feign method supplies no such header, so this experiment can fail with a missing-header response.

For a local header-binding exercise, the contract could be:

```java
@GetMapping("/orders/helloOrders")
String getOrdersUsingFeign(@RequestHeader("X-User-Id") Long userId);
```

Then the caller must pass an appropriate value. This demonstrates header transport; a caller-selected ID is not proof of authentication. Alternatively, use a dedicated non-user hello endpoint for a connectivity-only experiment.

## 5. Add Gateway routes and a custom filter

The Gateway uses `spring-cloud-starter-gateway-server-webflux`. Its remote settings must use the WebFlux namespace:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: inventory-service
              uri: lb://INVENTORY-SERVICE
              predicates:
                - Path=/api/v1/products/**
```

`Path` chooses the route; `lb://` resolves the service instance. This request uses the original `/api/v1` path.

To build a route filter factory:

1. Create a `@Component` class ending in `GatewayFilterFactory`.
2. Extend `AbstractGatewayFilterFactory<YourFactory.Config>`.
3. Put route settings in a `public static class Config` with getters/setters.
4. Call `super(Config.class)` in the constructor.
5. Implement `apply(Config)` and return `(exchange, chain) -> ...`.
6. Attach it under the intended route's `filters` list, using its name without the `GatewayFilterFactory` suffix.

`Config.class` enables runtime binding; the generic type makes `apply` type-safe. The filter runs per request; its configuration comes from the selected route.

For roles, authenticate first, validate the header/token, then read token roles and compare against configuration. The intended denial branch is:

```java
if (roles == null || !roles.contains(config.getRequiredRole())) {
    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
    return exchange.getResponse().setComplete();
}
return chain.filter(exchange);
```

This snippet assumes the token was authenticated, configuration was validated, and both sides use matching role case. The current role filter lowercases only the configured role and continues the chain after setting `403`; the snippet shows the behavior to implement next.

Use `401` for invalid/missing authentication, and handle JWT exceptions explicitly. Avoid logging bearer tokens.

To protect only `/api/v1/orders/adminPanel`, define a more specific route with a lower route `order` value than the general `/api/v1/orders/**` route, then attach `RequiredRole` to that admin route. A filter attached to the broad route restricts every Order path.

`GlobalFilter` participates in all matched Gateway routes; a route factory runs where it is attached. Merely marking a factory `@Component` makes it available, not globally active.

Memory cue: **continue = chain.filter(exchange); stop = response.setComplete().**

## 6. Set up Git-backed Config Server

### Step 1 — Create the separate configuration repository

```text
configuration-repo/
├── application.yml          shared settings
├── inventory-service.yml    Inventory settings
├── order-service.yml        Order settings
└── api-gateway.yml          Gateway settings
```

The names match client `spring.application.name`. `.yaml` is also supported. Use `inventory-service-dev.yml` for a `dev` profile override. Keep service-specific database settings out of shared `application.yml`.

Commit/push ordinary settings and secret placeholders. Keep actual passwords/tokens local.

### Step 2 — Create the Config Server application

Add `spring-cloud-config-server` and, for this project's registration, `spring-cloud-starter-netflix-eureka-client`. Enable it on the main application:

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

### Step 3 — Configure its own Git access locally

```yaml
spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: ${CONFIG_SERVER_URI}
          username: ${CONFIG_SERVER_USERNAME}
          password: ${CONFIG_SERVER_PASSWORD}
          default-label: main
server:
  port: 8888
```

Supply those variables through Config Server's IntelliJ Run Configuration, loading the ignored `.env`. For GitHub HTTPS access, use a repository-authorized token in the password variable. Git credentials allow repository access; they do not authenticate clients of the Config Server HTTP endpoint.

IntelliJ loads `.env` into the launched process. Spring does not automatically read that file; no dotenv library is necessary with this IDE setup. A terminal launch needs equivalent environment variables supplied separately.

### Step 4 — Check service/profile/branch selection

```text
http://localhost:8888/inventory-service/default/main
                       application      profile label
```

The JSON response lists property sources from shared and application-specific files. `default` means the default profile and `main` selects that Git branch. Inspect responses locally without sharing resolved credentials.

### Step 5 — Keep client secrets in the client's environment

In the remote `inventory-service.yml`:

```yaml
spring:
  datasource:
    password: ${INVENTORY_DB_PASSWORD}
```

Provide `INVENTORY_DB_PASSWORD` to the Inventory process. Gateway similarly needs `JWT_SECRET_KEY` when its remote configuration contains `${JWT_SECRET_KEY}`.

Ordinary JSON configuration responses retain these placeholders for the client to resolve. Config Server's own `.env` does not automatically distribute its environment variables to the other processes.

Memory cue: **Git stores the placeholder; the consuming service supplies the secret.**

## 7. Connect a service to Config Server

Add this dependency in each consuming application:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

Keep the name and connection information locally:

```yaml
spring:
  application:
    name: inventory-service
  config:
    import: configserver:http://localhost:8888
  cloud:
    config:
      label: main
```

The label setting is optional here because the server already defaults to `main`. If explicitly setting it on the client, the correct namespace is `spring.cloud.config.label`, not `spring.config.label`.

Move runtime settings to the matching Git file only after checking the server returns them. Preserve application identity and the import location so the client knows what to request and where to fetch it.

Without `optional:`, the import is required. Adding `optional:` allows unavailable configuration to be skipped, but does not invent missing database/route settings.

A Git commit does not automatically refresh running applications. For this learning setup, commit/push, check the Config Server response, then restart the consuming application. Dynamic refresh is a separate topic.

Suggested startup order:

```text
PostgreSQL containers -> Eureka -> Config Server -> Inventory -> Order -> Gateway
```

Memory cue: **name selects the file; profile selects the variant; label selects the Git revision.**

## 8. Troubleshoot from the first meaningful cause

| Symptom | Check / next action |
| --- | --- |
| `Could not resolve placeholder` | Supply that variable to the process which uses it. |
| `found duplicate key defaultZone` | Remove the duplicate within the same remote YAML mapping, then commit/push. |
| `main` parsing fails, then `master` missing | Fix the earlier YAML cause; a fallback branch will not repair invalid YAML. |
| Config endpoint returns no service-specific properties | Check filename, application name, profile, branch, and repository search location. |
| Feign bean is missing | Add OpenFeign starter and check `@EnableFeignClients` scans its package. |
| Feign cannot find a service instance | Check service ID, Eureka registration, and LoadBalancer dependency. |
| HTTP `404` from Feign | Compare complete paths and HTTP methods with the target controller. |
| HTTP `400`, missing `X-User-Id` | Target requires a header the caller has not supplied. |
| Inventory HTTP `500` | Inspect Inventory's actual exception: product ID, available stock, DTO/body shape. |
| Netty `UnknownHostException` | Check the hostname Eureka advertises is reachable from Gateway. |
| Roles are `null` | Token lacks the exact `roles` claim; `admin: true` is a different claim. |
| Denied request still reaches service | Stop with `setComplete()`; setting a status alone does not stop the chain. |

When building something new: test the target directly, test the caller, then test the Gateway route. Each check answers a different question.

## 9. Source files and official references

- [ProductController](../../inventory-service/inventory-service/src/main/java/com/aks/ecommerce/inventory_service/controller/ProductController.java): target HTTP mappings and reverse-call experiments.
- [OrdersService](../../order-service/order-service/src/main/java/com/aks/ecommerce/order_service/service/OrdersService.java): existing Feign invocation and persistence flow.
- [OrdersFeignClient](../../inventory-service/inventory-service/src/main/java/com/aks/ecommerce/inventory_service/client/OrdersFeignClient.java): Inventory-to-Order interface.
- [Config Server settings](../../config-server/config-server/src/main/resources/application.yaml): local Git placeholders and port.
- [Today's learning record](../learning-diary/2026-10-08-gateway-roles-and-config-server.md): observed results versus remaining work.
- [Spring Cloud OpenFeign](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html): interface scanning, discovery, and fixed-URL behavior.
- [Spring Cloud Config Client](https://docs.spring.io/spring-cloud-config/reference/client.html): required/optional imports and startup behavior.
- [Config environment repository](https://docs.spring.io/spring-cloud-config/reference/server/environment-repository.html): application, profile, and label selection.
- [Config placeholders](https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/overriding-properties-using-placeholders.html): client-side property resolution.

This guide was checked against local source on 8 October 2026. Remote configuration files and runtime tests were not rechecked during its creation.
