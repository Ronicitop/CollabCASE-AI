package com.collabcase.generacion.controlador;

import com.collabcase.generacion.dto.VistaPreviaBackendResponse;
import com.collabcase.generacion.servicio.GeneradorBackendService;
import com.collabcase.generacion.servicio.GeneradorProyectoSpringService;
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
public class GeneracionBackendController {

    private final GeneradorBackendService generadorBackendService;
    private final GeneradorProyectoSpringService generadorProyectoSpringService;

    @GetMapping("/proyectos/{proyectoId}/backend/vista-previa")
    public VistaPreviaBackendResponse vistaPrevia(
            @PathVariable UUID proyectoId
    ) {
        return generadorBackendService.generarVistaPrevia(
                proyectoId
        );
    }

    @GetMapping(
            value = "/proyectos/{proyectoId}/backend/zip",
            produces = "application/zip"
    )
    public ResponseEntity<byte[]> descargarBackend(
            @PathVariable UUID proyectoId
    ) {
        byte[] contenido =
                generadorProyectoSpringService.generarZip(
                        proyectoId
                );

        String nombreArchivo =
                "backend-generado-"
                        + proyectoId
                        + ".zip";

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
                .contentType(
                        MediaType.parseMediaType(
                                "application/zip"
                        )
                )
                .body(contenido);
    }
}
