import { useEffect, useState } from 'react'

export function useOnlineStatus(): boolean {
  const [online, setOnline] = useState<boolean>(
    navigator.onLine,
  )

  useEffect(() => {
    const marcarOnline = () => {
      setOnline(true)
    }

    const marcarOffline = () => {
      setOnline(false)
    }

    window.addEventListener('online', marcarOnline)
    window.addEventListener('offline', marcarOffline)

    return () => {
      window.removeEventListener('online', marcarOnline)
      window.removeEventListener('offline', marcarOffline)
    }
  }, [])

  return online
}