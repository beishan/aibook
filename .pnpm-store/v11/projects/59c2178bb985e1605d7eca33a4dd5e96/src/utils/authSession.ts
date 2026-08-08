export const AUTH_EXPIRED_EVENT = 'aibook:auth-expired'

export const clearStoredAuthSession = () => {
  localStorage.removeItem('token')
  window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT))
}
