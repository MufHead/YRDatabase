package com.yirankuma.yrdatabase.common.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息编解码器
 * 负责将PluginMessage序列化为字节流，以及反序列化
 *
 * 协议格式 (二进制):
 * [魔数4字节][版本1字节][消息类型1字节][时间戳8字节][数据长度4字节][JSON数据N字节][校验和4字节]
 */
public class MessageCodec {
    // 魔数，用于识别合法消息（"YRDB"的ASCII）
    private static final int MAGIC_NUMBER = 0x59524442;

    // 协议版本
    private static final byte PROTOCOL_VERSION = 0x01;

    // 最大消息大小 (1MB)
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024;

    private static final Gson GSON = new GsonBuilder().create();

    /**
     * 编码消息为字节数组
     */
    public static byte[] encode(PluginMessage message) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        try {
            // 1. 写入魔数
            dos.writeInt(MAGIC_NUMBER);

            // 2. 写入协议版本
            dos.writeByte(PROTOCOL_VERSION);

            // 3. 写入消息类型
            dos.writeByte(message.getType().getId());

            // 4. 写入时间戳
            dos.writeLong(message.getTimestamp());

            // 5. 将数据序列化为JSON
            String jsonData = GSON.toJson(message.getData());
            byte[] jsonBytes = jsonData.getBytes(StandardCharsets.UTF_8);

            // 6. 写入数据长度
            dos.writeInt(jsonBytes.length);

            // 7. 写入JSON数据
            dos.write(jsonBytes);

            // 8. 计算并写入校验和（简单的CRC32）
            byte[] allData = baos.toByteArray();
            int checksum = calculateChecksum(allData);
            dos.writeInt(checksum);

            return baos.toByteArray();
        } finally {
            dos.close();
        }
    }

    /**
     * 解码字节数组为消息
     */
    public static PluginMessage decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 22) { // 最小长度：4+1+1+8+4+0+4=22
            throw new IOException("Invalid message: too short");
        }

        if (bytes.length > MAX_MESSAGE_SIZE) {
            throw new IOException("Invalid message: exceeds max size");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // 1. 验证魔数
        int magic = buffer.getInt();
        if (magic != MAGIC_NUMBER) {
            throw new IOException("Invalid message: wrong magic number");
        }

        // 2. 验证协议版本
        byte version = buffer.get();
        if (version != PROTOCOL_VERSION) {
            throw new IOException("Invalid message: unsupported protocol version " + version);
        }

        // 3. 读取消息类型
        byte typeId = buffer.get();
        MessageType type;
        try {
            type = MessageType.fromId(typeId);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid message: unknown type " + typeId);
        }

        // 4. 读取时间戳
        long timestamp = buffer.getLong();

        // 5. 读取数据长度
        int dataLength = buffer.getInt();
        if (dataLength < 0 || dataLength > MAX_MESSAGE_SIZE - 22) {
            throw new IOException("Invalid message: invalid data length " + dataLength);
        }

        // 6. 读取JSON数据
        byte[] jsonBytes = new byte[dataLength];
        buffer.get(jsonBytes);
        String jsonData = new String(jsonBytes, StandardCharsets.UTF_8);

        // 7. 验证校验和
        int receivedChecksum = buffer.getInt();
        byte[] dataToCheck = new byte[bytes.length - 4]; // 除去校验和的部分
        System.arraycopy(bytes, 0, dataToCheck, 0, dataToCheck.length);
        int calculatedChecksum = calculateChecksum(dataToCheck);

        if (receivedChecksum != calculatedChecksum) {
            throw new IOException("Invalid message: checksum mismatch");
        }

        // 8. 解析JSON数据
        Map<String, Object> data;
        try {
            JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();
            data = new HashMap<>();
            jsonObject.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) {
                    if (entry.getValue().getAsJsonPrimitive().isNumber()) {
                        data.put(entry.getKey(), entry.getValue().getAsNumber());
                    } else if (entry.getValue().getAsJsonPrimitive().isBoolean()) {
                        data.put(entry.getKey(), entry.getValue().getAsBoolean());
                    } else {
                        data.put(entry.getKey(), entry.getValue().getAsString());
                    }
                } else {
                    data.put(entry.getKey(), entry.getValue().toString());
                }
            });
        } catch (Exception e) {
            throw new IOException("Invalid message: failed to parse JSON data", e);
        }

        // 9. 创建消息对象（手动设置时间戳）
        return new PluginMessage(type, data) {
            @Override
            public long getTimestamp() {
                return timestamp;
            }
        };
    }

    /**
     * 计算简单的校验和（CRC32风格）
     */
    private static int calculateChecksum(byte[] data) {
        int checksum = 0;
        for (byte b : data) {
            checksum = (checksum << 1) | (checksum >>> 31);
            checksum ^= b & 0xFF;
        }
        return checksum;
    }

    /**
     * 验证消息是否有效（不完全解码，仅检查格式）
     */
    public static boolean isValid(byte[] bytes) {
        if (bytes == null || bytes.length < 22) {
            return false;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            int magic = buffer.getInt();
            return magic == MAGIC_NUMBER;
        } catch (Exception e) {
            return false;
        }
    }
}
