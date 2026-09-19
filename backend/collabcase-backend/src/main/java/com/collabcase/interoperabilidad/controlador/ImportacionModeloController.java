package com.collabcase.interoperabilidad.controlador;

import com.collabcase.interoperabilidad.dto.ModeloImportadoXmi;
import com.collabcase.interoperabilidad.servicio.ImportacionModeloService;
import com.collabcase.interoperabilidad.servicio.ImportacionXmiService;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/interoperabilidad")
@RequiredArgsConstructor
public class ImportacionModeloController {

    private final ImportacionXmiService importacionXmiService;
    private final ImportacionModeloService importacionModeloService;

    @PostMapping(
            value = "/importar/xmi/analizar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ModeloImportadoXmi analizarXmi(
            @RequestPart("archivo") MultipartFile archivo
    ) {
        return importacionXmiService.analizar(archivo);
    }

    @PostMapping(
            value = "/proyectos/{proyectoId}/importar/xmi",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ModeloCompletoResponse importarXmi(
            @PathVariable UUID proyectoId,
            @RequestPart("archivo") MultipartFile archivo
    ) {
        return importacionModeloService.importarEnProyecto(
                proyectoId,
                archivo
        );
    }
}
