package com.campus.lostfound.util;

import com.campus.lostfound.model.Claim;
import com.campus.lostfound.model.Item;
import com.campus.lostfound.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class DataStore {
    private static final DataStore INSTANCE = new DataStore();
    private final Path dir;
    private final Path usersFile, itemsFile, claimsFile, itemEditsFile;

    private DataStore() {
        String configured = System.getProperty("campus.data.dir");
        dir = (configured == null || configured.isBlank())
                ? Paths.get(System.getProperty("user.home"), ".campus-lost-found", "data")
                : Paths.get(configured);
        usersFile = dir.resolve("users.txt");
        itemsFile = dir.resolve("items.txt");
        claimsFile = dir.resolve("claims.txt");
        itemEditsFile = dir.resolve("item_edits.txt");
        try {
            Files.createDirectories(dir);
            createIfMissing(usersFile);
            createIfMissing(itemsFile);
            createIfMissing(claimsFile);
            createIfMissing(itemEditsFile);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot initialize data directory: " + dir, e);
        }
    }

    public static DataStore get() { return INSTANCE; }
    public Path getDataDirectory() { return dir; }

    private void createIfMissing(Path p) throws IOException {
        if (Files.notExists(p)) Files.createFile(p);
    }

    // Base64-url encodes every field so user input cannot break the line-based file format.
    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }
    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
    private String row(String... values) {
        return Arrays.stream(values).map(this::encode).collect(Collectors.joining("|"));
    }
    private String[] fields(String line) {
        return Arrays.stream(line.split("\\|", -1)).map(this::decode).toArray(String[]::new);
    }
    private List<String> readLines(Path p) throws IOException {
        return Files.readAllLines(p, StandardCharsets.UTF_8);
    }
    private void append(Path p, String row) throws IOException {
        Files.writeString(p, row + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    private void replace(Path p, List<String> lines) throws IOException {
        Path temp = Files.createTempFile(dir, p.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public synchronized List<User> allUsers() throws IOException {
        List<User> out = new ArrayList<>();
        for (String line : readLines(usersFile)) {
            if (line.isBlank()) continue;
            String[] f = fields(line);
            if (f.length == 5) out.add(new User(f[0], f[1], f[2], f[3], f[4]));
        }
        Collections.reverse(out);
        return out;
    }

    public synchronized User findUserByEmail(String email) throws IOException {
        for (String line : readLines(usersFile)) {
            if (line.isBlank()) continue;
            String[] f = fields(line);
            if (f.length == 5 && f[2].equalsIgnoreCase(email)) return new User(f[0], f[1], f[2], f[3], f[4]);
        }
        return null;
    }
    public synchronized User findUserById(String id) throws IOException {
        for (String line : readLines(usersFile)) {
            if (line.isBlank()) continue;
            String[] f = fields(line);
            if (f.length == 5 && f[0].equals(id)) return new User(f[0], f[1], f[2], f[3], f[4]);
        }
        return null;
    }
    public synchronized User createUser(String name, String email, String passwordHash, String role) throws IOException {
        if (findUserByEmail(email) != null) throw new IllegalArgumentException("An account with that email already exists.");
        User user = new User(UUID.randomUUID().toString(), name, email.toLowerCase(Locale.ROOT), passwordHash, role);
        append(usersFile, row(user.getId(), user.getName(), user.getEmail(), user.getPasswordHash(), user.getRole()));
        return user;
    }
public synchronized void updatePasswordHash(String userId, String passwordHash)
        throws IOException {
    List<String> lines = readLines(usersFile);
    List<String> updated = new ArrayList<>();
    boolean found = false;

    for (String line : lines) {
        if (line.isBlank()) {
            updated.add(line);
            continue;
        }

        String[] f = fields(line);
        if (f.length == 5 && f[0].equals(userId)) {
            updated.add(row(f[0], f[1], f[2], passwordHash, f[4]));
            found = true;
        } else {
            updated.add(line);
        }
    }

    if (!found) {
        throw new IllegalArgumentException("User not found.");
    }

    replace(usersFile, updated);
}
    public synchronized void ensureAdmin(String email, String passwordHash) throws IOException {
        if (email != null && !email.isBlank() && findUserByEmail(email) == null) {
            createUser("Administrator", email.trim().toLowerCase(Locale.ROOT), passwordHash, "ADMIN");
        }
    }

    /** Deletes a user and the records tied to that account. The last administrator cannot be removed. */
    public synchronized void deleteUser(String userId) throws IOException {
        User target = findUserById(userId);
        if (target == null) throw new IllegalArgumentException("User not found.");

        List<User> users = allUsers();
        long adminCount = users.stream().filter(User::isAdmin).count();
        if (target.isAdmin() && adminCount <= 1) {
            throw new IllegalArgumentException("The last administrator cannot be deleted.");
        }

        Set<String> removedItemIds = new HashSet<>();
        List<String> photoNames = new ArrayList<>();
        List<String> keptItems = new ArrayList<>();
        for (String line : readLines(itemsFile)) {
            if (line.isBlank()) { keptItems.add(line); continue; }
            String[] f = fields(line);
            if (f.length >= 9 && f[1].equals(userId)) {
                removedItemIds.add(f[0]);
                if (f.length >= 11 && !f[10].isBlank()) photoNames.add(f[10]);
            } else keptItems.add(line);
        }

        List<String> keptClaims = new ArrayList<>();
        for (String line : readLines(claimsFile)) {
            if (line.isBlank()) { keptClaims.add(line); continue; }
            String[] f = fields(line);
            if (f.length == 6 && (f[2].equals(userId) || removedItemIds.contains(f[1]))) continue;
            keptClaims.add(line);
        }

        List<String> keptUsers = new ArrayList<>();
        for (String line : readLines(usersFile)) {
            if (line.isBlank()) { keptUsers.add(line); continue; }
            String[] f = fields(line);
            if (f.length == 5 && !f[0].equals(userId)) keptUsers.add(line);
        }

        replace(itemsFile, keptItems);
        replace(claimsFile, keptClaims);
        replace(usersFile, keptUsers);

        Path uploads = dir.resolve("uploads").normalize();
        for (String photoName : photoNames) {
            if (photoName.matches("[a-fA-F0-9-]+\\.(jpg|png)")) {
                Path photo = uploads.resolve(photoName).normalize();
                if (photo.getParent().equals(uploads)) Files.deleteIfExists(photo);
            }
        }
    }
    /** Deletes a listing, its associated claims, and its uploaded photo. */
    public synchronized void deleteItem(String itemId) throws IOException {
        Item target = findItem(itemId);
        if (target == null) {
            throw new IllegalArgumentException("Listing not found.");
        }

        List<String> keptItems = new ArrayList<>();
        List<String> photoNames = new ArrayList<>();

        for (String line : readLines(itemsFile)) {
            if (line.isBlank()) {
                keptItems.add(line);
                continue;
            }
            String[] f = fields(line);
            if (f.length >= 9 && f[0].equals(itemId)) {
                if (f.length >= 11 && !f[10].isBlank()) {
                    photoNames.add(f[10]);
                }
            } else {
                keptItems.add(line);
            }
        }

        List<String> keptClaims = new ArrayList<>();
        for (String line : readLines(claimsFile)) {
            if (line.isBlank()) {
                keptClaims.add(line);
                continue;
            }
            String[] f = fields(line);
            if (f.length == 6 && f[1].equals(itemId)) {
                continue;
            }
            keptClaims.add(line);
        }

        replace(itemsFile, keptItems);
        replace(claimsFile, keptClaims);

        Path uploads = dir.resolve("uploads").normalize();
        for (String photoName : photoNames) {
            if (photoName.matches("[a-fA-F0-9-]+\\.(jpg|png)")) {
                Path photo = uploads.resolve(photoName).normalize();
                if (photo.getParent().equals(uploads)) {
                    Files.deleteIfExists(photo);
                }
            }
        }
    }

    public synchronized List<Item> allItems() throws IOException {
        List<Item> out = new ArrayList<>();
        for (String line : readLines(itemsFile)) {
            if (line.isBlank()) continue;
            String[] f = fields(line);
            if (f.length == 9)
                out.add(new Item(f[0],f[1],f[2],f[3],f[4],f[5],f[6],f[7],f[8]));
            else if (f.length == 10)
                out.add(new Item(f[0],f[1],f[2],f[3],f[4],f[5],f[6],f[7],f[8],f[9]));
            else if (f.length == 11)
                out.add(new Item(f[0],f[1],f[2],f[3],f[4],f[5],f[6],f[7],f[8],f[9],f[10]));
            else if (f.length >= 12)
                out.add(new Item(f[0],f[1],f[2],f[3],f[4],f[5],f[6],f[7],f[8],f[9],f[10],f[11]));
        }
        Collections.reverse(out);
        return out;
    }
    public synchronized Item findItem(String id) throws IOException {
        for (Item item : allItems()) if (item.getId().equals(id)) return item;
        return null;
    }
    public synchronized Item createItem(String reporterId, String title, String description, String category,
                                        String type, String location, String date, String contactPhone) throws IOException {
        return createItem(reporterId, title, description, category, type, location, date, contactPhone, "");
    }

    public synchronized Item createItem(String reporterId, String title, String description, String category,
                                        String type, String location, String date, String contactPhone, String photoName) throws IOException {
        Item item = new Item(UUID.randomUUID().toString(), reporterId, title, description, category,
                type, location, date, "OPEN", contactPhone, photoName, "PENDING");
        append(itemsFile, row(item.getId(), item.getReporterId(), item.getTitle(), item.getDescription(),
                item.getCategory(), item.getType(), item.getLocation(), item.getDate(),
                item.getStatus(), item.getContactPhone(), item.getPhotoName(), item.getApprovalStatus()));
        return item;
    }

    public synchronized Item createItem(String reporterId, String title, String description, String category,
                                        String type, String location, String date) throws IOException {
        return createItem(reporterId, title, description, category, type, location, date, "");
    }
    public synchronized void setItemStatus(String itemId, String status) throws IOException {
        List<String> lines = readLines(itemsFile);
        List<String> updated = new ArrayList<>();
        boolean found = false;

        for (String line : lines) {
            if (line.isBlank()) {
                updated.add(line);
                continue;
            }

            String[] f = fields(line);
            if (f.length >= 9 && f[0].equals(itemId)) {
                // Preserve legacy reports as approved when adding the new field.
                if (f.length < 12) {
                    String legacyApproval = "APPROVED";
                    f = java.util.Arrays.copyOf(f, 12);
                    f[11] = legacyApproval;
                }
                f[8] = status;
                updated.add(row(f));
                found = true;
            } else {
                updated.add(line);
            }
        }

        if (!found) throw new IllegalArgumentException("Item not found.");
        replace(itemsFile, updated);
    }

    public synchronized void setItemApprovalStatus(String itemId, String approvalStatus) throws IOException {
        if (!"APPROVED".equals(approvalStatus) && !"REJECTED".equals(approvalStatus))
            throw new IllegalArgumentException("Invalid item approval status.");

        List<String> lines = readLines(itemsFile);
        List<String> updated = new ArrayList<>();
        boolean found = false;

        for (String line : lines) {
            if (line.isBlank()) {
                updated.add(line);
                continue;
            }

            String[] f = fields(line);
            if (f.length >= 9 && f[0].equals(itemId)) {
                if (f.length < 12) {
                    f = java.util.Arrays.copyOf(f, 12);
                    // Legacy reports are treated as approved before this change.
                    if (f.length > 11 && f[11] == null) f[11] = "APPROVED";
                }
                f[11] = approvalStatus;
                updated.add(row(f));
                found = true;
            } else {
                updated.add(line);
            }
        }

        if (!found) throw new IllegalArgumentException("Item not found.");
        replace(itemsFile, updated);
    }

    public synchronized Claim createClaim(String itemId, String claimantId, String proof) throws IOException {
        Item item = findItem(itemId);
        if (item == null) throw new IllegalArgumentException("Item not found.");
        if (!"APPROVED".equals(item.getApprovalStatus()))
            throw new IllegalArgumentException("This item has not been approved for public claims.");
        if (!"FOUND".equals(item.getType())) throw new IllegalArgumentException("Claims can only be submitted for found items.");
        if (!"OPEN".equals(item.getStatus())) throw new IllegalArgumentException("This item is not open for claims.");
        if (item.getReporterId().equals(claimantId)) throw new IllegalArgumentException("You cannot claim an item you reported.");
        for (Claim c : allClaims()) {
            if (c.getItemId().equals(itemId) && c.getClaimantId().equals(claimantId) && "PENDING".equals(c.getStatus()))
                throw new IllegalArgumentException("You already have a pending claim for this item.");
        }
        Claim claim = new Claim(UUID.randomUUID().toString(), itemId, claimantId, proof, "PENDING", Instant.now().toString());
        append(claimsFile, row(claim.getId(), claim.getItemId(), claim.getClaimantId(), claim.getProof(),
                claim.getStatus(), claim.getCreatedAt()));
        setItemStatus(itemId, "CLAIM_PENDING");
        return claim;
    }
    public synchronized List<Claim> allClaims() throws IOException {
        List<Claim> out = new ArrayList<>();
        for (String line : readLines(claimsFile)) {
            if (line.isBlank()) continue;
            String[] f = fields(line);
            if (f.length == 6) out.add(new Claim(f[0],f[1],f[2],f[3],f[4],f[5]));
        }
        Collections.reverse(out);
        return out;
    }
    public synchronized List<Claim> claimsForUser(String userId) throws IOException {
        return allClaims().stream().filter(c -> c.getClaimantId().equals(userId)).collect(Collectors.toList());
    }
    public synchronized void reviewClaim(String claimId, String decision) throws IOException {
        if (!decision.equals("APPROVED") && !decision.equals("REJECTED"))
            throw new IllegalArgumentException("Invalid claim decision.");
        List<String> lines = readLines(claimsFile);
        List<String> updated = new ArrayList<>();
        Claim selected = null;
        for (String line : lines) {
            if (line.isBlank()) { updated.add(line); continue; }
            String[] f = fields(line);
            if (f.length == 6 && f[0].equals(claimId)) {
                selected = new Claim(f[0],f[1],f[2],f[3],f[4],f[5]);
                if (!"PENDING".equals(f[4])) throw new IllegalArgumentException("This claim has already been reviewed.");
                f[4] = decision;
                updated.add(row(f));
            } else updated.add(line);
        }
        if (selected == null) throw new IllegalArgumentException("Claim not found.");
        replace(claimsFile, updated);
        String reviewedItemId = selected.getItemId();
        if ("APPROVED".equals(decision)) {
            setItemStatus(reviewedItemId, "RETURN_PENDING");
        } else {
            boolean anotherPending = allClaims().stream().anyMatch(c ->
                    c.getItemId().equals(reviewedItemId) && "PENDING".equals(c.getStatus()));
            setItemStatus(reviewedItemId, anotherPending ? "CLAIM_PENDING" : "OPEN");
        }
    }

    /** Stores proposed listing edits separately until an administrator reviews them. */
    public synchronized void saveItemEditProposal(
            String itemId, String reporterId, String title, String description,
            String category, String type, String location, String date,
            String contactPhone, String photoName) throws IOException {
        Item item = findItem(itemId);
        if (item == null) throw new IllegalArgumentException("Listing not found.");
        if (!item.getReporterId().equals(reporterId))
            throw new IllegalArgumentException("You can only edit your own listing.");
        if (!"APPROVED".equals(item.getApprovalStatus()))
            throw new IllegalArgumentException("Only approved listings can be edited.");

        // One pending proposal per listing. Keep the original listing untouched.
        List<String> lines = readLines(itemEditsFile);
        List<String> updated = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) continue;
            String[] f = fields(line);
            if (f.length >= 11 && f[1].equals(itemId)) {
                throw new IllegalArgumentException("This listing already has an edit awaiting review.");
            }
            updated.add(line);
        }
        String proposalId = UUID.randomUUID().toString();
        append(itemEditsFile, row(proposalId, itemId, reporterId, title, description,
                category, type, location, date, contactPhone, photoName, Instant.now().toString()));
    }

    /** Returns pending edit proposals as decoded field arrays.
     * Fields: proposalId, itemId, reporterId, title, description, category,
     * type, location, date, contactPhone, photoName, submittedAt.
     */
    public synchronized List<String[]> pendingItemEditProposals() throws IOException {
        List<String[]> out = new ArrayList<>();
        for (String line : readLines(itemEditsFile)) {
            if (line.isBlank()) continue;
            String[] f = fields(line);
            if (f.length >= 12) out.add(f);
        }
        Collections.reverse(out);
        return out;
    }

    public synchronized String[] findItemEditProposal(String proposalId) throws IOException {
        for (String[] f : pendingItemEditProposals()) {
            if (f[0].equals(proposalId)) return f;
        }
        return null;
    }

    /** Removes a proposal after a decision; does not touch the current listing. */
    /** Applies a reviewed edit to the existing listing without changing its ID or owner. */
    public synchronized Item applyItemEditProposal(String proposalId) throws IOException {
        String[] proposal = findItemEditProposal(proposalId);
        if (proposal == null) {
            throw new IllegalArgumentException("Edit proposal not found.");
        }

        String itemId = proposal[1];
        Item current = findItem(itemId);
        if (current == null) {
            throw new IllegalArgumentException("The original listing no longer exists.");
        }
        if (!current.getReporterId().equals(proposal[2])) {
            throw new IllegalArgumentException("The proposal owner does not match the listing owner.");
        }
        if (!"APPROVED".equals(current.getApprovalStatus())) {
            throw new IllegalArgumentException("The original listing is no longer approved.");
        }

        List<String> updated = new ArrayList<>();
        boolean found = false;

        for (String line : readLines(itemsFile)) {
            if (line.isBlank()) continue;
            String[] f = fields(line);

            if (f.length > 0 && f[0].equals(itemId)) {
                if (found) throw new IllegalArgumentException("Duplicate listing ID.");
                if (f.length < 12) {
                    f = java.util.Arrays.copyOf(f, 12);
                    f[8] = current.getStatus();
                    f[11] = current.getApprovalStatus();
                }

                // Proposal fields: ID, item ID, owner, title, description, category,
                // type, location, date, phone, photo, submitted timestamp.
                f[2] = proposal[3];
                f[3] = proposal[4];
                f[4] = proposal[5];
                f[5] = proposal[6];
                f[6] = proposal[7];
                f[7] = proposal[8];
                f[9] = proposal[9];
                f[10] = proposal[10];
                f[11] = "APPROVED";

                updated.add(row(f));
                found = true;
            } else {
                updated.add(line);
            }
        }

        if (!found) throw new IllegalArgumentException("The original listing no longer exists.");

        // Replace the listing first; keep the proposal if the write fails.
        replace(itemsFile, updated);
        removeItemEditProposal(proposalId);
        return findItem(itemId);
    }

    public synchronized String[] removeItemEditProposal(String proposalId) throws IOException {
        List<String> kept = new ArrayList<>();
        String[] selected = null;
        for (String line : readLines(itemEditsFile)) {
            if (line.isBlank()) continue;
            String[] f = fields(line);
            if (f.length >= 12 && f[0].equals(proposalId)) {
                if (selected != null) throw new IllegalArgumentException("Duplicate edit proposal ID.");
                selected = f;
            } else {
                kept.add(line);
            }
        }
        if (selected == null) throw new IllegalArgumentException("Edit proposal not found.");
        replace(itemEditsFile, kept);
        return selected;
    }

}