import type { CustomDockIconName } from '@/stores/dockIcons'

const DATABASE_NAME = 'aibook-ui-cache'
const DATABASE_VERSION = 1
const STORE_NAME = 'dock-icons'

export interface CachedDockIcon {
  id: string
  userId: number
  name: CustomDockIconName
  version: number
  blob: Blob
}

const cacheId = (userId: number, name: CustomDockIconName) => `${userId}:${name}`

const openDatabase = () => new Promise<IDBDatabase>((resolve, reject) => {
  const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION)
  request.onupgradeneeded = () => {
    if (!request.result.objectStoreNames.contains(STORE_NAME)) {
      request.result.createObjectStore(STORE_NAME, { keyPath: 'id' })
    }
  }
  request.onsuccess = () => resolve(request.result)
  request.onerror = () => reject(request.error)
})

const requestResult = <T>(request: IDBRequest<T>) => new Promise<T>((resolve, reject) => {
  request.onsuccess = () => resolve(request.result)
  request.onerror = () => reject(request.error)
})

export async function readCachedDockIcons(
  userId: number,
  names: CustomDockIconName[],
): Promise<CachedDockIcon[]> {
  const database = await openDatabase()
  try {
    const store = database.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME)
    const records = await Promise.all(names.map(name =>
      requestResult<CachedDockIcon | undefined>(store.get(cacheId(userId, name))),
    ))
    return records.filter((record): record is CachedDockIcon => Boolean(record?.blob))
  } finally {
    database.close()
  }
}

export async function writeCachedDockIcon(
  userId: number,
  name: CustomDockIconName,
  version: number,
  blob: Blob,
) {
  const database = await openDatabase()
  try {
    const transaction = database.transaction(STORE_NAME, 'readwrite')
    await requestResult(transaction.objectStore(STORE_NAME).put({
      id: cacheId(userId, name),
      userId,
      name,
      version,
      blob,
    } satisfies CachedDockIcon))
  } finally {
    database.close()
  }
}

export async function deleteCachedDockIcon(userId: number, name: CustomDockIconName) {
  const database = await openDatabase()
  try {
    const transaction = database.transaction(STORE_NAME, 'readwrite')
    await requestResult(transaction.objectStore(STORE_NAME).delete(cacheId(userId, name)))
  } finally {
    database.close()
  }
}
