package com.collabcase.colaboracion.dominio;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mensaje_sala")
public class MensajeSala {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "sesion_id", nullable = false)
    private UUID sesionId;

    @Column(name = "remitente_nombre", nullable = false, length = 120)
    private String remitenteNombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_emisor", nullable = false, length = 20)
    private TipoEmisor tipoEmisor;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String contenido;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    public enum TipoEmisor {
        USUARIO, IA, SISTEMA
    }

    public MensajeSala() {}

    public MensajeSala(UUID sesionId, String remitenteNombre, TipoEmisor tipoEmisor, String contenido) {
        this.sesionId = sesionId;
        this.remitenteNombre = remitenteNombre;
        this.tipoEmisor = tipoEmisor;
        this.contenido = contenido;
        this.fechaHora = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getSesionId() { return sesionId; }
    public void setSesionId(UUID sesionId) { this.sesionId = sesionId; }
    public String getRemitenteNombre() { return remitenteNombre; }
    public void setRemitenteNombre(String remitenteNombre) { this.remitenteNombre = remitenteNombre; }
    public TipoEmisor getTipoEmisor() { return tipoEmisor; }
    public void setTipoEmisor(TipoEmisor tipoEmisor) { this.tipoEmisor = tipoEmisor; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
}