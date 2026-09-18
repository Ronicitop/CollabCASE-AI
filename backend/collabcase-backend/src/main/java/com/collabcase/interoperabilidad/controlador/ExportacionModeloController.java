package com.collabcase.interoperabilidad.controlador;

import com.collabcase.interoperabilidad.servicio.ExportacionXmiService;
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
@RequestMapping("/api/interoperabilidad")
@RequiredArgsConstructor
public class ExportacionModeloController {

    private final ExportacionXmiService exportacionXmiService;

    @GetMapping(
            value = "/proyectos/{proyectoId}/exportar/xmi",
            produces = "application/xml"
    )
    public ResponseEntity<byte[]> exportarXmi(
            @PathVariable UUID proyectoId
    ) {

        byte[] contenido =
                exportacionXmiService.exportarProyecto(
                        proyectoId
                );

        ContentDisposition disposicion =
                ContentDisposition.attachment()
                        .filename(
                                "collabcase-"
                                        + proyectoId
                                        + ".xmi",
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposicion.toString()
                )
                .contentType(MediaType.APPLICATION_XML)
                .body(contenido);
    }
}
