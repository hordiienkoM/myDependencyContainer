import customEventBus.Subscriber;
import java.util.List;

public class IoC {
    private static final DependencyContainer container = new DependencyContainer();

    private IoC() {}

    public static void init(String rootPackage, ClassLoader classLoader) {
        container.init(rootPackage, classLoader);
    }

    public static void init(String rootPackage, ClassLoader classLoader, List<Subscriber> processors) {
        addProcessors(processors);
        init(rootPackage, classLoader);
    }

    public static void registerBean(Object instance) {
        container.register(instance);
    }

    public static void addProcessors(List<Subscriber> processors) {
        for (Subscriber processor: processors) {
            container.addSubscriber(processor);
        }
    }

    public static void removeProcessor(Subscriber subscriber) {
        container.removeSubscriber(subscriber);
    }

    public static <T> T getBean(Class<T> clazz) {
        return container.resolve(clazz);
    }
}