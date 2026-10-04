# Learning Diary — 5 October 2026

## Focus for today

Today I studied Spring Cloud Gateway filters and connected the course concepts to the API Gateway in this project. I also spent about one hour revising and practising Java generics.

## What I learned

### 1. WebFlux Gateway and reactive filter flow

The Gateway uses the WebFlux server implementation, which is why its filters use `ServerWebExchange`, `GatewayFilterChain`, and `Mono<Void>`.

- `ServerWebExchange` carries the incoming HTTP request and outgoing HTTP response.
- `GatewayFilterChain` represents the remaining filters and final route forwarding.
- `chain.filter(exchange)` continues the request through that chain.
- `Mono<Void>` represents work that completes later without returning a response body from the filter itself.

I also learned the basic difference between Eureka and Netty:

- Eureka is the service registry: it answers where a service instance is running.
- Netty is the reactive HTTP server/client engine used by the WebFlux Gateway to receive and forward requests.

### 2. Global filters and route filters

I implemented two filter scopes:

- `GlobalLoggingFilter` runs for every request that matches a Gateway route. It logs request information before forwarding and the response status after the chain completes.
- `OrdersLoggingFilter` is attached only to the `order-service` route, so it logs only requests matching `/api/v1/orders/**`.

This showed me that a global filter is registered as a `GlobalFilter` bean, while a route filter is registered as a filter factory and must be listed under the route in `application.yaml`.

### 3. `AbstractGatewayFilterFactory` and its `Config` type

I created `AuthenticationGatewayFilterFactory` using:

```java
AbstractGatewayFilterFactory<AuthenticationGatewayFilterFactory.Config>
```

The generic `Config` type makes the configuration for this filter type-safe. Calling `super(Config.class)` tells Gateway which class receives the route arguments.

For the Order route, this YAML:

```yaml
- name: Authentication
  args:
    enable: true
```

binds `enable: true` to `AuthenticationGatewayFilterFactory.Config`. This configuration belongs to this use of the filter, not to the whole application.

### 4. JWT authentication at the Gateway

The authentication filter now:

1. Reads the `Authorization` header.
2. Returns `401 Unauthorized` when the header is missing.
3. Uses `JwtService` and JJWT to parse a signed JWT and obtain the user ID from its subject.
4. Creates a new Gateway request containing `X-User-Id`.
5. Forwards the updated exchange to Order Service.

I learned that WebFlux requests are immutable. Calling `mutate().build()` creates a new request; the Gateway must forward a new exchange containing that request for Order Service to receive the header.

### 5. Eureka hostname debugging

I diagnosed a Gateway `UnknownHostException` caused by Eureka advertising a local Windows/WSL-style hostname that Netty could not resolve. For this single-machine learning setup, the service instances now advertise `localhost`, so Gateway can resolve the local Order and Inventory services reliably.

### 6. Java generics practice

I spent about one hour studying and practising:

- generic classes, methods, and constructors;
- generic type parameters and compile-time type safety;
- generic enums;
- wildcards such as `<?>`.

The Gateway factory gave me a practical project example: its generic type ensures that the correct `Config` object is used when Gateway creates the filter.

## What I applied in this project

| Area | Applied change |
| --- | --- |
| Gateway runtime | Changed API Gateway from the MVC starter to the WebFlux Gateway starter. |
| Global filtering | Added pre- and post-request logging through `GlobalLoggingFilter`. |
| Route filtering | Added `OrdersLoggingFilter` to the Order route. |
| Authentication | Added `AuthenticationGatewayFilterFactory`, its route-level `enable` setting, and `JwtService`. |
| Identity forwarding | Added the authenticated user ID as `X-User-Id` before forwarding to Order Service. |
| Downstream header reading | Updated the Order hello endpoint to read `X-User-Id`. |
| Service discovery | Configured local Eureka instance hostnames for reliable Gateway routing. |
| Secret handling | Replaced the JWT secret in YAML with the `JWT_SECRET_KEY` environment variable. |

## Request flow I can now explain

```text
Client request
  -> GlobalLoggingFilter
  -> OrdersLoggingFilter (Order paths only)
  -> Authentication filter
  -> JWT subject becomes X-User-Id
  -> Eureka resolves ORDER-SERVICE
  -> Netty forwards to Order Service
  -> response travels back through post-filter logic
```

## Verification today

- The API Gateway compiled successfully with `mvn -q -f api-gateway/api-gateway/pom.xml -DskipTests package`.
- The custom `Authentication` filter is registered as a Spring component and referenced from the Order route configuration.
- A full live request with a valid JWT still needs to be tested after setting `JWT_SECRET_KEY` in the IntelliJ run configuration.

## Next learning steps

1. Add `@RequestHeader("X-User-Id")` beside `@RequestBody` on the Order `POST /create` endpoint and test header handling on POST requests.
2. Validate malformed `Authorization` values, not only a missing header.
3. Ensure downstream services do not trust a client-supplied `X-User-Id` header.
4. Continue the reactive programming section of the course before adding more advanced Gateway behaviour.
