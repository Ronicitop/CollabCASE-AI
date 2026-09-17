package com.collabcase.modelado.controlador;

import com.collabcase.modelado.dominio.ClaseDiagrama;
import com.collabcase.modelado.servicio.ClaseDiagramaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clases-diagrama")
public class ClaseDiagramaController {

    private final ClaseDiagramaService claseDiagramaService;

    public ClaseDiagramaController(
            ClaseDiagramaService claseDiagramaService) {

        this.claseDiagramaService = claseDiagramaService;
    }

    @PostMapping("/modelo/{modeloId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ClaseDiagrama crear(
            @PathVariable UUID modeloId,
            @RequestBody ClaseDiagrama claseDiagrama) {

        return claseDiagramaService.crear(modeloId, claseDiagrama);
    }

    @GetMapping("/modelo/{modeloId}")
    public List<ClaseDiagrama> listarPorModelo(
            @PathVariable UUID modeloId) {

        return claseDiagramaService.listarPorModelo(modeloId);
    }

    @GetMapping("/{id}")
    public ClaseDiagrama buscarPorId(
            @PathVariable UUID id) {

        return claseDiagramaService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public ClaseDiagrama actualizar(
            @PathVariable UUID id,
            @RequestBody ClaseDiagrama claseDiagrama) {

        return claseDiagramaService.actualizar(id, claseDiagrama);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable UUID id) {

        claseDiagramaService.eliminar(id);
    }
}
