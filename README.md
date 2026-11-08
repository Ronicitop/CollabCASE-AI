# CollabCASE AI - Despliegue con Docker

Este proyecto cuenta con una configuración completa de Docker y Docker Compose para levantar en minutos todo el entorno:
- **Base de Datos**: PostgreSQL 17 (Alpine) con volumen persistente y healthcheck.
- **Backend**: Spring Boot 4 / Java 21 empaquetado en una imagen ligera JRE 21 Alpine con usuario no-root.
- **Frontend**: Aplicación React + TypeScript + Vite + PWA servida por Nginx con proxy inverso integrado para REST y WebSockets.

---

## 🚀 Inicio Rápido

### 1. Requisitos previos
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado y en ejecución.

### 2. Configurar variables de entorno (Opcional)
Si deseas modificar puertos o añadir claves de API para los modelos de IA (Gemini, Azure OpenAI u Ollama):
```bash
cp .env.example .env
```

### 3. Construir y levantar los contenedores
Desde la carpeta raíz del repositorio:
```bash
docker compose up -d --build
```

### 4. Acceso a la aplicación
- **Frontend (Web App)**: [http://localhost:3000](http://localhost:3000)
- **Backend API REST**: [http://localhost:8080/api/proyectos](http://localhost:8080/api/proyectos)
- **PostgreSQL**: `localhost:5433` (usuario: `postgres`, contraseña: `postgres`, bd: `collabcase_db`)
  *(Se mapea al puerto 5433 del host para evitar conflictos si ya tienes PostgreSQL instalado en el 5432).*

---

## 🛑 Detener los servicios

Para detener los contenedores:
```bash
docker compose down
```

Para detenerlos y eliminar los volúmenes de datos (reinicio limpio de base de datos):
```bash
docker compose down -v
```

---

## 📋 Ver registros (Logs)

Ver logs de todos los servicios en tiempo real:
```bash
docker compose logs -f
```

Ver logs de un servicio específico:
```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres
```

---

## 🧠 Integración con Inteligencia Artificial

En el archivo `.env` (o `.env.example`):
- **Google Gemini**: Asigna tu clave en `GEMINI_API_KEY=tu_clave`.
- **Azure OpenAI**: Asigna tus credenciales en `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_DEPLOYMENT` y `AZURE_OPENAI_BASE_URL`.
- **Ollama (Local en tu máquina)**: Ya está preconfigurado con `http://host.docker.internal:11434/api/generate` para que el contenedor pueda comunicarse directamente con tu instancia de Ollama ejecutándose en el sistema operativo anfitrión.

## App Android Flutter

La app de consulta está en `mobile/`. Usa por defecto la API `http://192.168.0.9:8080` y la web local `http://192.168.0.9:3000`. Consulta [la guía móvil](mobile/README.md) para Firebase, las pruebas por Wi-Fi y la lista de comprobación en tu celular.
