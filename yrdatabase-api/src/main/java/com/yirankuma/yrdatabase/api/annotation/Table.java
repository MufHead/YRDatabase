package com.yirankuma.yrdatabase.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a database table entity.
 *
 * @author YiranKuma
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {

    /**
     * Table name. If not specified, uses class name in snake_case.
     *
     * @return Table name
     */
    String value() default "";

    /**
     * Default cache TTL in seconds. -1 means use global default.
     *
     * @return Cache TTL
     */
    long cacheTTL() default -1;
}
