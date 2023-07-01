package customEventBus;

import customEventBus.Event;

public class PostProcessComponentEvent extends Event {
    private final Class<?> componentClass;

    public PostProcessComponentEvent(Class<?> componentClass) {
        this.componentClass = componentClass;
    }

    public Class<?> getComponentClass() {
        return componentClass;
    }
}