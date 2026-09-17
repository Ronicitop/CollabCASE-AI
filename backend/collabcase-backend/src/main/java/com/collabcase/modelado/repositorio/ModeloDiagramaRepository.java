package com.collabcase.modelado.repositorio;

import com.collabcase.modelado.dominio.ModeloDiagrama;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ModeloDiagramaRepository extends JpaRepository<ModeloDiagrama, UUID> {

    Optional<ModeloDiagrama> findByProyectoId(UUID proyectoId);
}
