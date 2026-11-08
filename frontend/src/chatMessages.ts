export interface MensajeDTO {
  id?: string
  sesionId: string
  remitenteNombre: string
  tipoEmisor: 'USUARIO' | 'IA' | 'SISTEMA'
  contenido: string
  fechaHora?: string
}

/** El historial puede llegar después de mensajes nuevos recibidos por STOMP. */
export function combinarMensajes(actuales: MensajeDTO[], recibidos: MensajeDTO[]): MensajeDTO[] {
  const porId = new Map<string, MensajeDTO>()
  const sinId: MensajeDTO[] = []
  for (const mensaje of [...actuales, ...recibidos]) {
    if (mensaje.id) porId.set(mensaje.id, mensaje)
    else sinId.push(mensaje)
  }
  return [...porId.values(), ...sinId].sort((a, b) =>
    (a.fechaHora ?? '9999').localeCompare(b.fechaHora ?? '9999') ||
    (a.id ?? '').localeCompare(b.id ?? ''),
  )
}

export function confirmarMensaje(actuales: MensajeDTO[], temporalId: string, guardado: MensajeDTO): MensajeDTO[] {
  return combinarMensajes(actuales.filter(m => m.id !== temporalId), [guardado])
}
