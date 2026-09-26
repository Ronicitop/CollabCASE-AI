import { openDB, type DBSchema, type IDBPDatabase } from 'idb'

const DB_NAME = 'collabcase-offline'
const DB_VERSION = 2

const STORE_PROYECTOS = 'proyectos'
const STORE_MODELOS = 'modelos'
const STORE_OPERACIONES = 'operaciones'

export type EstadoOperacionOffline =
  | 'PENDIENTE'
  | 'SINCRONIZANDO'
  | 'ERROR'

export interface ProyectoOffline {
  id: string
  nombre: string
  descripcion: string | null
  creadoEn: string
  actualizadoEn: string
}

export interface ModeloOffline {
  proyectoId: string
  modelo: unknown
  actualizadoEn: string
}

export interface OperacionOffline {
  id: string
  proyectoId: string
  tipo: string
  datos: Record<string, unknown>
  versionBase?: number
  creadaEn: string
  estado: EstadoOperacionOffline
  intentos: number
  ultimoError?: string
}

interface CollabCaseOfflineSchema extends DBSchema {
  proyectos: {
    key: string
    value: ProyectoOffline
  }

  modelos: {
    key: string
    value: ModeloOffline
  }

  operaciones: {
    key: string
    value: OperacionOffline
    indexes: {
      'por-proyecto': string
      'por-estado': EstadoOperacionOffline
    }
  }
}

let dbPromise: Promise<IDBPDatabase<CollabCaseOfflineSchema>> | null = null

function obtenerDb(): Promise<IDBPDatabase<CollabCaseOfflineSchema>> {
  if (!dbPromise) {
    dbPromise = openDB<CollabCaseOfflineSchema>(
      DB_NAME,
      DB_VERSION,
      {
        upgrade(db) {
          if (!db.objectStoreNames.contains(STORE_PROYECTOS)) {
            db.createObjectStore(STORE_PROYECTOS, {
              keyPath: 'id',
            })
          }

          if (!db.objectStoreNames.contains(STORE_MODELOS)) {
            db.createObjectStore(STORE_MODELOS, {
              keyPath: 'proyectoId',
            })
          }

          if (!db.objectStoreNames.contains(STORE_OPERACIONES)) {
            const operaciones = db.createObjectStore(
              STORE_OPERACIONES,
              {
                keyPath: 'id',
              },
            )

            operaciones.createIndex(
              'por-proyecto',
              'proyectoId',
            )

            operaciones.createIndex(
              'por-estado',
              'estado',
            )
          }
        },
      },
    )
  }

  return dbPromise
}

export async function guardarProyectoOffline(
  proyecto: ProyectoOffline,
): Promise<void> {
  const db = await obtenerDb()
  await db.put(STORE_PROYECTOS, proyecto)
}

export async function guardarProyectosOffline(
  proyectos: ProyectoOffline[],
): Promise<void> {
  const db = await obtenerDb()
  const tx = db.transaction(STORE_PROYECTOS, 'readwrite')

  await tx.store.clear()

  for (const proyecto of proyectos) {
    await tx.store.put(proyecto)
  }

  await tx.done
}

export async function obtenerProyectosOffline(): Promise<ProyectoOffline[]> {
  const db = await obtenerDb()
  const proyectos = await db.getAll(STORE_PROYECTOS)

  return proyectos.sort((a, b) =>
    b.actualizadoEn.localeCompare(a.actualizadoEn),
  )
}

export async function obtenerProyectoOffline(
  proyectoId: string,
): Promise<ProyectoOffline | undefined> {
  const db = await obtenerDb()
  return db.get(STORE_PROYECTOS, proyectoId)
}

export async function eliminarProyectoOffline(
  proyectoId: string,
): Promise<void> {
  const db = await obtenerDb()
  await db.delete(STORE_PROYECTOS, proyectoId)
}

export async function guardarModeloOffline(
  proyectoId: string,
  modelo: unknown,
): Promise<void> {
  const db = await obtenerDb()

  await db.put(STORE_MODELOS, {
    proyectoId,
    modelo,
    actualizadoEn: new Date().toISOString(),
  })
}

export async function obtenerModeloOffline(
  proyectoId: string,
): Promise<ModeloOffline | undefined> {
  const db = await obtenerDb()
  return db.get(STORE_MODELOS, proyectoId)
}

export async function eliminarModeloOffline(
  proyectoId: string,
): Promise<void> {
  const db = await obtenerDb()
  await db.delete(STORE_MODELOS, proyectoId)
}

export async function agregarOperacionOffline(
  operacion: Omit<
    OperacionOffline,
    'id' | 'creadaEn' | 'estado' | 'intentos'
  >,
): Promise<OperacionOffline> {
  const db = await obtenerDb()

  const nuevaOperacion: OperacionOffline = {
    ...operacion,
    id: crypto.randomUUID(),
    creadaEn: new Date().toISOString(),
    estado: 'PENDIENTE',
    intentos: 0,
  }

  await db.put(STORE_OPERACIONES, nuevaOperacion)

  return nuevaOperacion
}

export async function guardarModeloPendienteSincronizacion(
  proyectoId: string,
  modelo: unknown,
  versionBase?: number,
): Promise<OperacionOffline> {
  const db = await obtenerDb()
  const tx = db.transaction(STORE_OPERACIONES, 'readwrite')
  const operacionesProyecto = await tx.store
    .index('por-proyecto')
    .getAll(proyectoId)

  for (const operacion of operacionesProyecto) {
    if (
      operacion.tipo === 'GUARDAR_MODELO_COMPLETO' &&
      operacion.estado !== 'SINCRONIZANDO'
    ) {
      await tx.store.delete(operacion.id)
    }
  }

  const nuevaOperacion: OperacionOffline = {
    id: crypto.randomUUID(),
    proyectoId,
    tipo: 'GUARDAR_MODELO_COMPLETO',
    datos: { modelo },
    versionBase,
    creadaEn: new Date().toISOString(),
    estado: 'PENDIENTE',
    intentos: 0,
  }

  await tx.store.put(nuevaOperacion)
  await tx.done

  return nuevaOperacion
}

export async function obtenerOperacionesPendientes(
  proyectoId: string,
): Promise<OperacionOffline[]> {
  const db = await obtenerDb()

  const operaciones = await db.getAllFromIndex(
    STORE_OPERACIONES,
    'por-proyecto',
    proyectoId,
  )

  return operaciones
    .filter(
      (operacion) =>
        operacion.estado === 'PENDIENTE' ||
        operacion.estado === 'ERROR',
    )
    .sort((a, b) =>
      a.creadaEn.localeCompare(b.creadaEn),
    )
}

export async function contarOperacionesPendientes(
  proyectoId: string,
): Promise<number> {
  const operaciones =
    await obtenerOperacionesPendientes(proyectoId)

  return operaciones.length
}

export async function actualizarOperacionOffline(
  operacion: OperacionOffline,
): Promise<void> {
  const db = await obtenerDb()
  await db.put(STORE_OPERACIONES, operacion)
}

export async function eliminarOperacionOffline(
  operacionId: string,
): Promise<void> {
  const db = await obtenerDb()
  await db.delete(STORE_OPERACIONES, operacionId)
}

export async function limpiarOperacionesProyecto(
  proyectoId: string,
): Promise<void> {
  const db = await obtenerDb()

  const operaciones = await db.getAllFromIndex(
    STORE_OPERACIONES,
    'por-proyecto',
    proyectoId,
  )

  const tx = db.transaction(
    STORE_OPERACIONES,
    'readwrite',
  )

  for (const operacion of operaciones) {
    await tx.store.delete(operacion.id)
  }

  await tx.done
}
