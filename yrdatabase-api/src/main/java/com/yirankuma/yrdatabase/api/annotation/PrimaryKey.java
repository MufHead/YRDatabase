package com.yirankuma.yrdatabase.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the primary key of the table.
 *
 * @author YiranKuma
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PrimaryKey {

    /**
     * Column name. If not specified, uses field name in snake_case.
     *
     * @return Column name
     */
    String value() default "";

    /**
     * Whether the primary key is auto-generated.
     *
     * @return Auto-generate status
     */
    boolean autoGenerate() default false;
}
