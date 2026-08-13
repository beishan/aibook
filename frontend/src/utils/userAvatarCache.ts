const DATABASE_NAME = 'aibook-avatar-cache'
const DATABASE_VERSION = 1
const STORE_NAME = 'avatars'

export interface CachedUserAvatar {
  userId: number
  version: string
  blob: Blob
}

const openDatabase = () => new Promise<IDBDatabase>((resolve, reject) => {
  const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION)
  request.onupgradeneeded = () => {
    if (!request.result.objectStoreNames.contains(STORE_NAME)) {
      request.result.createObjectStore(STORE_NAME, { keyPath: 'userId' })
    }
  }
  request.onsuccess = () => resolve(request.result)
  request.onerror = () => reject(request.error)
})

const requestResult = <T>(request: IDBRequest<T>) => new Promise<T>((resolve, reject) => {
  request.onsuccess = () => resolve(request.result)
  request.onerror = () => reject(request.error)
})

export async function readCachedUserAvatar(userId: number) {
  const database = await openDatabase()
  try {
    const store = database.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME)
    return await requestResult<CachedUserAvatar | undefined>(store.get(userId))
  } finally {
    database.close()
  }
}

export async function writeCachedUserAvatar(userId: number, version: string, blob: Blob) {
  const database = await openDatabase()
  try {
    const store = database.transaction(STORE_NAME, 'readwrite').objectStore(STORE_NAME)
    await requestResult(store.put({ userId, version, blob } satisfies CachedUserAvatar))
  } finally {
    database.close()
  }
}

export async function deleteCachedUserAvatar(userId: number) {
  const database = await openDatabase()
  try {
    const store = database.transaction(STORE_NAME, 'readwrite').objectStore(STORE_NAME)
    await requestResult(store.delete(userId))
  } finally {
    database.close()
  }
}
