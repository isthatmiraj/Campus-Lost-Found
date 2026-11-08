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
        public final String itemTitle;
        public final String otherParticipantName;
        public final String latestMessage;
        public final int unreadCount;

        public Conversation(UUID id, String itemId, String reporterId,
                            String contactId, Timestamp lastMessageAt) {
            this(id, itemId, reporterId, contactId, lastMessageAt,
                    "Campus listing", "Campus user", "No messages yet", 0);
        }

        public Conversation(UUID id, String itemId, String reporterId,
                            String contactId, Timestamp lastMessageAt,
                            String itemTitle, String otherParticipantName,
                            String latestMessage) {
            this(id, itemId, reporterId, contactId, lastMessageAt,
                    itemTitle, otherParticipantName, latestMessage, 0);
        }

        public Conversation(UUID id, String itemId, String reporterId,
                            String contactId, Timestamp lastMessageAt,
                            String itemTitle, String otherParticipantName,
                            String latestMessage, int unreadCount) {
            this.id = id;
            this.itemId = itemId;
            this.reporterId = reporterId;
            this.contactId = contactId;
            this.lastMessageAt = lastMessageAt;
            this.itemTitle = itemTitle == null || itemTitle.isBlank() ? "Campus listing" : itemTitle;
            this.otherParticipantName = otherParticipantName == null || otherParticipantName.isBlank() ? "Campus user" : otherParticipantName;
            this.latestMessage = latestMessage == null || latestMessage.isBlank() ? "No messages yet" : latestMessage;
            this.unreadCount = Math.max(0, unreadCount);
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
                String recipientId = senderId.equals(conversation.reporterId)
                        ? conversation.contactId : conversation.reporterId;

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

                // Notification failure must never undo a successfully sent message.
                String preview = messageText.trim().replaceAll("\\s+", " ");
                if (preview.length() > 90) preview = preview.substring(0, 87) + "...";
                try {
                    WorkflowStore.get().notifyUser(
                            recipientId,
                            "New chat message: " + preview,
                            "/chat/room?conversationId=" + conversationId);
                } catch (IOException notificationError) {
                    // The message is already committed; the next inbox poll still finds it.
                }
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

            // Mark incoming messages read BEFORE selecting them so the returned
            // read flags are immediately accurate for the sender's next poll.
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
                SELECT c.id, c.item_id, c.reporter_id, c.contact_id, c.last_message_at,
                       (SELECT m.message_text FROM chat_messages m
                        WHERE m.conversation_id = c.id
                        ORDER BY m.sent_at DESC, m.id DESC LIMIT 1) AS latest_message,
                       (SELECT COUNT(*) FROM chat_messages um
                        WHERE um.conversation_id = c.id
                          AND um.sender_id <> ?
                          AND um.read_at IS NULL) AS unread_count
                FROM chat_conversations c
                WHERE c.reporter_id = ? OR c.contact_id = ?
                ORDER BY c.last_message_at DESC
                LIMIT 200
                """;

        try (Connection connection = ChatDatabase.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setString(2, userId);
            ps.setString(3, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String itemId = rs.getString("item_id");
                    String reporterId = rs.getString("reporter_id");
                    String contactId = rs.getString("contact_id");
                    String otherId = userId.equals(reporterId) ? contactId : reporterId;
                    Item item = DataStore.get().findItem(itemId);
                    User other = DataStore.get().findUserById(otherId);
                    conversations.add(new Conversation(
                            rs.getObject("id", UUID.class), itemId, reporterId, contactId,
                            rs.getTimestamp("last_message_at"),
                            item == null ? "Campus listing" : item.getTitle(),
                            other == null ? "Campus user" : other.getName(),
                            rs.getString("latest_message"),
                            rs.getInt("unread_count")));
                }
            }
        }

        return conversations;
    }


    /** Updates the current participant's typing heartbeat. */
    public static void setTyping(UUID conversationId, String userId,
                                 boolean typing) throws Exception {
        try (Connection connection = ChatDatabase.getConnection()) {
            Conversation conversation = findConversation(connection, conversationId);
            if (conversation == null) {
                throw new IllegalArgumentException("Conversation not found.");
            }
            requireParticipant(conversation, userId);

            if (typing) {
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO chat_typing_status
                            (conversation_id, user_id, updated_at)
                        VALUES (?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (conversation_id, user_id)
                        DO UPDATE SET updated_at = CURRENT_TIMESTAMP
                        """)) {
                    ps.setObject(1, conversationId);
                    ps.setString(2, userId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = connection.prepareStatement("""
                        DELETE FROM chat_typing_status
                        WHERE conversation_id = ? AND user_id = ?
                        """)) {
                    ps.setObject(1, conversationId);
                    ps.setString(2, userId);
                    ps.executeUpdate();
                }
            }

            // Opportunistically discard expired typing records.
            try (PreparedStatement ps = connection.prepareStatement("""
                    DELETE FROM chat_typing_status
                    WHERE updated_at < CURRENT_TIMESTAMP - INTERVAL '15 seconds'
                    """)) {
                ps.executeUpdate();
            }
        }
    }

    /** Checks only the other participant; stale typing states expire quickly. */
    public static boolean isOtherParticipantTyping(UUID conversationId,
                                                   String userId) throws Exception {
        try (Connection connection = ChatDatabase.getConnection()) {
            Conversation conversation = findConversation(connection, conversationId);
            if (conversation == null) {
                throw new IllegalArgumentException("Conversation not found.");
            }
            requireParticipant(conversation, userId);

            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT EXISTS (
                        SELECT 1 FROM chat_typing_status
                        WHERE conversation_id = ?
                          AND user_id <> ?
                          AND updated_at > CURRENT_TIMESTAMP - INTERVAL '6 seconds'
                    )
                    """)) {
                ps.setObject(1, conversationId);
                ps.setString(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getBoolean(1);
                }
            }
        }
    }

    public static final class AdminConversation {
        public final UUID id;
        public final String itemId;
        public final String itemTitle;
        public final String reporterId;
        public final String reporterName;
        public final String contactId;
        public final String contactName;
        public final Timestamp lastMessageAt;
        public final String latestMessage;
        public final long messageCount;

        public AdminConversation(UUID id, String itemId, String itemTitle,
                String reporterId, String reporterName,
                String contactId, String contactName,
                Timestamp lastMessageAt, String latestMessage,
                long messageCount) {
            this.id = id;
            this.itemId = itemId;
            this.itemTitle = itemTitle;
            this.reporterId = reporterId;
            this.reporterName = reporterName;
            this.contactId = contactId;
            this.contactName = contactName;
            this.lastMessageAt = lastMessageAt;
            this.latestMessage = latestMessage;
            this.messageCount = messageCount;
        }
    }

    // Admin-only listing. Authorization is enforced by MaintenanceServlet.
    public static List<AdminConversation> getAdminConversations(String search)
            throws Exception {
        List<AdminConversation> result = new ArrayList<>();
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        String sql = """
            SELECT c.id, c.item_id, c.reporter_id, c.contact_id,
                   c.last_message_at,
                   COUNT(m.id) AS message_count,
                   (SELECT m2.message_text
                    FROM chat_messages m2
                    WHERE m2.conversation_id = c.id
                    ORDER BY m2.sent_at DESC, m2.id DESC
                    LIMIT 1) AS latest_message
            FROM chat_conversations c
            LEFT JOIN chat_messages m ON m.conversation_id = c.id
            GROUP BY c.id
            ORDER BY c.last_message_at DESC NULLS LAST
            """;

        try (Connection connection = ChatDatabase.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String itemId = rs.getString("item_id");
                String reporterId = rs.getString("reporter_id");
                String contactId = rs.getString("contact_id");

                Item item = DataStore.get().findItem(itemId);
                User reporter = DataStore.get().findUserById(reporterId);
                User contact = DataStore.get().findUserById(contactId);

                String itemTitle = item == null ? "Campus listing" : item.getTitle();
                String reporterName = reporter == null ? "Unknown user" : reporter.getName();
                String contactName = contact == null ? "Unknown user" : contact.getName();
                String latest = rs.getString("latest_message");
                if (latest == null || latest.isBlank()) latest = "No messages yet";

                UUID id = rs.getObject("id", UUID.class);
                String searchable = (id + " " + itemId + " " + itemTitle + " "
                        + reporterId + " " + reporterName + " "
                        + contactId + " " + contactName + " " + latest)
                        .toLowerCase(Locale.ROOT);

                if (!term.isEmpty() && !searchable.contains(term)) continue;

                result.add(new AdminConversation(
                        id, itemId, itemTitle,
                        reporterId, reporterName,
                        contactId, contactName,
                        rs.getTimestamp("last_message_at"),
                        latest, rs.getLong("message_count")));
            }
        }

        return result;
    }

    // Unlike getMessages(), this method NEVER marks messages as read.
    public static List<Message> getAdminMessages(UUID conversationId)
            throws Exception {
        List<Message> messages = new ArrayList<>();

        try (Connection connection = ChatDatabase.getConnection()) {
            if (findConversation(connection, conversationId) == null) {
                throw new IllegalArgumentException("Conversation not found.");
            }

            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT id, sender_id, message_text, sent_at, read_at
                    FROM chat_messages
                    WHERE conversation_id = ?
                    ORDER BY sent_at ASC, id ASC
                    """)) {
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
        }

        return messages;
    }

    /** Permanently deletes complete conversation threads and their messages. */
    public static int adminDeleteConversations(Collection<UUID> conversationIds)
            throws SQLException {
        if (conversationIds == null || conversationIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one conversation.");
        }

        Set<UUID> uniqueIds = new LinkedHashSet<>(conversationIds);
        if (uniqueIds.contains(null)) {
            throw new IllegalArgumentException("Invalid conversation selection.");
        }

        List<UUID> ids = new ArrayList<>(uniqueIds);
        int deletedConversations = 0;

        try (Connection connection = ChatDatabase.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (int offset = 0; offset < ids.size(); offset += 500) {
                    List<UUID> batch = ids.subList(offset,
                            Math.min(offset + 500, ids.size()));
                    String placeholders = String.join(",",
                            Collections.nCopies(batch.size(), "?"));

                    try (PreparedStatement ps = connection.prepareStatement(
                            "DELETE FROM chat_messages WHERE conversation_id IN ("
                                    + placeholders + ")")) {
                        for (int i = 0; i < batch.size(); i++) {
                            ps.setObject(i + 1, batch.get(i));
                        }
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = connection.prepareStatement(
                            "DELETE FROM chat_conversations WHERE id IN ("
                                    + placeholders + ")")) {
                        for (int i = 0; i < batch.size(); i++) {
                            ps.setObject(i + 1, batch.get(i));
                        }
                        deletedConversations += ps.executeUpdate();
                    }
                }

                connection.commit();
                return deletedConversations;
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof SQLException) throw (SQLException) e;
                throw new SQLException("Could not delete selected conversations.", e);
            }
        }
    }

    public static boolean adminDeleteMessage(UUID conversationId, long messageId)
            throws SQLException {
        try (Connection connection = ChatDatabase.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int deleted;
                try (PreparedStatement ps = connection.prepareStatement("""
                        DELETE FROM chat_messages
                        WHERE id = ? AND conversation_id = ?
                        """)) {
                    ps.setLong(1, messageId);
                    ps.setObject(2, conversationId);
                    deleted = ps.executeUpdate();
                }

                if (deleted > 0) {
                    try (PreparedStatement ps = connection.prepareStatement("""
                            UPDATE chat_conversations
                            SET last_message_at = COALESCE(
                                (SELECT MAX(sent_at) FROM chat_messages
                                 WHERE conversation_id = ?),
                                CURRENT_TIMESTAMP)
                            WHERE id = ?
                            """)) {
                        ps.setObject(1, conversationId);
                        ps.setObject(2, conversationId);
                        ps.executeUpdate();
                    }
                }

                connection.commit();
                return deleted > 0;
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof SQLException) throw (SQLException) e;
                throw new SQLException(e);
            }
        }
    }

    public static int adminClearConversation(UUID conversationId)
            throws SQLException {
        try (Connection connection = ChatDatabase.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (findConversation(connection, conversationId) == null) {
                    throw new SQLException("Conversation not found.");
                }

                int deleted;
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM chat_messages WHERE conversation_id = ?")) {
                    ps.setObject(1, conversationId);
                    deleted = ps.executeUpdate();
                }

                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE chat_conversations
                        SET last_message_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """)) {
                    ps.setObject(1, conversationId);
                    ps.executeUpdate();
                }

                connection.commit();
                return deleted;
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof SQLException) throw (SQLException) e;
                throw new SQLException(e);
            }
        }
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
