# CampusFind upgrade: dark mode, item details, saved items, flags, stats, security

## Included
- Browser-persisted dark-mode toggle; light theme remains default.
- Dedicated `/item?id=...` detail page.
- Saved items stored in additive `saved-items.txt`.
- Listing flag reports stored in additive `listing-flags.txt`, with admin review/dismiss controls.
- Public campus listing totals (all/lost/found/returned).
- Security response headers and HttpOnly session cookie setting.
- Existing user/item/claim/photo/notification/audit/handover formats are not rewritten.

## Important
This is an additive source upgrade. Build and test locally before deploying. Security headers are a baseline, not a substitute for CSRF tokens, rate limiting, and production HTTPS.
