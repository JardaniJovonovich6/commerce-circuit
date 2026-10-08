# Refresh scope and feature toggles — step-by-step guide

Use this after the [Config Server setup guide](microservices-build-and-connect.md). The example is Order's existing dev-panel feature. Steps below describe an experiment you can perform; this documentation task did not execute the refresh cycle or modify remote Git settings.

## 1. Put the flag in the configuration repository

In remote `order-service.yml`:

```yaml
devPanel:
  toggle: true
```

Use an explicit boolean. Changing `true` to `false` is clearer than removing a key; removing a property does not reliably clear an already-loaded value during refresh.

Commit/push the file to `main`. Verify locally that Config Server's `GET http://localhost:8888/order-service/default/main` returns the expected flag. That endpoint may contain sensitive configuration, so inspect it locally rather than paste the whole response into documentation.

## 2. Read it in a refresh-scoped bean

The current [DevPanelConfiguration](../../order-service/order-service/src/main/java/com/aks/ecommerce/order_service/config/DevPanelConfiguration.java) uses `@Configuration`, `@RefreshScope`, `@Value("${devPanel.toggle}")`, and a Lombok getter.

A reusable variant for a new feature can use a component with an explicit getter and a default:

```java
@Component
@RefreshScope
public class FeatureFlags {
    @Value("${devPanel.toggle:false}")
    private boolean devPanelEnabled;

    public boolean isDevPanelEnabled() {
        return devPanelEnabled;
    }
}
```

This is a recipe, not an edit to the current project. The default `false` deliberately disables the feature when the property is absent. For mandatory configuration, omit the default and diagnose missing properties.

Why a separate bean? It gives the application one place to read this setting. Injecting it gives callers a proxy that finds the refreshed target. Adding `@RefreshScope` to a configuration class does not automatically put every `@Bean` it defines into refresh scope.

## 3. Read the flag when processing the request

Order currently does this in `/orders/devPanel`:

```java
if (devPanelConfiguration.getDevPaneltoggle()) {
    return "Welcome ...";
}
return "DevPanel is closed ...";
```

The current implementation returns `200` in both branches. If a later exercise needs a disabled endpoint status, return an appropriate `ResponseEntity` explicitly. This flag controls behavior; authentication/authorization checks remain separate.

Order's controller additionally has its own `@Value` strings, so it is also annotated `@RefreshScope`. If a controller only calls methods on a refresh-scoped flags bean, annotating that controller solely for that dependency is usually unnecessary.

## 4. Enable the refresh HTTP endpoint on Order

Order already has the Actuator starter and Config Client dependency. Effective configuration needs refresh exposure. For a focused local experiment:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,refresh
```

The reviewed shared Git config currently uses `include: "*"`. Exposing refresh allows a caller to trigger configuration changes, so keep this local experiment accessible only to intended users.

If adding the dependency/exposure for the first time, start or restart the application once. Thereafter eligible bean values can be refreshed while it stays running.

## 5. Test true -> false -> true

Use the direct Order URL to isolate configuration behavior from Gateway role filters:

```powershell
# Read current behavior (no stock/order mutation).
Invoke-RestMethod -Uri 'http://localhost:9020/api/v1/orders/devPanel'
```

Then:

1. Change remote `devPanel.toggle` to `false`; commit and push.
2. Check Config Server returns `false` for the same application/profile/branch.
3. Request Order's dev panel before refresh. It should still use its previous loaded value.
4. Refresh **Order's running instance**:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:9020/api/v1/actuator/refresh'
```

5. Request the dev panel again. It should show the closed message.
6. Restore `true` in Git, commit/push, refresh Order, and request again.

Expected observations:

| Stage | Config Git value | Order behavior |
| --- | --- | --- |
| Initial startup | `true` | Welcome message. |
| Push change, before client refresh | `false` | Previous value remains loaded. |
| After client refresh and next use | `false` | Closed message. |
| Restore, refresh, next use | `true` | Welcome message again. |

Actuator often returns an array of changed property keys. That response is evidence of changed configuration; the subsequent endpoint response verifies behavior.

The URL above assumes servlet context `/api/v1`, default Actuator base path `/actuator`, and no separate management port. Adjust it if those settings change. Use `POST`, not a browser's ordinary `GET` navigation.

Memory cue: **push changes the source; refresh updates the client; next use recreates the bean.**

## 6. Understand the shared-value experiment

Remote `application.yml` supplies `my.globalVariable`; Order and Inventory inject it. Updating that shared file does not automatically refresh every service.

Order's controller is refresh-scoped. Inventory's current controller is not. To perform the same refresh experiment in Inventory later, give the bean holding the field appropriate refresh support, expose refresh there, and refresh that Inventory instance. A restart also reinjects the value.

The active Order profile is currently `global`. To try remote `order-service-dev.yml`, activate `dev` before startup; shared `application.yml` still participates. Changing profiles dynamically is a separate exercise.

## 7. Common failures and useful boundaries

| Symptom | First check |
| --- | --- |
| `404` on refresh | Actuator dependency, `refresh` exposure, servlet context and management port. |
| `405` | Send `POST`. |
| Config Server shows new flag but feature stays old | Refresh the consuming client and check the bean has refresh support. |
| Dev override is absent | Activate `dev`; `global` does not select the dev file. |
| Missing-property exception | The key must exist in effective configuration or have a deliberate default. |
| One instance changes, another does not | Manual refresh is per instance; Bus broadcasting is not installed here. |

Other uses: maintenance messages, optional-feature switches, business limits, and configuration of eligible clients/beans. Refresh does not hot-reload Java code or guarantee that every setting can change safely; for example, the default Hikari datasource is not refreshable by this mechanism.

Official explanation: [Spring Cloud Refresh Scope](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/application-context-services.html). For future broadcasting: [Spring Cloud Bus endpoints](https://docs.spring.io/spring-cloud-bus/reference/spring-cloud-bus/bus-endpoints.html).
