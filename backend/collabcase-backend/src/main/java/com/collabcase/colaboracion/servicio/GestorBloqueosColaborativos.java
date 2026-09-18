package com.collabcase.colaboracion.servicio;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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
        return ejecutarConBloqueos(
                List.of(claveRecurso),
                accion
        );
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

    public <T> T ejecutarConBloqueos(
            Collection<String> clavesRecursos,
            Supplier<T> accion
    ) {

        List<String> clavesOrdenadas = clavesRecursos.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<ReentrantLock> bloqueosAdquiridos =
                new ArrayList<>();

        try {
            for (String clave : clavesOrdenadas) {
                ReentrantLock bloqueo = bloqueos.computeIfAbsent(
                        clave,
                        valor -> new ReentrantLock(true)
                );

                bloqueo.lock();
                bloqueosAdquiridos.add(bloqueo);
            }

            return accion.get();
        } finally {
            for (int i = bloqueosAdquiridos.size() - 1; i >= 0; i--) {
                bloqueosAdquiridos.get(i).unlock();
            }
        }
    }
}
