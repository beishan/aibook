import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import api from '@/utils/api'
import { audioApi, contentPosition, type Audiobook, type AudioSettings } from '@/utils/audiobook'
import { AUTH_EXPIRED_EVENT } from '@/utils/authSession'

/** The media element belongs to the application, never to a route component. */
export const useAudioPlayerStore = defineStore('audioPlayer', () => {
  const book = ref<Audiobook | null>(null)
  const trackId = ref(0)
  const positionMs = ref(0)
  const playing = ref(false)
  const buffering = ref(false)
  const error = ref('')
  const busy = ref(false)
  const sleepDeadline = ref<number | null>(null)
  const sleepAtChapterEnd = ref(false)
  const sleepRemaining = ref(0)
  const settings = ref<AudioSettings>({
    speed: 1,
    backSeconds: 15,
    forwardSeconds: 30,
    sleepMinutes: 30,
  })
  let element: HTMLAudioElement | null = null
  let session: {
    sessionId: string
    key: string
    contentRevision: number
  } | null = null
  let revision = 0
  let sequence = 0
  let elapsedMs = 0
  let lastTick = 0
  let timer: ReturnType<typeof setInterval> | null = null
  let syncChain: Promise<unknown> = Promise.resolve()
  let heartbeatTicks = 0
  let generation = 0
  let accountToken = ''
  let pendingPosition = 0
  let finished = false
  let sleepStopPending = false
  const track = computed(() => book.value?.tracks.find((item) => item.id === trackId.value))
  const totalPosition = computed(() =>
    book.value ? contentPosition(book.value, trackId.value, positionMs.value) : 0
  )
  const percent = computed(() =>
    book.value?.durationMs
      ? Math.min(100, Math.floor((totalPosition.value / book.value.durationMs) * 100))
      : 0
  )
  const chapter = computed(() =>
    book.value?.chapters.find((item) =>
      item.segments.some(
        (segment) =>
          segment.trackId === trackId.value &&
          positionMs.value >= segment.startMs &&
          positionMs.value < segment.endMs
      )
    )
  )
  let sleepChapterId: number | null = null

  function fail(message: string) {
    error.value = message
    element?.pause()
  }
  function ensureElement() {
    if (element) return element
    element = new Audio()
    element.preload = 'metadata'
    const currentElement = element
    function listen(event: string, handler: () => void) {
      currentElement.addEventListener(event, () => {
        if (element === currentElement) handler()
      })
    }
    listen('loadedmetadata', () => {
      if (!element) return
      element.currentTime = pendingPosition / 1000
      element.playbackRate = settings.value.speed
    })
    listen('timeupdate', () => {
      positionMs.value = Math.round((element?.currentTime || 0) * 1000)
      if (sleepDeadline.value !== null && Date.now() >= sleepDeadline.value) {
        sleepStopPending = true
        element?.pause()
        sleepDeadline.value = null
      }
      if (
        sleepAtChapterEnd.value &&
        sleepChapterId !== null &&
        chapter.value?.id !== sleepChapterId
      ) {
        sleepStopPending = true
        element?.pause()
        sleepAtChapterEnd.value = false
      }
    })
    listen('playing', () => {
      playing.value = true
      buffering.value = false
      lastTick = performance.now()
    })
    listen('waiting', () => {
      buffering.value = true
    })
    listen('pause', () => {
      playing.value = false
      void sync()
    })
    listen('ended', () => {
      playing.value = false
      const index = book.value?.tracks.findIndex((item) => item.id === trackId.value) ?? -1
      if (sleepAtChapterEnd.value || sleepStopPending) {
        sleepAtChapterEnd.value = false
        sleepStopPending = false
        void sync()
      } else if (book.value && index + 1 < book.value.tracks.length) {
        void selectTrack(book.value.tracks[index + 1].id, 0, true)
      } else {
        finished = true
        void sync()
      }
    })
    listen('error', () =>
      fail('音频加载失败，可能文件变化、网络中断或播放凭证失效。请重新打开收听。')
    )
    timer = setInterval(() => {
      if (accountToken && accountToken !== localStorage.getItem('token')) {
        void close()
        return
      }
      const now = performance.now()
      if (playing.value && !buffering.value && lastTick)
        elapsedMs += Math.min(30000, Math.max(0, now - lastTick))
      lastTick = now
      if (sleepDeadline.value !== null) {
        sleepRemaining.value = Math.max(0, Math.ceil((sleepDeadline.value - Date.now()) / 1000))
        if (!sleepRemaining.value) {
          element?.pause()
          sleepDeadline.value = null
        }
      }
      if (++heartbeatTicks % 10 === 0 && session && playing.value) void sync()
    }, 1000)
    return element
  }

  function sync(): Promise<unknown> {
    const current = session
    const currentBook = book.value
    if (
      !current ||
      !currentBook ||
      !trackId.value ||
      accountToken !== localStorage.getItem('token')
    )
      return Promise.resolve()
    const snapshot = {
      trackId: trackId.value,
      positionMs: Math.min(positionMs.value, track.value?.durationMs || 0),
      elapsedMs: Math.floor(elapsedMs),
      finished,
    }
    syncChain = syncChain
      .catch(() => undefined)
      .then(async () => {
        if (current !== session) return
        const { data } = await api.put(
          `/api/audiobooks/sessions/${current.sessionId}`,
          {
            ...snapshot,
            revision,
            sequence: sequence++,
            contentRevision: current.contentRevision,
          },
          { headers: { 'X-Suppress-Error-Toast': 'true' } }
        )
        if (current === session) {
          revision = data.revision
          currentBook.progress = data
        }
      })
      .catch((cause) => {
        if (current !== session) return
        fail(cause.response?.data?.message || '进度保存失败，已暂停；请检查网络后重新打开收听。')
        // Stop repeated writes after a conflict or an unknown accepted request.
        session = null
      })
    return syncChain
  }

  async function open(id: number, takeover = false) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    const openGeneration = ++generation
    try {
      await close(false)
      accountToken = localStorage.getItem('token') || ''
      const [detail, preference] = await Promise.all([
        audioApi.detail(id),
        api.get('/api/audiobooks/settings'),
      ])
      const { data } = await api.post(
        `/api/audiobooks/${id}/sessions`,
        { takeover },
        {
          headers: { 'X-Suppress-Error-Toast': 'true' },
        }
      )
      if (openGeneration !== generation || accountToken !== localStorage.getItem('token')) return
      session = data
      revision = data.progress.revision
      sequence = 0
      elapsedMs = 0
      finished = false
      settings.value = preference.data
      book.value = detail
      const selected =
        detail.tracks.find((item) => item.id === data.progress.trackId) || detail.tracks[0]
      if (!selected) throw new Error('此版本暂无音轨')
      ensureElement()
      installMediaSession()
      await selectTrack(selected.id, data.progress.positionMs || 0, true)
    } catch (cause: any) {
      error.value = cause.response?.data?.message || cause.message || '无法开始收听'
      throw cause
    } finally {
      busy.value = false
    }
  }

  async function selectTrack(id: number, start = 0, autoplay = false) {
    if (!book.value || !session || !book.value.tracks.some((item) => item.id === id)) return
    await sync()
    if (!session) return
    const audio = ensureElement()
    audio.pause()
    trackId.value = id
    positionMs.value = start
    pendingPosition = start
    finished = false
    audio.src = `/api/audio-media/${session.sessionId}/${id}?key=${encodeURIComponent(session.key)}`
    audio.load()
    if (autoplay) {
      try {
        await audio.play()
      } catch {
        error.value = '请点击播放开始收听；浏览器可能限制自动播放。'
      }
    }
    void sync()
  }

  async function toggle() {
    if (!element || !session) {
      error.value = '请重新打开收听以恢复会话'
      return
    }
    if (element.paused) {
      sleepStopPending = false
      error.value = ''
      try {
        await element.play()
      } catch {
        fail('无法播放，请重新打开收听')
      }
    } else element.pause()
  }

  function seek(ms: number) {
    if (!element || !track.value || !session) return
    positionMs.value = Math.max(0, Math.min(ms, track.value.durationMs))
    element.currentTime = positionMs.value / 1000
    finished = false
    void sync()
  }

  async function saveSettings(next: AudioSettings) {
    await api.put('/api/audiobooks/settings', next)
    settings.value = next
    if (element) element.playbackRate = next.speed
  }
  function sleep(minutes: number | 'chapter' | null) {
    sleepStopPending = false
    sleepAtChapterEnd.value = minutes === 'chapter'
    sleepChapterId = chapter.value?.id || null
    sleepDeadline.value = typeof minutes === 'number' ? Date.now() + minutes * 60000 : null
    sleepRemaining.value = typeof minutes === 'number' ? minutes * 60 : 0
  }
  async function addBookmark(title: string) {
    if (!book.value || !trackId.value) return
    await audioApi.bookmark(book.value.id, {
      trackId: trackId.value,
      positionMs: positionMs.value,
      title,
    })
    book.value.bookmarks = (await audioApi.detail(book.value.id)).bookmarks
  }

  async function close(invalidate = true) {
    if (invalidate) generation++
    const current = session
    if (element) element.pause()
    await sync()
    session = null
    if (current && accountToken === localStorage.getItem('token')) {
      await api
        .delete(`/api/audiobooks/sessions/${current.sessionId}`, {
          headers: { 'X-Suppress-Error-Toast': 'true' },
        })
        .catch(() => undefined)
    }
    if (element) {
      const old = element
      element = null
      old.removeAttribute('src')
      old.load()
    }
    if (timer) clearInterval(timer)
    timer = null
    book.value = null
    playing.value = false
    trackId.value = 0
    positionMs.value = 0
    sleep(null)
    if ('mediaSession' in navigator) navigator.mediaSession.metadata = null
  }
  function installMediaSession() {
    if (!('mediaSession' in navigator) || !book.value) return
    navigator.mediaSession.metadata = new MediaMetadata({
      title: book.value.title,
      artist: book.value.narrator,
    })
    const handlers: Record<string, () => void> = {
      play: () => {
        if (!playing.value) void toggle()
      },
      pause: () => element?.pause(),
      seekbackward: () => seek(positionMs.value - settings.value.backSeconds * 1000),
      seekforward: () => seek(positionMs.value + settings.value.forwardSeconds * 1000),
    }
    for (const [action, handler] of Object.entries(handlers)) {
      try {
        navigator.mediaSession.setActionHandler(action as MediaSessionAction, handler)
      } catch {
        /* Progressive enhancement. */
      }
    }
  }
  if (typeof window !== 'undefined') {
    window.addEventListener(AUTH_EXPIRED_EVENT, () => {
      void close()
    })
    window.addEventListener('aibook:audio-stop', () => {
      void close()
    })
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) void sync()
    })
  }
  return {
    book,
    trackId,
    track,
    positionMs,
    playing,
    buffering,
    busy,
    error,
    settings,
    percent,
    totalPosition,
    chapter,
    sleepDeadline,
    sleepAtChapterEnd,
    sleepRemaining,
    open,
    close,
    toggle,
    seek,
    selectTrack,
    saveSettings,
    sleep,
    addBookmark,
    sync,
  }
})
