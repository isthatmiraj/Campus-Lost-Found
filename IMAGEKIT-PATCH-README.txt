CampusFind ImageKit integration patch
=====================================

Changes:
- New item/edit photos are validated and uploaded to ImageKit using Java 17 HttpClient.
- ImageKit private key and endpoint are read from IMAGEKIT_PRIVATE_KEY and IMAGEKIT_URL_ENDPOINT.
- JSP image sources support both old local filenames and ImageKit HTTPS URLs.
- ImageKitMigrationTool uploads referenced legacy local photos and updates campus_items and campus_item_edit_proposals.
- Existing local photo files are not deleted by the migration tool.

Before using:
1. Set IMAGEKIT_PRIVATE_KEY and IMAGEKIT_URL_ENDPOINT in your local terminal without committing them.
2. Keep CHAT_DB_URL, CHAT_DB_USER, CHAT_DB_PASSWORD configured locally for the migration tool.
3. Ensure ~/.campus-lost-found/data/uploads contains the original photo files.
4. Build first: mvn clean package -DskipTests
5. Run migration once: java -cp "target/classes:$(cat /tmp/campusfind-classpath)" com.campus.lostfound.util.ImageKitMigrationTool
   If needed, refresh the dependency classpath first:
   mvn -q -DskipTests package dependency:build-classpath -Dmdep.outputFile=/tmp/campusfind-classpath
6. Check Neon photo_name values now start with https://ik.imagekit.io/ and test the app locally.
7. Only then deploy to Render.

Security:
- Never commit private keys.
- Remote ImageKit files are not automatically deleted by this patch. This avoids deleting images still referenced by another record; clean up unused assets from ImageKit later if desired.
- The migration tool is rerunnable and skips URLs, but don't run concurrent copies of it.
