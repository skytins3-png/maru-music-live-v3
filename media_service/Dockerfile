FROM node:22-bookworm-slim
WORKDIR /app
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       clamav clamav-freshclam ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /var/lib/clamav /data \
    && chown -R node:node /var/lib/clamav /data
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund
COPY server.js ./
COPY public ./public
USER node
ENV NODE_ENV=production
ENV PORT=8080
ENV MARU_STORAGE_DIR=/data
EXPOSE 8080
# FreshClam may need scheduled updates on the hosting provider.
# If signatures are missing, scanner fails closed and nothing is published.
CMD ["sh","-c","freshclam --quiet || true; node server.js"]
