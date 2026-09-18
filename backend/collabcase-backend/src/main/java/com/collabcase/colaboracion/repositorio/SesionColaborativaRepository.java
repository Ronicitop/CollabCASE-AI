package com.collabcase.colaboracion.repositorio;

import com.collabcase.colaboracion.dominio.SesionColaborativa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SesionColaborativaRepository
        extends JpaRepository<SesionColaborativa, UUID> {

    boolean existsByCodigo(String codigo);

    Optional<SesionColaborativa> findByCodigo(String codigo);
}
