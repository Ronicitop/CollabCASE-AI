package com.collabcase.colaboracion.controlador;

import com.collabcase.colaboracion.dto.SesionColaborativaResponse;
import com.collabcase.colaboracion.servicio.SesionColaborativaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sesiones-colaborativas")
@RequiredArgsConstructor
public class SesionColaborativaController {

    private final SesionColaborativaService sesionColaborativaService;

    @GetMapping("/proyecto/{proyectoId}")
    public java.util.List<SesionColaborativaResponse> listarPorProyecto(@PathVariable UUID proyectoId) {
        return sesionColaborativaService.listarPorProyecto(proyectoId);
    }

    @PostMapping("/proyecto/{proyectoId}")
    @ResponseStatus(HttpStatus.CREATED)
    public SesionColaborativaResponse iniciarSesion(
            @PathVariable UUID proyectoId
    ) {
        return sesionColaborativaService.iniciarSesion(proyectoId);
    }

    @PostMapping("/unirse/{codigo}")
    public SesionColaborativaResponse unirseSesion(
            @PathVariable String codigo) {
        return sesionColaborativaService.unirseSesion(codigo);
    }
}
