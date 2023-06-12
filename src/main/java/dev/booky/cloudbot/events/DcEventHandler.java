package dev.booky.cloudbot.events;
// Created by booky10 in CloudBot (13:22 12.06.23)

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DcEventHandler {

    /**
     * <code>-500</code> will be executed before <code>500</code>.
     */
    int priority() default 0;
}
