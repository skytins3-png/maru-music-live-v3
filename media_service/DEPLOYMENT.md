# MARU media platform — deployment steps and safeguards

**State of this PR:** Source and automated API tests are ready; **there is no live cloud endpoint yet**.
Do not tell listeners that the server is online until it is actually hosted and tested.

## Requirements
- One Android phone for the host; public listeners use their own browsers.
- Separate persistent cloud server with at least 2 GB free for ~105 original songs,
  thumbnails, attachments, moderation data and backups. 105 originals are ~647 MB;
  planned capacity must include multiple copies.
- HTTPS domain/certificate and secure reverse proxy routing to 127.0.0.1:8080.
- Environment secrets: \`MARU_ADMIN_TOKEN\` and \`MARU_HASH_SALT\` independently
  random, minimum 32 characters. Never hard-code or push them to GitHub.
- ClamAV signatures installed and refreshed. Without a working scan the backend
  **refuses publication**. Manual review is required too.
- Consent/license checks for Suno originals and any listener-submitted media.

## Deployment sequence
1. Provision server, domain, persistent disk volume and HTTPS reverse proxy.
2. Copy \`media_service\` to the server; create private \`.env\` from \`.env.example\`.
3. Run \`docker compose up --build -d\` on the server.
4. Check \`https://your-domain/api/health\` returns an OK JSON object.
5. Open \`https://your-domain/admin.html\` and enter owner-only admin token.
6. On galaxy MARU MEDIA VAULT APK, supply base URL + token under **노래 업로드**.
7. Select verified private songs on the device, confirm rights, run batch upload.
8. Songs arrive as **pending**. Confirm antivirus + rights and choose publish.
9. Verify second device/browser can view and play approved songs, post a test story,
   comment and like; ensure nonapproved files never appear publicly.
10. Verify all selected originals have SHA-256 and count exactly 105 unique; leave
    the 2 duplicate original files unmodified and privately retained.
11. Schedule encrypted offsite backups and periodic recovery test.

## Conditions that do not automatically follow
- This source is not a running cloud service; no one can upload from outside
  until the server is provisioned.
- Voice broadcast to BIGO LIVE and cross-app microphone routing are not added.
- Preview, likes, views, scores and public comments require server deployment
  and admin moderation to work across listeners.
- A ClamAV scan and manual approval reduce risk, not eliminate all threats.
- The app's on-device 'view/like/comment' statistics are intentionally different
  from the website's shared counts until a full sync protocol is implemented.
- JSON state store is single-process with private disk; do not run multi replicas
  using the same folder. Back up both files and data.json consistently.
- Original 107 songs and their metadata stay in the user's private ChatGPT
  Library and are NOT committed to public source control.
