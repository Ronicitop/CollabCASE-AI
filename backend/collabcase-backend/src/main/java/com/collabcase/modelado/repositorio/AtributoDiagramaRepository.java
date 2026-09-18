package com.collabcase.modelado.repositorio;

import com.collabcase.modelado.dominio.AtributoDiagrama;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtributoDiagramaRepository extends JpaRepository<AtributoDiagrama, UUID> {

    List<AtributoDiagrama> findByClaseId(UUID claseId);

    Optional<AtributoDiagrama> findByClaseIdAndNombre(
            UUID claseId,
            String nombre
    );

    boolean existsByClaseIdAndNombreIgnoreCase(
            UUID claseId,
            String nombre
    );

    boolean existsByClaseIdAndNombreIgnoreCaseAndIdNot(
            UUID claseId,
            String nombre,
            UUID id
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AtributoDiagrama a
               set a.nombre = :nombre,
                   a.tipoDato = :tipoDato,
                   a.permiteNulo = :permiteNulo,
                   a.identificador = :identificador
             where a.id = :atributoId
               and a.clase.id = :claseId
            """)
    int actualizarAtributo(
            @Param("atributoId") UUID atributoId,
            @Param("claseId") UUID claseId,
            @Param("nombre") String nombre,
            @Param("tipoDato") String tipoDato,
            @Param("permiteNulo") boolean permiteNulo,
            @Param("identificador") boolean identificador
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from AtributoDiagrama a
             where a.id = :atributoId
               and a.clase.id = :claseId
            """)
    int eliminarPorIdYClaseId(
            @Param("atributoId") UUID atributoId,
            @Param("claseId") UUID claseId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from AtributoDiagrama a
             where a.clase.id = :claseId
            """)
    int eliminarPorClaseId(
            @Param("claseId") UUID claseId
    );
}

