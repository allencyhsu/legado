<template>
  <div class="tts-player" :class="{ night: isNight, day: !isNight }" v-if="visible">
    <div class="tts-controls">
      <div class="tts-main-controls">
        <button
          class="tts-btn"
          @click="onPlayPause"
          :disabled="ttsStore.status === 'loading'"
        >
          <span v-if="ttsStore.status === 'loading'" class="tts-icon">...</span>
          <span v-else-if="ttsStore.status === 'playing'" class="tts-icon">&#9646;&#9646;</span>
          <span v-else class="tts-icon">&#9654;</span>
        </button>
        <button class="tts-btn" @click="onStop">
          <span class="tts-icon">&#9632;</span>
        </button>
        <span class="tts-progress" v-if="ttsStore.status !== 'idle'">
          {{ currentDisplay }} / {{ ttsStore.totalParagraphs }}
        </span>
      </div>
      <div class="tts-settings">
        <select v-model="ttsStore.settings.voice" @change="ttsStore.saveSettings()" class="tts-select">
          <option v-for="v in voices" :key="v" :value="v">{{ v }}</option>
        </select>
        <div class="tts-speed">
          <button class="tts-btn-sm" @click="decreaseSpeed">-</button>
          <span class="tts-speed-val">{{ ttsStore.settings.speed.toFixed(2) }}x</span>
          <button class="tts-btn-sm" @click="increaseSpeed">+</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  visible: boolean
  paragraphs: string[]
}>()

const emit = defineEmits<{
  stopped: []
}>()

const store = useBookStore()
const ttsStore = useTtsStore()
const isNight = computed(() => store.isNight)

const voices = [
  'zf_xiaoxiao', 'zf_xiaobei', 'zf_xiaoni', 'zf_xiaoyi',
  'zm_yunjian', 'zm_yunxi', 'zm_yunxia', 'zm_yunyang',
]

const currentDisplay = computed(() => {
  if (ttsStore.currentParagraph < 0) return 0
  // Count how many text paragraphs have been read
  let count = 0
  for (let i = 0; i <= ttsStore.currentParagraph && i < props.paragraphs.length; i++) {
    const text = props.paragraphs[i]?.replace(/<[^>]*>/g, '').trim()
    if (text && !/^\s*<img[^>]*>\s*$/.test(props.paragraphs[i])) count++
  }
  return count
})

const onPlayPause = () => {
  console.log('[TTS] onPlayPause, status:', ttsStore.status, 'paragraphs:', props.paragraphs.length)
  if (ttsStore.status === 'idle') {
    ttsStore.play(props.paragraphs)
  } else if (ttsStore.status === 'playing') {
    ttsStore.pause()
  } else if (ttsStore.status === 'paused') {
    ttsStore.resume()
  }
}

const onStop = () => {
  ttsStore.stop()
  emit('stopped')
}

const decreaseSpeed = () => {
  if (ttsStore.settings.speed > 0.5) {
    ttsStore.settings.speed = Math.round((ttsStore.settings.speed - 0.25) * 100) / 100
    ttsStore.saveSettings()
  }
}

const increaseSpeed = () => {
  if (ttsStore.settings.speed < 2.0) {
    ttsStore.settings.speed = Math.round((ttsStore.settings.speed + 0.25) * 100) / 100
    ttsStore.saveSettings()
  }
}
</script>

<style lang="scss" scoped>
.tts-player {
  position: fixed;
  bottom: 60px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 200;
  border-radius: 8px;
  padding: 8px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  user-select: none;
}

.tts-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

.tts-main-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tts-settings {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tts-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;

  &:disabled {
    opacity: 0.5;
    cursor: wait;
  }
}

.tts-btn-sm {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tts-icon {
  font-style: normal;
  line-height: 1;
}

.tts-progress {
  font-size: 12px;
  min-width: 50px;
  text-align: center;
}

.tts-select {
  height: 28px;
  border-radius: 4px;
  border: 1px solid #ccc;
  font-size: 12px;
  padding: 0 4px;
  background: transparent;
}

.tts-speed {
  display: flex;
  align-items: center;
  gap: 4px;
}

.tts-speed-val {
  font-size: 12px;
  min-width: 36px;
  text-align: center;
}

// Theme styles
.day {
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  color: #333;

  .tts-btn {
    background: #f0f0f0;
    color: #333;
    &:hover { background: #e0e0e0; }
  }
  .tts-btn-sm {
    background: #f0f0f0;
    color: #333;
    &:hover { background: #e0e0e0; }
  }
  .tts-select {
    color: #333;
  }
}

.night {
  background: rgba(50, 50, 50, 0.95);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.4);
  color: #aaa;

  .tts-btn {
    background: #444;
    color: #aaa;
    &:hover { background: #555; }
  }
  .tts-btn-sm {
    background: #444;
    color: #aaa;
    &:hover { background: #555; }
  }
  .tts-select {
    color: #aaa;
    background: #444;
    border-color: #555;
  }
}

@media screen and (max-width: 776px) {
  .tts-player {
    bottom: 48px;
    padding: 6px 12px;
  }
  .tts-controls {
    gap: 8px;
  }
}
</style>
