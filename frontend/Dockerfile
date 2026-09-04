FROM node:22-alpine3.22 AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . ./
RUN npm run build

FROM caddy:2.11.4-alpine
RUN addgroup -S devguide && adduser -S -G devguide -h /home/devguide devguide \
    && chown -R devguide:devguide /data /config
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build --chown=devguide:devguide /workspace/dist /srv
USER devguide
EXPOSE 80 443 443/udp 8080
