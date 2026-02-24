import { defineStore } from 'pinia'
import API from '@api'

export type TtsStatus = 'idle' | 'loading' | 'playing' | 'paused'

export interface TtsSettings {
  voice: string
  speed: number
  instruct: string
}

const TTS_SETTINGS_KEY = 'ttsSettings'

const loadSettings = (): TtsSettings => {
  try {
    const saved = localStorage.getItem(TTS_SETTINGS_KEY)
    if (saved) return { ...defaultSettings, ...JSON.parse(saved) }
  } catch {}
  return { ...defaultSettings }
}

const defaultSettings: TtsSettings = {
  voice: 'zf_xiaoxiao',
  speed: 1.0,
  instruct: '',
}

// Strip HTML tags for TTS input
const stripHtml = (text: string): string => {
  return text.replace(/<[^>]*>/g, '').trim()
}

export const useTtsStore = defineStore('tts', {
  state: () => ({
    status: 'idle' as TtsStatus,
    currentParagraph: -1,
    totalParagraphs: 0,
    chapterFinished: false,
    settings: loadSettings(),
    // internal state (not reactive for performance)
    _paragraphs: [] as string[],
    _audio: null as HTMLAudioElement | null,
    _currentBlobUrl: '' as string,
    _prefetchedBlob: null as Blob | null,
    _prefetchedIndex: -1,
    _abortController: null as AbortController | null,
  }),

  actions: {
    saveSettings() {
      localStorage.setItem(TTS_SETTINGS_KEY, JSON.stringify(this.settings))
    },

    async play(paragraphs: string[], startIndex = 0) {
      console.log('[TTS] play() called, paragraphs count:', paragraphs.length, 'startIndex:', startIndex)
      this.chapterFinished = false
      this.stop()

      // Filter out empty paragraphs and image-only paragraphs
      const textParagraphs: { index: number; text: string }[] = []
      paragraphs.forEach((p, i) => {
        const text = stripHtml(p)
        if (text.length > 0 && !/^\s*<img[^>]*>\s*$/.test(p)) {
          textParagraphs.push({ index: i, text })
        }
      })

      console.log('[TTS] textParagraphs after filter:', textParagraphs.length)
      if (textParagraphs.length === 0) {
        console.warn('[TTS] No text paragraphs to play!')
        return
      }

      // Find starting point
      let playStart = 0
      for (let i = 0; i < textParagraphs.length; i++) {
        if (textParagraphs[i].index >= startIndex) {
          playStart = i
          break
        }
      }

      this._paragraphs = paragraphs
      this.totalParagraphs = textParagraphs.length
      this.status = 'loading'

      await this._playSequence(textParagraphs, playStart)
    },

    async _playSequence(
      textParagraphs: { index: number; text: string }[],
      fromIndex: number,
    ) {
      for (let i = fromIndex; i < textParagraphs.length; i++) {
        if (this.status === 'idle') break

        const { index, text } = textParagraphs[i]
        this.currentParagraph = index
        this.status = 'loading'

        try {
          // Use prefetched blob if available
          let blob: Blob
          if (this._prefetchedIndex === i && this._prefetchedBlob) {
            blob = this._prefetchedBlob
            this._prefetchedBlob = null
            this._prefetchedIndex = -1
          } else {
            blob = await this._fetchAudio(text)
          }

          if (this.status === 'idle') break

          // Start prefetching next paragraph
          if (i + 1 < textParagraphs.length) {
            this._prefetch(textParagraphs, i + 1)
          }

          // Play current audio
          await this._playBlob(blob)

          if (this.status === 'idle') break
        } catch (e) {
          console.error('TTS playback error:', e)
          if (this.status !== 'idle') {
            // Skip failed paragraph and continue
            continue
          }
          break
        }
      }

      // Playback finished naturally
      if (this.status !== 'idle') {
        console.log('[TTS] Chapter playback finished naturally')
        this.chapterFinished = true
        this.status = 'idle'
        this.currentParagraph = -1
      }
    },

    async _fetchAudio(text: string): Promise<Blob> {
      const { voice, speed, instruct } = this.settings
      console.log('[TTS] Fetching audio for:', text.substring(0, 50), '...')
      const response = await API.ttsSpeak(
        text,
        voice,
        speed,
        instruct || undefined,
      )
      const blob = response.data as unknown as Blob
      console.log('[TTS] Received blob:', blob.size, 'bytes, type:', blob.type)
      if (blob.size === 0) {
        throw new Error('TTS returned empty audio')
      }
      return blob
    },

    _prefetch(
      textParagraphs: { index: number; text: string }[],
      nextIndex: number,
    ) {
      this._prefetchedBlob = null
      this._prefetchedIndex = -1
      const { text } = textParagraphs[nextIndex]
      this._fetchAudio(text)
        .then(blob => {
          this._prefetchedBlob = blob
          this._prefetchedIndex = nextIndex
        })
        .catch(() => {
          // Prefetch failure is non-critical
        })
    },

    _playBlob(blob: Blob): Promise<void> {
      return new Promise((resolve, reject) => {
        // Clean up previous
        this._cleanupAudio()

        const url = URL.createObjectURL(blob)
        this._currentBlobUrl = url
        console.log('[TTS] Playing blob URL:', url, 'size:', blob.size, 'type:', blob.type)
        const audio = new Audio(url)
        this._audio = audio

        audio.onended = () => {
          console.log('[TTS] Audio ended normally')
          this._cleanupAudio()
          resolve()
        }
        audio.onerror = (e) => {
          console.error('[TTS] Audio playback error:', e, 'error code:', audio.error?.code, 'message:', audio.error?.message)
          this._cleanupAudio()
          reject(new Error(`Audio playback error: ${audio.error?.message || 'unknown'}`))
        }

        this.status = 'playing'
        audio.play()
          .then(() => console.log('[TTS] audio.play() resolved'))
          .catch((e) => {
            console.error('[TTS] audio.play() rejected:', e)
            reject(e)
          })
      })
    },

    pause() {
      if (this.status === 'playing' && this._audio) {
        this._audio.pause()
        this.status = 'paused'
      }
    },

    resume() {
      if (this.status === 'paused' && this._audio) {
        this._audio.play()
        this.status = 'playing'
      }
    },

    stop() {
      this._cleanupAudio()
      this._prefetchedBlob = null
      this._prefetchedIndex = -1
      this._paragraphs = []
      this.status = 'idle'
      this.currentParagraph = -1
      this.totalParagraphs = 0
      this.chapterFinished = false
    },

    _cleanupAudio() {
      if (this._audio) {
        this._audio.pause()
        this._audio.onended = null
        this._audio.onerror = null
        this._audio = null
      }
      if (this._currentBlobUrl) {
        URL.revokeObjectURL(this._currentBlobUrl)
        this._currentBlobUrl = ''
      }
    },
  },
})
