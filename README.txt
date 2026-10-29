CampusFind PostgreSQL storage patch
==================================

Contents
--------
src/main/java/com/campus/lostfound/util/DataStore.java
src/main/java/com/campus/lostfound/util/WorkflowStore.java
src/main/java/com/campus/lostfound/util/CommunityStore.java
src/main/java/com/campus/lostfound/util/DataMigrationTool.java

What changed
------------
- DataStore now uses the existing ChatDatabase PostgreSQL connection for users,
  items, claims and edit proposals.
- WorkflowStore uses PostgreSQL for notifications, audit logs and handovers.
- CommunityStore uses PostgreSQL for saved items and listing flags.
- Existing public method signatures and model constructors are retained.
- Chat tables are not queried or modified by the migration utility.
- Audit log inserts omit the identity column so PostgreSQL generates it.

Important deployment notes
--------------------------
1. This patch does not migrate photo bytes to ImageKit. The `photo_name` field
   continues to store the existing value. Keep the existing photo behavior until
   ImageKit changes are implemented.
2. DataMigrationTool reads files from CAMPUS_MIGRATION_DATA_DIR, or by default
   ~/.campus-lost-found/data on the machine where it runs. Those files are on
   the user's laptop, not automatically available on Render. Do NOT run the
   importer on Render until the source files have been transferred securely.
3. The importer refuses to run if any of the nine campus_* destination tables
   contain rows, and wraps all inserts in one transaction. It does not touch
   chat_conversations or chat_messages.
4. Your current local source data includes users/items/claims/workflow data and
   a local photo. Preserve the existing backup.
5. Do not deploy this patch to production before planning the one-time data
   transfer and testing in a safe environment. Rolling back a Render deploy does
   not roll back Neon database writes.

Install
-------
From the project root, extract this ZIP so the files overwrite the matching
source paths, then run:
    mvn clean package -DskipTests

The changed storage classes were compiled together with javac against the
provided model classes and ChatDatabase. A full Maven build should still be run
in the user's project environment.
