// Mantener esta ruta permite actualizar también las instalaciones PWA anteriores.
if ('serviceWorker' in navigator && window.isSecureContext) {
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('/sw.js', { scope: '/', updateViaCache: 'none' })
      .then(registration => registration.update())
      .catch(error => console.warn('No se pudo actualizar el modo sin conexión:', error))
  })
}
