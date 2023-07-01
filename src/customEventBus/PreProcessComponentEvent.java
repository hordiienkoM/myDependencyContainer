package customEventBus;

import customEventBus.Event;

public class PreProcessComponentEvent extends Event {
    private final Class<?> componentClass;

    public PreProcessComponentEvent(Class<?> componentClass) {
        this.componentClass = componentClass;
    }

    public Class<?> getComponentClass() {
        return componentClass;
    }
}
