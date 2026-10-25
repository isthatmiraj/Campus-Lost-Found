# CampusFind workflow upgrade

This is a source update for the existing Java 17 / JSP / Servlet / Tomcat 10.1 project. It preserves the current users/items/claims text formats and photo filenames. New feature data is stored in three additional files in the same configured data directory: `notifications.txt`, `audit.txt`, and `handovers.txt`.

## Features
- Image preview and basic file-type/size validation in the report form. Server-side validation remains authoritative.
- Search filters for text, type, category, location, status, and date range.
- Personal `/dashboard` page with reports, claims, notifications, and handover confirmations.
- Notifications on new claims and claim decisions. Users can mark notifications as read.
- Approved claims become `RETURN_PENDING`; reporter and approved claimant each confirm the return. Only after both confirm does the item status become `RETURNED`.
- Admin analytics for lost, found, and returned reports, plus a recent audit log.

## Safe install on Linux Mint
1. Back up the project folder and data directory before deploying.
2. Extract this ZIP over the existing source project (`unzip -o ... -d ~/Downloads/campus-lost-found`).
3. Run `cd ~/Downloads/campus-lost-found && mvn clean package`; continue only if Maven prints `BUILD SUCCESS`.
4. Stop Tomcat, replace only the deployed app directory and WAR, copy the new `target/campus-lost-found.war` into `~/apache-tomcat/webapps/`, then start Tomcat.
5. Open `/campus-lost-found/dashboard`; admin analytics and audit log are at `/campus-lost-found/admin`.

## Notes
- Build requires JDK 17+ and Maven; runtime is intended for Tomcat 10.1 (Jakarta Servlet 6).
- New notification/audit/handover files are created lazily and do not overwrite the existing data files.
- This text-file implementation is suitable for a classroom/local project, not high-concurrency public production use.
- Maven was not installed in the generation environment, so a full Maven WAR build was unavailable here. All Java source files were syntax/type checked with local minimal Jakarta API stubs; JSP compilation and runtime integration should be verified on your machine with `mvn clean package` and Tomcat.
