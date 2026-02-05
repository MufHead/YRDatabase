package com.yirankuma.yrdatabase.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be indexed for faster queries.
 *
 * @author YiranKuma
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Index {

    /**
     * Index name. If not specified, auto-generates.
     *
     * @return Index name
     */
    String value() default "";

    /**
     * Whether the index is unique.
     *
     * @return Unique status
     */
    boolean unique() default false;
}
