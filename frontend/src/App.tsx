import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import {
  Background,
  BaseEdge,
  Controls,
  EdgeLabelRenderer,
  MiniMap,
  Position,
  ReactFlow,
  getSmoothStepPath,
  useInternalNode,
  useNodesState,
  type Edge,
  type EdgeProps,
  type Node,
} from '@xyflow/react'

import { Client } from '@stomp/stompjs'

import '@xyflow/react/dist/style.css'
import './App.css'
import { API_URL, WS_URL } from './config'
import { useOnlineStatus } from './offline/useOnlineStatus'
import {
  contarOperacionesPendientes,
  eliminarModeloOffline,
  eliminarProyectoOffline,
  guardarModeloOffline,
  guardarModeloPendienteSincronizacion,
  guardarProyectoOffline,
  guardarProyectosOffline,
  limpiarOperacionesProyecto,
  obtenerModeloOffline,
  obtenerOperacionesPendientes,
  obtenerProyectosOffline,
} from './offline/offlineDb'

type Proyecto = {
  id: string
  nombre: string
  descripcion: string | null
  creadoEn: string
  actualizadoEn: string
}

type AtributoDiagrama = {
  id: string
  nombre: string
  tipoDato: string
  permiteNulo: boolean
  identificador: boolean
}

type ClaseDiagrama = {
  id: string
  nombre: string
  posicionX: number
  posicionY: number
  atributos: AtributoDiagrama[]
}

type RelacionDiagrama = {
  id: string
  claseOrigenId: string
  claseDestinoId: string
  tipo: string
  multiplicidadOrigen: string | null
  multiplicidadDestino: string | null
  nombre: string | null
  claseAsociacionId: string | null
}

type ModeloCompleto = {
  id: string
  proyectoId: string
  version: number
  actualizadoEn: string
  clases: ClaseDiagrama[]
  relaciones: RelacionDiagrama[]
}

type AtributoSolicitudModelo = {
  id: string | null
  nombre: string
  tipoDato: string
  permiteNulo: boolean
  identificador: boolean
}

type ClaseSolicitudModelo = {
  id: string | null
  claveCliente: string
  nombre: string
  posicionX: number
  posicionY: number
  atributos: AtributoSolicitudModelo[]
}

type RelacionSolicitudModelo = {
  id: string | null
  claseOrigenClave: string
  claseDestinoClave: string
  tipo: string
  multiplicidadOrigen: string | null
  multiplicidadDestino: string | null
  nombre: string | null
  claseAsociacionClave: string | null
}

type SolicitudModeloCompleto = {
  clases: ClaseSolicitudModelo[]
  relaciones: RelacionSolicitudModelo[]
}


type SesionColaborativa = {
  id: string
  codigo: string
  proyectoId: string
  activa: boolean
  creadaEn: string
}

type EstadoConexion = 'desconectado' | 'conectando' | 'conectado'

const ordenarAtributosParaMostrar = (
  atributos: AtributoDiagrama[],
): AtributoDiagrama[] => {
  return [...atributos].sort((a, b) => {
    if (a.identificador === b.identificador) {
      return 0
    }

    return a.identificador ? -1 : 1
  })
}

type OperacionColaborativaResponse = {
  operacionId: string
  clienteId: string
  tipo: string
  modelo: ModeloCompleto
}

type ChatIaResponse = {
  mensaje: string
  accionesEjecutadas: string[]
  modelo: ModeloCompleto
}

type MensajeChatIa = {
  id: string
  autor: 'usuario' | 'ia'
  texto: string
  acciones?: string[]
}

type AlternativaReconocimientoVoz = {
  transcript: string
}

type ResultadoReconocimientoVoz = {
  [indice: number]: AlternativaReconocimientoVoz
  length: number
  isFinal: boolean
}

type EventoReconocimientoVoz = {
  results: {
    [indice: number]: ResultadoReconocimientoVoz
    length: number
  }
}

type ErrorReconocimientoVoz = {
  error: string
}

type ReconocedorVoz = {
  lang: string
  continuous: boolean
  interimResults: boolean
  maxAlternatives: number
  onstart: (() => void) | null
  onresult: ((evento: EventoReconocimientoVoz) => void) | null
  onerror: ((evento: ErrorReconocimientoVoz) => void) | null
  onend: (() => void) | null
  start: () => void
  stop: () => void
}

type ConstructorReconocedorVoz = new () => ReconocedorVoz

declare global {
  interface Window {
    SpeechRecognition?: ConstructorReconocedorVoz
    webkitSpeechRecognition?: ConstructorReconocedorVoz
  }
}

type DatosRelacionEdge = {
  nombre: string
  tipo: string
  multiplicidadOrigen: string
  multiplicidadDestino: string
  claseAsociacionX?: number
  claseAsociacionY?: number
}

const estiloEtiquetaRelacion = {
  position: 'absolute' as const,
  transform: 'translate(-50%, -50%)',
  background: 'rgba(255, 255, 255, 0.94)',
  padding: '2px 5px',
  borderRadius: 4,
  fontSize: 13,
  lineHeight: 1.2,
  whiteSpace: 'nowrap' as const,
  pointerEvents: 'none' as const,
  userSelect: 'none' as const,
}

const normalizarTipoRelacionVisual = (tipo?: string): string => {
  const valor = (tipo ?? 'ASOCIACION')
    .trim()
    .toUpperCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')

  const equivalencias: Record<string, string> = {
    ASSOCIATION: 'ASOCIACION',
    AGGREGATION: 'AGREGACION',
    COMPOSITION: 'COMPOSICION',
    GENERALIZATION: 'HERENCIA',
    GENERALIZACION: 'HERENCIA',
    INHERITANCE: 'HERENCIA',
    REALIZATION: 'REALIZACION',
    DEPENDENCY: 'DEPENDENCIA',
  }

  return equivalencias[valor] ?? valor
}

const puntosPoligono = (
  puntos: Array<{ x: number; y: number }>,
): string => puntos.map((punto) => `${punto.x},${punto.y}`).join(' ')

function RelacionUmlEdge({
  id,
  source,
  target,
  sourceX: sourceXFallback,
  sourceY: sourceYFallback,
  targetX: targetXFallback,
  targetY: targetYFallback,
  sourcePosition: sourcePositionFallback,
  targetPosition: targetPositionFallback,
  style,
  data,
}: EdgeProps) {
  // Enlace flotante estilo Enterprise Architect:
  // el extremo de la relación se calcula contra TODO el perímetro de la clase,
  // no contra un único punto central fijo.
  const nodoOrigen = useInternalNode(source)
  const nodoDestino = useInternalNode(target)

  const obtenerInterseccionRectangulo = (
    nodo: NonNullable<typeof nodoOrigen>,
    otroNodo: NonNullable<typeof nodoOrigen>,
  ) => {
    const ancho = nodo.measured.width ?? 240
    const alto = nodo.measured.height ?? 80
    const anchoOtro = otroNodo.measured.width ?? 240
    const altoOtro = otroNodo.measured.height ?? 80

    const posicion = nodo.internals.positionAbsolute
    const posicionOtro = otroNodo.internals.positionAbsolute

    const centroX = posicion.x + ancho / 2
    const centroY = posicion.y + alto / 2
    const centroOtroX = posicionOtro.x + anchoOtro / 2
    const centroOtroY = posicionOtro.y + altoOtro / 2

    const deltaX = centroOtroX - centroX
    const deltaY = centroOtroY - centroY

    if (deltaX === 0 && deltaY === 0) {
      return { x: centroX, y: centroY }
    }

    const escalaX =
      deltaX === 0 ? Number.POSITIVE_INFINITY : ancho / 2 / Math.abs(deltaX)
    const escalaY =
      deltaY === 0 ? Number.POSITIVE_INFINITY : alto / 2 / Math.abs(deltaY)
    const escala = Math.min(escalaX, escalaY)

    return {
      x: centroX + deltaX * escala,
      y: centroY + deltaY * escala,
    }
  }

  const obtenerLadoPerimetro = (
    nodo: NonNullable<typeof nodoOrigen>,
    punto: { x: number; y: number },
  ): Position => {
    const ancho = nodo.measured.width ?? 240
    const alto = nodo.measured.height ?? 80
    const posicion = nodo.internals.positionAbsolute

    const distancias: Array<{ posicion: Position; distancia: number }> = [
      { posicion: Position.Left, distancia: Math.abs(punto.x - posicion.x) },
      {
        posicion: Position.Right,
        distancia: Math.abs(punto.x - (posicion.x + ancho)),
      },
      { posicion: Position.Top, distancia: Math.abs(punto.y - posicion.y) },
      {
        posicion: Position.Bottom,
        distancia: Math.abs(punto.y - (posicion.y + alto)),
      },
    ]

    distancias.sort((a, b) => a.distancia - b.distancia)
    return distancias[0].posicion
  }

  let sourceX = sourceXFallback
  let sourceY = sourceYFallback
  let targetX = targetXFallback
  let targetY = targetYFallback
  let sourcePosition = sourcePositionFallback
  let targetPosition = targetPositionFallback

  if (nodoOrigen && nodoDestino) {
    const puntoOrigen = obtenerInterseccionRectangulo(nodoOrigen, nodoDestino)
    const puntoDestino = obtenerInterseccionRectangulo(nodoDestino, nodoOrigen)

    sourceX = puntoOrigen.x
    sourceY = puntoOrigen.y
    targetX = puntoDestino.x
    targetY = puntoDestino.y
    sourcePosition = obtenerLadoPerimetro(nodoOrigen, puntoOrigen)
    targetPosition = obtenerLadoPerimetro(nodoDestino, puntoDestino)
  }

  const [ruta, centroX, centroY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: 0,
    offset: 28,
  })

  const datos = data as DatosRelacionEdge | undefined
  const tipo = normalizarTipoRelacionVisual(datos?.tipo)

  const vectorExterior = (posicion: Position) => {
    switch (posicion) {
      case Position.Left:
        return { x: -1, y: 0 }
      case Position.Right:
        return { x: 1, y: 0 }
      case Position.Top:
        return { x: 0, y: -1 }
      case Position.Bottom:
      default:
        return { x: 0, y: 1 }
    }
  }

  // Las decoraciones UML se orientan según el segmento ortogonal real que
  // sale/entra de la clase. Así los triángulos, diamantes y flechas no quedan
  // inclinados aunque el resto de la relación tenga varios giros de 90°.
  const salidaOrigen = vectorExterior(sourcePosition)
  const exteriorDestino = vectorExterior(targetPosition)
  const entradaDestino = {
    x: -exteriorDestino.x,
    y: -exteriorDestino.y,
  }

  const perpendicularOrigen = {
    x: -salidaOrigen.y,
    y: salidaOrigen.x,
  }
  const perpendicularDestino = {
    x: -entradaDestino.y,
    y: entradaDestino.x,
  }

  // IMPORTANTE:
  // Esta lógica de Association Class se mantiene igual a la versión estable.
  const rutaClaseAsociacion =
    datos?.claseAsociacionX !== undefined &&
    datos?.claseAsociacionY !== undefined
      ? `M ${centroX},${centroY} L ${datos.claseAsociacionX},${datos.claseAsociacionY}`
      : null

  const esDiscontinua = tipo === 'REALIZACION' || tipo === 'DEPENDENCIA'

  const estiloRelacion = {
    ...style,
    ...(esDiscontinua ? { strokeDasharray: '8 6' } : {}),
  }

  // Triángulo UML en el destino: generalización/herencia y realización.
  const puntaTriangulo = { x: targetX, y: targetY }
  const centroBaseTriangulo = {
    x: targetX - entradaDestino.x * 18,
    y: targetY - entradaDestino.y * 18,
  }
  const baseTrianguloA = {
    x: centroBaseTriangulo.x + perpendicularDestino.x * 10,
    y: centroBaseTriangulo.y + perpendicularDestino.y * 10,
  }
  const baseTrianguloB = {
    x: centroBaseTriangulo.x - perpendicularDestino.x * 10,
    y: centroBaseTriangulo.y - perpendicularDestino.y * 10,
  }

  // Diamante UML en el origen: agregación/composición.
  const diamanteA = { x: sourceX, y: sourceY }
  const diamanteB = {
    x: sourceX + salidaOrigen.x * 10 + perpendicularOrigen.x * 7,
    y: sourceY + salidaOrigen.y * 10 + perpendicularOrigen.y * 7,
  }
  const diamanteC = {
    x: sourceX + salidaOrigen.x * 20,
    y: sourceY + salidaOrigen.y * 20,
  }
  const diamanteD = {
    x: sourceX + salidaOrigen.x * 10 - perpendicularOrigen.x * 7,
    y: sourceY + salidaOrigen.y * 10 - perpendicularOrigen.y * 7,
  }

  // Flecha abierta UML en el destino: dependencia.
  const flechaAbiertaA = {
    x: targetX - entradaDestino.x * 14 + perpendicularDestino.x * 7,
    y: targetY - entradaDestino.y * 14 + perpendicularDestino.y * 7,
  }
  const flechaAbiertaB = {
    x: targetX - entradaDestino.x * 14 - perpendicularDestino.x * 7,
    y: targetY - entradaDestino.y * 14 - perpendicularDestino.y * 7,
  }

  // Cardinalidades: se colocan junto al punto REAL del perímetro donde termina
  // cada relación, no alrededor del centro fijo de la clase.
  const distanciaDesdeClase = 22
  const separacionDeLinea = 12
  const perpendicularEtiquetaDestino = {
    x: -exteriorDestino.y,
    y: exteriorDestino.x,
  }

  const origenX =
    sourceX +
    salidaOrigen.x * distanciaDesdeClase +
    perpendicularOrigen.x * separacionDeLinea

  const origenY =
    sourceY +
    salidaOrigen.y * distanciaDesdeClase +
    perpendicularOrigen.y * separacionDeLinea

  const destinoX =
    targetX +
    exteriorDestino.x * distanciaDesdeClase +
    perpendicularEtiquetaDestino.x * separacionDeLinea

  const destinoY =
    targetY +
    exteriorDestino.y * distanciaDesdeClase +
    perpendicularEtiquetaDestino.y * separacionDeLinea

  return (
    <>
      <BaseEdge id={id} path={ruta} style={estiloRelacion} />

      {(tipo === 'HERENCIA' || tipo === 'REALIZACION') && (
        <polygon
          points={puntosPoligono([
            puntaTriangulo,
            baseTrianguloA,
            baseTrianguloB,
          ])}
          fill="white"
          stroke="currentColor"
          strokeWidth={1.5}
          vectorEffect="non-scaling-stroke"
          pointerEvents="none"
        />
      )}

      {(tipo === 'AGREGACION' || tipo === 'COMPOSICION') && (
        <polygon
          points={puntosPoligono([
            diamanteA,
            diamanteB,
            diamanteC,
            diamanteD,
          ])}
          fill={tipo === 'COMPOSICION' ? 'currentColor' : 'white'}
          stroke="currentColor"
          strokeWidth={1.5}
          vectorEffect="non-scaling-stroke"
          pointerEvents="none"
        />
      )}

      {tipo === 'DEPENDENCIA' && (
        <polyline
          points={puntosPoligono([
            flechaAbiertaA,
            puntaTriangulo,
            flechaAbiertaB,
          ])}
          fill="none"
          stroke="currentColor"
          strokeWidth={1.5}
          vectorEffect="non-scaling-stroke"
          pointerEvents="none"
        />
      )}

      {rutaClaseAsociacion && (
        <BaseEdge
          id={`${id}-clase-asociacion`}
          path={rutaClaseAsociacion}
          style={{
            ...style,
            strokeDasharray: '8 6',
          }}
        />
      )}

      <EdgeLabelRenderer>
        {datos?.multiplicidadOrigen && (
          <div
            className="nodrag nopan"
            style={{
              ...estiloEtiquetaRelacion,
              left: origenX,
              top: origenY,
              fontWeight: 600,
            }}
          >
            {datos.multiplicidadOrigen}
          </div>
        )}

        {datos?.multiplicidadDestino && (
          <div
            className="nodrag nopan"
            style={{
              ...estiloEtiquetaRelacion,
              left: destinoX,
              top: destinoY,
              fontWeight: 600,
            }}
          >
            {datos.multiplicidadDestino}
          </div>
        )}

        {datos?.nombre && (
          <div
            className="nodrag nopan"
            style={{
              ...estiloEtiquetaRelacion,
              left: centroX,
              top: centroY - 14,
              fontSize: 12,
              fontWeight: 600,
            }}
          >
            {datos.nombre}
          </div>
        )}
      </EdgeLabelRenderer>
    </>
  )
}

const tiposArista = {
  relacionUml: RelacionUmlEdge,
}

const construirNodos = (modelo: ModeloCompleto): Node[] => {
  return modelo.clases.map((clase) => ({
    id: clase.id,
    position: {
      x: clase.posicionX,
      y: clase.posicionY,
    },
    data: {
      label: (
        <div className="nodo-uml">
          <div className="nodo-uml-titulo">{clase.nombre}</div>

          <div className="nodo-uml-atributos">
            {clase.atributos.length === 0 ? (
              <span>Sin atributos</span>
            ) : (
              ordenarAtributosParaMostrar(clase.atributos).map((atributo) => (
                <div key={atributo.id}>
                  {atributo.identificador ? '🔑 ' : ''}
                  {atributo.nombre}: {atributo.tipoDato}
                </div>
              ))
            )}
          </div>
        </div>
      ),
    },
    style: {
      width: 240,
    },
  }))
}

const construirAristas = (modelo: ModeloCompleto): Edge[] => {
  return modelo.relaciones.map((relacion) => {
    const claseAsociacion = relacion.claseAsociacionId
      ? modelo.clases.find((clase) => clase.id === relacion.claseAsociacionId)
      : undefined

    return {
      id: relacion.id,
      source: relacion.claseOrigenId,
      target: relacion.claseDestinoId,
      type: 'relacionUml',
      data: {
        nombre: relacion.nombre?.trim() || '',
        tipo: relacion.tipo,
        multiplicidadOrigen: relacion.multiplicidadOrigen ?? '',
        multiplicidadDestino: relacion.multiplicidadDestino ?? '',
        claseAsociacionX: claseAsociacion
          ? claseAsociacion.posicionX + 120
          : undefined,
        claseAsociacionY: claseAsociacion
          ? claseAsociacion.posicionY
          : undefined,
      },
    }
  })
}

function App() {
  const online = useOnlineStatus()
  const [proyectos, setProyectos] = useState<Proyecto[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [proyectosDesdeOffline, setProyectosDesdeOffline] = useState(false)

  const [eliminandoProyectoId, setEliminandoProyectoId] =
    useState<string | null>(null)

  const [errorEliminacionProyecto, setErrorEliminacionProyecto] =
    useState('')

  const [mostrarFormulario, setMostrarFormulario] = useState(false)
  const [nombre, setNombre] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [errorCreacion, setErrorCreacion] = useState('')

  const [proyectoAbierto, setProyectoAbierto] = useState<Proyecto | null>(null)
  const [modeloAbierto, setModeloAbierto] = useState<ModeloCompleto | null>(null)
  const [abriendoId, setAbriendoId] = useState<string | null>(null)
  const [errorAbrir, setErrorAbrir] = useState('')
  const [nodos, setNodos, onNodesChange] = useNodesState<Node>([])
  const [guardandoModelo, setGuardandoModelo] = useState(false)
  const [errorGuardado, setErrorGuardado] = useState('')
  const [modeloDesdeOffline, setModeloDesdeOffline] = useState(false)
  const [operacionesPendientes, setOperacionesPendientes] = useState(0)
  const [sincronizandoOffline, setSincronizandoOffline] = useState(false)

  const clienteStompRef = useRef<Client | null>(null)
  const clienteIdRef = useRef(`cliente-${crypto.randomUUID()}`)
  const [sesionColaborativa, setSesionColaborativa] =
    useState<SesionColaborativa | null>(null)
  const [estadoConexion, setEstadoConexion] =
    useState<EstadoConexion>('desconectado')
  const [codigoSesion, setCodigoSesion] = useState('')
  const [procesandoSesion, setProcesandoSesion] = useState(false)
  const [errorColaboracion, setErrorColaboracion] = useState('')

  const [mensajeIa, setMensajeIa] = useState('')
  const [procesandoIa, setProcesandoIa] = useState(false)
  const [errorIa, setErrorIa] = useState('')
  const [historialIa, setHistorialIa] = useState<MensajeChatIa[]>([])
  const [procesandoArtefacto, setProcesandoArtefacto] = useState(false)
  const [errorArtefacto, setErrorArtefacto] = useState('')
  const inputXmiRef = useRef<HTMLInputElement | null>(null)
  const reconocedorVozRef = useRef<ReconocedorVoz | null>(null)
  const [escuchandoVoz, setEscuchandoVoz] = useState(false)
  const [errorVoz, setErrorVoz] = useState('')
  const inputCamaraRef = useRef<HTMLInputElement | null>(null)
  const inputImagenRef = useRef<HTMLInputElement | null>(null)
  const [procesandoImagen, setProcesandoImagen] = useState(false)
  const [errorImagen, setErrorImagen] = useState('')

  const [mostrarFormularioClase, setMostrarFormularioClase] = useState(false)
  const [nombreClase, setNombreClase] = useState('')
  const [creandoClase, setCreandoClase] = useState(false)
  const [errorClase, setErrorClase] = useState('')
  const [claseEditandoId, setClaseEditandoId] = useState<string | null>(null)
  const [eliminandoClaseId, setEliminandoClaseId] = useState<string | null>(null)

  const [mostrarFormularioRelacion, setMostrarFormularioRelacion] = useState(false)
  const [claseOrigenRelacion, setClaseOrigenRelacion] = useState('')
  const [claseDestinoRelacion, setClaseDestinoRelacion] = useState('')
  const [tipoRelacion, setTipoRelacion] = useState('ASOCIACION')
  const [multiplicidadOrigenRelacion, setMultiplicidadOrigenRelacion] = useState('1')
  const [multiplicidadDestinoRelacion, setMultiplicidadDestinoRelacion] = useState('*')
  const [nombreRelacion, setNombreRelacion] = useState('')
  const [creandoRelacion, setCreandoRelacion] = useState(false)
  const [errorRelacion, setErrorRelacion] = useState('')
  const [relacionEditandoId, setRelacionEditandoId] = useState<string | null>(null)
  const [eliminandoRelacionId, setEliminandoRelacionId] = useState<string | null>(null)

  const [claseSeleccionadaId, setClaseSeleccionadaId] = useState<string | null>(null)
  const [mostrarFormularioAtributo, setMostrarFormularioAtributo] = useState(false)
  const [nombreAtributo, setNombreAtributo] = useState('')
  const [tipoDatoAtributo, setTipoDatoAtributo] = useState('')
  const [permiteNuloAtributo, setPermiteNuloAtributo] = useState(false)
  const [identificadorAtributo, setIdentificadorAtributo] = useState(false)
  const [creandoAtributo, setCreandoAtributo] = useState(false)
  const [errorAtributo, setErrorAtributo] = useState('')
  const [atributoEditandoId, setAtributoEditandoId] = useState<string | null>(null)
  const [eliminandoAtributoId, setEliminandoAtributoId] = useState<string | null>(null)

  useEffect(() => {
    let cancelado = false

    const cargarProyectos = async () => {
      setCargando(true)
      setError('')

      try {
        if (!online) {
          throw new Error('SIN_CONEXION')
        }

        const respuesta = await fetch(`${API_URL}/api/proyectos`)

        if (!respuesta.ok) {
          throw new Error('No se pudieron cargar los proyectos')
        }

        const datos: Proyecto[] = await respuesta.json()

        if (cancelado) {
          return
        }

        setProyectos(datos)
        setProyectosDesdeOffline(false)

        void guardarProyectosOffline(datos).catch((error: unknown) => {
          console.error(
            'No se pudieron guardar los proyectos en IndexedDB.',
            error,
          )
        })
      } catch (errorCarga) {
        try {
          const proyectosLocales = await obtenerProyectosOffline()

          if (cancelado) {
            return
          }

          if (proyectosLocales.length > 0) {
            setProyectos(proyectosLocales)
            setProyectosDesdeOffline(true)
            setError('')
            return
          }

          setProyectos([])
          setProyectosDesdeOffline(false)

          if (
            errorCarga instanceof Error &&
            errorCarga.message !== 'SIN_CONEXION'
          ) {
            setError(
              `${errorCarga.message}. No hay proyectos guardados localmente.`,
            )
          } else {
            setError(
              'No hay proyectos guardados localmente para trabajar sin conexión.',
            )
          }
        } catch (errorLocal) {
          if (!cancelado) {
            setProyectos([])
            setProyectosDesdeOffline(false)
            setError(
              errorLocal instanceof Error
                ? errorLocal.message
                : 'No se pudieron cargar los proyectos locales.',
            )
          }
        }
      } finally {
        if (!cancelado) {
          setCargando(false)
        }
      }
    }

    void cargarProyectos()

    return () => {
      cancelado = true
    }
  }, [online])

  useEffect(() => {
    if (modeloAbierto) {
      setNodos(construirNodos(modeloAbierto))
    } else {
      setNodos([])
    }
  }, [modeloAbierto, setNodos])

  useEffect(() => {
    if (!modeloAbierto) {
      return
    }

    void guardarModeloOffline(modeloAbierto.proyectoId, modeloAbierto).catch(
      (error: unknown) => {
        console.error('No se pudo guardar el modelo en IndexedDB.', error)
      },
    )
  }, [modeloAbierto])


  useEffect(() => {
    return () => {
      if (clienteStompRef.current) {
        void clienteStompRef.current.deactivate()
      }

      if (reconocedorVozRef.current) {
        reconocedorVozRef.current.stop()
        reconocedorVozRef.current = null
      }
    }
  }, [])

  const crearProyecto = async (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()

    if (!nombre.trim()) {
      setErrorCreacion('El nombre del proyecto es obligatorio.')
      return
    }

    try {
      setGuardando(true)
      setErrorCreacion('')

      const respuesta = await fetch(
        `${API_URL}/api/proyectos`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
          },
          body: JSON.stringify({
            nombre: nombre.trim(),
            descripcion: descripcion.trim(),
          }),
        },
      )

      if (!respuesta.ok) {
        throw new Error('No se pudo crear el proyecto')
      }

      const nuevoProyecto: Proyecto = await respuesta.json()

      setProyectos((proyectosActuales) => [
        ...proyectosActuales,
        nuevoProyecto,
      ])

      void guardarProyectoOffline(nuevoProyecto).catch((error: unknown) => {
        console.error(
          'No se pudo guardar el proyecto en IndexedDB.',
          error,
        )
      })

      setNombre('')
      setDescripcion('')
      setMostrarFormulario(false)
    } catch (error) {
      if (error instanceof Error) {
        setErrorCreacion(error.message)
      } else {
        setErrorCreacion('Ocurrió un error al crear el proyecto')
      }
    } finally {
      setGuardando(false)
    }
  }

  const cancelarCreacion = () => {
    setNombre('')
    setDescripcion('')
    setErrorCreacion('')
    setMostrarFormulario(false)
  }

  const eliminarProyecto = async (proyecto: Proyecto) => {
  if (!online) {
    setErrorEliminacionProyecto(
      'Eliminar proyectos requiere conexión con el servidor.',
    )
    return
  }

  const confirmar = window.confirm(
    `¿Eliminar definitivamente el proyecto "${proyecto.nombre}"?\n\n` +
      'Se eliminarán también su modelo UML, clases, atributos, relaciones y sesiones colaborativas asociadas.\n\n' +
      'Esta acción no se puede deshacer.',
  )

  if (!confirmar) {
    return
  }

  try {
    setEliminandoProyectoId(proyecto.id)
    setErrorEliminacionProyecto('')

    const respuesta = await fetch(
      `${API_URL}/api/proyectos/${proyecto.id}`,
      {
        method: 'DELETE',
      },
    )

    if (!respuesta.ok) {
      throw new Error(
        await obtenerMensajeErrorHttp(
          respuesta,
          'No se pudo eliminar el proyecto.',
        ),
      )
    }

    await Promise.all([
      eliminarProyectoOffline(proyecto.id),
      eliminarModeloOffline(proyecto.id),
      limpiarOperacionesProyecto(proyecto.id),
    ])

    setProyectos((actuales) =>
      actuales.filter(
        (proyectoActual) => proyectoActual.id !== proyecto.id,
      ),
    )

    setErrorEliminacionProyecto('')
  } catch (error) {
    setErrorEliminacionProyecto(
      error instanceof Error
        ? error.message
        : 'Ocurrió un error al eliminar el proyecto.',
    )
  } finally {
    setEliminandoProyectoId(null)
  }
}

  const obtenerModeloCompleto = async (
    proyectoId: string,
  ): Promise<ModeloCompleto> => {
    let respuesta = await fetch(
      `${API_URL}/api/modelos-diagrama/proyecto/${proyectoId}/completo`,
    )

    if (respuesta.status === 404) {
      const respuestaCreacion = await fetch(
        `${API_URL}/api/modelos-diagrama/proyecto/${proyectoId}`,
        {
          method: 'POST',
        },
      )

      if (!respuestaCreacion.ok) {
        throw new Error('No se pudo crear el modelo UML del proyecto')
      }

      respuesta = await fetch(
        `${API_URL}/api/modelos-diagrama/proyecto/${proyectoId}/completo`,
      )
    }

    if (!respuesta.ok) {
      throw new Error('No se pudo cargar el modelo UML')
    }

    return respuesta.json()
  }

  const conectarWebSocket = (
    sesion: SesionColaborativa,
    modeloInicial: ModeloCompleto,
  ) => {
    if (clienteStompRef.current) {
      void clienteStompRef.current.deactivate()
    }

    setEstadoConexion('conectando')
    setErrorColaboracion('')

    const cliente = new Client({
      brokerURL: `${WS_URL}/ws`,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    })

    cliente.onConnect = () => {
      setEstadoConexion('conectado')

      cliente.subscribe(
        `/topic/sesiones/${sesion.codigo}/modelo`,
        (mensaje) => {
          try {
            const modeloRecibido: ModeloCompleto = JSON.parse(mensaje.body)

            if (modeloRecibido.proyectoId === sesion.proyectoId) {
              setModeloAbierto(modeloRecibido)
              setErrorGuardado('')
            }
          } catch {
            setErrorColaboracion(
              'Se recibió una actualización colaborativa inválida.',
            )
          }
        },
      )

      cliente.subscribe(
        `/topic/sesiones/${sesion.codigo}/operaciones`,
        (mensaje) => {
          try {
            const respuestaOperacion: OperacionColaborativaResponse =
              JSON.parse(mensaje.body)

            if (
              respuestaOperacion.modelo.proyectoId === sesion.proyectoId
            ) {
              setModeloAbierto(respuestaOperacion.modelo)
              setErrorGuardado('')
            }
          } catch {
            setErrorColaboracion(
              'Se recibió una operación colaborativa inválida.',
            )
          }
        },
      )
    }

    cliente.onStompError = (frame) => {
      setEstadoConexion('desconectado')
      setErrorColaboracion(
        frame.headers.message ||
          'Ocurrió un error en la conexión colaborativa.',
      )
    }

    cliente.onWebSocketClose = () => {
      setEstadoConexion('desconectado')
    }

    clienteStompRef.current = cliente
    setModeloAbierto(modeloInicial)
    cliente.activate()
  }

  const iniciarSesionColaborativa = async () => {
    if (!proyectoAbierto || !modeloAbierto) {
      return
    }

    try {
      setProcesandoSesion(true)
      setErrorColaboracion('')

      const respuesta = await fetch(
        `${API_URL}/api/sesiones-colaborativas/proyecto/${proyectoAbierto.id}`,
        {
          method: 'POST',
        },
      )

      if (!respuesta.ok) {
        throw new Error('No se pudo iniciar la sesión colaborativa.')
      }

      const sesion: SesionColaborativa = await respuesta.json()

      setSesionColaborativa(sesion)
      setCodigoSesion(sesion.codigo)
      conectarWebSocket(sesion, modeloAbierto)
    } catch (error) {
      if (error instanceof Error) {
        setErrorColaboracion(error.message)
      } else {
        setErrorColaboracion(
          'Ocurrió un error al iniciar la sesión colaborativa.',
        )
      }
    } finally {
      setProcesandoSesion(false)
    }
  }

  const unirseSesionColaborativa = async (
    evento: FormEvent<HTMLFormElement>,
  ) => {
    evento.preventDefault()

    const codigoNormalizado = codigoSesion.trim().toUpperCase()

    if (!codigoNormalizado) {
      setErrorColaboracion('Ingresa el código de la sesión.')
      return
    }

    try {
      setProcesandoSesion(true)
      setErrorColaboracion('')

      const respuestaSesion = await fetch(
        `${API_URL}/api/sesiones-colaborativas/unirse/${encodeURIComponent(codigoNormalizado)}`,
        {
          method: 'POST',
        },
      )

      if (!respuestaSesion.ok) {
        let mensaje = 'No se pudo unir a la sesión colaborativa.'

        try {
          const detalle = await respuestaSesion.json()

          if (detalle?.mensaje) {
            mensaje = detalle.mensaje
          }
        } catch {
          // Conservamos el mensaje general.
        }

        throw new Error(mensaje)
      }

      const sesion: SesionColaborativa = await respuestaSesion.json()

      const proyectoExistente = proyectos.find(
        (proyectoActual) => proyectoActual.id === sesion.proyectoId,
      )

      let proyectoSesion: Proyecto

      if (proyectoExistente) {
        proyectoSesion = proyectoExistente
      } else {
        const respuestaProyecto = await fetch(
          `${API_URL}/api/proyectos/${sesion.proyectoId}`,
        )

        if (!respuestaProyecto.ok) {
          throw new Error(
            'La sesión existe, pero no se pudo cargar su proyecto.',
          )
        }

        proyectoSesion = (await respuestaProyecto.json()) as Proyecto
      }

      const modelo = await obtenerModeloCompleto(sesion.proyectoId)

      setProyectoAbierto(proyectoSesion)
      setModeloAbierto(modelo)
      setSesionColaborativa(sesion)
      setCodigoSesion(sesion.codigo)
      conectarWebSocket(sesion, modelo)
    } catch (error) {
      if (error instanceof Error) {
        setErrorColaboracion(error.message)
      } else {
        setErrorColaboracion(
          'Ocurrió un error al unirse a la sesión colaborativa.',
        )
      }
    } finally {
      setProcesandoSesion(false)
    }
  }

  const salirSesionColaborativa = () => {
    if (clienteStompRef.current) {
      void clienteStompRef.current.deactivate()
      clienteStompRef.current = null
    }

    setSesionColaborativa(null)
    setEstadoConexion('desconectado')
    setCodigoSesion('')
    setErrorColaboracion('')
    if (reconocedorVozRef.current) {
      reconocedorVozRef.current.stop()
      reconocedorVozRef.current = null
    }

    setEscuchandoVoz(false)
    setErrorVoz('')
    setErrorImagen('')
    setMensajeIa('')
    setErrorIa('')
    setHistorialIa([])
  }

  const alternarReconocimientoVoz = () => {
    if (procesandoIa || procesandoImagen || estadoConexion !== 'conectado') {
      return
    }

    if (escuchandoVoz) {
      reconocedorVozRef.current?.stop()
      return
    }

    const ConstructorReconocimiento =
      window.SpeechRecognition ?? window.webkitSpeechRecognition

    if (!ConstructorReconocimiento) {
      setErrorVoz(
        'Este navegador no admite reconocimiento de voz. Prueba con Chrome o Edge.',
      )
      return
    }

    const reconocedor = new ConstructorReconocimiento()

    reconocedor.lang =
      navigator.language && navigator.language.toLowerCase().startsWith('es')
        ? navigator.language
        : 'es-ES'

    reconocedor.continuous = false
    reconocedor.interimResults = false
    reconocedor.maxAlternatives = 1

    reconocedor.onstart = () => {
      setEscuchandoVoz(true)
      setErrorVoz('')
    }

    reconocedor.onresult = (evento) => {
      let transcripcion = ''

      for (let indice = 0; indice < evento.results.length; indice += 1) {
        transcripcion += evento.results[indice]?.[0]?.transcript ?? ''
      }

      const textoReconocido = transcripcion.trim()

      if (textoReconocido) {
        setMensajeIa((actual) => {
          const textoActual = actual.trim()

          return textoActual
            ? `${textoActual} ${textoReconocido}`
            : textoReconocido
        })
      }
    }

    reconocedor.onerror = (evento) => {
      const mensaje =
        evento.error === 'not-allowed' || evento.error === 'service-not-allowed'
          ? 'No se concedió permiso para usar el micrófono.'
          : evento.error === 'no-speech'
            ? 'No se detectó voz. Intenta hablar nuevamente.'
            : `No se pudo reconocer la voz (${evento.error}).`

      setErrorVoz(mensaje)
    }

    reconocedor.onend = () => {
      setEscuchandoVoz(false)
      reconocedorVozRef.current = null
    }

    try {
      reconocedorVozRef.current = reconocedor
      reconocedor.start()
    } catch {
      reconocedorVozRef.current = null
      setEscuchandoVoz(false)
      setErrorVoz('No se pudo iniciar el reconocimiento de voz.')
    }
  }

  const abrirCamaraImagen = () => {
    if (
      procesandoIa ||
      procesandoImagen ||
      estadoConexion !== 'conectado'
    ) {
      return
    }

    setErrorImagen('')
    inputCamaraRef.current?.click()
  }

  const abrirSelectorImagen = () => {
    if (
      procesandoIa ||
      procesandoImagen ||
      estadoConexion !== 'conectado'
    ) {
      return
    }

    setErrorImagen('')
    inputImagenRef.current?.click()
  }

  const enviarImagenIa = async (evento: ChangeEvent<HTMLInputElement>) => {
    const archivo = evento.target.files?.[0]
    evento.target.value = ''

    if (!archivo) {
      return
    }

    if (!archivo.type.startsWith('image/')) {
      setErrorImagen('Selecciona una imagen válida del diagrama UML.')
      return
    }

    if (!sesionColaborativa) {
      setErrorImagen(
        'Inicia una sesión colaborativa para editar el modelo desde una imagen.',
      )
      return
    }

    if (estadoConexion !== 'conectado') {
      setErrorImagen(
        'La sesión colaborativa debe estar conectada antes de analizar una imagen.',
      )
      return
    }

    const instruccion =
      mensajeIa.trim() ||
      'Interpreta esta imagen como un diagrama UML de clases y aplica al modelo los cambios claramente identificables. No inventes elementos que no se vean con suficiente claridad.'

    const mensajeUsuario: MensajeChatIa = {
      id: crypto.randomUUID(),
      autor: 'usuario',
      texto: mensajeIa.trim()
        ? `📷 Imagen: ${archivo.name}\n${mensajeIa.trim()}`
        : `📷 Imagen UML: ${archivo.name}`,
    }

    try {
      setProcesandoImagen(true)
      setErrorImagen('')
      setErrorIa('')
      setHistorialIa((actual) => [...actual, mensajeUsuario])
      setMensajeIa('')

      const formulario = new FormData()
      formulario.append('archivo', archivo)
      formulario.append('clienteId', clienteIdRef.current)
      formulario.append('mensaje', instruccion)

      const respuesta = await fetch(
        `${API_URL}/api/ia/sesiones/${encodeURIComponent(
          sesionColaborativa.codigo,
        )}/imagen`,
        {
          method: 'POST',
          body: formulario,
        },
      )

      if (!respuesta.ok) {
        let mensajeError =
          'No se pudo interpretar la imagen con el asistente IA.'

        try {
          const detalle = await respuesta.json()

          if (detalle?.mensaje) {
            mensajeError = detalle.mensaje
          }
        } catch {
          // Conservamos el mensaje general.
        }

        throw new Error(mensajeError)
      }

      const resultado: ChatIaResponse = await respuesta.json()

      setModeloAbierto(resultado.modelo)

      setHistorialIa((actual) => [
        ...actual,
        {
          id: crypto.randomUUID(),
          autor: 'ia',
          texto: resultado.mensaje,
          acciones: resultado.accionesEjecutadas,
        },
      ])
    } catch (error) {
      if (error instanceof Error) {
        setErrorImagen(error.message)
      } else {
        setErrorImagen('Ocurrió un error al procesar la imagen del diagrama.')
      }
    } finally {
      setProcesandoImagen(false)
    }
  }

  const enviarMensajeIa = async (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()

    const mensajeLimpio = mensajeIa.trim()

    if (!mensajeLimpio) {
      setErrorIa('Escribe una instrucción para el asistente IA.')
      return
    }

    if (!sesionColaborativa) {
      setErrorIa(
        'Inicia una sesión colaborativa para usar el asistente IA sobre el modelo.',
      )
      return
    }

    if (estadoConexion !== 'conectado') {
      setErrorIa(
        'La sesión colaborativa debe estar conectada antes de usar el asistente IA.',
      )
      return
    }

    const mensajeUsuario: MensajeChatIa = {
      id: crypto.randomUUID(),
      autor: 'usuario',
      texto: mensajeLimpio,
    }

    try {
      setProcesandoIa(true)
      setErrorIa('')
      setHistorialIa((actual) => [...actual, mensajeUsuario])
      setMensajeIa('')

      const respuesta = await fetch(
        `${API_URL}/api/ia/sesiones/${encodeURIComponent(
          sesionColaborativa.codigo,
        )}/chat`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
          },
          body: JSON.stringify({
            clienteId: clienteIdRef.current,
            mensaje: mensajeLimpio,
          }),
        },
      )

      if (!respuesta.ok) {
        let mensajeError =
          'No se pudo procesar la instrucción con el asistente IA.'

        try {
          const detalle = await respuesta.json()

          if (detalle?.mensaje) {
            mensajeError = detalle.mensaje
          }
        } catch {
          // Conservamos el mensaje general.
        }

        throw new Error(mensajeError)
      }

      const resultado: ChatIaResponse = await respuesta.json()

      setModeloAbierto(resultado.modelo)

      setHistorialIa((actual) => [
        ...actual,
        {
          id: crypto.randomUUID(),
          autor: 'ia',
          texto: resultado.mensaje,
          acciones: resultado.accionesEjecutadas,
        },
      ])
    } catch (error) {
      if (error instanceof Error) {
        setErrorIa(error.message)
      } else {
        setErrorIa('Ocurrió un error al comunicarse con el asistente IA.')
      }
    } finally {
      setProcesandoIa(false)
    }
  }

  const esIdLocal = (id: string): boolean => id.startsWith('local-')

  const construirModeloLocalDesdeSolicitud = (
    solicitud: SolicitudModeloCompleto,
  ): ModeloCompleto => {
    if (!modeloAbierto || !proyectoAbierto) {
      throw new Error('No hay un modelo abierto para guardar localmente.')
    }

    const clavesAIds = new Map<string, string>()

    const clases: ClaseDiagrama[] = solicitud.clases.map((clase) => {
      const claseId = clase.id ?? `local-clase-${crypto.randomUUID()}`

      clavesAIds.set(clase.claveCliente, claseId)
      clavesAIds.set(claseId, claseId)

      return {
        id: claseId,
        nombre: clase.nombre,
        posicionX: clase.posicionX,
        posicionY: clase.posicionY,
        atributos: clase.atributos.map((atributo) => ({
          id: atributo.id ?? `local-atributo-${crypto.randomUUID()}`,
          nombre: atributo.nombre,
          tipoDato: atributo.tipoDato,
          permiteNulo: atributo.permiteNulo,
          identificador: atributo.identificador,
        })),
      }
    })

    const relaciones: RelacionDiagrama[] = solicitud.relaciones.map((relacion) => ({
      id: relacion.id ?? `local-relacion-${crypto.randomUUID()}`,
      claseOrigenId:
        clavesAIds.get(relacion.claseOrigenClave) ?? relacion.claseOrigenClave,
      claseDestinoId:
        clavesAIds.get(relacion.claseDestinoClave) ?? relacion.claseDestinoClave,
      tipo: relacion.tipo,
      multiplicidadOrigen: relacion.multiplicidadOrigen,
      multiplicidadDestino: relacion.multiplicidadDestino,
      nombre: relacion.nombre,
      claseAsociacionId: relacion.claseAsociacionClave
        ? clavesAIds.get(relacion.claseAsociacionClave) ??
          relacion.claseAsociacionClave
        : null,
    }))

    return {
      ...modeloAbierto,
      proyectoId: proyectoAbierto.id,
      actualizadoEn: new Date().toISOString(),
      clases,
      relaciones,
    }
  }

  const guardarCambioLocal = async (
    solicitud: SolicitudModeloCompleto,
  ): Promise<ModeloCompleto> => {
    if (!proyectoAbierto || !modeloAbierto) {
      throw new Error('No hay un proyecto abierto.')
    }

    const modeloLocal = construirModeloLocalDesdeSolicitud(solicitud)

    await guardarModeloOffline(proyectoAbierto.id, modeloLocal)
    await guardarModeloPendienteSincronizacion(
      proyectoAbierto.id,
      modeloLocal,
      modeloAbierto.version,
    )

    const cantidadPendiente = await contarOperacionesPendientes(proyectoAbierto.id)

    setModeloAbierto(modeloLocal)
    setModeloDesdeOffline(true)
    setOperacionesPendientes(cantidadPendiente)

    return modeloLocal
  }

  const guardarModeloConTransporte = async (
    solicitud: SolicitudModeloCompleto,
    mensajeError: string,
  ): Promise<ModeloCompleto | null> => {
    if (!proyectoAbierto) {
      throw new Error('No hay un proyecto abierto.')
    }

    if (!online) {
      return guardarCambioLocal(solicitud)
    }

    if (sesionColaborativa) {
      const cliente = clienteStompRef.current

      if (estadoConexion !== 'conectado' || !cliente?.connected) {
        throw new Error(
          'La sesión colaborativa no está conectada. Espera la reconexión antes de guardar cambios.',
        )
      }

      cliente.publish({
        destination: `/app/sesiones/${sesionColaborativa.codigo}/modelo`,
        body: JSON.stringify(solicitud),
      })

      return null
    }

    let respuesta: Response

    try {
      respuesta = await fetch(
        `${API_URL}/api/modelos-diagrama/proyecto/${proyectoAbierto.id}/completo`,
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
          },
          body: JSON.stringify(solicitud),
        },
      )
    } catch {
      // navigator.onLine puede seguir indicando conexión aunque el backend
      // no sea alcanzable. En ese caso preservamos el trabajo localmente.
      return guardarCambioLocal(solicitud)
    }

    if (!respuesta.ok) {
      let mensaje = mensajeError

      try {
        const detalle = await respuesta.json()

        if (detalle?.mensaje) {
          mensaje = detalle.mensaje
        }
      } catch {
        // Conservamos el mensaje general si la respuesta no contiene JSON.
      }

      throw new Error(mensaje)
    }

    return respuesta.json()
  }

  const construirSolicitudServidorDesdeModeloLocal = (
    modelo: ModeloCompleto,
  ): SolicitudModeloCompleto => ({
    clases: modelo.clases.map((clase) => ({
      id: esIdLocal(clase.id) ? null : clase.id,
      claveCliente: clase.id,
      nombre: clase.nombre,
      posicionX: clase.posicionX,
      posicionY: clase.posicionY,
      atributos: clase.atributos.map((atributo) => ({
        id: esIdLocal(atributo.id) ? null : atributo.id,
        nombre: atributo.nombre,
        tipoDato: atributo.tipoDato,
        permiteNulo: atributo.permiteNulo,
        identificador: atributo.identificador,
      })),
    })),
    relaciones: modelo.relaciones.map((relacion) => ({
      id: esIdLocal(relacion.id) ? null : relacion.id,
      claseOrigenClave: relacion.claseOrigenId,
      claseDestinoClave: relacion.claseDestinoId,
      tipo: relacion.tipo,
      multiplicidadOrigen: relacion.multiplicidadOrigen,
      multiplicidadDestino: relacion.multiplicidadDestino,
      nombre: relacion.nombre,
      claseAsociacionClave: relacion.claseAsociacionId,
    })),
  })

  const sincronizarCambiosOffline = async () => {
    if (!online || !proyectoAbierto || !modeloAbierto) {
      return
    }

    if (sesionColaborativa) {
      setErrorGuardado(
        'Sal de la sesión colaborativa antes de sincronizar cambios realizados sin conexión.',
      )
      return
    }

    try {
      setSincronizandoOffline(true)
      setErrorGuardado('')

      const pendientes = await obtenerOperacionesPendientes(proyectoAbierto.id)
      const snapshots = pendientes.filter(
        (operacion) => operacion.tipo === 'GUARDAR_MODELO_COMPLETO',
      )
      const ultimaOperacion = snapshots[snapshots.length - 1]

      if (!ultimaOperacion) {
        setOperacionesPendientes(0)
        return
      }

      const modeloLocal = ultimaOperacion.datos.modelo as ModeloCompleto | undefined

      if (!modeloLocal) {
        throw new Error('No se encontró el modelo local pendiente de sincronización.')
      }

      const modeloServidor = await obtenerModeloCompleto(proyectoAbierto.id)

      if (
        ultimaOperacion.versionBase !== undefined &&
        modeloServidor.version !== ultimaOperacion.versionBase
      ) {
        throw new Error(
          `Conflicto de sincronización: el servidor está en la versión ${modeloServidor.version} y tus cambios locales parten de la versión ${ultimaOperacion.versionBase}. No se sobrescribió ningún cambio.`,
        )
      }

      const solicitud = construirSolicitudServidorDesdeModeloLocal(modeloLocal)
      const respuesta = await fetch(
        `${API_URL}/api/modelos-diagrama/proyecto/${proyectoAbierto.id}/completo`,
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
          },
          body: JSON.stringify(solicitud),
        },
      )

      if (!respuesta.ok) {
        throw new Error(
          await obtenerMensajeErrorHttp(
            respuesta,
            'No se pudieron sincronizar los cambios locales.',
          ),
        )
      }

      const modeloSincronizado: ModeloCompleto = await respuesta.json()

      await guardarModeloOffline(proyectoAbierto.id, modeloSincronizado)
      await limpiarOperacionesProyecto(proyectoAbierto.id)

      setModeloAbierto(modeloSincronizado)
      setModeloDesdeOffline(false)
      setOperacionesPendientes(0)
      setErrorGuardado('')
    } catch (error) {
      setErrorGuardado(
        error instanceof Error
          ? error.message
          : 'Ocurrió un error al sincronizar los cambios locales.',
      )
    } finally {
      setSincronizandoOffline(false)
    }
  }

  const abrirProyecto = async (proyecto: Proyecto) => {
    try {
      setAbriendoId(proyecto.id)
      setErrorAbrir('')

      let modelo: ModeloCompleto
      let cargadoDesdeOffline = false
      const cantidadPendiente = await contarOperacionesPendientes(proyecto.id)

      if (cantidadPendiente > 0) {
        const pendientes = await obtenerOperacionesPendientes(proyecto.id)
        const snapshots = pendientes.filter(
          (operacion) => operacion.tipo === 'GUARDAR_MODELO_COMPLETO',
        )
        const snapshot = snapshots[snapshots.length - 1]
        const modeloPendiente = snapshot?.datos.modelo as ModeloCompleto | undefined

        if (modeloPendiente) {
          modelo = modeloPendiente
        } else {
          const modeloLocal = await obtenerModeloOffline(proyecto.id)

          if (!modeloLocal) {
            throw new Error(
              'Existen cambios pendientes, pero no se encontró la copia local del modelo.',
            )
          }

          modelo = modeloLocal.modelo as ModeloCompleto
        }

        cargadoDesdeOffline = true
      } else if (online) {
        try {
          modelo = await obtenerModeloCompleto(proyecto.id)
        } catch (errorServidor) {
          const modeloLocal = await obtenerModeloOffline(proyecto.id)

          if (!modeloLocal) {
            throw errorServidor
          }

          modelo = modeloLocal.modelo as ModeloCompleto
          cargadoDesdeOffline = true
        }
      } else {
        const modeloLocal = await obtenerModeloOffline(proyecto.id)

        if (!modeloLocal) {
          throw new Error(
            'Este proyecto todavía no tiene una copia local disponible.',
          )
        }

        modelo = modeloLocal.modelo as ModeloCompleto
        cargadoDesdeOffline = true
      }

      if (
        !modelo ||
        modelo.proyectoId !== proyecto.id ||
        !Array.isArray(modelo.clases) ||
        !Array.isArray(modelo.relaciones)
      ) {
        throw new Error(
          'La copia local del modelo no es válida o está incompleta.',
        )
      }

      setProyectoAbierto(proyecto)
      setModeloAbierto(modelo)
      setModeloDesdeOffline(cargadoDesdeOffline)
      setOperacionesPendientes(cantidadPendiente)

      if (!cargadoDesdeOffline) {
        void guardarProyectoOffline(proyecto).catch((error: unknown) => {
          console.error(
            'No se pudo actualizar el proyecto en IndexedDB.',
            error,
          )
        })
      }
    } catch (error) {
      if (error instanceof Error) {
        setErrorAbrir(error.message)
      } else {
        setErrorAbrir('Ocurrió un error al abrir el proyecto')
      }
    } finally {
      setAbriendoId(null)
    }
  }

  const obtenerMensajeErrorHttp = async (
    respuesta: Response,
    mensajeFallback: string,
  ): Promise<string> => {
    try {
      const detalle = await respuesta.json()

      if (detalle?.mensaje) {
        return detalle.mensaje
      }
    } catch {
      // La respuesta puede ser un archivo o texto sin JSON.
    }

    return mensajeFallback
  }

  const descargarArchivo = async (
    url: string,
    nombreFallback: string,
  ) => {
    const respuesta = await fetch(url)

    if (!respuesta.ok) {
      throw new Error(
        await obtenerMensajeErrorHttp(
          respuesta,
          'No se pudo descargar el archivo solicitado.',
        ),
      )
    }

    const blob = await respuesta.blob()
    const disposition = respuesta.headers.get('content-disposition')
    const coincidenciaNombre = disposition?.match(/filename="?([^";]+)"?/i)
    const nombreArchivo = coincidenciaNombre?.[1]?.trim() || nombreFallback

    const urlTemporal = URL.createObjectURL(blob)
    const enlace = document.createElement('a')

    enlace.href = urlTemporal
    enlace.download = nombreArchivo
    document.body.appendChild(enlace)
    enlace.click()
    enlace.remove()

    URL.revokeObjectURL(urlTemporal)
  }

  const exportarXmi = async () => {
    if (!proyectoAbierto) {
      return
    }

    try {
      setProcesandoArtefacto(true)
      setErrorArtefacto('')

      await descargarArchivo(
        `${API_URL}/api/interoperabilidad/proyectos/${proyectoAbierto.id}/exportar/xmi`,
        'modelo-collabcase.xmi',
      )
    } catch (error) {
      setErrorArtefacto(
        error instanceof Error
          ? error.message
          : 'Ocurrió un error al exportar el modelo XMI.',
      )
    } finally {
      setProcesandoArtefacto(false)
    }
  }

  const generarBackendZip = async () => {
    if (!proyectoAbierto) {
      return
    }

    try {
      setProcesandoArtefacto(true)
      setErrorArtefacto('')

      await descargarArchivo(
        `${API_URL}/api/generacion/proyectos/${proyectoAbierto.id}/backend/zip`,
        'backend-generado.zip',
      )
    } catch (error) {
      setErrorArtefacto(
        error instanceof Error
          ? error.message
          : 'Ocurrió un error al generar el backend.',
      )
    } finally {
      setProcesandoArtefacto(false)
    }
  }

  const generarPostman = async () => {
    if (!proyectoAbierto) {
      return
    }

    try {
      setProcesandoArtefacto(true)
      setErrorArtefacto('')

      await descargarArchivo(
        `${API_URL}/api/generacion/proyectos/${proyectoAbierto.id}/postman`,
        'collabcase.postman_collection.json',
      )
    } catch (error) {
      setErrorArtefacto(
        error instanceof Error
          ? error.message
          : 'Ocurrió un error al generar la colección Postman.',
      )
    } finally {
      setProcesandoArtefacto(false)
    }
  }

  const abrirSelectorXmi = () => {
    if (sesionColaborativa) {
      setErrorArtefacto(
        'Sal de la sesión colaborativa antes de importar XMI para evitar reemplazar el modelo mientras otros usuarios lo editan.',
      )
      return
    }

    setErrorArtefacto('')
    inputXmiRef.current?.click()
  }

  const importarXmi = async (evento: ChangeEvent<HTMLInputElement>) => {
    if (!proyectoAbierto) {
      return
    }

    const archivo = evento.target.files?.[0]

    if (!archivo) {
      return
    }

    const confirmar = window.confirm(
      `¿Importar "${archivo.name}"? El modelo actual se sincronizará con el contenido del XMI.`,
    )

    if (!confirmar) {
      evento.target.value = ''
      return
    }

    try {
      setProcesandoArtefacto(true)
      setErrorArtefacto('')

      const formulario = new FormData()
      formulario.append('archivo', archivo)

      const respuesta = await fetch(
        `${API_URL}/api/interoperabilidad/proyectos/${proyectoAbierto.id}/importar/xmi`,
        {
          method: 'POST',
          body: formulario,
        },
      )

      if (!respuesta.ok) {
        throw new Error(
          await obtenerMensajeErrorHttp(
            respuesta,
            'No se pudo importar el archivo XMI.',
          ),
        )
      }

      const modeloActualizado: ModeloCompleto = await respuesta.json()

      setModeloAbierto(modeloActualizado)
      setClaseSeleccionadaId(null)
      setMostrarFormularioAtributo(false)
      setAtributoEditandoId(null)
    } catch (error) {
      setErrorArtefacto(
        error instanceof Error
          ? error.message
          : 'Ocurrió un error al importar el modelo XMI.',
      )
    } finally {
      evento.target.value = ''
      setProcesandoArtefacto(false)
    }
  }

  const guardarPosicionNodo = async (nodoMovido: Node) => {
    if (!proyectoAbierto || !modeloAbierto) {
      return
    }

    try {
      setGuardandoModelo(true)
      setErrorGuardado('')

      if (sesionColaborativa && online) {
        const cliente = clienteStompRef.current

        if (estadoConexion !== 'conectado' || !cliente?.connected) {
          throw new Error(
            'La sesión colaborativa no está conectada. Espera la reconexión antes de mover una clase.',
          )
        }

        cliente.publish({
          destination: `/app/sesiones/${sesionColaborativa.codigo}/operaciones`,
          body: JSON.stringify({
            operacionId: crypto.randomUUID(),
            clienteId: clienteIdRef.current,
            tipo: 'MOVER_CLASE',
            datos: {
              claseId: nodoMovido.id,
              posicionX: nodoMovido.position.x,
              posicionY: nodoMovido.position.y,
            },
          }),
        })

        return
      }

      const posicionesPorId = new Map(
        nodos.map((nodo) => [nodo.id, nodo.position]),
      )

      posicionesPorId.set(nodoMovido.id, nodoMovido.position)

      const solicitud = {
        clases: modeloAbierto.clases.map((clase) => {
          const posicion = posicionesPorId.get(clase.id) ?? {
            x: clase.posicionX,
            y: clase.posicionY,
          }

          return {
            id: clase.id,
            claveCliente: clase.id,
            nombre: clase.nombre,
            posicionX: posicion.x,
            posicionY: posicion.y,
            atributos: clase.atributos.map((atributo) => ({
              id: atributo.id,
              nombre: atributo.nombre,
              tipoDato: atributo.tipoDato,
              permiteNulo: atributo.permiteNulo,
              identificador: atributo.identificador,
            })),
          }
        }),

        relaciones: modeloAbierto.relaciones.map((relacion) => ({
          id: relacion.id,
          claseOrigenClave: relacion.claseOrigenId,
          claseDestinoClave: relacion.claseDestinoId,
          tipo: relacion.tipo,
          multiplicidadOrigen: relacion.multiplicidadOrigen,
          multiplicidadDestino: relacion.multiplicidadDestino,
          nombre: relacion.nombre,
          claseAsociacionClave: relacion.claseAsociacionId,
        })),
      }

      const modeloActualizado = await guardarModeloConTransporte(
        solicitud,
        'No se pudo guardar la nueva posición de la clase',
      )

      if (modeloActualizado) {
        setModeloAbierto(modeloActualizado)
      }
    } catch (error) {
      // Volvemos a las posiciones confirmadas por el modelo canónico.
      setNodos(construirNodos(modeloAbierto))

      if (error instanceof Error) {
        setErrorGuardado(error.message)
      } else {
        setErrorGuardado('Ocurrió un error al guardar la posición')
      }
    } finally {
      setGuardandoModelo(false)
    }
  }

  const guardarClase = async (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()

    if (!proyectoAbierto || !modeloAbierto) {
      return
    }

    const nombreLimpio = nombreClase.trim()

    if (!nombreLimpio) {
      setErrorClase('El nombre de la clase es obligatorio.')
      return
    }

    const nombreDuplicado = modeloAbierto.clases.some(
      (clase) =>
        clase.id !== claseEditandoId &&
        clase.nombre.trim().toLowerCase() === nombreLimpio.toLowerCase(),
    )

    if (nombreDuplicado) {
      setErrorClase('Ya existe una clase con ese nombre en el modelo.')
      return
    }

    try {
      setCreandoClase(true)
      setErrorClase('')
      setErrorGuardado('')

      if (sesionColaborativa && online && claseEditandoId !== null) {
        const cliente = clienteStompRef.current

        if (estadoConexion !== 'conectado' || !cliente?.connected) {
          throw new Error(
            'La sesión colaborativa no está conectada. Espera la reconexión antes de renombrar una clase.',
          )
        }

        cliente.publish({
          destination: `/app/sesiones/${sesionColaborativa.codigo}/operaciones`,
          body: JSON.stringify({
            operacionId: crypto.randomUUID(),
            clienteId: clienteIdRef.current,
            tipo: 'RENOMBRAR_CLASE',
            datos: {
              claseId: claseEditandoId,
              nombre: nombreLimpio,
            },
          }),
        })

        setNombreClase('')
        setClaseEditandoId(null)
        setMostrarFormularioClase(false)
        return
      }

      if (sesionColaborativa && online && claseEditandoId === null) {
        const cliente = clienteStompRef.current

        if (estadoConexion !== 'conectado' || !cliente?.connected) {
          throw new Error(
            'La sesión colaborativa no está conectada. Espera la reconexión antes de crear una clase.',
          )
        }

        const cantidadClases = modeloAbierto.clases.length
        const columna = cantidadClases % 3
        const fila = Math.floor(cantidadClases / 3)

        cliente.publish({
          destination: `/app/sesiones/${sesionColaborativa.codigo}/operaciones`,
          body: JSON.stringify({
            operacionId: crypto.randomUUID(),
            clienteId: clienteIdRef.current,
            tipo: 'CREAR_CLASE',
            datos: {
              nombre: nombreLimpio,
              posicionX: 80 + columna * 280,
              posicionY: 80 + fila * 180,
            },
          }),
        })

        setNombreClase('')
        setClaseEditandoId(null)
        setMostrarFormularioClase(false)
        return
      }

      const posicionesPorId = new Map(
        nodos.map((nodo) => [nodo.id, nodo.position]),
      )

      const cantidadClases = modeloAbierto.clases.length
      const columna = cantidadClases % 3
      const fila = Math.floor(cantidadClases / 3)

      const claveNuevaClase = `nueva-${crypto.randomUUID()}`

      const clasesExistentes = modeloAbierto.clases.map((clase) => {
        const posicion = posicionesPorId.get(clase.id) ?? {
          x: clase.posicionX,
          y: clase.posicionY,
        }

        return {
          id: clase.id,
          claveCliente: clase.id,
          nombre:
            clase.id === claseEditandoId ? nombreLimpio : clase.nombre,
          posicionX: posicion.x,
          posicionY: posicion.y,
          atributos: clase.atributos.map((atributo) => ({
            id: atributo.id,
            nombre: atributo.nombre,
            tipoDato: atributo.tipoDato,
            permiteNulo: atributo.permiteNulo,
            identificador: atributo.identificador,
          })),
        }
      })

      const solicitud = {
        clases:
          claseEditandoId === null
            ? [
                ...clasesExistentes,
                {
                  id: null,
                  claveCliente: claveNuevaClase,
                  nombre: nombreLimpio,
                  posicionX: 80 + columna * 280,
                  posicionY: 80 + fila * 180,
                  atributos: [],
                },
              ]
            : clasesExistentes,

        relaciones: modeloAbierto.relaciones.map((relacion) => ({
          id: relacion.id,
          claseOrigenClave: relacion.claseOrigenId,
          claseDestinoClave: relacion.claseDestinoId,
          tipo: relacion.tipo,
          multiplicidadOrigen: relacion.multiplicidadOrigen,
          multiplicidadDestino: relacion.multiplicidadDestino,
          nombre: relacion.nombre,
          claseAsociacionClave: relacion.claseAsociacionId,
        })),
      }

      const modeloActualizado = await guardarModeloConTransporte(
        solicitud,
        claseEditandoId === null
          ? 'No se pudo crear la clase UML'
          : 'No se pudo actualizar la clase UML',
      )

      if (modeloActualizado) {
        setModeloAbierto(modeloActualizado)
      }
      setNombreClase('')
      setClaseEditandoId(null)
      setMostrarFormularioClase(false)
    } catch (error) {
      if (error instanceof Error) {
        setErrorClase(error.message)
      } else {
        setErrorClase('Ocurrió un error al guardar la clase UML')
      }
    } finally {
      setCreandoClase(false)
    }
  }

  const editarClase = (clase: ClaseDiagrama) => {
    setClaseEditandoId(clase.id)
    setNombreClase(clase.nombre)
    setErrorClase('')
    setMostrarFormularioClase(true)
  }

  const eliminarClase = async (clase: ClaseDiagrama) => {
    if (!proyectoAbierto || !modeloAbierto) {
      return
    }

    const relacionesAfectadas = modeloAbierto.relaciones.filter(
      (relacion) =>
        relacion.claseOrigenId === clase.id ||
        relacion.claseDestinoId === clase.id ||
        relacion.claseAsociacionId === clase.id,
    ).length

    const mensajeConfirmacion =
      relacionesAfectadas > 0
        ? `¿Eliminar la clase "${clase.nombre}"? También se eliminarán ${relacionesAfectadas} relación(es) asociada(s).`
        : `¿Eliminar la clase "${clase.nombre}"?`

    if (!window.confirm(mensajeConfirmacion)) {
      return
    }

    try {
      setEliminandoClaseId(clase.id)
      setErrorClase('')
      setErrorGuardado('')

      if (sesionColaborativa && online) {
        const cliente = clienteStompRef.current

        if (estadoConexion !== 'conectado' || !cliente?.connected) {
          throw new Error(
            'La sesión colaborativa no está conectada. Espera la reconexión antes de eliminar una clase.',
          )
        }

        cliente.publish({
          destination: `/app/sesiones/${sesionColaborativa.codigo}/operaciones`,
          body: JSON.stringify({
            operacionId: crypto.randomUUID(),
            clienteId: clienteIdRef.current,
            tipo: 'ELIMINAR_CLASE',
            datos: {
              claseId: clase.id,
            },
          }),
        })

        if (claseSeleccionadaId === clase.id) {
          setClaseSeleccionadaId(null)
          setMostrarFormularioAtributo(false)
          setAtributoEditandoId(null)
          setNombreAtributo('')
          setTipoDatoAtributo('')
          setPermiteNuloAtributo(false)
          setIdentificadorAtributo(false)
          setErrorAtributo('')
        }

        if (claseEditandoId === clase.id) {
          setClaseEditandoId(null)
          setNombreClase('')
          setMostrarFormularioClase(false)
        }

        return
      }

      const posicionesPorId = new Map(
        nodos.map((nodo) => [nodo.id, nodo.position]),
      )

      const solicitud = {
        clases: modeloAbierto.clases
          .filter((claseActual) => claseActual.id !== clase.id)
          .map((claseActual) => {
            const posicion = posicionesPorId.get(claseActual.id) ?? {
              x: claseActual.posicionX,
              y: claseActual.posicionY,
            }

            return {
              id: claseActual.id,
              claveCliente: claseActual.id,
              nombre: claseActual.nombre,
              posicionX: posicion.x,
              posicionY: posicion.y,
              atributos: claseActual.atributos.map((atributo) => ({
                id: atributo.id,
                nombre: atributo.nombre,
                tipoDato: atributo.tipoDato,
                permiteNulo: atributo.permiteNulo,
                identificador: atributo.identificador,
              })),
            }
          }),

        relaciones: modeloAbierto.relaciones
          .filter(
            (relacion) =>
              relacion.claseOrigenId !== clase.id &&
              relacion.claseDestinoId !== clase.id &&
              relacion.claseAsociacionId !== clase.id,
          )
          .map((relacion) => ({
            id: relacion.id,
            claseOrigenClave: relacion.claseOrigenId,
            claseDestinoClave: relacion.claseDestinoId,
            tipo: relacion.tipo,
            multiplicidadOrigen: relacion.multiplicidadOrigen,
            multiplicidadDestino: relacion.multiplicidadDestino,
            nombre: relacion.nombre,
            claseAsociacionClave: relacion.claseAsociacionId,
          })),
      }

      const modeloActualizado = await guardarModeloConTransporte(
        solicitud,
        'No se pudo eliminar la clase UML',
      )

      if (modeloActualizado) {
        setModeloAbierto(modeloActualizado)
      }

      if (claseSeleccionadaId === clase.id) {
        setClaseSeleccionadaId(null)
        setMostrarFormularioAtributo(false)
        setAtributoEditandoId(null)
        setNombreAtributo('')
        setTipoDatoAtributo('')
        setPermiteNuloAtributo(false)
        setIdentificadorAtributo(false)
        setErrorAtributo('')
      }

      if (claseEditandoId === clase.id) {
        setClaseEditandoId(null)
        setNombreClase('')
        setMostrarFormularioClase(false)
      }
    } catch (error) {
      if (error instanceof Error) {
        setErrorClase(error.message)
      } else {
        setErrorClase('Ocurrió un error al eliminar la clase UML')
      }
    } finally {
      setEliminandoClaseId(null)
    }
  }

  const cancelarCreacionClase = () => {
    setNombreClase('')
    setClaseEditandoId(null)
    setErrorClase('')
    setMostrarFormularioClase(false)
  }

  const guardarAtributo = async (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()

    if (!proyectoAbierto || !modeloAbierto || !claseSeleccionadaId) {
      return
    }

    const nombreLimpio = nombreAtributo.trim()
    const tipoDatoLimpio = tipoDatoAtributo.trim()

    if (!nombreLimpio) {
      setErrorAtributo('El nombre del atributo es obligatorio.')
      return
    }

    if (!tipoDatoLimpio) {
      setErrorAtributo('El tipo de dato es obligatorio.')
      return
    }

    const claseSeleccionada = modeloAbierto.clases.find(
      (clase) => clase.id === claseSeleccionadaId,
    )

    if (!claseSeleccionada) {
      setErrorAtributo('La clase seleccionada ya no está disponible.')
      return
    }

    const nombreDuplicado = claseSeleccionada.atributos.some(
      (atributo) =>
        atributo.id !== atributoEditandoId &&
        atributo.nombre.trim().toLowerCase() === nombreLimpio.toLowerCase(),
    )

    if (nombreDuplicado) {
      setErrorAtributo('Ya existe un atributo con ese nombre en la clase.')
      return
    }

    try {
      setCreandoAtributo(true)
      setErrorAtributo('')
      setErrorGuardado('')

      if (sesionColaborativa && online) {
        const cliente = clienteStompRef.current

        if (estadoConexion !== 'conectado' || !cliente?.connected) {
          throw new Error(
            'La sesión colaborativa no está conectada. Espera la reconexión antes de guardar un atributo.',
          )
        }

        cliente.publish({
          destination: `/app/sesiones/${sesionColaborativa.codigo}/operaciones`,
          body: JSON.stringify({
            operacionId: crypto.randomUUID(),
            clienteId: clienteIdRef.current,
            tipo:
              atributoEditandoId === null
                ? 'CREAR_ATRIBUTO'
                : 'ACTUALIZAR_ATRIBUTO',
            datos: {
              ...(atributoEditandoId !== null
                ? { atributoId: atributoEditandoId }
                : {}),
              claseId: claseSeleccionadaId,
              nombre: nombreLimpio,
              tipoDato: tipoDatoLimpio,
              permiteNulo: permiteNuloAtributo,
              identificador: identificadorAtributo,
            },
          }),
        })

        setNombreAtributo('')
        setTipoDatoAtributo('')
        setPermiteNuloAtributo(false)
        setIdentificadorAtributo(false)
        setAtributoEditandoId(null)
        setMostrarFormularioAtributo(false)
        return
      }

      const posicionesPorId = new Map(
        nodos.map((nodo) => [nodo.id, nodo.position]),
      )

      const solicitud = {
        clases: modeloAbierto.clases.map((clase) => {
          const posicion = posicionesPorId.get(clase.id) ?? {
            x: clase.posicionX,
            y: clase.posicionY,
          }

          const atributos: Array<{
            id: string | null
            nombre: string
            tipoDato: string
            permiteNulo: boolean
            identificador: boolean
          }> = clase.atributos.map((atributo) => {
            if (
              clase.id === claseSeleccionadaId &&
              atributo.id === atributoEditandoId
            ) {
              return {
                id: atributo.id,
                nombre: nombreLimpio,
                tipoDato: tipoDatoLimpio,
                permiteNulo: permiteNuloAtributo,
                identificador: identificadorAtributo,
              }
            }

            return {
              id: atributo.id,
              nombre: atributo.nombre,
              tipoDato: atributo.tipoDato,
              permiteNulo: atributo.permiteNulo,
              identificador: atributo.identificador,
            }
          })

          if (
            clase.id === claseSeleccionadaId &&
            atributoEditandoId === null
          ) {
            atributos.push({
              id: null,
              nombre: nombreLimpio,
              tipoDato: tipoDatoLimpio,
              permiteNulo: permiteNuloAtributo,
              identificador: identificadorAtributo,
            })
          }

          return {
            id: clase.id,
            claveCliente: clase.id,
            nombre: clase.nombre,
            posicionX: posicion.x,
            posicionY: posicion.y,
            atributos,
          }
        }),

        relaciones: modeloAbierto.relaciones.map((relacion) => ({
          id: relacion.id,
          claseOrigenClave: relacion.claseOrigenId,
          claseDestinoClave: relacion.claseDestinoId,
          tipo: relacion.tipo,
          multiplicidadOrigen: relacion.multiplicidadOrigen,
          multiplicidadDestino: relacion.multiplicidadDestino,
          nombre: relacion.nombre,
          claseAsociacionClave: relacion.claseAsociacionId,
        })),
      }

      const modeloActualizado = await guardarModeloConTransporte(
        solicitud,
        atributoEditandoId === null
          ? 'No se pudo crear el atributo UML'
          : 'No se pudo actualizar el atributo UML',
      )

      if (modeloActualizado) {
        setModeloAbierto(modeloActualizado)
      }
      setNombreAtributo('')
      setTipoDatoAtributo('')
      setPermiteNuloAtributo(false)
      setIdentificadorAtributo(false)
      setAtributoEditandoId(null)
      setMostrarFormularioAtributo(false)
    } catch (error) {
      if (error instanceof Error) {
        setErrorAtributo(error.message)
      } else {
        setErrorAtributo('Ocurrió un error al guardar el atributo UML')
      }
    } finally {
      setCreandoAtributo(false)
    }
  }

  const editarAtributo = (atributo: AtributoDiagrama) => {
    setAtributoEditandoId(atributo.id)
    setNombreAtributo(atributo.nombre)
    setTipoDatoAtributo(atributo.tipoDato)
    setPermiteNuloAtributo(atributo.permiteNulo)
    setIdentificadorAtributo(atributo.identificador)
    setErrorAtributo('')
    setMostrarFormularioAtributo(true)
  }

  const eliminarAtributo = async (atributo: AtributoDiagrama) => {
    if (!proyectoAbierto || !modeloAbierto || !claseSeleccionadaId) {
      return
    }

    const confirmar = window.confirm(
      `¿Eliminar el atributo "${atributo.nombre}"?`,
    )

    if (!confirmar) {
      return
    }

    try {
      setEliminandoAtributoId(atributo.id)
      setErrorAtributo('')
      setErrorGuardado('')

      if (sesionColaborativa && online) {
        const cliente = clienteStompRef.current

        if (estadoConexion !== 'conectado' || !cliente?.connected) {
          throw new Error(
            'La sesión colaborativa no está conectada. Espera la reconexión antes de eliminar un atributo.',
          )
        }

        cliente.publish({
          destination: `/app/sesiones/${sesionColaborativa.codigo}/operaciones`,
          body: JSON.stringify({
            operacionId: crypto.randomUUID(),
            clienteId: clienteIdRef.current,
            tipo: 'ELIMINAR_ATRIBUTO',
            datos: {
              atributoId: atributo.id,
              claseId: claseSeleccionadaId,
            },
          }),
        })

        if (atributoEditandoId === atributo.id) {
          setNombreAtributo('')
          setTipoDatoAtributo('')
          setPermiteNuloAtributo(false)
          setIdentificadorAtributo(false)
          setAtributoEditandoId(null)
          setMostrarFormularioAtributo(false)
        }

        return
      }

      const posicionesPorId = new Map(
        nodos.map((nodo) => [nodo.id, nodo.position]),
      )

      const solicitud = {
        clases: modeloAbierto.clases.map((clase) => {
          const posicion = posicionesPorId.get(clase.id) ?? {
            x: clase.posicionX,
            y: clase.posicionY,
          }

          return {
            id: clase.id,
            claveCliente: clase.id,
            nombre: clase.nombre,
            posicionX: posicion.x,
            posicionY: posicion.y,
            atributos: clase.atributos
              .filter(
                (atributoActual) =>
                  !(
                    clase.id === claseSeleccionadaId &&
                    atributoActual.id === atributo.id
                  ),
              )
              .map((atributoActual) => ({
                id: atributoActual.id,
                nombre: atributoActual.nombre,
                tipoDato: atributoActual.tipoDato,
                permiteNulo: atributoActual.permiteNulo,
                identificador: atributoActual.identificador,
              })),
          }
        }),

        relaciones: modeloAbierto.relaciones.map((relacion) => ({
          id: relacion.id,
          claseOrigenClave: relacion.claseOrigenId,
          claseDestinoClave: relacion.claseDestinoId,
          tipo: relacion.tipo,
          multiplicidadOrigen: relacion.multiplicidadOrigen,
          multiplicidadDestino: relacion.multiplicidadDestino,
          nombre: relacion.nombre,
          claseAsociacionClave: relacion.claseAsociacionId,
        })),
      }

      const modeloActualizado = await guardarModeloConTransporte(
        solicitud,
        'No se pudo eliminar el atributo UML',
      )

      if (modeloActualizado) {
        setModeloAbierto(modeloActualizado)
      }

      if (atributoEditandoId === atributo.id) {
        setNombreAtributo('')
        setTipoDatoAtributo('')
        setPermiteNuloAtributo(false)
        setIdentificadorAtributo(false)
        setAtributoEditandoId(null)
        setMostrarFormularioAtributo(false)
      }
    } catch (error) {
      if (error instanceof Error) {
        setErrorAtributo(error.message)
      } else {
        setErrorAtributo('Ocurrió un error al eliminar el atributo UML')
      }
    } finally {
      setEliminandoAtributoId(null)
    }
  }

  const cancelarCreacionAtributo = () => {
    setNombreAtributo('')
    setTipoDatoAtributo('')
    setPermiteNuloAtributo(false)
    setIdentificadorAtributo(false)
    setAtributoEditandoId(null)
    setErrorAtributo('')
    setMostrarFormularioAtributo(false)
  }

  const guardarRelacion = async (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()

    if (!proyectoAbierto || !modeloAbierto) {
      return
    }

    if (!claseOrigenRelacion) {
      setErrorRelacion('Debes seleccionar una clase de origen.')
      return
    }

    if (!claseDestinoRelacion) {
      setErrorRelacion('Debes seleccionar una clase de destino.')
      return
    }

    const tipoLimpio = tipoRelacion.trim()

    if (!tipoLimpio) {
      setErrorRelacion('El tipo de relación es obligatorio.')
      return
    }

    try {
      setCreandoRelacion(true)
      setErrorRelacion('')
      setErrorGuardado('')

      if (sesionColaborativa && online) {
        const cliente = clienteStompRef.current

        if (estadoConexion !== 'conectado' || !cliente?.connected) {
          throw new Error(
            'La sesión colaborativa no está conectada. Espera la reconexión antes de guardar una relación.',
          )
        }

        cliente.publish({
          destination: `/app/sesiones/${sesionColaborativa.codigo}/operaciones`,
          body: JSON.stringify({
            operacionId: crypto.randomUUID(),
            clienteId: clienteIdRef.current,
            tipo:
              relacionEditandoId === null
                ? 'CREAR_RELACION'
                : 'ACTUALIZAR_RELACION',
            datos:
              relacionEditandoId === null
                ? {
                    claseOrigenId: claseOrigenRelacion,
                    claseDestinoId: claseDestinoRelacion,
                    tipo: tipoLimpio,
                    multiplicidadOrigen:
                      multiplicidadOrigenRelacion.trim() || null,
                    multiplicidadDestino:
                      multiplicidadDestinoRelacion.trim() || null,
                    nombre: nombreRelacion.trim() || null,
                    claseAsociacionId: null,
                  }
                : {
                    relacionId: relacionEditandoId,
                    tipo: tipoLimpio,
                    multiplicidadOrigen:
                      multiplicidadOrigenRelacion.trim() || null,
                    multiplicidadDestino:
                      multiplicidadDestinoRelacion.trim() || null,
                    nombre: nombreRelacion.trim() || null,
                    claseAsociacionId:
                      modeloAbierto.relaciones.find(
                        (relacion) => relacion.id === relacionEditandoId,
                      )?.claseAsociacionId ?? null,
                  },
          }),
        })

        setRelacionEditandoId(null)
        setClaseOrigenRelacion('')
        setClaseDestinoRelacion('')
        setTipoRelacion('ASOCIACION')
        setMultiplicidadOrigenRelacion('1')
        setMultiplicidadDestinoRelacion('*')
        setNombreRelacion('')
        setMostrarFormularioRelacion(false)
        return
      }

      const posicionesPorId = new Map(
        nodos.map((nodo) => [nodo.id, nodo.position]),
      )

      const relacionesExistentes = modeloAbierto.relaciones.map((relacion) => {
        if (relacion.id === relacionEditandoId) {
          return {
            id: relacion.id,
            claseOrigenClave: relacion.claseOrigenId,
            claseDestinoClave: relacion.claseDestinoId,
            tipo: tipoLimpio,
            multiplicidadOrigen: multiplicidadOrigenRelacion.trim() || null,
            multiplicidadDestino: multiplicidadDestinoRelacion.trim() || null,
            nombre: nombreRelacion.trim() || null,
            claseAsociacionClave: relacion.claseAsociacionId,
          }
        }

        return {
          id: relacion.id,
          claseOrigenClave: relacion.claseOrigenId,
          claseDestinoClave: relacion.claseDestinoId,
          tipo: relacion.tipo,
          multiplicidadOrigen: relacion.multiplicidadOrigen,
          multiplicidadDestino: relacion.multiplicidadDestino,
          nombre: relacion.nombre,
          claseAsociacionClave: relacion.claseAsociacionId,
        }
      })

      const solicitud = {
        clases: modeloAbierto.clases.map((clase) => {
          const posicion = posicionesPorId.get(clase.id) ?? {
            x: clase.posicionX,
            y: clase.posicionY,
          }

          return {
            id: clase.id,
            claveCliente: clase.id,
            nombre: clase.nombre,
            posicionX: posicion.x,
            posicionY: posicion.y,
            atributos: clase.atributos.map((atributo) => ({
              id: atributo.id,
              nombre: atributo.nombre,
              tipoDato: atributo.tipoDato,
              permiteNulo: atributo.permiteNulo,
              identificador: atributo.identificador,
            })),
          }
        }),

        relaciones:
          relacionEditandoId === null
            ? [
                ...relacionesExistentes,
                {
                  id: null,
                  claseOrigenClave: claseOrigenRelacion,
                  claseDestinoClave: claseDestinoRelacion,
                  tipo: tipoLimpio,
                  multiplicidadOrigen:
                    multiplicidadOrigenRelacion.trim() || null,
                  multiplicidadDestino:
                    multiplicidadDestinoRelacion.trim() || null,
                  nombre: nombreRelacion.trim() || null,
                  claseAsociacionClave: null,
                },
              ]
            : relacionesExistentes,
      }

      const modeloActualizado = await guardarModeloConTransporte(
        solicitud,
        relacionEditandoId === null
          ? 'No se pudo crear la relación UML'
          : 'No se pudo actualizar la relación UML',
      )

      if (modeloActualizado) {
        setModeloAbierto(modeloActualizado)
      }
      setRelacionEditandoId(null)
      setClaseOrigenRelacion('')
      setClaseDestinoRelacion('')
      setTipoRelacion('ASOCIACION')
      setMultiplicidadOrigenRelacion('1')
      setMultiplicidadDestinoRelacion('*')
      setNombreRelacion('')
      setMostrarFormularioRelacion(false)
    } catch (error) {
      if (error instanceof Error) {
        setErrorRelacion(error.message)
      } else {
        setErrorRelacion('Ocurrió un error al guardar la relación UML')
      }
    } finally {
      setCreandoRelacion(false)
    }
  }

  const editarRelacion = (relacion: RelacionDiagrama) => {
    setRelacionEditandoId(relacion.id)
    setClaseOrigenRelacion(relacion.claseOrigenId)
    setClaseDestinoRelacion(relacion.claseDestinoId)
    setTipoRelacion(relacion.tipo)
    setMultiplicidadOrigenRelacion(relacion.multiplicidadOrigen ?? '')
    setMultiplicidadDestinoRelacion(relacion.multiplicidadDestino ?? '')
    setNombreRelacion(relacion.nombre ?? '')
    setErrorRelacion('')
    setMostrarFormularioRelacion(true)
  }

  const eliminarRelacion = async (relacion: RelacionDiagrama) => {
    if (!proyectoAbierto || !modeloAbierto) {
      return
    }

    const claseOrigen = modeloAbierto.clases.find(
      (clase) => clase.id === relacion.claseOrigenId,
    )
    const claseDestino = modeloAbierto.clases.find(
      (clase) => clase.id === relacion.claseDestinoId,
    )

    const descripcionRelacion =
      relacion.nombre?.trim() ||
      `${claseOrigen?.nombre ?? 'Origen'} → ${claseDestino?.nombre ?? 'Destino'}`

    const confirmar = window.confirm(
      `¿Eliminar la relación "${descripcionRelacion}"? Las clases conectadas se conservarán.`,
    )

    if (!confirmar) {
      return
    }

    try {
      setEliminandoRelacionId(relacion.id)
      setErrorRelacion('')
      setErrorGuardado('')

      if (sesionColaborativa && online) {
        const cliente = clienteStompRef.current

        if (estadoConexion !== 'conectado' || !cliente?.connected) {
          throw new Error(
            'La sesión colaborativa no está conectada. Espera la reconexión antes de eliminar una relación.',
          )
        }

        cliente.publish({
          destination: `/app/sesiones/${sesionColaborativa.codigo}/operaciones`,
          body: JSON.stringify({
            operacionId: crypto.randomUUID(),
            clienteId: clienteIdRef.current,
            tipo: 'ELIMINAR_RELACION',
            datos: {
              relacionId: relacion.id,
            },
          }),
        })

        if (relacionEditandoId === relacion.id) {
          setRelacionEditandoId(null)
          setClaseOrigenRelacion('')
          setClaseDestinoRelacion('')
          setTipoRelacion('ASOCIACION')
          setMultiplicidadOrigenRelacion('1')
          setMultiplicidadDestinoRelacion('*')
          setNombreRelacion('')
          setMostrarFormularioRelacion(false)
        }

        return
      }

      const posicionesPorId = new Map(
        nodos.map((nodo) => [nodo.id, nodo.position]),
      )

      const solicitud = {
        clases: modeloAbierto.clases.map((clase) => {
          const posicion = posicionesPorId.get(clase.id) ?? {
            x: clase.posicionX,
            y: clase.posicionY,
          }

          return {
            id: clase.id,
            claveCliente: clase.id,
            nombre: clase.nombre,
            posicionX: posicion.x,
            posicionY: posicion.y,
            atributos: clase.atributos.map((atributo) => ({
              id: atributo.id,
              nombre: atributo.nombre,
              tipoDato: atributo.tipoDato,
              permiteNulo: atributo.permiteNulo,
              identificador: atributo.identificador,
            })),
          }
        }),

        relaciones: modeloAbierto.relaciones
          .filter((relacionActual) => relacionActual.id !== relacion.id)
          .map((relacionActual) => ({
            id: relacionActual.id,
            claseOrigenClave: relacionActual.claseOrigenId,
            claseDestinoClave: relacionActual.claseDestinoId,
            tipo: relacionActual.tipo,
            multiplicidadOrigen: relacionActual.multiplicidadOrigen,
            multiplicidadDestino: relacionActual.multiplicidadDestino,
            nombre: relacionActual.nombre,
            claseAsociacionClave: relacionActual.claseAsociacionId,
          })),
      }

      const modeloActualizado = await guardarModeloConTransporte(
        solicitud,
        'No se pudo eliminar la relación UML',
      )

      if (modeloActualizado) {
        setModeloAbierto(modeloActualizado)
      }

      if (relacionEditandoId === relacion.id) {
        setRelacionEditandoId(null)
        setClaseOrigenRelacion('')
        setClaseDestinoRelacion('')
        setTipoRelacion('ASOCIACION')
        setMultiplicidadOrigenRelacion('1')
        setMultiplicidadDestinoRelacion('*')
        setNombreRelacion('')
        setMostrarFormularioRelacion(false)
      }
    } catch (error) {
      if (error instanceof Error) {
        setErrorRelacion(error.message)
      } else {
        setErrorRelacion('Ocurrió un error al eliminar la relación UML')
      }
    } finally {
      setEliminandoRelacionId(null)
    }
  }

  const cancelarCreacionRelacion = () => {
    setRelacionEditandoId(null)
    setClaseOrigenRelacion('')
    setClaseDestinoRelacion('')
    setTipoRelacion('ASOCIACION')
    setMultiplicidadOrigenRelacion('1')
    setMultiplicidadDestinoRelacion('*')
    setNombreRelacion('')
    setErrorRelacion('')
    setMostrarFormularioRelacion(false)
  }

  const cerrarProyecto = () => {
    salirSesionColaborativa()
    setProyectoAbierto(null)
    setModeloAbierto(null)
    setModeloDesdeOffline(false)
    setOperacionesPendientes(0)
    setSincronizandoOffline(false)
    setErrorAbrir('')
    setErrorGuardado('')
    setMostrarFormularioRelacion(false)
    setRelacionEditandoId(null)
    setEliminandoRelacionId(null)
    setClaseOrigenRelacion('')
    setClaseDestinoRelacion('')
    setTipoRelacion('ASOCIACION')
    setMultiplicidadOrigenRelacion('1')
    setMultiplicidadDestinoRelacion('*')
    setNombreRelacion('')
    setErrorRelacion('')
    setMostrarFormularioClase(false)
    setNombreClase('')
    setClaseEditandoId(null)
    setEliminandoClaseId(null)
    setErrorClase('')
    setClaseSeleccionadaId(null)
    setMostrarFormularioAtributo(false)
    setNombreAtributo('')
    setTipoDatoAtributo('')
    setPermiteNuloAtributo(false)
    setIdentificadorAtributo(false)
    setAtributoEditandoId(null)
    setEliminandoAtributoId(null)
    setErrorAtributo('')
    setErrorArtefacto('')
  }

  if (proyectoAbierto && modeloAbierto) {
    const aristas = construirAristas(modeloAbierto)
    const claseSeleccionada =
      modeloAbierto.clases.find((clase) => clase.id === claseSeleccionadaId) ??
      null

    return (
      <div className="app">
        <header className="encabezado">
          <div>
            <h1>CollabCASE AI</h1>
            <p>{proyectoAbierto.nombre}</p>
          </div>

          <button
            className="boton-volver"
            type="button"
            onClick={cerrarProyecto}
          >
            ← Volver a proyectos
          </button>
        </header>

        <main className="contenido">
          <section className="panel-editor">
            <div className="panel-colaboracion">
              <div>
                <h3>Colaboración en tiempo real</h3>

                {sesionColaborativa ? (
                  <p>
                    Código: <strong>{sesionColaborativa.codigo}</strong>
                    {' · '}
                    Estado:{' '}
                    <strong>
                      {estadoConexion === 'conectado'
                        ? 'Conectado'
                        : estadoConexion === 'conectando'
                          ? 'Conectando...'
                          : 'Desconectado'}
                    </strong>
                  </p>
                ) : (
                  <p>Inicia una sesión para compartir este proyecto.</p>
                )}

                <p
                  style={{
                    margin: '8px 0 0',
                    fontWeight: 700,
                    color: online ? '#15803d' : '#b91c1c',
                  }}
                >
                  {online
                    ? '🟢 Internet disponible'
                    : '🔴 Sin conexión · trabajando localmente'}
                </p>

                {modeloDesdeOffline && (
                  <p
                    style={{
                      margin: '6px 0 0',
                      color: '#526071',
                      fontWeight: 600,
                    }}
                  >
                    💾 Modelo cargado desde el almacenamiento local.
                  </p>
                )}

                {operacionesPendientes > 0 && (
                  <p
                    style={{
                      margin: '6px 0 0',
                      color: '#9a6700',
                      fontWeight: 700,
                    }}
                  >
                    🕒 Hay cambios locales pendientes de sincronización.
                  </p>
                )}
              </div>

              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                {online && operacionesPendientes > 0 && !sesionColaborativa && (
                  <button
                    type="button"
                    onClick={() => void sincronizarCambiosOffline()}
                    disabled={sincronizandoOffline}
                  >
                    {sincronizandoOffline
                      ? 'Sincronizando...'
                      : 'Sincronizar cambios'}
                  </button>
                )}

                {sesionColaborativa ? (
                  <button
                    className="boton-salir-sesion"
                    type="button"
                    onClick={salirSesionColaborativa}
                  >
                    Salir de la sesión
                  </button>
                ) : (
                  <button
                    className="boton-iniciar-sesion"
                    type="button"
                    onClick={iniciarSesionColaborativa}
                    disabled={procesandoSesion || !online || operacionesPendientes > 0}
                    title={
                      !online
                        ? 'La colaboración requiere conexión.'
                        : operacionesPendientes > 0
                          ? 'Sincroniza primero los cambios locales.'
                          : undefined
                    }
                  >
                    {procesandoSesion
                      ? 'Iniciando...'
                      : 'Iniciar sesión colaborativa'}
                  </button>
                )}
              </div>
            </div>

            {errorColaboracion && (
              <p className="error-formulario">{errorColaboracion}</p>
            )}

            <section
              aria-label="Interoperabilidad y generación"
              style={{
                marginTop: 18,
                marginBottom: 18,
                border: '1px solid #d9e2f0',
                borderRadius: 12,
                padding: 18,
                background: '#ffffff',
              }}
            >
              <div style={{ marginBottom: 12 }}>
                <h3 style={{ margin: 0 }}>Interoperabilidad y generación</h3>
                <p style={{ margin: '6px 0 0', color: '#526071' }}>
                  Importa o exporta XMI y genera el backend Spring Boot o la
                  colección Postman del modelo actual.
                </p>
              </div>

              <input
                ref={inputXmiRef}
                type="file"
                accept=".xmi,.xml,application/xml,text/xml"
                onChange={importarXmi}
                style={{ display: 'none' }}
              />

              <div
                style={{
                  display: 'flex',
                  gap: 10,
                  flexWrap: 'wrap',
                }}
              >
                <button
                  type="button"
                  onClick={abrirSelectorXmi}
                  disabled={
                    procesandoArtefacto || sesionColaborativa !== null || !online
                  }
                >
                  Importar XMI
                </button>

                <button
                  type="button"
                  onClick={exportarXmi}
                  disabled={procesandoArtefacto || !online}
                >
                  Exportar XMI
                </button>

                <button
                  type="button"
                  onClick={generarBackendZip}
                  disabled={procesandoArtefacto || !online}
                >
                  Generar backend ZIP
                </button>

                <button
                  type="button"
                  onClick={generarPostman}
                  disabled={procesandoArtefacto || !online}
                >
                  Generar Postman
                </button>
              </div>

              {!online && (
                <p style={{ margin: '10px 0 0', color: '#6b7280' }}>
                  Estas funciones requieren conexión con el backend. El editor UML
                  básico continúa disponible sin Internet.
                </p>
              )}

              {sesionColaborativa && (
                <p style={{ margin: '10px 0 0', color: '#6b7280' }}>
                  Para importar XMI, sal primero de la sesión colaborativa.
                  Exportar y generar artefactos sí está permitido.
                </p>
              )}

              {procesandoArtefacto && (
                <p style={{ marginBottom: 0 }}>Procesando...</p>
              )}

              {errorArtefacto && (
                <p className="error-formulario">{errorArtefacto}</p>
              )}
            </section>

            <section
              aria-label="Asistente IA"
              style={{
                marginTop: 18,
                marginBottom: 22,
                border: '1px solid #d9e2f0',
                borderRadius: 12,
                padding: 18,
                background: '#ffffff',
              }}
            >
              <div style={{ marginBottom: 12 }}>
                <h3 style={{ margin: 0 }}>Asistente IA</h3>
                <p style={{ margin: '6px 0 0', color: '#526071' }}>
                  Edita el diagrama mediante texto, voz o una imagen UML tomada
                  desde la cámara o seleccionada desde el dispositivo.
                </p>
              </div>

              {!sesionColaborativa ? (
                <p
                  style={{
                    margin: 0,
                    padding: 12,
                    borderRadius: 8,
                    background: '#f6f8fb',
                    color: '#526071',
                  }}
                >
                  Inicia una sesión colaborativa para habilitar el asistente IA.
                </p>
              ) : (
                <>
                  <input
                    ref={inputCamaraRef}
                    type="file"
                    accept="image/*"
                    capture="environment"
                    onChange={enviarImagenIa}
                    style={{ display: 'none' }}
                  />

                  <input
                    ref={inputImagenRef}
                    type="file"
                    accept="image/*"
                    onChange={enviarImagenIa}
                    style={{ display: 'none' }}
                  />

                  {historialIa.length > 0 && (
                    <div
                      style={{
                        display: 'grid',
                        gap: 10,
                        maxHeight: 260,
                        overflowY: 'auto',
                        marginBottom: 14,
                        padding: 4,
                      }}
                    >
                      {historialIa.map((mensaje) => (
                        <div
                          key={mensaje.id}
                          style={{
                            justifySelf:
                              mensaje.autor === 'usuario' ? 'end' : 'start',
                            maxWidth: '86%',
                            padding: '10px 12px',
                            borderRadius: 10,
                            background:
                              mensaje.autor === 'usuario'
                                ? '#e8f0ff'
                                : '#f3f6f8',
                          }}
                        >
                          <strong>
                            {mensaje.autor === 'usuario' ? 'Tú' : 'CollabCASE AI'}
                          </strong>

                          <p style={{ margin: '5px 0 0', whiteSpace: 'pre-wrap' }}>
                            {mensaje.texto}
                          </p>

                          {mensaje.acciones &&
                            mensaje.acciones.length > 0 && (
                              <ul style={{ margin: '8px 0 0', paddingLeft: 20 }}>
                                {mensaje.acciones.map((accion, indice) => (
                                  <li key={`${mensaje.id}-${indice}`}>
                                    {accion}
                                  </li>
                                ))}
                              </ul>
                            )}
                        </div>
                      ))}
                    </div>
                  )}

                  <form onSubmit={enviarMensajeIa}>
                    <label
                      htmlFor="mensajeIa"
                      style={{
                        display: 'block',
                        fontWeight: 600,
                        marginBottom: 6,
                      }}
                    >
                      Instrucción
                    </label>

                    <textarea
                      id="mensajeIa"
                      value={mensajeIa}
                      onChange={(evento) => setMensajeIa(evento.target.value)}
                      rows={3}
                      maxLength={1000}
                      placeholder="Ej: Crea una clase Cliente con id UUID como identificador y nombre String"
                      disabled={
                        procesandoIa ||
                        procesandoImagen ||
                        estadoConexion !== 'conectado'
                      }
                      style={{
                        width: '100%',
                        boxSizing: 'border-box',
                        resize: 'vertical',
                        padding: 10,
                        border: '1px solid #cfd8e3',
                        borderRadius: 8,
                        font: 'inherit',
                      }}
                    />

                    {errorVoz && (
                      <p className="error-formulario">{errorVoz}</p>
                    )}

                    {errorImagen && (
                      <p className="error-formulario">{errorImagen}</p>
                    )}

                    {errorIa && (
                      <p className="error-formulario">{errorIa}</p>
                    )}

                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        gap: 10,
                        marginTop: 10,
                        flexWrap: 'wrap',
                      }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 10,
                          flexWrap: 'wrap',
                        }}
                      >
                        <button
                          type="button"
                          onClick={alternarReconocimientoVoz}
                          disabled={
                            procesandoIa ||
                            procesandoImagen ||
                            estadoConexion !== 'conectado'
                          }
                          aria-pressed={escuchandoVoz}
                          style={{
                            padding: '9px 14px',
                            borderRadius: 8,
                            border: '1px solid #cfd8e3',
                            cursor:
                              procesandoIa ||
                              procesandoImagen ||
                              estadoConexion !== 'conectado'
                                ? 'not-allowed'
                                : 'pointer',
                            fontWeight: 600,
                          }}
                        >
                          {escuchandoVoz ? '⏹ Detener' : '🎙️ Dictar por voz'}
                        </button>

                        {escuchandoVoz && (
                          <span style={{ fontWeight: 600 }}>
                            Escuchando...
                          </span>
                        )}

                        <button
                          type="button"
                          onClick={abrirCamaraImagen}
                          disabled={
                            procesandoIa ||
                            procesandoImagen ||
                            estadoConexion !== 'conectado'
                          }
                          style={{
                            padding: '9px 14px',
                            borderRadius: 8,
                            border: '1px solid #cfd8e3',
                            cursor:
                              procesandoIa ||
                              procesandoImagen ||
                              estadoConexion !== 'conectado'
                                ? 'not-allowed'
                                : 'pointer',
                            fontWeight: 600,
                          }}
                          title="En el celular abre la cámara trasera para fotografiar el diagrama UML."
                        >
                          {procesandoImagen
                            ? 'Analizando imagen...'
                            : '📷 Tomar foto'}
                        </button>

                        <button
                          type="button"
                          onClick={abrirSelectorImagen}
                          disabled={
                            procesandoIa ||
                            procesandoImagen ||
                            estadoConexion !== 'conectado'
                          }
                          style={{
                            padding: '9px 14px',
                            borderRadius: 8,
                            border: '1px solid #cfd8e3',
                            cursor:
                              procesandoIa ||
                              procesandoImagen ||
                              estadoConexion !== 'conectado'
                                ? 'not-allowed'
                                : 'pointer',
                            fontWeight: 600,
                          }}
                          title="Selecciona una imagen ya guardada en el dispositivo."
                        >
                          🖼️ Elegir imagen
                        </button>
                      </div>

                      <button
                        className="boton-guardar"
                        type="submit"
                        disabled={
                          procesandoIa ||
                          procesandoImagen ||
                          estadoConexion !== 'conectado' ||
                          !mensajeIa.trim()
                        }
                      >
                        {procesandoIa ? 'Procesando con IA...' : 'Enviar a IA'}
                      </button>
                    </div>
                  </form>
                </>
              )}
            </section>

            <div className="barra-editor">
              <div>
                <h2>Editor UML</h2>

                <p>
                  Versión del modelo: <strong>{modeloAbierto.version}</strong>
                </p>

                <p>
                  Clases: <strong>{modeloAbierto.clases.length}</strong>
                </p>

                <p>
                  Relaciones: <strong>{modeloAbierto.relaciones.length}</strong>
                </p>
              </div>

              <div className="acciones-principales-editor">
                <button
                  className="boton-nueva-relacion"
                  type="button"
                  onClick={() => {
                    setRelacionEditandoId(null)
                    setClaseOrigenRelacion('')
                    setClaseDestinoRelacion('')
                    setTipoRelacion('ASOCIACION')
                    setMultiplicidadOrigenRelacion('1')
                    setMultiplicidadDestinoRelacion('*')
                    setNombreRelacion('')
                    setErrorRelacion('')
                    setMostrarFormularioRelacion(true)
                  }}
                  disabled={
                    modeloAbierto.clases.length === 0 ||
                    creandoRelacion ||
                    creandoClase ||
                    guardandoModelo ||
                    eliminandoClaseId !== null ||
                    eliminandoRelacionId !== null
                  }
                >
                  + Nueva relación
                </button>

                <button
                  className="boton-nueva-clase"
                  type="button"
                  onClick={() => {
                    setClaseEditandoId(null)
                    setNombreClase('')
                    setErrorClase('')
                    setMostrarFormularioClase(true)
                  }}
                  disabled={
                    creandoClase ||
                    creandoRelacion ||
                    guardandoModelo ||
                    eliminandoClaseId !== null
                  }
                >
                  + Nueva clase
                </button>
              </div>
            </div>

            {mostrarFormularioRelacion && (
              <form
                className="formulario-relacion"
                onSubmit={guardarRelacion}
              >
                <h3>{relacionEditandoId ? 'Editar relación' : 'Nueva relación'}</h3>

                <div className="campos-relacion">
                  <div className="campo">
                    <label htmlFor="claseOrigenRelacion">Clase origen</label>

                    <select
                      id="claseOrigenRelacion"
                      value={claseOrigenRelacion}
                      onChange={(evento) =>
                        setClaseOrigenRelacion(evento.target.value)
                      }
                      disabled={relacionEditandoId !== null}
                    >
                      <option value="">Selecciona una clase</option>

                      {modeloAbierto.clases.map((clase) => (
                        <option key={clase.id} value={clase.id}>
                          {clase.nombre}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="campo">
                    <label htmlFor="claseDestinoRelacion">Clase destino</label>

                    <select
                      id="claseDestinoRelacion"
                      value={claseDestinoRelacion}
                      onChange={(evento) =>
                        setClaseDestinoRelacion(evento.target.value)
                      }
                      disabled={relacionEditandoId !== null}
                    >
                      <option value="">Selecciona una clase</option>

                      {modeloAbierto.clases.map((clase) => (
                        <option key={clase.id} value={clase.id}>
                          {clase.nombre}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                {relacionEditandoId && (
                  <p className="ayuda-relacion">
                    El origen y el destino se mantienen para esta relación.
                  </p>
                )}

                <div className="campos-relacion">
                  <div className="campo">
                    <label htmlFor="tipoRelacion">Tipo</label>

                    <select
                      id="tipoRelacion"
                      value={tipoRelacion}
                      onChange={(evento) =>
                        setTipoRelacion(evento.target.value)
                      }
                    >
                      <option value="ASOCIACION">Asociación</option>
                      <option value="AGREGACION">Agregación</option>
                      <option value="COMPOSICION">Composición</option>
                      <option value="HERENCIA">Generalización / Herencia</option>
                      <option value="REALIZACION">Realización</option>
                      <option value="DEPENDENCIA">Dependencia</option>
                    </select>
                  </div>

                  <div className="campo">
                    <label htmlFor="nombreRelacion">Nombre</label>

                    <input
                      id="nombreRelacion"
                      type="text"
                      value={nombreRelacion}
                      onChange={(evento) =>
                        setNombreRelacion(evento.target.value)
                      }
                      maxLength={100}
                      placeholder="Ej: contiene"
                    />
                  </div>
                </div>

                <div className="campos-relacion">
                  <div className="campo">
                    <label htmlFor="multiplicidadOrigenRelacion">
                      Multiplicidad origen
                    </label>

                    <input
                      id="multiplicidadOrigenRelacion"
                      type="text"
                      value={multiplicidadOrigenRelacion}
                      onChange={(evento) =>
                        setMultiplicidadOrigenRelacion(evento.target.value)
                      }
                      maxLength={20}
                      placeholder="Ej: 1"
                    />
                  </div>

                  <div className="campo">
                    <label htmlFor="multiplicidadDestinoRelacion">
                      Multiplicidad destino
                    </label>

                    <input
                      id="multiplicidadDestinoRelacion"
                      type="text"
                      value={multiplicidadDestinoRelacion}
                      onChange={(evento) =>
                        setMultiplicidadDestinoRelacion(evento.target.value)
                      }
                      maxLength={20}
                      placeholder="Ej: *"
                    />
                  </div>
                </div>

                {errorRelacion && (
                  <p className="error-formulario">{errorRelacion}</p>
                )}

                <div className="acciones-formulario">
                  <button
                    className="boton-cancelar"
                    type="button"
                    onClick={cancelarCreacionRelacion}
                    disabled={creandoRelacion}
                  >
                    Cancelar
                  </button>

                  <button
                    className="boton-guardar"
                    type="submit"
                    disabled={creandoRelacion}
                  >
                    {creandoRelacion
                      ? 'Guardando...'
                      : relacionEditandoId
                        ? 'Guardar cambios'
                        : 'Crear relación'}
                  </button>
                </div>
              </form>
            )}

            {modeloAbierto.relaciones.length > 0 && (
              <div className="panel-relaciones">
                <div className="encabezado-panel-relaciones">
                  <h3>Relaciones del modelo</h3>
                  <span>{modeloAbierto.relaciones.length}</span>
                </div>

                <div className="lista-relaciones">
                  {modeloAbierto.relaciones.map((relacion) => {
                    const claseOrigen = modeloAbierto.clases.find(
                      (clase) => clase.id === relacion.claseOrigenId,
                    )
                    const claseDestino = modeloAbierto.clases.find(
                      (clase) => clase.id === relacion.claseDestinoId,
                    )

                    return (
                      <div className="fila-relacion" key={relacion.id}>
                        <div className="info-relacion">
                          <strong>
                            {claseOrigen?.nombre ?? 'Clase origen'} →{' '}
                            {claseDestino?.nombre ?? 'Clase destino'}
                          </strong>

                          <span>
                            {relacion.nombre || 'Sin nombre'} · {relacion.tipo}
                          </span>

                          <span>
                            Multiplicidad: {relacion.multiplicidadOrigen ?? '-'} →{' '}
                            {relacion.multiplicidadDestino ?? '-'}
                          </span>

                          {relacion.claseAsociacionId && (
                            <span>
                              Clase de asociación:{' '}
                              {modeloAbierto.clases.find(
                                (clase) => clase.id === relacion.claseAsociacionId,
                              )?.nombre ?? 'No disponible'}
                            </span>
                          )}
                        </div>

                        <div className="acciones-relacion">
                          <button
                            className="boton-editar-relacion"
                            type="button"
                            onClick={() => editarRelacion(relacion)}
                            disabled={
                              creandoRelacion ||
                              creandoClase ||
                              creandoAtributo ||
                              guardandoModelo ||
                              eliminandoRelacionId !== null
                            }
                          >
                            Editar
                          </button>

                          <button
                            className="boton-eliminar-relacion"
                            type="button"
                            onClick={() => eliminarRelacion(relacion)}
                            disabled={
                              creandoRelacion ||
                              creandoClase ||
                              creandoAtributo ||
                              guardandoModelo ||
                              eliminandoRelacionId !== null
                            }
                          >
                            {eliminandoRelacionId === relacion.id
                              ? 'Eliminando...'
                              : 'Eliminar'}
                          </button>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            )}

            {mostrarFormularioClase && (
              <form className="formulario-clase" onSubmit={guardarClase}>
                <div className="campo">
                  <label htmlFor="nombreClase">Nombre de la clase</label>

                  <input
                    id="nombreClase"
                    type="text"
                    value={nombreClase}
                    onChange={(evento) => setNombreClase(evento.target.value)}
                    maxLength={100}
                    placeholder="Ej: Cliente"
                    autoFocus
                  />
                </div>

                {errorClase && (
                  <p className="error-formulario">{errorClase}</p>
                )}

                <div className="acciones-formulario">
                  <button
                    className="boton-cancelar"
                    type="button"
                    onClick={cancelarCreacionClase}
                    disabled={creandoClase}
                  >
                    Cancelar
                  </button>

                  <button
                    className="boton-guardar"
                    type="submit"
                    disabled={creandoClase}
                  >
                    {creandoClase
                      ? 'Guardando...'
                      : claseEditandoId
                        ? 'Guardar cambios'
                        : 'Crear clase'}
                  </button>
                </div>
              </form>
            )}

            <div className="panel-clase-seleccionada">
              {claseSeleccionada ? (
                <>
                  <div className="encabezado-clase-seleccionada">
                    <div>
                      <h3>Clase seleccionada: {claseSeleccionada.nombre}</h3>
                      <p>
                        Atributos: <strong>{claseSeleccionada.atributos.length}</strong>
                      </p>
                    </div>

                    <div className="acciones-clase">
                      <button
                        className="boton-editar-clase"
                        type="button"
                        onClick={() => editarClase(claseSeleccionada)}
                        disabled={
                          creandoClase ||
                          creandoRelacion ||
                          creandoAtributo ||
                          guardandoModelo ||
                          eliminandoClaseId !== null ||
                          eliminandoRelacionId !== null
                        }
                      >
                        Editar clase
                      </button>

                      <button
                        className="boton-eliminar-clase"
                        type="button"
                        onClick={() => eliminarClase(claseSeleccionada)}
                        disabled={
                          creandoClase ||
                          creandoRelacion ||
                          creandoAtributo ||
                          guardandoModelo ||
                          eliminandoClaseId !== null ||
                          eliminandoRelacionId !== null
                        }
                      >
                        {eliminandoClaseId === claseSeleccionada.id
                          ? 'Eliminando...'
                          : 'Eliminar clase'}
                      </button>

                      <button
                        className="boton-nuevo-atributo"
                        type="button"
                        onClick={() => {
                          setAtributoEditandoId(null)
                          setNombreAtributo('')
                          setTipoDatoAtributo('')
                          setPermiteNuloAtributo(false)
                          setIdentificadorAtributo(false)
                          setErrorAtributo('')
                          setMostrarFormularioAtributo(true)
                        }}
                        disabled={
                          creandoAtributo ||
                          creandoRelacion ||
                          creandoClase ||
                          guardandoModelo ||
                          eliminandoClaseId !== null ||
                          eliminandoRelacionId !== null
                        }
                      >
                        + Atributo
                      </button>
                    </div>
                  </div>

                  {claseSeleccionada.atributos.length > 0 && (
                    <div className="lista-atributos-panel">
                      {ordenarAtributosParaMostrar(
                        claseSeleccionada.atributos,
                      ).map((atributo) => (
                        <div className="fila-atributo" key={atributo.id}>
                          <div className="info-atributo">
                            <strong>{atributo.nombre}</strong>
                            <span>{atributo.tipoDato}</span>
                            {atributo.identificador && (
                              <span className="etiqueta-atributo">
                                Identificador
                              </span>
                            )}
                            {atributo.permiteNulo && (
                              <span className="etiqueta-atributo">
                                Permite nulo
                              </span>
                            )}
                          </div>

                          <div className="acciones-atributo">
                            <button
                              className="boton-editar-atributo"
                              type="button"
                              onClick={() => editarAtributo(atributo)}
                              disabled={
                                creandoAtributo ||
                                eliminandoAtributoId !== null
                              }
                            >
                              Editar
                            </button>

                            <button
                              className="boton-eliminar-atributo"
                              type="button"
                              onClick={() => eliminarAtributo(atributo)}
                              disabled={
                                creandoAtributo ||
                                eliminandoAtributoId !== null
                              }
                            >
                              {eliminandoAtributoId === atributo.id
                                ? 'Eliminando...'
                                : 'Eliminar'}
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}

                  {mostrarFormularioAtributo && (
                    <form
                      className="formulario-atributo"
                      onSubmit={guardarAtributo}
                    >
                      <div className="campo">
                        <label htmlFor="nombreAtributo">Nombre</label>

                        <input
                          id="nombreAtributo"
                          type="text"
                          value={nombreAtributo}
                          onChange={(evento) =>
                            setNombreAtributo(evento.target.value)
                          }
                          maxLength={100}
                          placeholder="Ej: correo"
                          autoFocus
                        />
                      </div>

                      <div className="campo">
                        <label htmlFor="tipoDatoAtributo">Tipo de dato</label>

                        <input
                          id="tipoDatoAtributo"
                          type="text"
                          value={tipoDatoAtributo}
                          onChange={(evento) =>
                            setTipoDatoAtributo(evento.target.value)
                          }
                          maxLength={50}
                          placeholder="Ej: String"
                        />
                      </div>

                      <div className="opciones-atributo">
                        <label className="opcion-checkbox">
                          <input
                            type="checkbox"
                            checked={permiteNuloAtributo}
                            onChange={(evento) =>
                              setPermiteNuloAtributo(evento.target.checked)
                            }
                          />
                          Permite nulo
                        </label>

                        <label className="opcion-checkbox">
                          <input
                            type="checkbox"
                            checked={identificadorAtributo}
                            onChange={(evento) =>
                              setIdentificadorAtributo(evento.target.checked)
                            }
                          />
                          Identificador
                        </label>
                      </div>

                      {errorAtributo && (
                        <p className="error-formulario">{errorAtributo}</p>
                      )}

                      <div className="acciones-formulario">
                        <button
                          className="boton-cancelar"
                          type="button"
                          onClick={cancelarCreacionAtributo}
                          disabled={creandoAtributo}
                        >
                          Cancelar
                        </button>

                        <button
                          className="boton-guardar"
                          type="submit"
                          disabled={creandoAtributo}
                        >
                          {creandoAtributo
                            ? 'Guardando...'
                            : atributoEditandoId
                              ? 'Guardar cambios'
                              : 'Crear atributo'}
                        </button>
                      </div>
                    </form>
                  )}
                </>
              ) : (
                <p className="ayuda-seleccion">
                  Haz clic sobre una clase del lienzo para seleccionarla y
                  administrar sus atributos.
                </p>
              )}
            </div>

            {guardandoModelo && <p>Guardando posición...</p>}

            {errorGuardado && (
              <p className="error-formulario">{errorGuardado}</p>
            )}
          </section>

          <section className="lienzo-uml">
            {nodos.length === 0 ? (
              <div className="mensaje modelo-vacio">
                <p>Este proyecto todavía no tiene clases UML.</p>
              </div>
            ) : (
              <ReactFlow
                nodes={nodos}
                edges={aristas}
                edgeTypes={tiposArista}
                onNodesChange={onNodesChange}
                onEdgeClick={(_, arista) => {
                  const relacion = modeloAbierto.relaciones.find(
                    (relacionActual) => relacionActual.id === arista.id,
                  )

                  if (relacion) {
                    editarRelacion(relacion)
                  }
                }}
                onNodeClick={(_, nodo) => {
                  setClaseSeleccionadaId(nodo.id)
                  setMostrarFormularioRelacion(false)
                  setRelacionEditandoId(null)
                  setErrorRelacion('')
                  setMostrarFormularioAtributo(false)
                  setAtributoEditandoId(null)
                  setErrorAtributo('')
                  setMostrarFormularioClase(false)
                  setClaseEditandoId(null)
                  setNombreClase('')
                  setErrorClase('')
                }}
                onNodeDragStop={(_, nodo) => guardarPosicionNodo(nodo)}
                fitView
                nodesDraggable={
                  !guardandoModelo &&
                  !creandoClase &&
                  !creandoRelacion &&
                  !creandoAtributo &&
                  eliminandoAtributoId === null &&
                  eliminandoClaseId === null &&
                  eliminandoRelacionId === null
                }
                nodesConnectable={false}
              >
                <Background gap={20} size={1} />
                <MiniMap />
                <Controls />
              </ReactFlow>
            )}
          </section>
        </main>
      </div>
    )
  }

  return (
    <div className="app">
      <header className="encabezado">
        <div>
          <h1>CollabCASE AI</h1>
          <p>Herramienta CASE colaborativa para modelado UML</p>
          <p
            style={{
              margin: '6px 0 0',
              fontWeight: 700,
              color: online ? '#15803d' : '#b91c1c',
            }}
          >
            {online
              ? '🟢 Internet disponible'
              : '🔴 Sin conexión · trabajando localmente'}
          </p>
        </div>
      </header>

      <main className="contenido">
        <section className="titulo-seccion">
          <div>
            <h2>Mis proyectos</h2>
            <p>Selecciona un proyecto para continuar con el modelado.</p>
          </div>

          <button
            className="boton-nuevo"
            type="button"
            onClick={() => setMostrarFormulario(true)}
            disabled={!online}
            title={!online ? 'Crear proyectos requiere conexión.' : undefined}
          >
            + Nuevo proyecto
          </button>
        </section>

        {proyectosDesdeOffline && (
          <p
            style={{
              margin: '0 0 16px',
              padding: '10px 12px',
              borderRadius: 8,
              background: '#f6f8fb',
              color: '#526071',
              fontWeight: 600,
            }}
          >
            💾 Mostrando proyectos guardados en este dispositivo.
          </p>
        )}

        <form
          className="formulario-unirse-sesion"
          onSubmit={unirseSesionColaborativa}
        >
          <div>
            <h3>Unirse a una sesión colaborativa</h3>
            <p>Ingresa el código compartido por el responsable de la sesión.</p>
          </div>

          <div className="fila-unirse-sesion">
            <input
              type="text"
              value={codigoSesion}
              onChange={(evento) =>
                setCodigoSesion(evento.target.value.toUpperCase())
              }
              maxLength={8}
              placeholder="Ej: 6AA03CE2"
            />

            <button
              className="boton-unirse-sesion"
              type="submit"
              disabled={procesandoSesion || !online}
              title={!online ? 'Unirse a una sesión requiere conexión.' : undefined}
            >
              {procesandoSesion ? 'Uniéndose...' : 'Unirse'}
            </button>
          </div>

          {errorColaboracion && (
            <p className="error-formulario">{errorColaboracion}</p>
          )}
        </form>

        {mostrarFormulario && (
          <form className="formulario-proyecto" onSubmit={crearProyecto}>
            <h3>Nuevo proyecto</h3>

            <div className="campo">
              <label htmlFor="nombre">Nombre</label>

              <input
                id="nombre"
                type="text"
                value={nombre}
                onChange={(evento) => setNombre(evento.target.value)}
                maxLength={120}
                placeholder="Ej: Sistema de biblioteca"
                autoFocus
              />
            </div>

            <div className="campo">
              <label htmlFor="descripcion">Descripción</label>

              <textarea
                id="descripcion"
                value={descripcion}
                onChange={(evento) => setDescripcion(evento.target.value)}
                maxLength={500}
                placeholder="Describe brevemente el proyecto"
                rows={4}
              />
            </div>

            {errorCreacion && (
              <p className="error-formulario">{errorCreacion}</p>
            )}

            <div className="acciones-formulario">
              <button
                className="boton-cancelar"
                type="button"
                onClick={cancelarCreacion}
                disabled={guardando}
              >
                Cancelar
              </button>

              <button
                className="boton-guardar"
                type="submit"
                disabled={guardando}
              >
                {guardando ? 'Creando...' : 'Crear proyecto'}
              </button>
            </div>
          </form>
        )}

        {errorAbrir && (
          <div className="mensaje error">
            <p>{errorAbrir}</p>
          </div>
        )}

        {cargando && (
          <div className="mensaje">
            <p>Cargando proyectos...</p>
          </div>
        )}

        {error && (
          <div className="mensaje error">
            <p>{error}</p>
          </div>
        )}

        {!cargando && !error && proyectos.length === 0 && (
          <div className="mensaje">
            <p>No hay proyectos registrados.</p>
          </div>
        )}

        {errorEliminacionProyecto && (
          <div className="mensaje error">
            <p>{errorEliminacionProyecto}</p>
          </div>
        )}

        {!cargando && !error && proyectos.length > 0 && (
          <section className="lista-proyectos">
            {proyectos.map((proyecto) => (
              <article className="tarjeta-proyecto" key={proyecto.id}>
                <div>
                  <h3>{proyecto.nombre}</h3>
                  <p>
                    {proyecto.descripcion || 'Proyecto sin descripción'}
                  </p>
                </div>

                <div
                  style={{
                    display: 'flex',
                    gap: 10,
                    alignItems: 'center',
                    flexWrap: 'wrap',
                  }}
                >
                  <button
                    className="boton-abrir"
                    type="button"
                    onClick={() => abrirProyecto(proyecto)}
                    disabled={
                      abriendoId === proyecto.id ||
                      eliminandoProyectoId !== null
                    }
                  >
                    {abriendoId === proyecto.id ? 'Abriendo...' : 'Abrir'}
                  </button>

                  <button
                    type="button"
                    onClick={() => void eliminarProyecto(proyecto)}
                    disabled={
                      !online ||
                      eliminandoProyectoId !== null ||
                      abriendoId !== null
                    }
                    style={{
                      background: '#b91c1c',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: 8,
                      padding: '10px 14px',
                      fontWeight: 700,
                      cursor:
                        !online ||
                          eliminandoProyectoId !== null ||
                          abriendoId !== null
                          ? 'not-allowed'
                          : 'pointer',
                    }}
                    title={
                      !online
                        ? 'Eliminar proyectos requiere conexión.'
                        : 'Eliminar proyecto permanentemente'
                    }
                  >
                    {eliminandoProyectoId === proyecto.id
                      ? 'Eliminando...'
                      : 'Eliminar'}
                  </button>
                </div>
              </article>
            ))}
          </section>
        )}
      </main>
    </div>
  )
}

export default App