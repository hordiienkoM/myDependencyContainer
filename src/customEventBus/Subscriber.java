package customEventBus;



public interface Subscriber {
    void handleEvent(Event event);
}
