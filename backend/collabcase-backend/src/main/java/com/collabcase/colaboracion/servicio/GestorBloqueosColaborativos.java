package com.collabcase.colaboracion.servicio;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
public class GestorBloqueosColaborativos {

    private final ConcurrentMap<String, ReentrantLock> bloqueos =
            new ConcurrentHashMap<>();

    public <T> T ejecutarConBloqueo(
            String claveRecurso,
            Supplier<T> accion
    ) {
        ReentrantLock bloqueo = bloqueos.computeIfAbsent(
                claveRecurso,
                clave -> new ReentrantLock(true)
        );

        bloqueo.lock();

        try {
            return accion.get();
        } finally {
            bloqueo.unlock();
        }
    }

    public void ejecutarConBloqueo(
            String claveRecurso,
            Runnable accion
    ) {
        ejecutarConBloqueo(
                claveRecurso,
                () -> {
                    accion.run();
                    return null;
                }
        );
    }
}
