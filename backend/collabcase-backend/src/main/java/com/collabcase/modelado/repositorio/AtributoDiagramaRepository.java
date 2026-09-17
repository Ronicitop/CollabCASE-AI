package com.collabcase.modelado.repositorio;

import com.collabcase.modelado.dominio.AtributoDiagrama;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtributoDiagramaRepository extends JpaRepository<AtributoDiagrama, UUID> {

    List<AtributoDiagrama> findByClaseId(UUID claseId);

    Optional<AtributoDiagrama> findByClaseIdAndNombre(
            UUID claseId,
            String nombre
    );
}
