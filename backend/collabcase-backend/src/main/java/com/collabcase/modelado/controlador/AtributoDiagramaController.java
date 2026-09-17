package com.collabcase.modelado.controlador;

import com.collabcase.modelado.dominio.AtributoDiagrama;
import com.collabcase.modelado.servicio.AtributoDiagramaService;
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
@RequestMapping("/api/atributos-diagrama")
public class AtributoDiagramaController {

    private final AtributoDiagramaService atributoDiagramaService;

    public AtributoDiagramaController(
            AtributoDiagramaService atributoDiagramaService) {

        this.atributoDiagramaService = atributoDiagramaService;
    }

    @PostMapping("/clase/{claseId}")
    @ResponseStatus(HttpStatus.CREATED)
    public AtributoDiagrama crear(
            @PathVariable UUID claseId,
            @RequestBody AtributoDiagrama atributoDiagrama) {

        return atributoDiagramaService.crear(claseId, atributoDiagrama);
    }

    @GetMapping("/clase/{claseId}")
    public List<AtributoDiagrama> listarPorClase(
            @PathVariable UUID claseId) {

        return atributoDiagramaService.listarPorClase(claseId);
    }

    @GetMapping("/{id}")
    public AtributoDiagrama buscarPorId(
            @PathVariable UUID id) {

        return atributoDiagramaService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public AtributoDiagrama actualizar(
            @PathVariable UUID id,
            @RequestBody AtributoDiagrama atributoDiagrama) {

        return atributoDiagramaService.actualizar(id, atributoDiagrama);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable UUID id) {

        atributoDiagramaService.eliminar(id);
    }
}
