import annotation.Autowired;
import annotation.Component;
import annotation.PostConstructor;
import annotation.Qualifier;
import customEventBus.EventBus;
import customEventBus.PostProcessComponentEvent;
import customEventBus.PreProcessComponentEvent;
import customEventBus.Subscriber;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class DependencyContainer {
    private final Map<Class<?>, Object> dependencies;
    private final Map<String, Object> specifiedDependencies;
    private final EventBus eventBus;

    private Set<Class<?>> allComponents;

    private String rootPackage;
    private ClassLoader classLoader;

    public DependencyContainer() {
        dependencies = new HashMap<>();
        specifiedDependencies = new HashMap<>();
        eventBus = new EventBus();
    }

    public void init(String rootPackage, ClassLoader classLoader) {
        this.rootPackage = rootPackage;
        this.classLoader = classLoader;
        run();
    }
    public void run() {
        try {
            List<Class<?>> componentClasses = scanForComponents(this.rootPackage, this.classLoader);
            this.allComponents = new HashSet<>(componentClasses);
            new DependencyChecker().checkDependencies(componentClasses);
            componentsCreating(componentClasses);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to run SpringEmulator", e);
        }
    }

    public void register(Object instance) {
        Class<?> clazz = instance.getClass();
        eventBus.post(new PreProcessComponentEvent(clazz));
        Annotation[] annotations = clazz.getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation instanceof Component) {
                String value = ((Component) annotation).value();
                if (!value.isEmpty()) {
                    specifiedDependencies.put(value, instance);
                }
                break;
            }
        }
        dependencies.put(clazz, instance);
        eventBus.post(new PostProcessComponentEvent(clazz));
    }

    public <T> T resolve(Class<T> clazz) {
        return clazz.cast(dependencies.get(clazz));
    }

    public <T> T resolve(Class<T> clazz, String qualifierValue) {
        return clazz.cast(specifiedDependencies.get(qualifierValue));
    }

    public boolean isRegistered(Class<?> clazz) {
        return dependencies.containsKey(clazz);
    }

    public boolean isRegistered(String qualifierValue) {
        return specifiedDependencies.containsKey(qualifierValue);
    }

    private List<Class<?>> scanForComponents(String rootPackage, ClassLoader classLoader) throws IOException, ClassNotFoundException {
        List<Class<?>> componentClasses = new ArrayList<>();
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


    private void componentsCreating(List<Class<?>> componentClasses) {
        Set<Class<?>> toCreateSet = new HashSet<>(componentClasses);
        int lastElementIndex = componentClasses.size() - 1;
        while (lastElementIndex >= 0) {
            Class<?> clazz = componentClasses.get(lastElementIndex);
            componentClasses.remove(lastElementIndex);
            lastElementIndex = componentClasses.size() - 1;
            if (isRegistered(clazz)) {
                toCreateSet.remove(clazz);
                continue;
            }
            if (!toCreateSet.contains(clazz)) {
                continue;
            }
            createInstance(clazz, toCreateSet);
        }
    }

    private Object createInstance(Class<?> clazz, Set<Class<?>> toCreateSet) {
        Constructor<?> constructor = findConstructor(clazz);
        Object[] parameters = createConstructorParameters(constructor, toCreateSet);
        Object instance = createInstance(constructor, parameters);
        runPostConstructorAnnotation(instance);
        register(instance);
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
    public void addSubscriber(Subscriber subscriber) {
        eventBus.register(PreProcessComponentEvent.class, subscriber);
        eventBus.register(PostProcessComponentEvent.class, subscriber);
    }

    public void removeSubscriber(Subscriber subscriber) {
        eventBus.unregister(PreProcessComponentEvent.class, subscriber);
        eventBus.unregister(PostProcessComponentEvent.class, subscriber);
    }

    private Object[] createConstructorParameters(Constructor<?> constructor, Set<Class<?>> toCreateSet) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
        Object[] parameters = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            Annotation[] annotations = parameterAnnotations[i];

            if (hasQualifierAnnotation(annotations)) {
                String qualifierValue = extractQualifierValue(annotations);
                parameters[i] = resolveParameterWithQualifier(parameterType, qualifierValue, toCreateSet);
            } else {
                parameters[i] = resolveParameter(parameterType, toCreateSet);
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

    private Object resolveParameterWithQualifier(Class<?> parameterType, String qualifierValue, Set<Class<?>> toCreateSet) {
        if (isRegistered(qualifierValue)) {
            return resolve(parameterType, qualifierValue);
        } else {
            Class<?> resolvedClass = findClassByQualifierValue(toCreateSet, qualifierValue);
            return createInstance(resolvedClass, toCreateSet);
        }
    }

    private Object resolveParameter(Class<?> parameterType, Set<Class<?>> toCreateSet) {
        if (isNotCreatableInstance(parameterType, toCreateSet)) {
            throw new RuntimeException("Class " + parameterType.getName() + " cannot be created. It is not a component");
        }
        if (isRegistered(parameterType)) {
            return resolve(parameterType);
        }
        return createInstance(parameterType, toCreateSet);
    }

    public boolean isNotCreatableInstance(Class<?> clazz, Set<Class<?>> toCreateSet) {
        for (Class<?> componentClass : allComponents) {
            if (allComponents.contains(clazz)){
                return false;
            }
            if (clazz.isAssignableFrom(componentClass)) {
                Object instance = createInstance(componentClass, toCreateSet);
                register(instance);
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
