FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
ARG VITE_GOOGLE_CLOUD_PROJECT
ARG VITE_GRAFANA_URL
ENV VITE_GOOGLE_CLOUD_PROJECT=$VITE_GOOGLE_CLOUD_PROJECT VITE_GRAFANA_URL=$VITE_GRAFANA_URL
RUN npm run build

FROM nginx:1.27-alpine AS cloud
ENV NGINX_ENVSUBST_FILTER="^(PORT|API_GATEWAY_URL|API_GATEWAY_HOST)$"
COPY cloud-run.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:${PORT}/health || exit 1

FROM nginx:1.27-alpine AS local
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1/health || exit 1
