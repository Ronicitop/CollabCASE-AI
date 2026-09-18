import { useEffect, useState } from 'react'

type Proyecto = {
  id: string
  nombre: string
  descripcion: string
  creadoEn: string
  actualizadoEn: string
}

function App() {
  const [proyectos, setProyectos] = useState<Proyecto[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

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

  return (
    <div>
      <h1>CollabCASE AI</h1>

      {cargando && <p>Cargando proyectos...</p>}

      {error && <p>{error}</p>}

      {!cargando && !error && proyectos.length === 0 && (
        <p>No hay proyectos registrados.</p>
      )}

      {!cargando && !error && proyectos.length > 0 && (
        <div>
          <h2>Proyectos</h2>

          {proyectos.map((proyecto) => (
            <div key={proyecto.id}>
              <h3>{proyecto.nombre}</h3>
              <p>{proyecto.descripcion}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default App