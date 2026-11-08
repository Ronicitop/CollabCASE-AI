import test from 'node:test'
import assert from 'node:assert/strict'
import { combinarMensajes, confirmarMensaje } from '../src/chatMessages.ts'

const message = (id, date, session = 'sesion-1') => ({
  id, fechaHora: date, sesionId: session, remitenteNombre: 'Equipo', tipoEmisor: 'USUARIO', contenido: 'Hola',
})

test('el historial tardío conserva mensajes nuevos y reúne distintas sesiones', () => {
  const older = message('a', '2026-11-08T09:00:00')
  const newer = message('b', '2026-11-08T10:00:00', 'sesion-2')
  assert.deepEqual(combinarMensajes([newer], [older]), [older, newer])
})

test('historial y WebSocket repetidos se deduplican por UUID', () => {
  const first = message('a', '2026-11-08T09:00:00')
  const second = message('b', '2026-11-08T09:00:00')
  assert.deepEqual(combinarMensajes([second, first], [first, second]), [first, second])
})

test('un eco WebSocket antes de la respuesta HTTP no duplica el mensaje confirmado', () => {
  const temporary = message('temp-1', undefined)
  const saved = message('real-1', '2026-11-08T09:00:00')
  const anotherPending = message('temp-2', undefined)
  const received = combinarMensajes([temporary, anotherPending], [saved])
  assert.deepEqual(confirmarMensaje(received, 'temp-1', saved), [saved, anotherPending])
})
