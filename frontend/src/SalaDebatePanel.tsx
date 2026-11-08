import { API_URL } from './config';
import React, { useState, useEffect, useRef, useCallback } from 'react';
import type { Client, StompSubscription } from '@stomp/stompjs';
import { combinarMensajes, confirmarMensaje } from './chatMessages';
import type { MensajeDTO } from './chatMessages';

interface SalaDebatePanelProps {
  sesionId: string;
  proyectoId: string;
  conectado: boolean;
  usuarioNombre: string;
  stompClient?: Client | null;
  backendUrl?: string;
}

export const SalaDebatePanel: React.FC<SalaDebatePanelProps> = ({
  sesionId,
  proyectoId,
  conectado,
  usuarioNombre,
  stompClient,
  backendUrl = API_URL
}) => {
  const [mensajes, setMensajes] = useState<MensajeDTO[]>([]);
  const [nuevoMensaje, setNuevoMensaje] = useState('');
  const [cargando, setCargando] = useState(false);
  const [minimizado, setMinimizado] = useState(false);
  const [errorHistorial, setErrorHistorial] = useState('');
  const finMensajesRef = useRef<HTMLDivElement>(null);
  const contadorTemporal = useRef(0);

  const refrescarMensajes = useCallback(async (signal: AbortSignal) => {
    try {
      const res = await fetch(`${backendUrl}/api/sala/proyecto/${proyectoId}/mensajes`, { signal });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: MensajeDTO[] = await res.json();
      if (!Array.isArray(data) || signal.aborted) return;
      setMensajes(prev => combinarMensajes(prev, data));
      setErrorHistorial('');
      if (data.at(-1)?.tipoEmisor === 'IA') setCargando(false);
    } catch (err) {
      if (signal.aborted) return;
      setErrorHistorial('No se pudo actualizar el historial. Se reintentará al reconectar.');
      console.warn('Aviso sincronizando historial del proyecto:', err);
    }
  }, [backendUrl, proyectoId]);

  // Suscribirse antes de consultar el historial evita perder mensajes durante la carga.
  // conectado cambia también al reconectar el mismo cliente STOMP.
  useEffect(() => {
    const abort = new AbortController();
    let subscription: StompSubscription | undefined;
    if (conectado && stompClient?.connected) {
      subscription = stompClient.subscribe(`/topic/proyecto/${proyectoId}/mensajes`, payload => {
        if (abort.signal.aborted) return;
        try {
          const msg: MensajeDTO = JSON.parse(payload.body);
          setMensajes(prev => combinarMensajes(prev, [msg]));
          if (msg.tipoEmisor === 'IA') setCargando(false);
        } catch (error) { console.warn('Mensaje de chat inválido:', error); }
      });
    }
    queueMicrotask(() => {
      if (!abort.signal.aborted) void refrescarMensajes(abort.signal);
    });
    return () => {
      abort.abort();
      if (stompClient?.connected) subscription?.unsubscribe();
    };
  }, [stompClient, conectado, proyectoId, refrescarMensajes]);

  // Respaldo REST si falta WebSocket o mientras se espera una respuesta de IA.
  useEffect(() => {
    if (conectado && !cargando) return;
    const abort = new AbortController();
    const timer = setInterval(() => { void refrescarMensajes(abort.signal); }, cargando ? 2500 : 5000);
    const timeout = cargando ? setTimeout(() => setCargando(false), 12000) : undefined;
    return () => { abort.abort(); clearInterval(timer); clearTimeout(timeout); };
  }, [conectado, cargando, refrescarMensajes]);

  useEffect(() => {
    if (!minimizado) {
      finMensajesRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [mensajes, cargando, minimizado]);

  // 3. Envío de mensaje
  const enviarMensaje = async (e: React.FormEvent) => {
    e.preventDefault();
    const texto = nuevoMensaje.trim();
    if (!texto) return;

    const esConsultaIa = texto.toLowerCase().includes('@ia') || texto.toLowerCase().startsWith('ia:');
    if (esConsultaIa) {
      setCargando(true);
    }

    const payload: MensajeDTO = {
      sesionId,
      remitenteNombre: usuarioNombre || 'Ingeniero',
      tipoEmisor: 'USUARIO',
      contenido: texto,
    };

    const temporalId = `temp-${Date.now()}-${++contadorTemporal.current}`;
    setMensajes((prev) => combinarMensajes(prev, [{ ...payload, id: temporalId }]));
    setNuevoMensaje('');

    try {
      const res = await fetch(`${backendUrl}/api/sala/${sesionId}/mensaje`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      const guardado = await res.json();
      setMensajes((prev) => confirmarMensaje(prev, temporalId, guardado));
    } catch (err) {
      setMensajes(prev => prev.filter(m => m.id !== temporalId));
      setErrorHistorial('No se pudo enviar el mensaje. Puedes volver a intentarlo.');
      setNuevoMensaje(texto);
      console.error('Error enviando mensaje:', err);
      setCargando(false);
    }
  };

  // Botón flotante al estar minimizado
  if (minimizado) {
    return (
      <button
        onClick={() => setMinimizado(false)}
        style={styles.floatingButton}
        title="Abrir Sala de Debate & IA"
      >
        💬 Sala de Debate & IA ▲ {cargando && <span style={styles.typingDot}>● Escribiendo...</span>}
      </button>
    );
  }

  return (
    <aside style={styles.container}>
      <header style={styles.header}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <h3 style={styles.title}>💬 Chat del proyecto & IA Par</h3>
          <span style={styles.badge}>Sesión Activa</span>
        </div>
        <button
          onClick={() => setMinimizado(true)}
          style={styles.minimizeButton}
          title="Minimizar panel"
        >
          ─
        </button>
      </header>

      <div style={styles.participantsBar}>
        <span style={styles.participantTag}>👤 {usuarioNombre || 'Tú'}</span>
        <span style={{ ...styles.participantTag, backgroundColor: '#4f46e5', color: '#fff' }}>
          🤖 CollabCASE AI (Par)
        </span>
      </div>

      {errorHistorial && <p role="alert" style={{ padding: 8, margin: 0 }}>{errorHistorial}</p>}
      <div style={styles.messagesContainer}>
        {mensajes.length === 0 ? (
          <p style={styles.emptyState}>Todavía no hay mensajes en este proyecto. Escribe algo o usa @ia para consultar.</p>
        ) : (
          mensajes.map((m, idx) => {
            const esIa = m.tipoEmisor === 'IA' || m.remitenteNombre.includes('CollabCASE AI');
            const esMio = m.remitenteNombre === (usuarioNombre || 'Ingeniero');

            return (
              <div
                key={m.id || idx}
                style={{
                  ...styles.bubbleWrapper,
                  alignSelf: esIa ? 'center' : esMio ? 'flex-end' : 'flex-start',
                }}
              >
                <span style={styles.senderLabel}>
                  {esIa ? '🤖 ' + m.remitenteNombre : m.remitenteNombre}
                </span>
                <div
                  style={{
                    ...styles.bubble,
                    backgroundColor: esIa ? '#eef2ff' : esMio ? '#2563eb' : '#f1f5f9',
                    color: esMio ? '#ffffff' : '#0f172a',
                    border: esIa ? '1px solid #c7d2fe' : 'none',
                  }}
                >
                  <p style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{m.contenido}</p>
                </div>
              </div>
            );
          })
        )}
        {cargando && (
          <div style={styles.loadingBubble}>
            🤖 <em>CollabCASE AI está analizando el contexto y redactando respuesta...</em>
          </div>
        )}
        <div ref={finMensajesRef} />
      </div>

      <form onSubmit={enviarMensaje} style={styles.inputArea}>
        <input
          type="text"
          value={nuevoMensaje}
          onChange={(e) => setNuevoMensaje(e.target.value)}
          placeholder="Escribe al grupo (usa @ia para consultar)..."
          style={styles.input}
        />
        <button type="submit" style={styles.sendButton}>
          Enviar
        </button>
      </form>
    </aside>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    position: 'fixed',
    top: '70px',
    right: '0',
    bottom: '0',
    width: '380px',
    backgroundColor: '#ffffff',
    borderLeft: '2px solid #cbd5e1',
    boxShadow: '-4px 0 20px rgba(0, 0, 0, 0.15)',
    display: 'flex',
    flexDirection: 'column',
    zIndex: 9999,
    boxSizing: 'border-box',
  },
  floatingButton: {
    position: 'fixed',
    bottom: '24px',
    right: '24px',
    backgroundColor: '#2563eb',
    color: '#ffffff',
    border: 'none',
    borderRadius: '24px',
    padding: '12px 20px',
    fontWeight: 600,
    fontSize: '13px',
    boxShadow: '0 4px 14px rgba(37, 99, 235, 0.4)',
    cursor: 'pointer',
    zIndex: 9999,
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  typingDot: {
    fontSize: '11px',
    color: '#a5f3fc',
    marginLeft: '6px',
  },
  header: {
    padding: '12px 16px',
    borderBottom: '1px solid #e2e8f0',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#ffffff',
  },
  title: {
    margin: 0,
    fontSize: '15px',
    fontWeight: 600,
    color: '#0f172a',
  },
  minimizeButton: {
    background: 'none',
    border: 'none',
    fontSize: '18px',
    color: '#64748b',
    cursor: 'pointer',
    padding: '2px 8px',
    borderRadius: '4px',
    lineHeight: '1',
  },
  badge: {
    fontSize: '11px',
    backgroundColor: '#dcfce7',
    color: '#15803d',
    padding: '2px 8px',
    borderRadius: '12px',
    fontWeight: 500,
  },
  participantsBar: {
    padding: '8px 16px',
    backgroundColor: '#f8fafc',
    borderBottom: '1px solid #e2e8f0',
    display: 'flex',
    gap: '8px',
    flexWrap: 'wrap',
  },
  participantTag: {
    fontSize: '12px',
    padding: '2px 8px',
    borderRadius: '6px',
    backgroundColor: '#e2e8f0',
    color: '#334155',
  },
  messagesContainer: {
    flex: 1,
    overflowY: 'auto',
    padding: '16px',
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
    backgroundColor: '#f8fafc',
  },
  bubbleWrapper: {
    display: 'flex',
    flexDirection: 'column',
    maxWidth: '90%',
  },
  senderLabel: {
    fontSize: '11px',
    color: '#64748b',
    marginBottom: '3px',
    fontWeight: 500,
  },
  bubble: {
    padding: '10px 14px',
    borderRadius: '12px',
    fontSize: '13px',
    lineHeight: '1.4',
  },
  loadingBubble: {
    fontSize: '12px',
    color: '#4f46e5',
    padding: '8px 12px',
    backgroundColor: '#eef2ff',
    borderRadius: '8px',
    fontStyle: 'italic',
  },
  emptyState: {
    color: '#94a3b8',
    fontSize: '13px',
    textAlign: 'center',
    marginTop: '30px',
  },
  inputArea: {
    display: 'flex',
    gap: '8px',
    padding: '12px 16px',
    borderTop: '1px solid #e2e8f0',
    backgroundColor: '#ffffff',
  },
  input: {
    flex: 1,
    padding: '9px 12px',
    border: '1px solid #cbd5e1',
    borderRadius: '8px',
    fontSize: '13px',
    outline: 'none',
  },
  sendButton: {
    padding: '9px 16px',
    backgroundColor: '#2563eb',
    color: '#ffffff',
    border: 'none',
    borderRadius: '8px',
    fontSize: '13px',
    fontWeight: 600,
    cursor: 'pointer',
  },
};
