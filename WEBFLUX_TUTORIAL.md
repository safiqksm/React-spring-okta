# WebFlux and Reactive Programming in This Repo

This file has two parts. **Part 1** (written before any refactor) explains how
the `gateway` module uses Spring WebFlux and Project Reactor today, with real
code from this repo. **Part 2** (written after the refactor on branch
`feature/gateway-servlet-refactor`) documents exactly what changed and why,
file by file.

If you only remember one thing from this doc: **`gateway` is the only
reactive module in this repo.** `service-1`, `service-2`, and `service-3` are
all plain Spring MVC (the traditional "Servlet" stack) already — they're your
built-in "blocking" reference to compare against everything below.

---

## Part 1 — How it works today

### 1.1 Two web stacks, one Spring

Spring Boot ships two completely different ways to build a web app:

| | Servlet (Spring MVC) | Reactive (Spring WebFlux) |
|---|---|---|
| Server | Tomcat/Jetty (thread-per-request) | Netty (event-loop) |
| Concurrency model | One OS thread blocks per in-flight request | A small, fixed pool of threads never blocks; each request is a chain of callbacks that resume when data is ready |
| Return types | Plain values (`Jwt`, `Map`, `ResponseEntity<X>`) | `Mono<T>` (0 or 1 result) / `Flux<T>` (0..N results), both asynchronous |
| What happens on I/O (DB call, HTTP call, disk read) | The thread **blocks** — sits idle, doing nothing, holding onto its stack — until the I/O completes | The thread **returns immediately**; the I/O operation is registered, and whichever event-loop thread is free when the result arrives resumes the callback chain |

`service-1`, `service-2`, `service-3` all use the Servlet stack:
`spring-boot-starter-web`, plain `@RestController`s returning `Map<String,
Object>` directly, `HttpServletRequest`, and `OncePerRequestFilter` (see
`service-1/.../SecurityConfig.java`'s `inboundAuthorizationDebugFilter`). No
`Mono` anywhere in those three modules.

`gateway` uses the reactive stack:
`spring-cloud-starter-gateway-server-webflux` (`gateway/build.gradle`),
Netty instead of Tomcat, `Mono<...>` return types everywhere, `WebFilter`
instead of `OncePerRequestFilter`, `ServerWebExchange` instead of
`HttpServletRequest`/`HttpServletResponse`.

### 1.2 Why a *gateway* specifically reaches for reactive

A reverse proxy's job is almost entirely I/O: accept a connection from the
browser, open a connection to a downstream service, wait, relay the response
back. Very little CPU work happens in between. That's exactly the workload
non-blocking I/O is built for — a gateway fielding thousands of concurrent
proxied connections needs thousands of *pending operations*, but not
thousands of *OS threads* sitting idle waiting for each one to finish. A
small fixed thread pool can service all of them, because none of those
threads ever sits blocked.

The trade-off: reactive code is harder to read, harder to debug (stack traces
point into Reactor's internals, not your business logic), and introduces a
sharp new rule you must never break — **never run blocking code directly on
an event-loop thread**, because blocking one of those few threads stalls
*every other in-flight request* sharing that thread, not just yours. You'll
see this rule show up concretely in section 1.4.

For a local POC proxying two internal routes with no real concurrent load,
this trade-off mostly buys complexity without buying the benefit it exists
for — which is exactly the argument for the refactor in Part 2.

### 1.3 Reactor vocabulary you need, taught through this repo's own code

**`Mono<T>`** — a publisher of zero or one value, delivered asynchronously.
Where blocking code returns a `Jwt` directly, reactive code returns a
`Mono<Jwt>`: a promise of a `Jwt` that will show up later. Nothing happens
until something *subscribes* to it — Spring's WebFlux machinery does that
subscribing for you at the edges (when a `@RestController` method returns a
`Mono`, or when the security filter chain processes a `WebFilter`).

**`.flatMap(fn)`** — "when this Mono produces a value, pass it to `fn`, which
itself returns another Mono; flatten the two into one." This is how reactive
code chains async steps without ever blocking to get an intermediate result.
From `gateway/.../DpopProofWebFilter.java`:

```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return exchange.getPrincipal()                              // Mono<Principal>
            .ofType(JwtAuthenticationToken.class)                // Mono<JwtAuthenticationToken> (or empty)
            .flatMap(authentication -> validate(exchange, authentication.getToken())) // Mono<Boolean>
            .defaultIfEmpty(true)                                 // if the Mono above was empty, use `true`
            .flatMap(valid -> valid ? chain.filter(exchange) : exchange.getResponse().setComplete());
}
```

Read this top to bottom as: get the current principal *(async, might not
exist yet)* → if it's a JWT, validate its DPoP proof *(async — see 1.4)* → if
there was no JWT at all, treat that as "valid" (nothing to check) → if valid,
continue the filter chain; if not, finish the response with whatever status
`validate()` already set.

**`.defaultIfEmpty(x)`** — supplies a fallback value if the upstream `Mono`
completes without ever emitting one. Here: no authenticated JWT principal
means there's nothing to DPoP-check, so treat that case as `true` (valid) and
move on.

**`ServerWebExchange`** — WebFlux's single bundled object for "the request
and the response and the principal and everything else," replacing the
Servlet stack's separate `HttpServletRequest request, HttpServletResponse
response` parameters you already see in `service-1`'s
`inboundAuthorizationDebugFilter`.

**`WebFilter`** (reactive) vs **`OncePerRequestFilter`** (servlet) — the two
filter interfaces. Reactive:

```java
public interface WebFilter {
    Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain);
}
```

Servlet (already used in `service-1`):

```java
public abstract class OncePerRequestFilter extends GenericFilterBean {
    protected abstract void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException;
}
```

Same *purpose* (run some logic, then either continue the chain or stop), but
the reactive version has to return a `Mono<Void>` describing "the work to be
done," rather than just doing the work and returning `void`.

### 1.4 The one place this repo has to fight the "never block the event loop" rule

`DpopProofWebFilter.validate()` needs to do real, synchronous, CPU-bound
work: parse a JWK from the DPoP proof's header and compute its SHA-256
thumbprint (`JWK.parse(...).computeThumbprint()`). That's a blocking-style
call — nothing about it is asynchronous — and running it directly on a Netty
event-loop thread would stall every other request sharing that thread for
however long the crypto takes. So the filter explicitly moves it off the
event loop:

```java
return Mono.fromCallable(() -> validateProof(exchange, accessToken, proof))
        .subscribeOn(Schedulers.boundedElastic())   // <- run validateProof() on a separate worker-thread pool
        .onErrorResume(exception -> reject(exchange, exception.getClass().getSimpleName()));
```

`Schedulers.boundedElastic()` is Reactor's pool of worker threads reserved
*specifically* for this situation — blocking or CPU-heavy work that has no
choice but to run somewhere, just not on the small fixed event-loop pool.
`.subscribeOn(...)` tells Reactor which pool should run the callable.

This is the single clearest example in this codebase of reactive
programming's central discipline: you must always know which thread your
code runs on, and explicitly relocate anything blocking. In the Servlet
world, this problem doesn't exist — `service-1`, `service-2`, `service-3`
never need a `Schedulers` equivalent, because every request already has its
own dedicated thread, and blocking that thread only affects that one request.

### 1.5 File-by-file inventory of what's reactive here

- **`RouteConfig.java`** — `RouteLocatorBuilder`/`RouteLocator`, Spring Cloud
  Gateway's reactive routing DSL. Declares `/api/service-1/**` →
  `http://localhost:8081` and `/api/settings/**` → `http://localhost:8082`;
  under the hood, proxies each request using Netty's reactive HTTP client.
- **`SecurityConfig.java`** — `@EnableWebFluxSecurity`, `ServerHttpSecurity`,
  `SecurityWebFilterChain` (the reactive equivalents of `@EnableWebSecurity`/
  `HttpSecurity`/`SecurityFilterChain` you'll find in every `service-*`
  module's `SecurityConfig`). The custom `dpopAwareTokenConverter()` returns
  a `Mono<Authentication>` via a `ServerAuthenticationConverter`.
- **`DpopProofWebFilter.java`** — see 1.4.
- **`RevocationCheckWebFilter.java`** — same `WebFilter`/`Mono<Void>` shape,
  no blocking work inside it (just a map lookup), so it doesn't need a
  `Scheduler` — a useful contrast with `DpopProofWebFilter`.
- **`JwtDebugWebFilter.java`** — the simplest one: `.doOnNext(...)` runs a
  side effect (a debug log line) when the Mono emits, `.then(...)` continues
  the chain afterward.
- **`GlobalTokenRevocationController.java`** — `Mono<ResponseEntity<Void>>`,
  `ReactiveJwtDecoder`/`NimbusReactiveJwtDecoder` (reactive JWT decoding),
  `.flatMap(...)`/`.onErrorResume(...)` instead of try/catch.

Everything else in `gateway` (`GatewayApplication.java`, `CorsConfig.java`,
`RevocationDenyList.java`) is plain Java/Spring config with no reactive types
at all — those files won't change in the refactor.

---

## Part 2 — What changed in the refactor

*(Filled in after the refactor on `feature/gateway-servlet-refactor`.)*
