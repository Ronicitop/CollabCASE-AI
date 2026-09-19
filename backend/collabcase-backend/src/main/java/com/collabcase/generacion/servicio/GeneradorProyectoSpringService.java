package com.collabcase.generacion.servicio;

import com.collabcase.generacion.dto.ArchivoGeneradoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class GeneradorProyectoSpringService {

    private final GeneradorBackendService generadorBackendService;

    public byte[] generarZip(UUID proyectoId) {

        List<ArchivoGeneradoResponse> archivos =
                new ArrayList<>();

        archivos.add(
                new ArchivoGeneradoResponse(
                        "pom.xml",
                        generarPom()
                )
        );

        archivos.add(
                new ArchivoGeneradoResponse(
                        "src/main/java/com/generado/backend/BackendGeneradoApplication.java",
                        generarAplicacionPrincipal()
                )
        );

        archivos.add(
                new ArchivoGeneradoResponse(
                        "src/main/resources/application.properties",
                        generarApplicationProperties()
                )
        );

        archivos.add(
                new ArchivoGeneradoResponse(
                        "README.md",
                        generarReadme()
                )
        );

        archivos.addAll(
                generadorBackendService.generarArchivos(
                        proyectoId
                )
        );

        return comprimir(archivos);
    }

    private byte[] comprimir(
            List<ArchivoGeneradoResponse> archivos
    ) {
        Set<String> rutas =
                new HashSet<>();

        try (
                ByteArrayOutputStream salida =
                        new ByteArrayOutputStream();

                ZipOutputStream zip =
                        new ZipOutputStream(
                                salida,
                                StandardCharsets.UTF_8
                        )
        ) {

            for (ArchivoGeneradoResponse archivo : archivos) {

                validarRuta(archivo.ruta());

                if (!rutas.add(archivo.ruta())) {
                    throw new IllegalStateException(
                            "Ruta generada duplicada: "
                                    + archivo.ruta()
                    );
                }

                ZipEntry entrada =
                        new ZipEntry(
                                archivo.ruta()
                        );

                zip.putNextEntry(entrada);

                zip.write(
                        archivo.contenido()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );

                zip.closeEntry();
            }

            zip.finish();

            return salida.toByteArray();

        } catch (IOException ex) {
            throw new IllegalStateException(
                    "No se pudo construir el ZIP del backend generado",
                    ex
            );
        }
    }

    private void validarRuta(String ruta) {

        if (ruta == null
                || ruta.isBlank()
                || ruta.startsWith("/")
                || ruta.startsWith("\\")
                || ruta.contains("..")) {

            throw new IllegalArgumentException(
                    "Ruta de archivo generada no válida: "
                            + ruta
            );
        }
    }

    private String generarPom() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">

                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>4.1.1</version>
                        <relativePath/>
                    </parent>

                    <groupId>com.generado</groupId>
                    <artifactId>backend-generado</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>backend-generado</name>

                    <properties>
                        <java.version>21</java.version>
                    </properties>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-webmvc</artifactId>
                        </dependency>

                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>

                        <dependency>
                            <groupId>org.postgresql</groupId>
                            <artifactId>postgresql</artifactId>
                            <scope>runtime</scope>
                        </dependency>

                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <optional>true</optional>
                        </dependency>

                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>

                </project>
                """;
    }

    private String generarAplicacionPrincipal() {
        return """
                package com.generado.backend;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class BackendGeneradoApplication {

                    public static void main(String[] args) {
                        SpringApplication.run(
                                BackendGeneradoApplication.class,
                                args
                        );
                    }
                }
                """;
    }

    private String generarApplicationProperties() {
        return """
                spring.application.name=backend-generado

                spring.datasource.url=jdbc:postgresql://localhost:5432/${DB_NAME:backend_generado}
                spring.datasource.username=${DB_USER:postgres}
                spring.datasource.password=${DB_PASSWORD}

                spring.jpa.hibernate.ddl-auto=update
                spring.jpa.open-in-view=false

                server.port=${PORT:8081}
                """;
    }

    private String generarReadme() {
        return """
                # Backend generado por CollabCASE AI

                Proyecto Spring Boot generado desde el modelo UML canónico.

                Requisitos:
                - Java 21
                - Maven 3.9+
                - PostgreSQL

                Variables de entorno:
                - DB_PASSWORD: contraseña de PostgreSQL
                - DB_USER: usuario de PostgreSQL (opcional, por defecto postgres)
                - DB_NAME: base de datos (opcional, por defecto backend_generado)
                - PORT: puerto HTTP (opcional, por defecto 8081)

                Antes de ejecutar, cree la base de datos indicada por DB_NAME.

                Ejecución:

                    mvn spring-boot:run
                """;
    }
}
