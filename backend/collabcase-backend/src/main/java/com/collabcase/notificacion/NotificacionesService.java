package com.collabcase.notificacion;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/** Nunca realiza llamadas de red dentro de la transacción de modelado. */
@Service
public class NotificacionesService {
    private static final Logger log = LoggerFactory.getLogger(NotificacionesService.class);
    private final EmisorPush emisor;
    private final boolean habilitado;
    private final ScheduledExecutorService executor;
    private final Map<UUID, Boolean> pendientes = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public NotificacionesService(EmisorPush emisor, @Value("${push.enabled:false}") boolean habilitado) {
        this(emisor, habilitado, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "collabcase-push");
            t.setDaemon(true);
            return t;
        }));
    }

    NotificacionesService(EmisorPush emisor, boolean habilitado, ScheduledExecutorService executor) {
        this.emisor = emisor;
        this.habilitado = habilitado;
        this.executor = executor;
    }

    public void modelo(UUID proyectoId) {
        despuesDeConfirmar(() -> {
            if (pendientes.putIfAbsent(proyectoId, true) == null) {
                executor.schedule(() -> {
                    pendientes.remove(proyectoId);
                    enviarSeguro(new AvisoPush("MODELO", proyectoId, null, null));
                }, 60, TimeUnit.SECONDS);
            }
        });
    }

    public void proyecto(UUID proyectoId) {
        despuesDeConfirmar(() -> executor.execute(() ->
                enviarSeguro(new AvisoPush("PROYECTO", proyectoId, null, null))));
    }

    public void mensaje(UUID proyectoId, UUID sesionId, UUID mensajeId) {
        despuesDeConfirmar(() -> executor.execute(() ->
                enviarSeguro(new AvisoPush("MENSAJE", proyectoId, sesionId, mensajeId))));
    }

    private void despuesDeConfirmar(Runnable accion) {
        if (!habilitado) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { ejecutarSeguro(accion); }
            });
        } else ejecutarSeguro(accion);
    }

    private void ejecutarSeguro(Runnable accion) {
        try { accion.run(); }
        catch (Exception ex) { log.warn("No se pudo programar un aviso push: {}", ex.getClass().getSimpleName()); }
    }

    private void enviarSeguro(AvisoPush aviso) {
        try { emisor.enviar(aviso); }
        catch (Exception ex) {
            log.warn("No se pudo enviar push del tipo {}: {}", aviso.tipo(), ex.getClass().getSimpleName());
        }
    }

    @PreDestroy public void cerrar() { executor.shutdownNow(); }
}
