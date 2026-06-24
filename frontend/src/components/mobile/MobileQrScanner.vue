<template>
  <section class="mobile-scanner">
    <div class="scanner-frame">
      <div
        :id="readerDomId"
        class="scanner-reader"
        :class="{ active: cameraActive }"
        v-show="cameraActive"
      ></div>
      <div class="scanner-placeholder" v-show="!cameraActive">
        <el-icon :size="34"><Camera /></el-icon>
        <strong>{{ label }}</strong>
        <span>摄像头不可用时可选择图片或手动输入</span>
      </div>
    </div>

    <div class="scanner-actions">
      <el-button type="primary" :icon="Camera" :disabled="disabled" @click="toggleCamera">
        {{ cameraActive ? '关闭摄像头' : '启动摄像头' }}
      </el-button>
      <el-button :disabled="disabled" @click="openFilePicker">选择图片</el-button>
    </div>

    <el-alert
      v-if="scannerError"
      type="error"
      :closable="true"
      show-icon
      :title="scannerError"
      @close="scannerError = ''"
    />
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { Camera } from '@element-plus/icons-vue'
import { Html5Qrcode } from 'html5-qrcode'

const props = defineProps({
  readerId: {
    type: String,
    required: true
  },
  label: {
    type: String,
    default: '扫描二维码'
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['decoded', 'error'])

const readerDomId = computed(() => `mobile-qr-reader-${props.readerId}`)
const cameraActive = ref(false)
const scannerError = ref('')

let html5QrCode = null

function scannerMessage(error) {
  const raw = String(error?.message || error || '')
  if (/permission|denied|notallowed/i.test(raw)) {
    return '摄像头权限被拒绝，请允许浏览器访问摄像头，或使用图片识别/手动输入。'
  }
  if (/secure|https|localhost/i.test(raw)) {
    return '当前访问方式不支持摄像头，请使用 HTTPS、localhost，或改用图片识别/手动输入。'
  }
  if (/notfound|not found|device/i.test(raw)) {
    return '未检测到可用摄像头，请使用图片识别或手动输入。'
  }
  return raw ? `摄像头启动失败：${raw}` : '摄像头启动失败，请使用图片识别或手动输入。'
}

function setScannerError(message) {
  scannerError.value = message
  emit('error', message)
}

async function toggleCamera() {
  scannerError.value = ''
  if (cameraActive.value) {
    await stopCamera()
    return
  }
  await startCamera()
}

async function startCamera() {
  try {
    html5QrCode = new Html5Qrcode(readerDomId.value)
    cameraActive.value = true
    await nextTick()
    await html5QrCode.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: 220, height: 220 } },
      async (decodedText) => {
        emit('decoded', decodedText)
        await stopCamera()
      },
      () => {}
    )
  } catch (error) {
    cameraActive.value = false
    setScannerError(scannerMessage(error))
  }
}

async function stopCamera() {
  if (!html5QrCode) {
    cameraActive.value = false
    return
  }
  const instance = html5QrCode
  html5QrCode = null
  try {
    await instance.stop()
  } catch {
    // The scanner may already be stopped if startup failed after allocation.
  }
  cameraActive.value = false
}

function openFilePicker() {
  scannerError.value = ''
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = async (event) => {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }
    const tmp = document.createElement('div')
    tmp.id = `${readerDomId.value}-file`
    tmp.style.display = 'none'
    document.body.appendChild(tmp)
    const fileScanner = new Html5Qrcode(tmp.id)
    try {
      const result = await fileScanner.scanFile(file, true)
      emit('decoded', result)
    } catch {
      setScannerError('未能识别二维码，请更换图片或手动输入。')
    } finally {
      document.body.removeChild(tmp)
    }
  }
  input.click()
}

onBeforeUnmount(() => {
  stopCamera()
})
</script>

<style scoped>
.mobile-scanner {
  display: grid;
  gap: 12px;
}

.scanner-frame {
  min-height: 260px;
  display: grid;
}

.scanner-reader,
.scanner-placeholder {
  min-height: 260px;
  border: 1px dashed #94a3b8;
  border-radius: 8px;
  overflow: hidden;
  background: #f8fafc;
}

.scanner-reader.active {
  border-color: #2563eb;
}

.scanner-placeholder {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 8px;
  color: #475569;
  text-align: center;
  padding: 18px;
}

.scanner-placeholder span {
  color: #64748b;
  font-size: 13px;
}

.scanner-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
</style>
