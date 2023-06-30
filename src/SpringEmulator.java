import annotation.Autowired;
import annotation.Component;
import annotation.PostConstructor;
import annotation.Qualifier;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class SpringEmulator {
    private Set<Class<?>> allComponents;
    public void run(DependencyContainer container, Class<?> callingClass) {
        try {
            String rootPackage = getRootPackage(callingClass);
            List<Class<?>> componentClasses = scanForComponents(rootPackage);
            this.allComponents = new HashSet<>(componentClasses);
            new DependencyChecker().checkDependencies(componentClasses);
            componentsCreating(componentClasses, container);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to run SpringEmulator", e);
        }
    }

    private String getRootPackage(Class<?> callingClass) {
        String className = callingClass.getName();
        int lastDotIndex = className.lastIndexOf('.');
        return lastDotIndex != -1 ? className.substring(0, lastDotIndex) : "";
    }

    private List<Class<?>> scanForComponents(String rootPackage) throws IOException, ClassNotFoundException {
        List<Class<?>> componentClasses = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = rootPackage.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            File directory = new File(resource.getFile());
            if (directory.exists() && directory.isDirectory()) {
                scanDirectoryForComponents(rootPackage, directory, componentClasses);
            }
        }
        return componentClasses;
    }

    private void scanDirectoryForComponents(String rootPackage, File directory, List<Class<?>> componentClasses)
            throws ClassNotFoundException {
        File[] files = directory.listFiles();
        if (files == null)
            return;
        for (File file : files) {
            if (file.isDirectory()) {
                String packageName = rootPackage.isEmpty()
                        ? file.getName()
                        : rootPackage + "." + file.getName();
                scanDirectoryForComponents(packageName, file, componentClasses);
            } else if (file.getName().endsWith(".class")) {
                String className;
                if (rootPackage.isEmpty()) {
                    className = file.getName().substring(0, file.getName().length() - 6);
                } else {
                    className = rootPackage + "." + file.getName().substring(0, file.getName().length() - 6);
                }
                Class<?> clazz = Class.forName(className);
                if (isComponent(clazz)) {
                    componentClasses.add(clazz);
                }
            }
        }
    }

    private boolean isComponent(Class<?> clazz) {
        Annotation annotation = clazz.getAnnotation(Component.class);
        return annotation != null;
    }


    private void componentsCreating(List<Class<?>> componentClasses, DependencyContainer container) {
        Set<Class<?>> toCreateSet = new HashSet<>(componentClasses);
        int lastElementIndex = componentClasses.size() - 1;
        while (lastElementIndex >= 0) {
            Class<?> clazz = componentClasses.get(lastElementIndex);
            componentClasses.remove(lastElementIndex);
            lastElementIndex = componentClasses.size() - 1;
            if (container.isRegistered(clazz)) {
                toCreateSet.remove(clazz);
                continue;
            }
            if (!toCreateSet.contains(clazz)) {
                continue;
            }
            createInstance(clazz, container, toCreateSet);
        }
    }

    private Object createInstance(Class<?> clazz, DependencyContainer container, Set<Class<?>> toCreateSet) {
        Constructor<?> constructor = findConstructor(clazz);
        Object[] parameters = createConstructorParameters(constructor, container, toCreateSet);
        Object instance = createInstance(constructor, parameters);
        runPostConstructorAnnotation(instance);
        container.register(clazz, instance);
        toCreateSet.remove(clazz);
        return instance;
    }



    private void runPostConstructorAnnotation(Object instance) {
        for (Method method : instance.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(PostConstructor.class)) {
                method.setAccessible(true);
                try {
                    method.invoke(instance);
                } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
                    System.err.println("Error while executing method " + instance.getClass().getName() + "." + method.getName());
                }
            }
        }
    }

    private Object[] createConstructorParameters(Constructor<?> constructor, DependencyContainer container, Set<Class<?>> toCreateSet) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
        Object[] parameters = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            Annotation[] annotations = parameterAnnotations[i];

            if (hasQualifierAnnotation(annotations)) {
                String qualifierValue = extractQualifierValue(annotations);
                parameters[i] = resolveParameterWithQualifier(parameterType, qualifierValue, container, toCreateSet);
            } else {
                parameters[i] = resolveParameter(parameterType, container, toCreateSet);
            }
        }

        return parameters;
    }

    private boolean hasQualifierAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Qualifier) {
                return true;
            }
        }
        return false;
    }

    private String extractQualifierValue(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Qualifier) {
                return ((Qualifier) annotation).value();
            }
        }
        throw new IllegalArgumentException("Qualifier annotation not found.");
    }

    private Object resolveParameterWithQualifier(Class<?> parameterType, String qualifierValue, DependencyContainer container, Set<Class<?>> toCreateSet) {
        if (container.isRegistered(qualifierValue)) {
            return container.resolve(parameterType, qualifierValue);
        } else {
            Class<?> resolvedClass = findClassByQualifierValue(toCreateSet, qualifierValue);
            return createInstance(resolvedClass, container, toCreateSet);
        }
    }

    private Object resolveParameter(Class<?> parameterType, DependencyContainer container, Set<Class<?>> toCreateSet) {
        if (isNotCreatableInstance(parameterType, container, toCreateSet)) {
            throw new RuntimeException("Class " + parameterType.getName() + " cannot be created. It is not a component");
        }
        if (container.isRegistered(parameterType)) {
            return container.resolve(parameterType);
        }
        return createInstance(parameterType, container, toCreateSet);
    }

    public boolean isNotCreatableInstance(Class<?> clazz, DependencyContainer container, Set<Class<?>> toCreateSet) {
        for (Class<?> componentClass : allComponents) {
            if (allComponents.contains(clazz)){
                return false;
            }
            if (clazz.isAssignableFrom(componentClass)) {
                Object instance = createInstance(componentClass, container, toCreateSet);
                container.register(clazz, instance);
                return false;
            }
        }
        return true;
    }

    private Class<?> findClassByQualifierValue(Set<Class<?>> toCreateSet, String qualifierValue) {
        for (Class<?> clazz : toCreateSet) {
            if (clazz.isAnnotationPresent(Component.class)) {
                Component componentAnnotation = clazz.getAnnotation(Component.class);
                if (componentAnnotation.value().equals(qualifierValue)) {
                    return clazz;
                }
            }
        }
        throw new IllegalArgumentException("Class with qualifier value '" + qualifierValue + "' not found.");
    }


    private Constructor<?> findConstructor(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            if (constructor.isAnnotationPresent(Autowired.class)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        Constructor<?> constructor = constructors[0];
        constructor.setAccessible(true);
        return constructor;
    }

    private <T> T createInstance(Constructor<?> constructor, Object[] parameters) {
        try {
            return (T) constructor.newInstance(parameters);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to create instance using constructor: " + constructor, e);
        }
    }
}