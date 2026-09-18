package com.collabcase.interoperabilidad.servicio;

import com.collabcase.modelado.dto.ModeloCompletoResponse;
import com.collabcase.modelado.servicio.ModeloDiagramaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportacionModeloService {

    private final ModeloDiagramaService modeloDiagramaService;

    public ModeloCompletoResponse obtenerModeloParaExportacion(
            UUID proyectoId
    ) {
        return modeloDiagramaService.obtenerModeloCompleto(
                proyectoId
        );
    }
}
