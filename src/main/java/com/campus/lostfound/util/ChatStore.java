package com.campus.lostfound.util;

import com.campus.lostfound.model.Item;
import com.campus.lostfound.model.User;

import java.io.IOException;
import java.sql.*;
import java.util.*;

public final class ChatStore {

    private ChatStore() {}

    public static final class Message {
        public final long id;
        public final String senderId;
        public final String messageText;
        public final Timestamp sentAt;
        public final boolean read;

        public Message(long id, String senderId, String messageText,
                       Timestamp sentAt, boolean read) {
            this.id = id;
            this.senderId = senderId;
            this.messageText = messageText;
            this.sentAt = sentAt;
            this.read = read;
        }
    }

    public static final class Conversation {
        public final UUID id;
        public final String itemId;
        public final String reporterId;
        public final String contactId;
        public final Timestamp lastMessageAt;

        public Conversation(UUID id, String itemId, String reporterId,
                            String contactId, Timestamp lastMessageAt) {
            this.id = id;
            this.itemId = itemId;
            this.reporterId = reporterId;
            this.contactId = contactId;
            this.lastMessageAt = lastMessageAt;
        }
    }

    private static Item findEligibleItem(String itemId) throws IOException {
        Item item = DataStore.get().findItem(itemId);

        if (item == null
                || !"APPROVED".equalsIgnoreCase(item.getApprovalStatus())
                || !"OPEN".equalsIgnoreCase(item.getStatus())) {
            throw new IllegalArgumentException(
                    "This item is unavailable for new conversations.");
        }

        return item;
    }

    private static void requireParticipant(
            Conversation conversation, String userId) {
        if (userId == null
                || (!userId.equals(conversation.reporterId)
                && !userId.equals(conversation.contactId))) {
            throw new SecurityException(
                    "You are not authorized to access this conversation.");
        }
    }

    private static Conversation findConversation(
            Connection connection, UUID conversationId) throws SQLException {
        String sql = """
                SELECT id, item_id, reporter_id, contact_id, last_message_at
                FROM chat_conversations
                WHERE id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, conversationId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                return new Conversation(
                        rs.getObject("id", UUID.class),
                        rs.getString("item_id"),
                        rs.getString("reporter_id"),
                        rs.getString("contact_id"),
                        rs.getTimestamp("last_message_at"));
            }
        }
    }

    public static Conversation startConversation(
            String itemId, String contactId) throws Exception {

        if (contactId == null || contactId.isBlank()) {
            throw new SecurityException("Please sign in first.");
        }

        Item item = findEligibleItem(itemId);

        if (contactId.equals(item.getReporterId())) {
            throw new IllegalArgumentException(
                    "You cannot start a conversation with yourself.");
        }

        User contact = DataStore.get().findUserById(contactId);
        User reporter = DataStore.get().findUserById(item.getReporterId());

        if (contact == null || reporter == null) {
            throw new IllegalArgumentException(
                    "The item reporter or contacting user no longer exists.");
        }

        String sql = """
                INSERT INTO chat_conversations
                    (id, item_id, reporter_id, contact_id)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (item_id, contact_id)
                DO UPDATE SET item_id = EXCLUDED.item_id
                RETURNING id, item_id, reporter_id, contact_id, last_message_at
                """;

        try (Connection connection = ChatDatabase.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, item.getId());
            ps.setString(3, item.getReporterId());
            ps.setString(4, contactId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Could not open conversation.");
                }

                Conversation conversation = new Conversation(
                        rs.getObject("id", UUID.class),
                        rs.getString("item_id"),
                        rs.getString("reporter_id"),
                        rs.getString("contact_id"),
                        rs.getTimestamp("last_message_at"));

                if (!item.getReporterId().equals(conversation.reporterId)) {
                    throw new SecurityException(
                            "Conversation reporter does not match this item.");
                }

                return conversation;
            }
        }
    }

    public static void sendMessage(
            UUID conversationId, String senderId, String messageText)
            throws Exception {

        if (messageText == null || messageText.trim().isEmpty()
                || messageText.trim().length() > 2000) {
            throw new IllegalArgumentException(
                    "Messages must contain 1–2000 characters.");
        }

        try (Connection connection = ChatDatabase.getConnection()) {
            connection.setAutoCommit(false);

            try {
                Conversation conversation =
                        findConversation(connection, conversationId);

                if (conversation == null) {
                    throw new IllegalArgumentException(
                            "Conversation not found.");
                }

                requireParticipant(conversation, senderId);

                // Confirm the reporter still matches the stored item.
                Item item = findEligibleItemForExistingChat(
                        conversation.itemId);

                if (!conversation.reporterId.equals(item.getReporterId())) {
                    throw new SecurityException(
                            "Conversation ownership validation failed.");
                }

                String sql = """
                        INSERT INTO chat_messages
                            (conversation_id, sender_id, message_text)
                        VALUES (?, ?, ?)
                        """;

                try (PreparedStatement ps =
                             connection.prepareStatement(sql)) {
                    ps.setObject(1, conversationId);
                    ps.setString(2, senderId);
                    ps.setString(3, messageText.trim());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE chat_conversations "
                                + "SET last_message_at = CURRENT_TIMESTAMP "
                                + "WHERE id = ?")) {
                    ps.setObject(1, conversationId);
                    ps.executeUpdate();
                }

                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static Item findEligibleItemForExistingChat(String itemId)
            throws IOException {
        Item item = DataStore.get().findItem(itemId);

        if (item == null
                || !"APPROVED".equalsIgnoreCase(item.getApprovalStatus())) {
            throw new IllegalArgumentException(
                    "This item is no longer available for chat.");
        }

        return item;
    }

    public static List<Message> getMessages(
            UUID conversationId, String userId) throws Exception {

        List<Message> messages = new ArrayList<>();

        try (Connection connection = ChatDatabase.getConnection()) {
            Conversation conversation =
                    findConversation(connection, conversationId);

            if (conversation == null) {
                throw new IllegalArgumentException(
                        "Conversation not found.");
            }

            requireParticipant(conversation, userId);

            String sql = """
                    SELECT id, sender_id, message_text, sent_at, read_at
                    FROM chat_messages
                    WHERE conversation_id = ?
                    ORDER BY id ASC
                    LIMIT 500
                    """;

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, conversationId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        messages.add(new Message(
                                rs.getLong("id"),
                                rs.getString("sender_id"),
                                rs.getString("message_text"),
                                rs.getTimestamp("sent_at"),
                                rs.getTimestamp("read_at") != null));
                    }
                }
            }

            // Mark the other participant's messages as read.
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE chat_messages
                    SET read_at = CURRENT_TIMESTAMP
                    WHERE conversation_id = ?
                      AND sender_id <> ?
                      AND read_at IS NULL
                    """)) {
                ps.setObject(1, conversationId);
                ps.setString(2, userId);
                ps.executeUpdate();
            }
        }

        return messages;
    }

    public static List<Conversation> getInbox(String userId)
            throws Exception {

        if (userId == null || userId.isBlank()) {
            throw new SecurityException("Please sign in first.");
        }

        List<Conversation> conversations = new ArrayList<>();

        String sql = """
                SELECT id, item_id, reporter_id, contact_id, last_message_at
                FROM chat_conversations
                WHERE reporter_id = ? OR contact_id = ?
                ORDER BY last_message_at DESC
                LIMIT 200
                """;

        try (Connection connection = ChatDatabase.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setString(2, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    conversations.add(new Conversation(
                            rs.getObject("id", UUID.class),
                            rs.getString("item_id"),
                            rs.getString("reporter_id"),
                            rs.getString("contact_id"),
                            rs.getTimestamp("last_message_at")));
                }
            }
        }

        return conversations;
    }

    public static int getUnreadCount(String userId) throws Exception {
        if (userId == null || userId.isBlank()) {
            throw new SecurityException("Please sign in first.");
        }

        String sql = """
                SELECT COUNT(*)
                FROM chat_messages m
                JOIN chat_conversations c
                  ON c.id = m.conversation_id
                WHERE (c.reporter_id = ? OR c.contact_id = ?)
                  AND m.sender_id <> ?
                  AND m.read_at IS NULL
                """;

        try (Connection connection = ChatDatabase.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setString(2, userId);
            ps.setString(3, userId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
