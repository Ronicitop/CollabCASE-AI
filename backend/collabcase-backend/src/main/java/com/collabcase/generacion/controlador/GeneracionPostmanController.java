package com.collabcase.generacion.controlador;

import com.collabcase.generacion.servicio.GeneradorPostmanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/generacion")
@RequiredArgsConstructor
public class GeneracionPostmanController {

    private final GeneradorPostmanService generadorPostmanService;

    @GetMapping(
            value = "/proyectos/{proyectoId}/postman",
            produces = "application/json"
    )
    public ResponseEntity<byte[]> descargarColeccion(
            @PathVariable UUID proyectoId
    ) {

        byte[] contenido =
                generadorPostmanService.generarColeccion(
                        proyectoId
                );

        String nombreArchivo =
                "collabcase-"
                        + proyectoId
                        + ".postman_collection.json";

        ContentDisposition disposicion =
                ContentDisposition.attachment()
                        .filename(
                                nombreArchivo,
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposicion.toString()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(contenido);
    }
}
