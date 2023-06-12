package dev.booky.cloudbot.events;
// Created by booky10 in CloudBot (13:30 12.06.23)

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import discord4j.core.event.domain.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DcEventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("CloudBot");
    private final ListMultimap<Class<?>, EventEntry> events = ArrayListMultimap.create();

    public Mono<Void> invoke(Event event) {
        List<EventEntry> events = new ArrayList<>();
        Class<?> eventClass = event.getClass();
        do {
            events.addAll(this.events.get(eventClass));
            eventClass = eventClass.getSuperclass();
        } while (eventClass != Event.class && eventClass != null);
        events.sort(Comparator.comparingInt(EventEntry::priority));

        System.out.println("dispatching " + event.getClass().getSimpleName() + " to " + events.size() + " receivers");
        Mono<Void> mono = Mono.empty();
        for (EventEntry entry : events) {
            Mono<Void> entryMono = entry.invoke(event);
            System.out.println("invoked invoker of " + entry.listener().getClass().getSimpleName() + ": " + entry.invoker());
            if (entryMono != Mono.<Void>empty()) {
                mono = mono.and(entryMono);
            }
        }
        return mono;
    }

    // see comment at cast for reason why this isn't unchecked
    @SuppressWarnings("unchecked")
    public void register(DcListener listener) {
        MethodHandles.Lookup lookup;
        try {
            lookup = MethodHandles.privateLookupIn(listener.getClass(), MethodHandles.lookup());
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Can't access listener class" + listener.getClass().getName());
        }

        for (Method method : listener.getClass().getDeclaredMethods()) {
            method.trySetAccessible();

            int mods = method.getModifiers();
            if (Modifier.isInterface(mods) || Modifier.isAbstract(mods)
                    || Modifier.isNative(mods) || Modifier.isStatic(mods)) {
                continue;
            }

            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1 || !Event.class.isAssignableFrom(parameters[0])) {
                continue;
            }
            if (method.getReturnType() != Mono.class) {
                continue;
            }

            DcEventHandler handlerAnnot = method.getAnnotation(DcEventHandler.class);
            if (handlerAnnot == null) {
                continue;
            }

            try {
                // can be safely cast, is checked before
                Class<? extends Event> eventClass = (Class<? extends Event>) parameters[0];
                EventEntry entry = new EventEntry(listener, handlerAnnot.priority(), lookup.unreflect(method));
                this.events.put(eventClass, entry);
            } catch (IllegalAccessException exception) {
                throw new IllegalArgumentException("Can't access event method " + method.getName()
                        + " in listener class " + listener.getClass().getName());
            }
        }
    }

    private record EventEntry(
            DcListener listener,
            int priority,
            MethodHandle invoker
    ) {

        public Mono<Void> invoke(Event event) {
            try {
                Mono<?> mono = (Mono<?>) this.invoker.invoke(this.listener, event);
                return mono.then();
            } catch (Throwable throwable) {
                LOGGER.warn("Error occurred while invoking listener for event {}",
                        event.getClass(), throwable);
                return Mono.empty();
            }
        }
    }
}
