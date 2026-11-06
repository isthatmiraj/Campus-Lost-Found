CampusFind Admin Presence + User Search
========================================

This update adds admin-only online/offline indicators and live user search.

Presence behavior
-----------------
- Each authenticated browser session has its own opaque SHA-256 session-key hash
  in campus_user_presence. The raw servlet session ID is never stored.
- A client heartbeat is sent approximately every 60 seconds while the visitor
  has interacted with the page recently.
- The user is considered online when at least one of their browser sessions
  has checked in during the last 2 minutes.
- Logging out removes only that browser session's presence row. Other active
  devices remain online.
- Browser closure, lost connectivity, or an abandoned tab naturally expires
  from the online set after 2 minutes.
- Heartbeats and admin status refreshes stop when the page is hidden or has
  been inactive for more than 2 minutes. This avoids using background polling
  to keep the existing 30-minute HTTP session alive.
- Admin status data is returned only to authenticated administrators.

Database
--------
BootstrapServlet runs DatabaseSchema.initialize() on startup. The additive
CREATE TABLE IF NOT EXISTS statement creates campus_user_presence and its
indexes. It does not drop, reset, or modify existing user/listing/chat data.

User search
-----------
The Registered users table filters its already-loaded rows by partial,
case-insensitive name or email matches. It does not make additional database
requests.

Verification
------------
JavaScript syntax and web.xml XML structure were checked. Maven is not
installed in the editing environment, so `mvn clean package` could not be
run here. Run it locally before deployment.
