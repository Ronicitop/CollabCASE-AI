package com.collabcase.modelado.repositorio;

import com.collabcase.modelado.dominio.ClaseDiagrama;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaseDiagramaRepository extends JpaRepository<ClaseDiagrama, UUID> {

    List<ClaseDiagrama> findByModeloId(UUID modeloId);

    Optional<ClaseDiagrama> findByModeloIdAndNombre(
            UUID modeloId,
            String nombre
    );
}
