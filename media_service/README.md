# MARU RADIO hosted song & story server (DEPLOYMENT-READY SOURCE, NOT DEPLOYED)

This backend is intentionally NOT hosted on GitHub Pages; uploads need a private persistent HTTPS server. No user's original audio is checked into public GitHub. There are zero default admin credentials.

## Intended production setup
1. Create a private cloud server with HTTPS reverse proxy (not the broadcaster's phone).
2. Install Node >=20, ClamAV scanning tools; maintain private persistent disk, protected backups.
3. In \`media_service\`, run \`npm install --omit=dev\`.
4. Set unique secure environment variables in .env.example through hosting provider secret manager; NEVER commit real secrets.
5. Use \`node server.js\` behind HTTPS (e.g. managed reverse proxy).
6. Enter public HTTPS base URL and admin token in Android MARU MEDIA VAULT upload tab. Token stays in memory, not stored in the app.
7. Uploaded files remain PENDING, then admin confirms scan and rights and selects publish. No automatic publication.
8. Public site lives at /; admin moderation at /admin.html. Do not expose the private storage directory through the web server.

## Endpoints
- \`POST /api/upload\`: authenticated multipart: audio/title/sha256/language/genre/rights_confirmed
- \`GET /api/admin/tracks\`; \`POST /api/admin/tracks/:id/review\` (approve/reject with optional downloadAllowed)
- \`POST /api/stories\`: public multipart pending moderation.
- \`GET /api/admin/stories\`; \`POST /api/admin/stories/:id/review\`
- \`GET /api/tracks\`, \`GET /api/audio/:id\`, \`GET /api/download/:id\`, \`POST /api/view/:id\`, \`POST /api/like/:id\`
- \`GET /api/comments/:id\`, \`POST /api/comments/:id\`, moderation required.

## Constraints
- Server can be run in a single process only. JSON atomic persistence is not a clustered database and needs backups.
- User-submitted attachment scans require ClamAV available. If scanner unavailable, media remains quarantine and can NEVER be published via admin endpoint; public text-only stories can be approved.
- Malware scanner is not foolproof; no security system guarantees 100% protection.
- The public portal is not yet hosted. GitHub Actions is used only for CI and APK distribution.
- ONE phone for host control; other listeners open a browser on their own devices. Server must be separately provisioned.
- Suno commercial rights and download licenses need checking per track. 'Approved' publishing is a conscious owner action.
