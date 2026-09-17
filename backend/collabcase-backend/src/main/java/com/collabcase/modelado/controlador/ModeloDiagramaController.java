package com.collabcase.modelado.controlador;

import com.collabcase.modelado.dominio.ModeloDiagrama;
import com.collabcase.modelado.dto.ModeloCompletoResponse;
import com.collabcase.modelado.servicio.ModeloDiagramaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.collabcase.modelado.dto.ModeloCompletoRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@RestController
@RequestMapping("/api/modelos-diagrama")
public class ModeloDiagramaController {

    private final ModeloDiagramaService modeloDiagramaService;

    public ModeloDiagramaController(
            ModeloDiagramaService modeloDiagramaService) {

        this.modeloDiagramaService = modeloDiagramaService;
    }

    @PostMapping("/proyecto/{proyectoId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ModeloDiagrama crearParaProyecto(
            @PathVariable UUID proyectoId) {

        return modeloDiagramaService.crearParaProyecto(proyectoId);
    }

    @GetMapping("/{id}")
    public ModeloDiagrama buscarPorId(
            @PathVariable UUID id) {

        return modeloDiagramaService.buscarPorId(id);
    }

    @GetMapping("/proyecto/{proyectoId}")
    public ModeloDiagrama buscarPorProyecto(
            @PathVariable UUID proyectoId) {

        return modeloDiagramaService.buscarPorProyecto(proyectoId);
    }

    @GetMapping("/proyecto/{proyectoId}/completo")
    public ModeloCompletoResponse obtenerModeloCompleto(
            @PathVariable UUID proyectoId) {

        return modeloDiagramaService.obtenerModeloCompleto(proyectoId);
    }

    @PutMapping("/proyecto/{proyectoId}/completo")
    public ModeloCompletoResponse guardarModeloCompleto(
            @PathVariable UUID proyectoId,
            @Valid @RequestBody ModeloCompletoRequest request) {

        return modeloDiagramaService.guardarModeloCompleto(
                proyectoId,
                request);
    }
}