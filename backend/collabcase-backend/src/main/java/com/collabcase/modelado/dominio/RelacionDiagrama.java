package com.collabcase.modelado.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "relaciones_diagrama")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelacionDiagrama {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(
            name = "modelo_id",
            nullable = false
    )
    private ModeloDiagrama modelo;

    @ManyToOne(optional = false)
    @JoinColumn(
            name = "clase_origen_id",
            nullable = false
    )
    private ClaseDiagrama claseOrigen;

    @ManyToOne(optional = false)
    @JoinColumn(
            name = "clase_destino_id",
            nullable = false
    )
    private ClaseDiagrama claseDestino;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(name = "multiplicidad_origen", length = 20)
    private String multiplicidadOrigen;

    @Column(name = "multiplicidad_destino", length = 20)
    private String multiplicidadDestino;

    @Column(length = 100)
    private String nombre;
}
