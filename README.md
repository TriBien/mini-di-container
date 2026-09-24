# mini-di-container

A deliberately small Java dependency-injection container designed as a reverse-engineering lab for **dependency inversion, composition, and inversion of control (IoC)**.

The point is not to recreate Spring. The point is to make the mechanism that Spring automates small enough to see.

## Learning target

You should finish this lab able to explain DI without starting with Spring:

> An object should receive the collaborators it needs from outside rather than deciding how to construct or locate those collaborators itself. A composition root/container chooses concrete implementations and connects the object graph.

That gives us two distinct ideas:

- **Dependency inversion**: application code depends on stable abstractions/contracts; concrete details are selected at the composition boundary.
- **Dependency injection**: a concrete mechanism for supplying those dependencies to an object.
- **Inversion of control**: the application no longer controls all object creation/wiring/lifecycle decisions; a composition mechanism does.

DI is therefore one way to implement IoC. DI is not the same thing as the broader design principle of dependency inversion.

---

## 1. Start with the dependency graph

Suppose we have:

```text
OrderService ──depends on──> PaymentGateway
                                 ▲
                                 │ implemented by
                                 │
                         StripePaymentGateway
```

The important question is **who creates whom?**

Without DI:

```java
class OrderService {
    private final PaymentGateway gateway = new StripePaymentGateway();
}
```

The graph is hidden inside the class. `OrderService` now knows a concrete construction detail.

With composition at the edge:

```java
class OrderService {
    private final PaymentGateway gateway;

    OrderService(PaymentGateway gateway) {
        this.gateway = gateway;
    }
}
```

A composition root decides:

```java
PaymentGateway gateway = new StripePaymentGateway();
OrderService orders = new OrderService(gateway);
```

The business object has no responsibility for selecting or constructing `PaymentGateway`.

The container in this repository merely automates this composition step.

### Mental model

Think of the application as a directed graph:

```text
A -> B -> D
A -> C -> D
```

An arrow means: **A needs B**.

To build `A`, the container recursively resolves `B` and `C`, which recursively resolve their dependencies. A valid acyclic graph can be assembled bottom-up.

A cycle is different:

```text
A -> B -> A
```

There is no valid bottom-up construction order. A container must either support special early references/proxies or reject the graph. This mini-container rejects it explicitly because that makes the invariant visible.

---

## 2. What exactly is inverted?

The inversion is easiest to see by comparing control flow.

### Ordinary construction

```text
Application object
    |
    +--> decides dependency implementation
    +--> calls constructor
    +--> controls lifetime
    +--> stores/caches objects
```

### Container-managed composition

```text
Application object <---- dependencies ---- Composition root / container
                                           |
                                           +--> chooses implementation
                                           +--> constructs graph
                                           +--> manages scope/lifecycle
                                           +--> may wrap objects
```

The object still owns its **behavior**. It no longer owns every **construction decision**.

This distinction is crucial: IoC is not “the framework calls my method.” More fundamentally, it is a change in **who controls object assembly and lifecycle policy**.

---

## 3. Repository map

```text
mini-di-container/
├── pom.xml
├── README.md
├── src/
│   ├── main/java/com/example/minidi/
│   │   ├── annotations/
│   │   │   ├── Component.java
│   │   │   ├── Inject.java
│   │   │   ├── PostConstruct.java
│   │   │   ├── PreDestroy.java
│   │   │   └── Scope.java
│   │   ├── core/
│   │   │   ├── MiniApplicationContext.java
│   │   │   ├── BeanDefinition.java
│   │   │   ├── BeanScope.java
│   │   │   └── DiException.java
│   │   ├── domain/
│   │   │   ├── OrderService.java
│   │   │   ├── ConstructorInjectedService.java
│   │   │   ├── PaymentGateway.java
│   │   │   ├── StripePaymentGateway.java
│   │   │   ├── Clock.java
│   │   │   ├── SystemClock.java
│   │   │   ├── FieldInjectedService.java
│   │   │   ├── ConstructorInjectedService.java
│   │   │   ├── LifecycleProbe.java
│   │   │   ├── PrototypeProbe.java
│   │   │   ├── CircularA.java
│   │   │   └── CircularB.java
│   │   ├── experiments/
│   │   │   ├── Main.java
│   │   │   └── LoggingProxyExperiment.java
│   └── test/java/com/example/minidi/
│       └── MiniApplicationContextTest.java
```

---

# Experiment 1 — Constructor injection

Run:

```bash
./run.sh
```

Or, with Maven installed:

```bash
mvn test
mvn -q exec:java -Dexec.mainClass=com.example.minidi.experiments.Main
```

The main experiment creates:

```text
OrderService
    |
    +--> PaymentGateway -> StripePaymentGateway
    |
    +--> Clock          -> SystemClock
```

The constructor makes the dependency graph explicit:

```java
public OrderService(PaymentGateway gateway, Clock clock) { ... }
```

### Why this matters

Constructor injection has a strong invariant:

> After the constructor returns, the object can be in a valid dependency-complete state.

This gives you:

- required dependencies visible at the API boundary;
- natural immutability with `final` fields;
- simple unit testing without reflection;
- failures during construction rather than later at first use.

A useful design heuristic is:

> **Required for correctness → constructor dependency.**

---

# Experiment 2 — Field injection

`FieldInjectedService` deliberately uses:

```java
@Inject
private Clock clock;
```

The container therefore has to do this in stages:

```text
1. construct object
2. find injectable fields
3. resolve dependencies
4. write fields reflectively
5. run @PostConstruct
6. publish instance
```

This demonstrates both the convenience and the hidden state transition introduced by field injection.

Before field injection, the object exists but is incomplete.

That is why field injection weakens a simple object-level invariant: construction no longer means “ready to use.”

It is also harder to instantiate such a class outside the container without duplicating framework behavior.

The experiment intentionally keeps field injection in the project because understanding why it is mechanically possible is more useful than simply memorizing a rule against it.

---

# Experiment 3 — Lifecycle

`LifecycleProbe` records these events:

```text
construct
postConstruct
use
preDestroy
```

The mini-container models a simplified lifecycle:

```text
                  resolve
                    |
                    v
                instantiate
                    |
                    v
               inject fields
                    |
                    v
              @PostConstruct
                    |
                    v
              cache singleton
                    |
                    v
                  use
                    |
                  close
                    |
                    v
               @PreDestroy
```

The important idea is that lifecycle is **container policy**, not business behavior.

## Singleton vs prototype

This container supports two scopes:

- `SINGLETON`: one managed instance per context and bean definition.
- `PROTOTYPE`: a new instance each time `getBean()` resolves that definition.

The prototype experiment demonstrates:

```text
getBean(PrototypeProbe) -> instance #1
getBean(PrototypeProbe) -> instance #2
```

The two references are different.

The container still initializes each prototype, but it deliberately does not retain them for destruction. That mirrors the important Spring lifecycle distinction: prototype instances are created/configured by the container, but full destruction management is left to the client.

---

# Experiment 4 — Circular dependency

Try:

```java
context.getBean(CircularA.class);
```

The graph is:

```text
CircularA -> CircularB -> CircularA
```

The container tracks a construction stack. On the second attempt to construct `CircularA`, it detects that `CircularA` is already being constructed and fails with a descriptive `DiException`.

This is an important graph lesson:

> Dependency resolution is not merely object creation. It is graph traversal with state.

A production IoC container has to decide what to do with cycles. Different injection strategies have different possibilities. Constructor cycles are especially direct because neither object can be fully constructed before the other exists.

---

# Experiment 5 — Proxying

`LoggingProxyExperiment` uses Java's built-in `Proxy` API to wrap `PaymentGateway`.

Conceptually:

```text
caller
  |
  v
proxy --before--> target --after--> return
```

The target does not know it is being logged.

That is the basic shape behind a large class of framework features:

```text
transaction boundary
security
metrics
caching
logging
retry
```

Spring AOP is proxy-based: depending on configuration and available interfaces, Spring can use JDK dynamic proxies or CGLIB-generated subclasses. 

This is why the production mental model should not stop at “Spring injects my object.” A mature container can **transform the object reference that gets published**.

### CGLIB Proxy Mechanism

CGLIB (Code Generation Library) creates dynamic proxies by **subclassing the target class at runtime**. Unlike JDK dynamic proxies, it does not require the target to implement an interface; it generates a subclass that overrides methods and intercepts calls via callbacks.

#### How it works

1. **Enhancer** creates a subclass of the target class.
2. Register a **MethodInterceptor** (callback) to intercept method invocations.
3. In `intercept(...)`, execute cross-cutting logic (before/after), then delegate to the original method using `proxy.invokeSuper(obj, args)`.
4. `enhancer.create()` returns an instance of the generated subclass, castable to the target class type.

> **Note:** `final` classes, `final` methods, and `private` methods cannot be overridden, so they cannot be proxied by CGLIB.

#### Code Example

```java
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

public class CglibProxyExample {
    // Target class (no interface required)
    static class GreetingService {
        public String greet(String name) {
            return "Hello, " + name + "!";
        }
    }

    // Interceptor to add logging
    static class LoggingInterceptor implements MethodInterceptor {
        @Override
        public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
            System.out.println("[CGLIB] Before: " + method.getName());
            Object result = proxy.invokeSuper(obj, args); // call original
            System.out.println("[CGLIB] After:  " + method.getName() + " -> " + result);
            return result;
        }
    }

    public static void main(String[] args) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(GreetingService.class);
        enhancer.setCallback(new LoggingInterceptor());

        GreetingService proxy = (GreetingService) enhancer.create();
        System.out.println(proxy.greet("World"));
    }
}
```

**Output:**

```text
[CGLIB] Before: greet
[CGLIB] After:  greet -> Hello, World!
Hello, World!
```

#### JDK vs CGLIB

| Aspect | JDK Dynamic Proxy | CGLIB |
|---|---|---|
| **Basis** | Implements interfaces | Subclasses the target class |
| **Requirement** | Must implement at least one interface | Any non-final concrete class |
| **Proxy Type** | Implements target interfaces | Extends target class |
| **Proxied Members** | Only interface methods | Public/protected overridable instance methods |
| **Limitations** | Cannot proxy classes/non-interface methods | Cannot proxy `final` classes or `final`/`private` methods |
| **Default in Spring** | When interfaces exist | When no interfaces exist (or `proxy-target-class="true"`) |

#### Guidelines for Using CGLIB

- **Prefer interfaces.** Favor interface-based design + JDK proxies when possible.
- **Use when needed.** Use CGLIB only when the target has no interfaces, or you explicitly need class-based proxies.
- **Avoid `final`.** Do not mark proxy targets or key methods `final` if they need to be intercepted.
- **Delegate correctly.** Always use `proxy.invokeSuper(obj, args)` (not `method.invoke(target, args)`) to call the original implementation.
- **Ensure a no-arg constructor.** The generated subclass requires an accessible no-argument constructor on the superclass.
- **Keep interceptors stateless.** Share callbacks across proxies; avoid storing per-invocation mutable state in interceptor fields.
- **Respect self-invocation.** Calls to other methods on `this` inside the proxied class bypass the proxy (AOP limitation for both JDK and CGLIB).
- **Spring note:** Set `@EnableAspectJAutoProxy(proxyTargetClass = true)` to force CGLIB globally when needed.

---

# 4. What Spring hides

The mini-container intentionally exposes a simplified version of the work that a production container has to perform.

## Spring `ApplicationContext`

At a conceptual level, `ApplicationContext` is a richer container/runtime that manages object definitions, dependency resolution, scopes, lifecycle, events, resource integration, and extension points. Spring's documentation describes DI as a specialized form of IoC: objects declare dependencies through constructors, factory-method arguments, or properties, and the IoC container supplies those dependencies.

Our equivalent is:

```java
MiniApplicationContext context = new MiniApplicationContext();
context.register(...);
context.bind(...);
context.getBean(...);
context.close();
```

Spring hides the mechanics behind much richer metadata and infrastructure.

## Bean lifecycle

Our lifecycle is intentionally small:

```text
construct
-> inject
-> @PostConstruct
-> use
-> @PreDestroy
```

Spring supports multiple lifecycle mechanisms, including `@PostConstruct`, `@PreDestroy`, `InitializingBean`, `DisposableBean`, custom init/destroy methods, and broader `Lifecycle`/`SmartLifecycle` participation. Spring's own docs recommend `@PostConstruct`/`@PreDestroy` when framework-specific lifecycle interfaces are unnecessary. 

## Post-processors

A major hidden layer is `BeanPostProcessor`.

Conceptually:

```text
raw instance
    |
    v
before-initialization processors
    |
    v
initialization callbacks
    |
    v
after-initialization processors
    |
    v
possibly wrapped/proxied reference
```

Spring documents that post-processors can act on each bean instance and may wrap a bean with a proxy; Spring AOP infrastructure uses this mechanism for proxy wrapping. 

That is a major conceptual jump from our toy container:

> **The container may not publish the raw object it instantiated. It may publish a decorated object graph node.**

## Scopes

Our singleton/prototype model is intentionally narrow. Spring's default bean scope is singleton, with prototype and other scopes available. Its singleton is per-container/per-bean rather than the GoF global singleton pattern. 

## Proxies

Our proxy experiment uses JDK dynamic proxies directly.

Spring adds infrastructure around that idea: AOP proxies can be JDK dynamic proxies or CGLIB-generated subclasses, and the proxy can carry an interceptor/advice chain around the target.

---

# 5. The deep model: composition is the center

DI is often taught as a framework feature. A more durable model is:

```text
                ARCHITECTURE
                     |
          +----------+----------+
          |                     |
     stable contracts      concrete details
          |                     |
          +----------+----------+
                     |
               composition root
                     |
             object dependency graph
                     |
                 runtime
```

The **composition root** is the boundary where choices become concrete.

For example:

```java
PaymentGateway gateway = new StripePaymentGateway();
Clock clock = new SystemClock();
OrderService service = new OrderService(gateway, clock);
```

A DI container does not invent composition. It **automates and centralizes it**.

This distinction matters because you can use dependency inversion perfectly well without a DI framework:

```java
// Manual DI
var payment = new StripePaymentGateway();
var clock = new SystemClock();
var orders = new OrderService(payment, clock);
```

Spring can automate the same relationship:

```text
configuration metadata
        |
        v
ApplicationContext
        |
        +--> construct StripePaymentGateway
        +--> construct SystemClock
        +--> construct OrderService
        +--> inject references
        +--> apply lifecycle processors
        +--> possibly publish proxies
```

The design principle exists independently of the framework.

---

# 6. Constructor vs field injection — compare the invariants

| Dimension | Constructor injection | Field injection |
|---|---|---|
| Required dependency visible? | Yes | No |
| Object complete after constructor? | Usually yes | No |
| Immutable field possible? | Yes | Usually no |
| Plain `new` testing | Easy | Container/reflection often needed |
| Dependency list discoverable? | Yes | Less explicit |
| Circular dependencies | Exposed immediately | May permit more container-specific behavior |
| Reflection required | Not for injection | Yes |

The important architectural difference is not “one annotation is better.” It is the **state model of the object**.

Constructor injection favors:

```text
construction == dependency completeness
```

Field injection favors:

```text
construction != dependency completeness
```

That is the deeper trade-off.

---

# 7. What the container is really doing

A simplified `getBean(A)` algorithm is approximately:

```text
getBean(A)
  |
  +-- singleton already exists? --> return cached reference
  |
  +-- cycle detected? -----------> fail
  |
  +-- inspect definition
  |
  +-- choose constructor
  |
  +-- resolve constructor dependencies recursively
  |
  +-- instantiate A
  |
  +-- inject fields
  |
  +-- run initialization callbacks
  |
  +-- cache if singleton
  |
  +-- return reference
```

That is dependency resolution as a recursive graph traversal.

### Complexity intuition

For an acyclic graph with `V` bean instances and `E` dependency edges, a straightforward first-time construction pass is roughly **O(V + E)**, ignoring reflection, class loading, proxy generation, and framework metadata overhead. Singleton caching is what prevents repeated traversal from recreating the same graph nodes.

### The subtle part: identity

The graph is not only about types. It is about **object identity plus scope**.

```text
same type + singleton scope -> same object identity
same type + prototype scope -> different object identities
```

This is why “DI container” and “factory” are not quite synonyms. A container manages a graph whose nodes can have lifecycle and identity policies.

---

# 8. Acceptance checklist

You should be able to answer these without mentioning Spring:

### What is DI?

An object receives its collaborators from an external composition mechanism instead of constructing or locating them itself.

### What is IoC?

Control over a concern such as object creation, assembly, or lifecycle is moved from the application object to an external mechanism.

### What is dependency inversion?

High-level policy should not be forced to depend directly on volatile concrete details. Both can depend on stable abstractions, with concrete implementations selected at the composition boundary.

### What is composition?

The act of selecting concrete components and connecting them into a runnable object graph.

### What does a DI container add?

Automation around composition: registration/metadata, graph resolution, scope/identity, lifecycle, error detection, and often interception/proxying.

### What does Spring hide?

Among other things: dependency resolution, lifecycle callbacks, scopes, post-processors, and proxy/interceptor infrastructure. The mini-container makes these steps explicit so the framework abstraction can later be recognized rather than memorized.

---

# 9. Suggested lab sequence

Run the experiments in this order:

1. Read `OrderService` and `MiniApplicationContext` before running anything.
2. Run the tests and inspect the lifecycle event order.
3. Change `OrderService` from constructor injection to field injection and observe what becomes less explicit.
4. Compare `ConstructorInjectedService` with `FieldInjectedService`.
5. Change a bean from singleton to prototype and compare object identity.
6. Trigger the circular dependency test and follow the construction stack.
7. Read `LoggingProxyExperiment` and relate “wrapper reference” to Spring AOP proxies.
8. Manually wire the same object graph without the container. This is the key control experiment.

The final exercise should be to delete the container entirely and write the composition root by hand. If the application design still makes sense, you understand DI rather than merely understanding the framework.

---

## Production mapping reference

This lab intentionally maps to current Spring Framework concepts, while keeping the implementation independent of Spring:

- IoC/DI concepts: Spring's IoC container and `ApplicationContext`. 
- Lifecycle callbacks: initialization/destruction hooks and lifecycle interfaces.
- Post-processing and proxy wrapping: `BeanPostProcessor` and Spring AOP infrastructure.
- Proxy mechanism: JDK dynamic proxies and CGLIB. 
- Scopes: singleton/prototype semantics. 

At the time of writing, the Spring documentation lists 7.0.9 as the latest stable 7.0 release and 6.2.19 as a stable 6.2 line.
