# E-Commerce Microservices — Learning Journey

> **Learning and experimentation project.** This repository is where I am learning Spring Boot microservices step by step by building a small e-commerce flow. It is **not a production application**, is not being prepared for production, and is intentionally allowed to contain unfinished code, simple error handling, and experiments while I learn.

## Why I am building this

I wanted to move beyond a single Spring Boot CRUD application and understand how separate services can work together. Instead of only reading theory, I am building and testing each concept in a small project:

- one service manages products and stock;
- another service creates and cancels orders;
- a discovery server helps services find each other;
- an API Gateway gives one entry point for selected routes;
- PostgreSQL containers let each main service keep its own data.

The goal is to understand the request flow and the reason behind each technology—not to make a complete e-commerce website.

## Current project map

```text
Client
  |
  v
API Gateway :8080 (Applied AuthenticationGatwayFilter using JwtToken)
  |
  +--> Order Service :9020 --------OpenFeign--------> Inventory Service :9010
  |          |                                              |
  |          v                                              v
  |     Order PostgreSQL :5434                       Inventory PostgreSQL :5433
  |
  +--> Inventory Service :9010

All applications register with / use Eureka Discovery Server :8761
```

Each service has the `/api/v1` context path, so the gateway forwards these paths without removing that prefix.

## Services I have built so far

| Application | What I am learning through it | Local port |
| --- | --- | --- |
| `inventory-service` | Product CRUD, JPA repositories, DTO mapping, stock reduction and stock restoration | `9010` |
| `order-service` | Order persistence, calling Inventory before saving an order, and cancellation flow | `9020` |
| `discovery-service` | Service registration and lookup with Netflix Eureka | `8761` |
| `api-gateway` | Gateway route configuration and load-balanced `lb://...` destinations | `8080` |
| `inventory-postgres` | PostgreSQL database used by Inventory | host `5433` |
| `order-postgres` | PostgreSQL database used by Orders | host `5434` |

## What works in the current learning flow

### Inventory service

I have practiced:

- creating products and listing products;
- mapping between `Product` entities and `Productdto` using ModelMapper;
- loading seed data with `data.sql` after JPA initialization;
- reducing stock for every item in an incoming order request;
- adding stock back when an order is cancelled.

The main stock logic is in [`ProductService.java`](inventory-service/inventory-service/src/main/java/com/aks/ecommerce/inventory_service/service/ProductService.java). It loops through the requested items, loads each product, checks available stock, updates the stock value, and returns the total price.

### Order service

I have practiced:

- creating orders with order items;
- linking each `OrderItem` back to its parent `Orders` entity before saving;
- listing all orders and finding an order by id;
- cancelling an order unless its status is `DELIVERED`;
- asking Inventory to restore stock during cancellation.

When creating an order, the Order Service first calls Inventory to reduce stock and calculate the total price. It then saves the order with status `CONFIRMED`.

### Service-to-service communication

I have tried two synchronous ways for one service to call another:

| Approach | Where I used it | What I learned |
| --- | --- | --- |
| `DiscoveryClient` + `RestClient` | Controller experiments such as fetching orders or product lists | Eureka returns a running service instance; `RestClient` sends the HTTP request to it. |
| OpenFeign | Order Service calling Inventory, plus an Inventory-to-Order experiment | A Java interface can describe another service's HTTP endpoints. |

For example, `InventoryOpenFeignClient` calls these Inventory endpoints:

- `PUT /api/v1/products/reduce-stocks`
- `PUT /api/v1/products/addStocks`

### Eureka and API Gateway

All applications point to Eureka at `http://localhost:8761/eureka`.

The Gateway currently routes:

| Request sent to Gateway | Destination |
| --- | --- |
| `/api/v1/orders/**` | `ORDER-SERVICE` |
| `/api/v1/products/**` | `INVENTORY-SERVICE` |

This helped me understand that Eureka tells the gateway **where a service instance is**, while the gateway configuration decides **which request path goes to which service**.

## Technologies explored so far

| Technology | How I am using or exploring it here |
| --- | --- |
| Java 25 | Language used for all services |
| Spring Boot 4.1.1 | Creating the independent backend applications |
| Spring Web MVC | Controllers and REST endpoints |
| Spring Data JPA / Hibernate | Persisting products, orders, and order items |
| PostgreSQL 15 | Separate local databases for Inventory and Orders |
| Docker Compose | Starting the two PostgreSQL containers |
| Lombok | Reducing boilerplate with annotations such as `@RequiredArgsConstructor` and `@Slf4j` |
| ModelMapper | Converting between entities and DTOs |
| Spring Cloud Netflix Eureka | Service discovery server and service clients |
| Spring Cloud Gateway Server WebFlux | Routing selected API paths through port `8080`, with reactive global and route filters |
| OpenFeign | Declarative HTTP calls between services |
| `RestClient` | Direct HTTP-call experiments after resolving a service through Eureka |
| Spring Boot Actuator | Exploring application endpoints; Order Service exposes actuator endpoints locally |
| Resilience4j | Dependency and annotation/configuration experiments for retry, rate limiting, and circuit breaker; these experiments are currently commented out, so they are not active in the running flow |
| Maven | Managing dependencies and building each service |

## Local setup notes I learned

- The root `docker-compose.yaml` starts two PostgreSQL containers.
- Inventory connects to PostgreSQL on `localhost:5433`; Orders connects on `localhost:5434`.
- PostgreSQL containers use `Asia/Kolkata` as their timezone.
- Each application is its own Maven project, so I open/run them separately in IntelliJ.
- A useful startup order while learning is: PostgreSQL containers → Discovery Service → Inventory Service → Order Service → API Gateway.

## Useful endpoints for testing locally

These are the endpoints currently present in the controllers. They are for local learning/testing only.

| Method | Direct service URL | Purpose |
| --- | --- | --- |
| `GET` | `http://localhost:9020/api/v1/orders/helloOrders` | Simple Order Service check |
| `GET` | `http://localhost:9010/api/v1/products/getproducts` | List products |
| `POST` | `http://localhost:9010/api/v1/products/create` | Create a product |
| `POST` | `http://localhost:9020/api/v1/orders/create` | Create an order and reduce stock |
| `DELETE` | `http://localhost:9020/api/v1/orders/cancel/{id}` | Cancel an order and add stock back |
| `GET` | `http://localhost:8080/api/v1/orders/helloOrders` | Same Order check through Gateway |

## Things I am intentionally still learning

This is a progress record, not a feature promise. Some code is deliberately simple because the current focus is understanding the fundamentals. Areas I have started exploring or plan to revisit while learning include:

- transactions around multi-item stock updates;
- clearer exception handling and API responses;
- validation of order quantities and request bodies;
- retry, rate limiter, and circuit breaker behaviour with Resilience4j;
- improving tests;
- asynchronous communication concepts such as Kafka and typed RPC concepts such as gRPC.

## Repository layout

```text
ECommerce/
├── api-gateway/api-gateway/
├── discovery-service/discovery-service/
├── inventory-service/inventory-service/
├── order-service/order-service/
├── docker-compose.yaml
└── README.md
```

## My learning takeaway so far

Microservices are not just “many Spring Boot projects.” In this repository I am learning that each service has its own responsibility and data, and that extra pieces such as Eureka, Gateway, Docker, and Feign solve specific communication or setup problems. I am adding one concept at a time so I can understand what it does before moving to the next one.
