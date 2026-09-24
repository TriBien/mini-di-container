package com.example.minidi.core;

import com.example.minidi.annotations.Component;
import com.example.minidi.annotations.Inject;
import com.example.minidi.annotations.PostConstruct;
import com.example.minidi.annotations.PreDestroy;
import com.example.minidi.annotations.Scope;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately small DI container.
 *
 * It supports:
 * - explicit registrations and interface -> implementation bindings
 * - constructor injection
 * - field injection
 * - @PostConstruct / @PreDestroy callbacks
 * - singleton and prototype scope
 * - circular dependency detection for construction
 */
public final class MiniApplicationContext implements AutoCloseable {
    private final Map<Class<?>, BeanDefinition> definitions = new LinkedHashMap<>();
    private final Map<Class<?>, Object> singletonBeans = new LinkedHashMap<>();
    private final List<BeanDefinition> singletonCreationOrder = new ArrayList<>();
    private final ThreadLocal<Deque<Class<?>>> constructionStack =
            ThreadLocal.withInitial(ArrayDeque::new);

    public <T> MiniApplicationContext register(Class<T> implementation) {
        Component component = implementation.getAnnotation(Component.class);
        BeanScope scope = component == null ? BeanScope.SINGLETON : component.scope();
        Scope explicitScope = implementation.getAnnotation(Scope.class);
        if (explicitScope != null) {
            scope = explicitScope.value();
        }

        definitions.put(implementation,
                new BeanDefinition(implementation, implementation, scope));
        return this;
    }

    public <T, I extends T> MiniApplicationContext bind(Class<T> contract, Class<I> implementation) {
        register(implementation);
        BeanDefinition implementationDefinition = definitions.get(implementation);
        definitions.put(contract,
                new BeanDefinition(contract, implementation, implementationDefinition.scope()));
        return this;
    }

    public <T> T getBean(Class<T> contract) {
        BeanDefinition definition = definitions.get(contract);
        if (definition == null) {
            if (!contract.isInterface() && !Modifier.isAbstract(contract.getModifiers())) {
                register(contract);
                definition = definitions.get(contract);
            } else {
                throw new DiException("No bean definition for " + contract.getName());
            }
        }
        return contract.cast(getOrCreate(definition));
    }

    private Object getOrCreate(BeanDefinition definition) {
        if (definition.scope() == BeanScope.SINGLETON) {
            Object existing = singletonBeans.get(definition.implementation());
            if (existing != null) {
                return existing;
            }
        }

        Deque<Class<?>> stack = constructionStack.get();
        if (stack.contains(definition.contract())) {
            throw new DiException("Circular dependency detected: " + formatCycle(stack, definition.contract()));
        }

        stack.addLast(definition.contract());
        try {
            Object bean = instantiate(definition.implementation());
            injectFields(bean);
            invokeLifecycle(bean, PostConstruct.class);

            if (definition.scope() == BeanScope.SINGLETON) {
                singletonBeans.put(definition.implementation(), bean);
                singletonCreationOrder.add(definition);
            }
            return bean;
        } finally {
            stack.removeLast();
            if (stack.isEmpty()) {
                constructionStack.remove();
            }
        }
    }

    private Object instantiate(Class<?> implementation) {
        Constructor<?> constructor = selectConstructor(implementation);
        try {
            constructor.setAccessible(true);
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            Object[] arguments = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                arguments[i] = getBean(parameterTypes[i]);
            }
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException e) {
            throw new DiException("Constructor of " + implementation.getName() + " threw an exception", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new DiException("Could not instantiate " + implementation.getName(), e);
        }
    }

    private Constructor<?> selectConstructor(Class<?> implementation) {
        Constructor<?>[] constructors = implementation.getDeclaredConstructors();
        List<Constructor<?>> injected = List.of(constructors).stream()
                .filter(c -> c.isAnnotationPresent(Inject.class))
                .toList();

        if (injected.size() > 1) {
            throw new DiException("Multiple @Inject constructors found on " + implementation.getName());
        }
        if (injected.size() == 1) {
            return injected.get(0);
        }
        if (constructors.length == 1) {
            return constructors[0];
        }
        try {
            return implementation.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new DiException("No unambiguous constructor for " + implementation.getName()
                    + "; add @Inject to the intended constructor");
        }
    }

    private void injectFields(Object bean) {
        for (Class<?> type = bean.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Inject.class)) {
                    continue;
                }
                if (Modifier.isStatic(field.getModifiers())) {
                    throw new DiException("Cannot inject static field " + field);
                }
                Object dependency = getBean(field.getType());
                try {
                    field.setAccessible(true);
                    field.set(bean, dependency);
                } catch (IllegalAccessException e) {
                    throw new DiException("Could not inject field " + field, e);
                }
            }
        }
    }

    private void invokeLifecycle(Object bean, Class<? extends java.lang.annotation.Annotation> annotationType) {
        for (Class<?> type = bean.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(annotationType)) {
                    continue;
                }
                if (method.getParameterCount() != 0) {
                    throw new DiException("Lifecycle method must have no parameters: " + method);
                }
                try {
                    method.setAccessible(true);
                    method.invoke(bean);
                } catch (InvocationTargetException e) {
                    throw new DiException("Lifecycle method failed: " + method, e.getCause());
                } catch (ReflectiveOperationException e) {
                    throw new DiException("Could not invoke lifecycle method: " + method, e);
                }
            }
        }
    }

    private String formatCycle(Deque<Class<?>> stack, Class<?> repeating) {
        List<String> path = new ArrayList<>();
        boolean include = false;
        for (Class<?> type : stack) {
            if (type.equals(repeating)) {
                include = true;
            }
            if (include) {
                path.add(type.getSimpleName());
            }
        }
        path.add(repeating.getSimpleName());
        return String.join(" -> ", path);
    }

    @Override
    public void close() {
        for (int i = singletonCreationOrder.size() - 1; i >= 0; i--) {
            BeanDefinition definition = singletonCreationOrder.get(i);
            Object bean = singletonBeans.get(definition.implementation());
            if (bean != null) {
                invokeLifecycle(bean, PreDestroy.class);
            }
        }
        singletonBeans.clear();
        singletonCreationOrder.clear();
    }
}
