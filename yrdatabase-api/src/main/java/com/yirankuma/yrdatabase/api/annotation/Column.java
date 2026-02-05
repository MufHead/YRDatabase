package com.yirankuma.yrdatabase.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a database column with custom configuration.
 *
 * @author YiranKuma
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {

    /**
     * Column name. If not specified, uses field name in snake_case.
     *
     * @return Column name
     */
    String value() default "";

    /**
     * SQL column type. If not specified, infers from Java type.
     *
     * @return SQL type
     */
    String type() default "";

    /**
     * Whether the column can be null.
     *
     * @return Nullable status
     */
    boolean nullable() default true;

    /**
     * Default value expression (SQL).
     *
     * @return Default value
     */
    String defaultValue() default "";

    /**
     * Column length (for VARCHAR, etc.).
     *
     * @return Column length
     */
    int length() default 255;
}
