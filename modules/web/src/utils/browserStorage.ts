const getBrowserStorage = (name: 'localStorage' | 'sessionStorage') => {
  if (typeof window === 'undefined') return undefined
  try {
    return window[name]
  } catch {
    return undefined
  }
}

const getStorageItem = (
  name: 'localStorage' | 'sessionStorage',
  key: string,
) => {
  try {
    return getBrowserStorage(name)?.getItem(key) ?? null
  } catch {
    return null
  }
}

const setStorageItem = (
  name: 'localStorage' | 'sessionStorage',
  key: string,
  value: string,
) => {
  try {
    getBrowserStorage(name)?.setItem(key, value)
  } catch {
    // Storage can throw in private mode or when disabled; state still lives in Pinia.
  }
}

const removeStorageItem = (
  name: 'localStorage' | 'sessionStorage',
  key: string,
) => {
  try {
    getBrowserStorage(name)?.removeItem(key)
  } catch {
    // Ignore unavailable browser storage.
  }
}

export const getLocalStorageItem = (key: string) =>
  getStorageItem('localStorage', key)
export const setLocalStorageItem = (key: string, value: string) =>
  setStorageItem('localStorage', key, value)
export const removeLocalStorageItem = (key: string) =>
  removeStorageItem('localStorage', key)

export const getSessionStorageItem = (key: string) =>
  getStorageItem('sessionStorage', key)
export const setSessionStorageItem = (key: string, value: string) =>
  setStorageItem('sessionStorage', key, value)
export const removeSessionStorageItem = (key: string) =>
  removeStorageItem('sessionStorage', key)
