package customEventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventBus {
    private final Map<Class<?>, List<Subscriber>> subscribers;

    public EventBus() {
        this.subscribers = new HashMap<>();
    }

    public void register(Class<?> eventType, Subscriber subscriber) {
        subscribers.computeIfAbsent(eventType, key -> new ArrayList<>()).add(subscriber);
    }

    public void unregister(Class<?> eventType, Subscriber subscriber) {
        List<Subscriber> eventSubscribers = subscribers.get(eventType);
        if (eventSubscribers != null) {
            eventSubscribers.remove(subscriber);
            if (eventSubscribers.isEmpty()) {
                subscribers.remove(eventType);
            }
        }
    }

    public void post(Event event) {
        List<Subscriber> eventSubscribers = subscribers.get(event.getClass());
        if (eventSubscribers != null) {
            for (Subscriber subscriber : eventSubscribers) {
                subscriber.handleEvent(event);
            }
        }
    }
}
