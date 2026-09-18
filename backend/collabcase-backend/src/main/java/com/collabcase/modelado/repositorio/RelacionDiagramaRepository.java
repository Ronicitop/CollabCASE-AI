package com.collabcase.modelado.repositorio;

import com.collabcase.modelado.dominio.RelacionDiagrama;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RelacionDiagramaRepository extends JpaRepository<RelacionDiagrama, UUID> {

    List<RelacionDiagrama> findByModeloId(UUID modeloId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RelacionDiagrama r
               set r.tipo = :tipo,
                   r.multiplicidadOrigen = :multiplicidadOrigen,
                   r.multiplicidadDestino = :multiplicidadDestino,
                   r.nombre = :nombre
             where r.id = :relacionId
               and r.modelo.id = :modeloId
            """)
    int actualizarRelacion(
            @Param("relacionId") UUID relacionId,
            @Param("modeloId") UUID modeloId,
            @Param("tipo") String tipo,
            @Param("multiplicidadOrigen") String multiplicidadOrigen,
            @Param("multiplicidadDestino") String multiplicidadDestino,
            @Param("nombre") String nombre
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from RelacionDiagrama r
             where r.id = :relacionId
               and r.modelo.id = :modeloId
            """)
    int eliminarPorIdYModeloId(
            @Param("relacionId") UUID relacionId,
            @Param("modeloId") UUID modeloId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from RelacionDiagrama r
             where r.claseOrigen.id = :claseId
                or r.claseDestino.id = :claseId
            """)
    int eliminarPorClaseId(
            @Param("claseId") UUID claseId
    );
}
