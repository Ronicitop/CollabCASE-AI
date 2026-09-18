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

  const crearClase = async (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()

    if (!proyectoAbierto || !modeloAbierto) {
      return
    }

    const nombreLimpio = nombreClase.trim()

    if (!nombreLimpio) {
      setErrorClase('El nombre de la clase es obligatorio.')
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

      const solicitud = {
        clases: [
          ...modeloAbierto.clases.map((clase) => {
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
          {
            id: null,
            claveCliente: claveNuevaClase,
            nombre: nombreLimpio,
            posicionX: 80 + columna * 280,
            posicionY: 80 + fila * 180,
            atributos: [],
          },
        ],

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
        let mensaje = 'No se pudo crear la clase UML'

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
      setMostrarFormularioClase(false)
    } catch (error) {
      if (error instanceof Error) {
        setErrorClase(error.message)
      } else {
        setErrorClase('Ocurrió un error al crear la clase UML')
      }
    } finally {
      setCreandoClase(false)
    }
  }

  const cancelarCreacionClase = () => {
    setNombreClase('')
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

  const cerrarProyecto = () => {
    setProyectoAbierto(null)
    setModeloAbierto(null)
    setErrorAbrir('')
    setErrorGuardado('')
    setMostrarFormularioClase(false)
    setNombreClase('')
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

              <button
                className="boton-nueva-clase"
                type="button"
                onClick={() => {
                  setErrorClase('')
                  setMostrarFormularioClase(true)
                }}
                disabled={creandoClase || guardandoModelo}
              >
                + Nueva clase
              </button>
            </div>

            {mostrarFormularioClase && (
              <form className="formulario-clase" onSubmit={crearClase}>
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
                    {creandoClase ? 'Creando...' : 'Crear clase'}
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
                        creandoAtributo || creandoClase || guardandoModelo
                      }
                    >
                      + Atributo
                    </button>
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
                onNodeClick={(_, nodo) => {
                  setClaseSeleccionadaId(nodo.id)
                  setMostrarFormularioAtributo(false)
                  setErrorAtributo('')
                }}
                onNodeDragStop={(_, nodo) => guardarPosicionNodo(nodo)}
                fitView
                nodesDraggable={
                  !guardandoModelo &&
                  !creandoClase &&
                  !creandoAtributo &&
                  eliminandoAtributoId === null
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