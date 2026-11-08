const quitarBarraFinal = (valor: string) => valor.replace(/\/+$/, '')

export const API_URL = quitarBarraFinal(
  import.meta.env.VITE_API_URL || 'http://192.168.0.9:8080',
)

export const WS_URL = quitarBarraFinal(
  import.meta.env.VITE_WS_URL || 'ws://192.168.0.9:8080',
)