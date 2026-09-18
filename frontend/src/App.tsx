import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
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

  const cerrarProyecto = () => {
    setProyectoAbierto(null)
    setModeloAbierto(null)
    setErrorAbrir('')
  }

  if (proyectoAbierto && modeloAbierto) {
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
          </section>

          <section className="lista-clases">
            {modeloAbierto.clases.length === 0 ? (
              <div className="mensaje">
                <p>Este proyecto todavía no tiene clases UML.</p>
              </div>
            ) : (
              modeloAbierto.clases.map((clase) => (
                <article className="tarjeta-clase" key={clase.id}>
                  <h3>{clase.nombre}</h3>

                  {clase.atributos.length === 0 ? (
                    <p>Sin atributos</p>
                  ) : (
                    <ul>
                      {clase.atributos.map((atributo) => (
                        <li key={atributo.id}>
                          {atributo.nombre}: {atributo.tipoDato}
                        </li>
                      ))}
                    </ul>
                  )}
                </article>
              ))
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