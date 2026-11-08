import test from 'node:test'
import assert from 'node:assert/strict'
import { generarUuid } from '../src/uuid.ts'

test('la web puede generar UUID válidos en HTTP local sin crypto.randomUUID', () => {
  const id = generarUuid({ getRandomValues: bytes => {
    for (let i = 0; i < bytes.length; i++) bytes[i] = i
    return bytes
  } })
  assert.equal(id, '00010203-0405-4607-8809-0a0b0c0d0e0f')
})

test('conserva los bits de versión y variante y evita colisiones', () => {
  const api = { getRandomValues: globalThis.crypto.getRandomValues.bind(globalThis.crypto) }
  const ids = Array.from({ length: 100 }, () => generarUuid(api))
  assert.equal(new Set(ids).size, 100)
  for (const id of ids) assert.match(id, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
})
