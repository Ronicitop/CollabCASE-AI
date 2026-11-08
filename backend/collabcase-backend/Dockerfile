# ==========================================
# Fase 1: Compilación con Maven y JDK 21
# ==========================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Descargar dependencias para aprovechar la caché de capas de Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copiar código fuente y empaquetar el JAR ejecutable (omitiendo tests para agilizar el build)
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Fase 2: Imagen final liviana con JRE 21
# ==========================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Crear usuario y grupo sin privilegios de root por seguridad
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copiar el archivo JAR empaquetado desde la fase builder
COPY --from=builder /build/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
