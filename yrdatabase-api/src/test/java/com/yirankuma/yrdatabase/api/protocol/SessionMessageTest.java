package com.yirankuma.yrdatabase.api.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SessionMessage.
 *
 * @author YiranKuma
 */
@DisplayName("SessionMessage Tests")
class SessionMessageTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("Should create player join message")
        void shouldCreatePlayerJoinMessage() {
            SessionMessage msg = SessionMessage.playerJoin("uuid-123", "TestPlayer", "lobby");
            
            assertEquals(MessageType.PLAYER_JOIN, msg.getType());
            assertEquals("uuid-123", msg.getPlayerId());
            assertEquals("TestPlayer", msg.getPlayerName());
            assertEquals("lobby", msg.getSourceServer());
            assertTrue(msg.getTimestamp() > 0);
            assertNotNull(msg.getData());
        }

        @Test
        @DisplayName("Should create player quit message")
        void shouldCreatePlayerQuitMessage() {
            SessionMessage msg = SessionMessage.playerQuit("uuid-123", "TestPlayer", "survival");
            
            assertEquals(MessageType.PLAYER_QUIT, msg.getType());
            assertEquals("uuid-123", msg.getPlayerId());
            assertEquals("TestPlayer", msg.getPlayerName());
            assertEquals("survival", msg.getSourceServer());
        }

        @Test
        @DisplayName("Should create player transfer message")
        void shouldCreatePlayerTransferMessage() {
            SessionMessage msg = SessionMessage.playerTransfer("uuid-123", "TestPlayer", "lobby", "survival");
            
            assertEquals(MessageType.PLAYER_TRANSFER, msg.getType());
            assertEquals("uuid-123", msg.getPlayerId());
            assertEquals("TestPlayer", msg.getPlayerName());
            assertEquals("lobby", msg.getSourceServer());
            assertEquals("survival", msg.getTargetServer());
        }

        @Test
        @DisplayName("Should create heartbeat message")
        void shouldCreateHeartbeatMessage() {
            SessionMessage msg = SessionMessage.heartbeat("uuid-123", "lobby");
            
            assertEquals(MessageType.HEARTBEAT, msg.getType());
            assertEquals("uuid-123", msg.getPlayerId());
            assertEquals("lobby", msg.getSourceServer());
        }
    }

    @Nested
    @DisplayName("Serialization")
    class Serialization {

        @Test
        @DisplayName("Should serialize to JSON")
        void shouldSerializeToJson() {
            SessionMessage msg = SessionMessage.playerJoin("uuid-123", "TestPlayer", "lobby");
            String json = msg.toJson();
            
            assertNotNull(json);
            assertTrue(json.contains("\"type\":\"PLAYER_JOIN\""));
            assertTrue(json.contains("\"playerId\":\"uuid-123\""));
            assertTrue(json.contains("\"playerName\":\"TestPlayer\""));
        }

        @Test
        @DisplayName("Should deserialize from JSON")
        void shouldDeserializeFromJson() {
            SessionMessage original = SessionMessage.playerJoin("uuid-123", "TestPlayer", "lobby");
            String json = original.toJson();
            
            SessionMessage deserialized = SessionMessage.fromJson(json);
            
            assertEquals(original.getType(), deserialized.getType());
            assertEquals(original.getPlayerId(), deserialized.getPlayerId());
            assertEquals(original.getPlayerName(), deserialized.getPlayerName());
            assertEquals(original.getSourceServer(), deserialized.getSourceServer());
        }

        @Test
        @DisplayName("Should serialize to bytes")
        void shouldSerializeToBytes() {
            SessionMessage msg = SessionMessage.playerJoin("uuid-123", "TestPlayer", "lobby");
            byte[] bytes = msg.toBytes();
            
            assertNotNull(bytes);
            assertTrue(bytes.length > 0);
            
            String jsonFromBytes = new String(bytes, StandardCharsets.UTF_8);
            assertTrue(jsonFromBytes.contains("PLAYER_JOIN"));
        }

        @Test
        @DisplayName("Should deserialize from bytes")
        void shouldDeserializeFromBytes() {
            SessionMessage original = SessionMessage.playerJoin("uuid-123", "TestPlayer", "lobby");
            byte[] bytes = original.toBytes();
            
            SessionMessage deserialized = SessionMessage.fromBytes(bytes);
            
            assertEquals(original.getType(), deserialized.getType());
            assertEquals(original.getPlayerId(), deserialized.getPlayerId());
            assertEquals(original.getPlayerName(), deserialized.getPlayerName());
        }

        @Test
        @DisplayName("Should roundtrip all message types")
        void shouldRoundtripAllMessageTypes() {
            SessionMessage[] messages = {
                SessionMessage.playerJoin("id1", "Player1", "server1"),
                SessionMessage.playerQuit("id2", "Player2", "server2"),
                SessionMessage.playerTransfer("id3", "Player3", "from", "to"),
                SessionMessage.heartbeat("id4", "server4")
            };

            for (SessionMessage original : messages) {
                byte[] bytes = original.toBytes();
                SessionMessage deserialized = SessionMessage.fromBytes(bytes);
                
                assertEquals(original.getType(), deserialized.getType(), 
                    "Type should match for " + original.getType());
                assertEquals(original.getPlayerId(), deserialized.getPlayerId());
            }
        }
    }

    @Nested
    @DisplayName("Timestamp")
    class Timestamp {

        @Test
        @DisplayName("Should set timestamp on creation")
        void shouldSetTimestampOnCreation() {
            long before = System.currentTimeMillis();
            SessionMessage msg = SessionMessage.playerJoin("uuid", "player", "server");
            long after = System.currentTimeMillis();
            
            assertTrue(msg.getTimestamp() >= before);
            assertTrue(msg.getTimestamp() <= after);
        }
    }
}
