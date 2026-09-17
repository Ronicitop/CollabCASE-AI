package com.collabcase.modelado.controlador;

import com.collabcase.modelado.dominio.RelacionDiagrama;
import com.collabcase.modelado.servicio.RelacionDiagramaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/relaciones-diagrama")
public class RelacionDiagramaController {

    private final RelacionDiagramaService relacionDiagramaService;

    public RelacionDiagramaController(
            RelacionDiagramaService relacionDiagramaService) {

        this.relacionDiagramaService = relacionDiagramaService;
    }

    @PostMapping("/modelo/{modeloId}")
    @ResponseStatus(HttpStatus.CREATED)
    public RelacionDiagrama crear(
            @PathVariable UUID modeloId,
            @RequestParam UUID claseOrigenId,
            @RequestParam UUID claseDestinoId,
            @RequestBody RelacionDiagrama relacionDiagrama) {

        return relacionDiagramaService.crear(
                modeloId,
                claseOrigenId,
                claseDestinoId,
                relacionDiagrama
        );
    }

    @GetMapping("/modelo/{modeloId}")
    public List<RelacionDiagrama> listarPorModelo(
            @PathVariable UUID modeloId) {

        return relacionDiagramaService.listarPorModelo(modeloId);
    }

    @GetMapping("/{id}")
    public RelacionDiagrama buscarPorId(
            @PathVariable UUID id) {

        return relacionDiagramaService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public RelacionDiagrama actualizar(
            @PathVariable UUID id,
            @RequestBody RelacionDiagrama relacionDiagrama) {

        return relacionDiagramaService.actualizar(id, relacionDiagrama);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable UUID id) {

        relacionDiagramaService.eliminar(id);
    }
}
