package com.collabcase.modelado.repositorio;

import com.collabcase.modelado.dominio.ClaseDiagrama;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaseDiagramaRepository extends JpaRepository<ClaseDiagrama, UUID> {

    List<ClaseDiagrama> findByModeloId(UUID modeloId);

    Optional<ClaseDiagrama> findByModeloIdAndNombre(
            UUID modeloId,
            String nombre
    );

    boolean existsByModeloIdAndNombreIgnoreCase(
            UUID modeloId,
            String nombre
    );

    boolean existsByModeloIdAndNombreIgnoreCaseAndIdNot(
            UUID modeloId,
            String nombre,
            UUID id
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ClaseDiagrama c
               set c.posicionX = :posicionX,
                   c.posicionY = :posicionY
             where c.id = :claseId
               and c.modelo.id = :modeloId
            """)
    int actualizarPosicion(
            @Param("claseId") UUID claseId,
            @Param("modeloId") UUID modeloId,
            @Param("posicionX") double posicionX,
            @Param("posicionY") double posicionY
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ClaseDiagrama c
               set c.nombre = :nombre
             where c.id = :claseId
               and c.modelo.id = :modeloId
            """)
    int actualizarNombre(
            @Param("claseId") UUID claseId,
            @Param("modeloId") UUID modeloId,
            @Param("nombre") String nombre
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from ClaseDiagrama c
             where c.id = :claseId
               and c.modelo.id = :modeloId
            """)
    int eliminarPorIdYModeloId(
            @Param("claseId") UUID claseId,
            @Param("modeloId") UUID modeloId
    );
}
