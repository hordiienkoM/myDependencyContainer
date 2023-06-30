import annotation.Autowired;
import annotation.Component;

import java.lang.reflect.Constructor;
import java.util.*;

public class DependencyChecker {
    private Set<Class<?>> visited;
    private Stack<Class<?>> stack;

    public void checkDependencies(List<Class<?>> componentClasses) {
        visited = new HashSet<>();
        stack = new Stack<>();

        for (Class<?> clazz : componentClasses) {
            if (!visited.contains(clazz)) {
                checkDependenciesRecursive(clazz);
            }
        }
    }

    private void checkDependenciesRecursive(Class<?> clazz) {
        visited.add(clazz);
        stack.push(clazz);

        // Получите список зависимостей класса clazz, например, через рефлексию
        Set<Class<?>> dependencies = getDependencies(clazz);

        for (Class<?> dependency : dependencies) {
            if (!visited.contains(dependency)) {
                checkDependenciesRecursive(dependency);
            } else if (stack.contains(dependency)) {
                // Найдена круговая зависимость
                String report = reportCircularDependency(stack, dependency);
                // Выбросьте исключение или выполните другую обработку
                throw new RuntimeException(report);
            }
        }

        stack.pop();
    }

    private Set<Class<?>> getDependencies(Class<?> clazz) {
        Set<Class<?>> dependencies = new HashSet<>();
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        for (Constructor<?> constructor : constructors) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (constructor.isAnnotationPresent(Autowired.class)) {
                dependencies = new HashSet<>();
                addDependencies(parameterTypes, dependencies);
                return dependencies;
            }
            addDependencies(parameterTypes, dependencies);
        }

        return dependencies;
    }

    private void addDependencies(Class<?>[] parameterTypes, Set<Class<?>> dependencies) {
        for (Class<?> parameterType : parameterTypes) {
            if (parameterType.isAnnotationPresent(Component.class)) {
                dependencies.add(parameterType);
            }
        }
    }

    private String reportCircularDependency(Stack<Class<?>> stack, Class<?> dependency) {
        StringBuilder sb = new StringBuilder();
        sb.append("Circular dependency detected:\n");
        for (Class<?> clazz : stack) {
            sb.append(clazz.getName()).append(" -> ");
        }
        sb.append(dependency.getName());
        return sb.toString();
    }
}

