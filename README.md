# Campus Lost & Found Management System

A beginner-friendly Java web application using JSP, Servlets, and file handling (no database).

## Requirements
- JDK 17+
- Maven 3.8+
- Apache Tomcat 10.1+
- Browser

## Build
From this directory:
```bash
mvn clean package
```
This creates `target/campus-lost-found.war`.

## Run on Tomcat
Copy the WAR into Tomcat's `webapps/` directory, start Tomcat, then visit:
`http://localhost:8080/campus-lost-found/`

Example:
```bash
cp target/campus-lost-found.war /path/to/apache-tomcat-10.1.x/webapps/
 /path/to/apache-tomcat-10.1.x/bin/startup.sh
```

## Data files
Data is stored outside the deployed app, by default in:
`~/.campus-lost-found/data/`
The application creates `users.txt`, `items.txt`, and `claims.txt` automatically.
You can override the folder with the JVM property:
`-Dcampus.data.dir=/absolute/path/to/data`

## Admin account
On first startup, an admin account is created only if both environment variables are supplied:
- `CAMPUS_ADMIN_EMAIL`
- `CAMPUS_ADMIN_PASSWORD`

Example (set these before starting Tomcat):
```bash
export CAMPUS_ADMIN_EMAIL=admin@college.example
export CAMPUS_ADMIN_PASSWORD='Use-A-Strong-Password-Here'
```
Then start Tomcat from that same terminal. If Tomcat is already running, stop it first.
For real deployments, use a unique strong password and do not use a shared/public computer for the admin account.

If no admin environment variables are configured, registration creates student accounts only and admin features remain unavailable.

## Features
- Register and log in
- Report lost/found items, with reporter email and optional contact phone shown on found-item listings
- Search listings
- Submit claims with ownership clues
- Admin dashboard showing all users, item reports, contact details, and claims; approve/reject claims
- File-based persistence
- Password hashing using PBKDF2

## Important limitations
This is an academic demo, not a production service. Flat files are not ideal for concurrent multi-user writes, backups, or sensitive data. Use only sample data and do not store student IDs, phone numbers, wallet contents, or other sensitive information in public listings.


## Item photos

Reports may include an optional JPG or PNG photo (maximum 5 MB). Images are stored separately under `~/.campus-lost-found/data/uploads/`, outside the deployed WAR, so redeploying the app does not remove them. The application serves them through `/photo/<generated-file-name>`.


## Workflow upgrade (additive storage)
- Personal dashboard: `/dashboard` shows own reports, claims, notifications, and return confirmations.
- Advanced listing filters: query, type, category, location, status, and date range.
- Photo preview before upload (JPG/PNG, max 5 MB).
- Claim review creates notifications for the claimant and reporter.
- Approved claims enter `RETURN_PENDING`; both the reporter and claimant must confirm the handover before status becomes `RETURNED`.
- Admin dashboard shows basic lost/found/returned analytics and the 30 most recent audit entries.
- New files `notifications.txt`, `audit.txt`, and `handovers.txt` are created automatically in the existing data directory. Existing `users.txt`, `items.txt`, `claims.txt`, and upload files are not migrated or overwritten.

Build with Java 17 and Maven: `mvn clean package`. Test locally before replacing the deployed WAR.
