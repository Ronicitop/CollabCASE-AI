package com.collabcase.colaboracion.servicio;

import com.collabcase.colaboracion.dominio.SesionColaborativa;
import com.collabcase.colaboracion.dto.SesionColaborativaResponse;
import com.collabcase.colaboracion.repositorio.SesionColaborativaRepository;
import com.collabcase.proyecto.dominio.Proyecto;
import com.collabcase.proyecto.repositorio.ProyectoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SesionColaborativaService {

    private final SesionColaborativaRepository sesionColaborativaRepository;
    private final ProyectoRepository proyectoRepository;

    @Transactional
    public SesionColaborativaResponse iniciarSesion(UUID proyectoId) {

        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Proyecto no encontrado")
                );

        String codigo = generarCodigoUnico();

        SesionColaborativa sesion = SesionColaborativa.builder()
                .codigo(codigo)
                .proyecto(proyecto)
                .activa(true)
                .build();

        SesionColaborativa sesionGuardada =
                sesionColaborativaRepository.save(sesion);

        return convertirAResponse(sesionGuardada);
    }

    @Transactional(readOnly = true)
    public SesionColaborativaResponse unirseSesion(String codigo) {

        String codigoNormalizado = codigo.trim().toUpperCase();

        SesionColaborativa sesion = sesionColaborativaRepository
                .findByCodigo(codigoNormalizado)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Sesión colaborativa no encontrada"));

        if (!sesion.isActiva()) {
            throw new IllegalStateException(
                    "La sesión colaborativa ya no está activa");
        }

        return convertirAResponse(sesion);
    }

    private String generarCodigoUnico() {

        String codigo;

        do {
            codigo = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase();
        } while (sesionColaborativaRepository.existsByCodigo(codigo));

        return codigo;
    }

    private SesionColaborativaResponse convertirAResponse(
            SesionColaborativa sesion
    ) {
        return new SesionColaborativaResponse(
                sesion.getId(),
                sesion.getCodigo(),
                sesion.getProyecto().getId(),
                sesion.isActiva(),
                sesion.getCreadaEn()
        );
    }
}
