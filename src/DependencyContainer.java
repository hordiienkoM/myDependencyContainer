import annotation.Component;
import prePostProcessor.ComponentPostProcessor;
import prePostProcessor.ComponentPreProcessor;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DependencyContainer {
    private final Map<Class<?>, Object> dependencies;
    private final Map<String, Object> specifiedDependencies;
    private final List<ComponentPreProcessor> preProcessors;
    private final List<ComponentPostProcessor> postProcessors;

    public DependencyContainer() {
        dependencies = new HashMap<>();
        specifiedDependencies = new HashMap<>();
        preProcessors = new LinkedList<>();
        postProcessors = new LinkedList<>();
    }

    public <T> void register(Class<T> clazz, Object instance) {
        startPreProcessors(clazz);
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
        startPostProcessors(clazz);
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

    public void addPreProcessor(ComponentPreProcessor processor) {
        preProcessors.add(processor);
    }

    public void addPostProcessor(ComponentPostProcessor processor) {
        postProcessors.add(processor);
    }

    private <T> void startPreProcessors(Class<T> clazz) {
        for (ComponentPreProcessor processor : preProcessors) {
            processor.preProcess(clazz);
        }
    }

    private <T> void startPostProcessors(Class<T> clazz) {
        for (ComponentPostProcessor processor : postProcessors) {
            processor.postProcess(clazz);
        }
    }
}
