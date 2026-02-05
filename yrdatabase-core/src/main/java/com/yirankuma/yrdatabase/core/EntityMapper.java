package com.yirankuma.yrdatabase.core;

import com.yirankuma.yrdatabase.api.annotation.Column;
import com.yirankuma.yrdatabase.api.annotation.PrimaryKey;
import com.yirankuma.yrdatabase.api.annotation.Table;
import com.yirankuma.yrdatabase.api.annotation.Transient;
import lombok.Data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Maps entity classes to database schema and handles serialization.
 *
 * @author YiranKuma
 */
public class EntityMapper<T> {

    private final Class<T> entityClass;
    private final String tableName;
    private final Map<String, FieldMapping> fieldMappings;
    private final String primaryKeyColumn;
    private final Field primaryKeyField;

    public EntityMapper(Class<T> entityClass) {
        this.entityClass = entityClass;
        this.tableName = resolveTableName(entityClass);
        this.fieldMappings = new LinkedHashMap<>();
        
        String pkColumn = null;
        Field pkField = null;

        for (Field field : getAllFields(entityClass)) {
            if (shouldSkipField(field)) {
                continue;
            }

            FieldMapping mapping = createFieldMapping(field);
            fieldMappings.put(mapping.getColumnName(), mapping);

            if (field.isAnnotationPresent(PrimaryKey.class)) {
                pkColumn = mapping.getColumnName();
                pkField = field;
            }
        }

        this.primaryKeyColumn = pkColumn != null ? pkColumn : "id";
        this.primaryKeyField = pkField;
    }

    private String resolveTableName(Class<T> clazz) {
        Table tableAnn = clazz.getAnnotation(Table.class);
        if (tableAnn != null && !tableAnn.value().isEmpty()) {
            return tableAnn.value();
        }
        return toSnakeCase(clazz.getSimpleName());
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    private boolean shouldSkipField(Field field) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
            return true;
        }
        return field.isAnnotationPresent(Transient.class);
    }

    private FieldMapping createFieldMapping(Field field) {
        field.setAccessible(true);

        String columnName;
        String sqlType;
        boolean nullable = true;
        String defaultValue = null;

        // Check for @Column annotation
        Column columnAnn = field.getAnnotation(Column.class);
        if (columnAnn != null) {
            columnName = columnAnn.value().isEmpty() ? toSnakeCase(field.getName()) : columnAnn.value();
            sqlType = columnAnn.type().isEmpty() ? inferSqlType(field.getType(), columnAnn.length()) : columnAnn.type();
            nullable = columnAnn.nullable();
            defaultValue = columnAnn.defaultValue().isEmpty() ? null : columnAnn.defaultValue();
        } else {
            columnName = toSnakeCase(field.getName());
            sqlType = inferSqlType(field.getType(), 255);
        }

        // Check for @PrimaryKey annotation
        PrimaryKey pkAnn = field.getAnnotation(PrimaryKey.class);
        if (pkAnn != null) {
            if (!pkAnn.value().isEmpty()) {
                columnName = pkAnn.value();
            }
            sqlType = sqlType + " PRIMARY KEY";
            nullable = false;
        }

        return new FieldMapping(field, columnName, sqlType, nullable, defaultValue);
    }

    private String inferSqlType(Class<?> type, int length) {
        if (type == String.class) {
            return "VARCHAR(" + length + ")";
        } else if (type == int.class || type == Integer.class) {
            return "INT";
        } else if (type == long.class || type == Long.class) {
            return "BIGINT";
        } else if (type == boolean.class || type == Boolean.class) {
            return "TINYINT(1)";
        } else if (type == double.class || type == Double.class) {
            return "DOUBLE";
        } else if (type == float.class || type == Float.class) {
            return "FLOAT";
        } else if (type == byte[].class) {
            return "BLOB";
        } else if (type == java.util.Date.class || type == java.sql.Timestamp.class) {
            return "TIMESTAMP";
        }
        return "TEXT";
    }

    private String toSnakeCase(String camelCase) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    // ==================== Public API ====================

    public String getTableName() {
        return tableName;
    }

    public String getPrimaryKeyColumn() {
        return primaryKeyColumn;
    }

    public Map<String, String> getTableSchema() {
        Map<String, String> schema = new LinkedHashMap<>();
        for (FieldMapping mapping : fieldMappings.values()) {
            StringBuilder typeDef = new StringBuilder(mapping.getSqlType());
            if (!mapping.isNullable()) {
                typeDef.append(" NOT NULL");
            }
            if (mapping.getDefaultValue() != null) {
                typeDef.append(" DEFAULT ").append(mapping.getDefaultValue());
            }
            schema.put(mapping.getColumnName(), typeDef.toString());
        }
        return schema;
    }

    public Map<String, Object> toMap(T entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (FieldMapping mapping : fieldMappings.values()) {
            try {
                Object value = mapping.getField().get(entity);
                map.put(mapping.getColumnName(), value);
            } catch (IllegalAccessException e) {
                // Skip inaccessible fields
            }
        }
        return map;
    }

    public T fromMap(Map<String, Object> map) {
        try {
            T entity = entityClass.getDeclaredConstructor().newInstance();
            for (FieldMapping mapping : fieldMappings.values()) {
                Object value = map.get(mapping.getColumnName());
                if (value != null) {
                    mapping.getField().set(entity, convertValue(value, mapping.getField().getType()));
                }
            }
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create entity from map", e);
        }
    }

    public String getPrimaryKeyValue(T entity) {
        if (primaryKeyField == null) {
            return null;
        }
        try {
            Object value = primaryKeyField.get(entity);
            return value != null ? value.toString() : null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType.isInstance(value)) {
            return value;
        }

        // Number conversions
        if (value instanceof Number) {
            Number num = (Number) value;
            if (targetType == int.class || targetType == Integer.class) {
                return num.intValue();
            } else if (targetType == long.class || targetType == Long.class) {
                return num.longValue();
            } else if (targetType == double.class || targetType == Double.class) {
                return num.doubleValue();
            } else if (targetType == float.class || targetType == Float.class) {
                return num.floatValue();
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                return num.intValue() != 0;
            }
        }

        // String to primitive conversions
        if (value instanceof String) {
            String str = (String) value;
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(str);
            } else if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(str);
            } else if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(str);
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(str);
            }
        }

        return value;
    }

    @Data
    public static class FieldMapping {
        private final Field field;
        private final String columnName;
        private final String sqlType;
        private final boolean nullable;
        private final String defaultValue;
    }
}
