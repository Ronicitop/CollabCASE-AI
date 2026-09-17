package com.collabcase.proyecto.controlador;

import com.collabcase.proyecto.dominio.Proyecto;
import com.collabcase.proyecto.servicio.ProyectoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/proyectos")
public class ProyectoController {

    private final ProyectoService proyectoService;

    public ProyectoController(ProyectoService proyectoService) {
        this.proyectoService = proyectoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Proyecto crear(@RequestBody Proyecto proyecto) {
        return proyectoService.crear(proyecto);
    }

    @GetMapping
    public List<Proyecto> listarTodos() {
        return proyectoService.listarTodos();
    }

    @GetMapping("/{id}")
    public Proyecto buscarPorId(@PathVariable UUID id) {
        return proyectoService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public Proyecto actualizar(
            @PathVariable UUID id,
            @RequestBody Proyecto proyecto) {

        return proyectoService.actualizar(id, proyecto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id) {
        proyectoService.eliminar(id);
    }
}
