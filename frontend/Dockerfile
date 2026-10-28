FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

# Unprivileged nginx: runs as non-root on port 8080 (OpenShift-friendly).
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/paystream-console/browser /usr/share/nginx/html
EXPOSE 8080
