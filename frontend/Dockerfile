# ==========================================
# Fase 1: Compilación de Frontend React con Node
# ==========================================
FROM node:22-alpine AS builder

WORKDIR /app

# Instalar dependencias
COPY package*.json ./
RUN npm ci

# Copiar el código fuente
COPY . .

# Argumentos de construcción para URLs del API y WebSocket
ARG VITE_API_URL=http://localhost:8080
ARG VITE_WS_URL=ws://localhost:8080

ENV VITE_API_URL=$VITE_API_URL
ENV VITE_WS_URL=$VITE_WS_URL

# Construir artefactos de producción (Vite + TypeScript + PWA)
RUN npm run build

# ==========================================
# Fase 2: Servidor Nginx para producción
# ==========================================
FROM nginx:alpine

# Copiar configuración personalizada de Nginx (SPA + Proxy Inverso + WebSockets)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copiar archivos compilados desde la fase builder
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
