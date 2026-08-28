import { ref } from 'vue'
import api from '@/utils/api'

const ALL_RANDOM_COVERS_HIDDEN_STORAGE_KEY = 'aibook-random-cover-library-all-hidden'
const RANDOM_COVER_OVERRIDES_STORAGE_KEY = 'aibook-random-cover-library-visibility-overrides'
const LEGACY_MIGRATION_STORAGE_KEY = 'aibook-random-cover-privacy-database-migrated'

type RandomCoverOverrides = Record<string, boolean>

const readStoredBoolean = (storageKey: string) => {
  try {
    return localStorage.getItem(storageKey) === 'true'
  } catch {
    return false
  }
}

const readOverrides = (): RandomCoverOverrides => {
  try {
    const storedValue = localStorage.getItem(RANDOM_COVER_OVERRIDES_STORAGE_KEY)
    if (!storedValue) return {}
    const parsedValue = JSON.parse(storedValue)
    if (!parsedValue || typeof parsedValue !== 'object' || Array.isArray(parsedValue)) return {}

    return Object.fromEntries(
      Object.entries(parsedValue).filter(([, hidden]) => typeof hidden === 'boolean'),
    )
  } catch {
    return {}
  }
}

interface CoverPrivacyScope {
  initialized: boolean
  allHidden: boolean
  overrides: Record<string, boolean>
}

const persistLocal = (storageKey: string, value: string) => {
  try {
    localStorage.setItem(storageKey, value)
  } catch {
    // 浏览器禁止本地存储时仍可在当前会话中切换封面可见性。
  }
}

export const allRandomCoversHidden = ref(
  readStoredBoolean(ALL_RANDOM_COVERS_HIDDEN_STORAGE_KEY),
)
const randomCoverOverrides = ref<RandomCoverOverrides>(readOverrides())
let saveQueue: Promise<unknown> = Promise.resolve()

const normalizeOverrides = (value: unknown): RandomCoverOverrides => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  return Object.fromEntries(
    Object.entries(value).filter(([id, hidden]) => /^\d+$/.test(id) && typeof hidden === 'boolean'),
  )
}

const applyRandomCoverSettings = (allHidden: boolean, overrides: RandomCoverOverrides) => {
  allRandomCoversHidden.value = allHidden
  randomCoverOverrides.value = overrides
  persistLocal(ALL_RANDOM_COVERS_HIDDEN_STORAGE_KEY, String(allHidden))
  persistLocal(RANDOM_COVER_OVERRIDES_STORAGE_KEY, JSON.stringify(overrides))
}

const persistRemote = () => {
  const activeToken = localStorage.getItem('token')
  if (!activeToken) return
  const payload = {
    initialized: true,
    allHidden: allRandomCoversHidden.value,
    overrides: { ...randomCoverOverrides.value },
  }
  saveQueue = saveQueue
    .catch(() => undefined)
    .then(() => localStorage.getItem('token') === activeToken
      ? api.put('/api/cover-privacy/random-covers', payload)
      : undefined)
    .catch(error => console.error('Failed to persist random cover privacy:', error))
}

export const isRandomCoverHidden = (coverId: number) =>
  randomCoverOverrides.value[String(coverId)] ?? allRandomCoversHidden.value

export const toggleAllRandomCovers = () => {
  allRandomCoversHidden.value = !allRandomCoversHidden.value
  randomCoverOverrides.value = {}
  persistLocal(ALL_RANDOM_COVERS_HIDDEN_STORAGE_KEY, String(allRandomCoversHidden.value))
  persistLocal(RANDOM_COVER_OVERRIDES_STORAGE_KEY, '{}')
  persistRemote()
}

export const toggleRandomCover = (coverId: number) => {
  const nextHidden = !isRandomCoverHidden(coverId)
  const nextOverrides = { ...randomCoverOverrides.value }

  if (nextHidden === allRandomCoversHidden.value) {
    delete nextOverrides[String(coverId)]
  } else {
    nextOverrides[String(coverId)] = nextHidden
  }

  randomCoverOverrides.value = nextOverrides
  persistLocal(RANDOM_COVER_OVERRIDES_STORAGE_KEY, JSON.stringify(nextOverrides))
  persistRemote()
}

export const removeRandomCoverOverride = (coverId: number) => {
  if (!(String(coverId) in randomCoverOverrides.value)) return
  const nextOverrides = { ...randomCoverOverrides.value }
  delete nextOverrides[String(coverId)]
  randomCoverOverrides.value = nextOverrides
  persistLocal(RANDOM_COVER_OVERRIDES_STORAGE_KEY, JSON.stringify(nextOverrides))
  persistRemote()
}

export const hydrateRandomCoverPrivacy = async (userId: number) => {
  if (!localStorage.getItem('token')) return
  try {
    const { data } = await api.get<CoverPrivacyScope>('/api/cover-privacy/random-covers', {
      headers: { 'X-Suppress-Error-Toast': 'true' },
    })
    if (data.initialized) {
      applyRandomCoverSettings(Boolean(data.allHidden), normalizeOverrides(data.overrides))
    } else {
      const legacyAlreadyMigrated = localStorage.getItem(LEGACY_MIGRATION_STORAGE_KEY) !== null
      const allHidden = legacyAlreadyMigrated ? false : allRandomCoversHidden.value
      const overrides = legacyAlreadyMigrated ? {} : randomCoverOverrides.value
      applyRandomCoverSettings(allHidden, overrides)
      await api.put('/api/cover-privacy/random-covers', {
        initialized: true,
        allHidden,
        overrides,
      })
    }
    persistLocal(LEGACY_MIGRATION_STORAGE_KEY, String(userId))
  } catch (error) {
    console.error('Failed to hydrate random cover privacy:', error)
  }
}

export const resetRandomCoverPrivacy = () => {
  allRandomCoversHidden.value = false
  randomCoverOverrides.value = {}
  saveQueue = Promise.resolve()
}
