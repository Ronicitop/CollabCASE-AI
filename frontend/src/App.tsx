import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import {
  Background,
  Controls,
  MarkerType,
  MiniMap,
  ReactFlow,
  useNodesState,
  type Edge,
  type Node,
} from '@xyflow/react'

import '@xyflow/react/dist/style.css'
import './App.css'

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
}

type ModeloCompleto = {
  id: string
  proyectoId: string
  version: number
  actualizadoEn: string
  clases: ClaseDiagrama[]
  relaciones: RelacionDiagrama[]
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
              clase.atributos.map((atributo) => (
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
  return modelo.relaciones.map((relacion) => ({
    id: relacion.id,
    source: relacion.claseOrigenId,
    target: relacion.claseDestinoId,
    label: relacion.nombre ?? relacion.tipo,
    markerEnd: {
      type: MarkerType.ArrowClosed,
    },
  }))
}

function App() {
  const [proyectos, setProyectos] = useState<Proyecto[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

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
    fetch('http://localhost:8080/api/proyectos')
      .then((respuesta) => {
        if (!respuesta.ok) {
          throw new Error('No se pudieron cargar los proyectos')
        }

        return respuesta.json()
      })
      .then((datos) => {
        setProyectos(datos)
      })
      .catch((error) => {
        setError(error.message)
      })
      .finally(() => {
        setCargando(false)
      })
  }, [])

  useEffect(() => {
    if (modeloAbierto) {
      setNodos(construirNodos(modeloAbierto))
    } else {
      setNodos([])
    }
  }, [modeloAbierto, setNodos])

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
        'http://localhost:8080/api/proyectos',
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

  const obtenerModeloCompleto = async (
    proyectoId: string,
  ): Promise<ModeloCompleto> => {
    let respuesta = await fetch(
      `http://localhost:8080/api/modelos-diagrama/proyecto/${proyectoId}/completo`,
    )

    if (respuesta.status === 404) {
      const respuestaCreacion = await fetch(
        `http://localhost:8080/api/modelos-diagrama/proyecto/${proyectoId}`,
        {
          method: 'POST',
        },
      )

      if (!respuestaCreacion.ok) {
        throw new Error('No se pudo crear el modelo UML del proyecto')
      }

      respuesta = await fetch(
        `http://localhost:8080/api/modelos-diagrama/proyecto/${proyectoId}/completo`,
      )
    }

    if (!respuesta.ok) {
      throw new Error('No se pudo cargar el modelo UML')
    }

    return respuesta.json()
  }

  const abrirProyecto = async (proyecto: Proyecto) => {
    try {
      setAbriendoId(proyecto.id)
      setErrorAbrir('')

      const modelo = await obtenerModeloCompleto(proyecto.id)

      setProyectoAbierto(proyecto)
      setModeloAbierto(modelo)
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

  const guardarPosicionNodo = async (nodoMovido: Node) => {
    if (!proyectoAbierto || !modeloAbierto) {
      return
    }

    try {
      setGuardandoModelo(true)
      setErrorGuardado('')

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
        })),
      }

      const respuesta = await fetch(
        `http://localhost:8080/api/modelos-diagrama/proyecto/${proyectoAbierto.id}/completo`,
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
          },
          body: JSON.stringify(solicitud),
        },
      )

      if (!respuesta.ok) {
        let mensaje = 'No se pudo guardar la nueva posición de la clase'

        try {
          const detalle = await respuesta.json()

          if (detalle?.mensaje) {
            mensaje = detalle.mensaje
          }
        } catch {
          // Si la respuesta no contiene JSON, conservamos el mensaje general.
        }

        throw new Error(mensaje)
      }

      const modeloActualizado: ModeloCompleto = await respuesta.json()

      setModeloAbierto(modeloActualizado)
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
        })),
      }

      const respuesta = await fetch(
        `http://localhost:8080/api/modelos-diagrama/proyecto/${proyectoAbierto.id}/completo`,
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
          },
          body: JSON.stringify(solicitud),
        },
      )

      if (!respuesta.ok) {
        let mensaje =
          claseEditandoId === null
            ? 'No se pudo crear la clase UML'
            : 'No se pudo actualizar la clase UML'

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

      const modeloActualizado: ModeloCompleto = await respuesta.json()

      setModeloAbierto(modeloActualizado)
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
        relacion.claseDestinoId === clase.id,
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
              relacion.claseDestinoId !== clase.id,
          )
          .map((relacion) => ({
            id: relacion.id,
            claseOrigenClave: relacion.claseOrigenId,
            claseDestinoClave: relacion.claseDestinoId,
            tipo: relacion.tipo,
            multiplicidadOrigen: relacion.multiplicidadOrigen,
            multiplicidadDestino: relacion.multiplicidadDestino,
            nombre: relacion.nombre,
          })),
      }

      const respuesta = await fetch(
        `http://localhost:8080/api/modelos-diagrama/proyecto/${proyectoAbierto.id}/completo`,
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
          },
          body: JSON.stringify(solicitud),
        },
      )

      if (!respuesta.ok) {
        let mensaje = 'No se pudo eliminar la clase UML'

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

      const modeloActualizado: ModeloCompleto = await respuesta.json()

      setModeloAbierto(modeloActualizado)

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
        })),
      }

      const respuesta = await fetch(
        `http://localhost:8080/api/modelos-diagrama/proyecto/${proyectoAbierto.id}/completo`,
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
          },
          body: JSON.stringify(solicitud),
        },
      )

      if (!respuesta.ok) {
        let mensaje =
          atributoEditandoId === null
            ? 'No se pudo crear el atributo UML'
            : 'No se pudo actualizar el atributo UML'

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

      const modeloActualizado: ModeloCompleto = await respuesta.json()

      setModeloAbierto(modeloActualizado)
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
        })),
      }

      const respuesta = await fetch(
        `http://localhost:8080/api/modelos-diagrama/proyecto/${proyectoAbierto.id}/completo`,
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
          },
          body: JSON.stringify(solicitud),
        },
      )

      if (!respuesta.ok) {
        let mensaje = 'No se pudo eliminar el atributo UML'

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

      const modeloActualizado: ModeloCompleto = await respuesta.json()

      setModeloAbierto(modeloActualizado)

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
                },
              ]
            : relacionesExistentes,
      }

      const respuesta = await fetch(
        `http://localhost:8080/api/modelos-diagrama/proyecto/${proyectoAbierto.id}/completo`,
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
          },
          body: JSON.stringify(solicitud),
        },
      )

      if (!respuesta.ok) {
        let mensaje =
          relacionEditandoId === null
            ? 'No se pudo crear la relación UML'
            : 'No se pudo actualizar la relación UML'

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

      const modeloActualizado: ModeloCompleto = await respuesta.json()

      setModeloAbierto(modeloActualizado)
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
          })),
      }

      const respuesta = await fetch(
        `http://localhost:8080/api/modelos-diagrama/proyecto/${proyectoAbierto.id}/completo`,
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
          },
          body: JSON.stringify(solicitud),
        },
      )

      if (!respuesta.ok) {
        let mensaje = 'No se pudo eliminar la relación UML'

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

      const modeloActualizado: ModeloCompleto = await respuesta.json()
      setModeloAbierto(modeloActualizado)

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
    setProyectoAbierto(null)
    setModeloAbierto(null)
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

                    <input
                      id="tipoRelacion"
                      type="text"
                      value={tipoRelacion}
                      onChange={(evento) =>
                        setTipoRelacion(evento.target.value)
                      }
                      maxLength={30}
                      placeholder="Ej: ASOCIACION"
                    />
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
                      {claseSeleccionada.atributos.map((atributo) => (
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
          >
            + Nuevo proyecto
          </button>
        </section>

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

                <button
                  className="boton-abrir"
                  type="button"
                  onClick={() => abrirProyecto(proyecto)}
                  disabled={abriendoId === proyecto.id}
                >
                  {abriendoId === proyecto.id ? 'Abriendo...' : 'Abrir'}
                </button>
              </article>
            ))}
          </section>
        )}
      </main>
    </div>
  )
}

export default App