# Learning record — 9 October 2026

Today I moved from fetching configuration at startup to using refreshed configuration in a running service. This record describes my local source and the configuration repository snapshot `49e2f423b4a8ac0259706034a94f37a3a2c3869d`, inspected through authenticated Git.

## What I built

In [DevPanelConfiguration](../../order-service/order-service/src/main/java/com/aks/ecommerce/order_service/config/DevPanelConfiguration.java):

```java
@Configuration
@Data
@RefreshScope
public class DevPanelConfiguration {
    @Value("${devPanel.toggle}")
    private Boolean devPaneltoggle;
}
```

- `@Configuration` registers a Spring configuration bean.
- `@Value` injects the property into the field when the bean is initialized.
- `@Data` generates accessors, including `getDevPaneltoggle()`.
- `@RefreshScope` supplies a proxy whose cached target can be cleared on refresh and recreated when next used.

The remote `order-service.yml` contains `devPanel.toggle: true`. Order's controller injects `DevPanelConfiguration` and reads the getter for each dev-panel request.

```text
true  -> welcome message with service/shared properties
false -> closed message
```

The controller itself is also refresh-scoped because its own `@Value` fields read `my.variable` and `my.globalVariable`.

Memory cue: **@Value reads during bean creation; @RefreshScope allows that bean to be recreated.**

## Shared versus profile-specific configuration

The config repository contains:

| File | Learning purpose |
| --- | --- |
| `application.yml` | Shared Eureka/Actuator settings and `my.globalVariable`. |
| `order-service.yml` | Order settings, base `my.variable`, and dev-panel flag. |
| `order-service-dev.yml` | Overrides `my.variable` for the `dev` profile. |
| `inventory-service.yaml` | Inventory database/HTTP settings; duplicate Eureka key is absent at the reviewed revision. |
| `api-gateway.yml` | WebFlux routes and JWT secret placeholder. |

My local Order configuration activates `global`. That does not select `order-service-dev.yml`. To practise the dev override, I would activate `dev` before startup and compare `/order-service/dev/main` with `/order-service/default/main` on Config Server.

Shared `application.yml` is loaded for clients without a special `global` profile. The shared variable's string value includes the word `production`, but that is only demonstration text; this remains a learning project.

Memory cue: **application.yml is shared; service-profile.yml is selected by the active profile.**

## What refresh means

The manual learning flow is:

```text
Edit Git flag -> commit/push -> POST Order's actuator/refresh
  -> updated client Environment -> clear refresh-scoped targets
  -> next request creates targets using updated values
```

Committing Git alone does not update already-injected fields. Adding an Actuator dependency alone does not refresh a bean. Call the endpoint on the consuming client whose behavior should change, not just on Config Server.

Order uses `/api/v1` as its servlet context, so its usual refresh URL in this setup is `http://localhost:9020/api/v1/actuator/refresh`. No live refresh request was sent during this review.

## Other code applied today

- Inventory's `ProductController` now injects `my.globalVariable` and has `/products/adminPanel` to display it.
- Gateway, Config Server, and Discovery now declare the Actuator starter; Order already declared it.
- The remote shared config exposes Actuator endpoints with `include: "*"`. A focused refresh exercise only needs selected endpoints such as `health,info,refresh` on the consuming client.

## Review findings to remember

1. The Order toggle wiring is coherent; source review does not prove a live true/false refresh test passed.
2. Inventory's controller has no `@RefreshScope`, so its `@Value` field is initialized at startup and will not be reinjected by the current refresh setup.
3. The dev-panel toggle chooses a message. Both branches currently return HTTP `200`; it does not enforce a user role or remove the endpoint.
4. `global` and `dev` are different profile names; the remote dev override is not selected by the current local profile.
5. Git contains literal database credentials for local development. Keep real/reused secrets in client environment variables; do not copy credentials into learning docs.
6. Each running client instance needs refresh independently. Automatic broadcasting with Spring Cloud Bus is a future topic, not implemented here.

## Other uses I can explore

- Display a maintenance message without restarting an app.
- Enable a recommendation feature or change a business threshold.
- Adjust an eligible bean's configuration after a refresh.

These require application code that actually reads the setting and a bean that supports refresh. Server ports, arbitrary infrastructure beans, and all database pools are not automatically changeable this way.

Continue with the [repeatable toggle experiment](../guides/refresh-scope-and-feature-toggles.md).
