package com.yirankuma.yrdatabase.impl;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;

/**
 * Gson 类型适配器，用于修复 Redis 序列化/反序列化问题
 */
public class GsonTypeAdapters {

    /**
     * 统一的日期格式，保持 MySQL 时间格式 "yyyy-MM-dd HH:mm:ss"
     */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * Timestamp 类型适配器
     * 修复问题1：保持 MySQL 时间格式 "yyyy-MM-dd HH:mm:ss"
     */
    public static class TimestampAdapter implements JsonSerializer<Timestamp>, JsonDeserializer<Timestamp> {
        @Override
        public JsonElement serialize(Timestamp src, Type typeOfSrc, JsonSerializationContext context) {
            synchronized (DATE_FORMAT) {
                return new JsonPrimitive(DATE_FORMAT.format(src));
            }
        }

        @Override
        public Timestamp deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                synchronized (DATE_FORMAT) {
                    return new Timestamp(DATE_FORMAT.parse(json.getAsString()).getTime());
                }
            } catch (ParseException e) {
                throw new JsonParseException("Failed to parse timestamp: " + json.getAsString(), e);
            }
        }
    }

    /**
     * Date 类型适配器
     * 修复问题1：保持 MySQL 时间格式 "yyyy-MM-dd HH:mm:ss"
     * 支持 java.util.Date 类型（很多插件使用 new Date()）
     */
    public static class DateAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {
        @Override
        public JsonElement serialize(Date src, Type typeOfSrc, JsonSerializationContext context) {
            synchronized (DATE_FORMAT) {
                return new JsonPrimitive(DATE_FORMAT.format(src));
            }
        }

        @Override
        public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                synchronized (DATE_FORMAT) {
                    return DATE_FORMAT.parse(json.getAsString());
                }
            } catch (ParseException e) {
                throw new JsonParseException("Failed to parse date: " + json.getAsString(), e);
            }
        }
    }

    /**
     * 数字类型适配器
     * 修复问题2：根据数值大小和是否包含小数点智能判断类型
     * - 整数范围内且无小数点 → Integer/Long
     * - 包含小数点或超出范围 → Double
     */
    public static class NumberAdapter implements JsonDeserializer<Number> {
        @Override
        public Number deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (!json.isJsonPrimitive()) {
                throw new JsonParseException("Expected a primitive number");
            }

            String numberStr = json.getAsString();

            // 如果包含小数点，返回 Double
            if (numberStr.contains(".")) {
                return json.getAsDouble();
            }

            // 尝试解析为整数
            try {
                long longValue = json.getAsLong();

                // 如果在 Integer 范围内，返回 Integer，否则返回 Long
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    return (int) longValue;
                } else {
                    return longValue;
                }
            } catch (NumberFormatException e) {
                // 如果解析失败，返回 Double
                return json.getAsDouble();
            }
        }
    }
}
