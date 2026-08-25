import { ref } from 'vue'

const ALL_RANDOM_COVERS_HIDDEN_STORAGE_KEY = 'aibook-random-cover-library-all-hidden'
const RANDOM_COVER_OVERRIDES_STORAGE_KEY = 'aibook-random-cover-library-visibility-overrides'

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

const persist = (storageKey: string, value: string) => {
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

export const isRandomCoverHidden = (coverId: number) =>
  randomCoverOverrides.value[String(coverId)] ?? allRandomCoversHidden.value

export const toggleAllRandomCovers = () => {
  allRandomCoversHidden.value = !allRandomCoversHidden.value
  randomCoverOverrides.value = {}
  persist(ALL_RANDOM_COVERS_HIDDEN_STORAGE_KEY, String(allRandomCoversHidden.value))
  persist(RANDOM_COVER_OVERRIDES_STORAGE_KEY, '{}')
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
  persist(RANDOM_COVER_OVERRIDES_STORAGE_KEY, JSON.stringify(nextOverrides))
}

export const removeRandomCoverOverride = (coverId: number) => {
  if (!(String(coverId) in randomCoverOverrides.value)) return
  const nextOverrides = { ...randomCoverOverrides.value }
  delete nextOverrides[String(coverId)]
  randomCoverOverrides.value = nextOverrides
  persist(RANDOM_COVER_OVERRIDES_STORAGE_KEY, JSON.stringify(nextOverrides))
}
