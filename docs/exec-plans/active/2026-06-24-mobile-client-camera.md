# Mobile Client Camera Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace mobile H5 simulated scan shortcuts with real QR camera/image scanning for inventory tag codes and outbound order QR codes.

**Architecture:** Add a small scanner utility layer for decoded-text normalization, a reusable `MobileQrScanner` Vue component that owns `html5-qrcode` lifecycle, then wire the component into the three mobile pages. Business pages keep ownership of inbound, outbound, and inventory tag query API calls.

**Tech Stack:** Vue 3 Composition API, Element Plus, `html5-qrcode`, Vitest + jsdom, existing WMS API wrappers.

## Global Constraints

- Implementation branch/worktree: `.worktrees/mobile-camera` on `feat/mobile-client-camera`.
- Spec source: `docs/specs/2026-06-24-mobile-real-scan-design.md`.
- Keep mobile H5 routes under `/mobile`; do not add native Android, PDA, scanner-gun, label-printer, or device SDK integration.
- Keep existing business APIs unchanged.
- Keep manual inventory tag code and outbound order number input as fallback.
- Scanner component must not call WMS business APIs; it only emits decoded text and scanner errors.
- Use `html5-qrcode` as the primary QR decoder.
- Frontend logic changes require `cd frontend && npm test`.
- Mobile UI and scanner dependency path changes require `cd frontend && npm run build`.

---

## File Structure

- Create: `frontend/src/utils/scanPayload.js`
  - Pure helpers for decoded QR payload normalization.
- Create: `frontend/src/utils/scanPayload.test.js`
  - Red/green coverage for raw codes and URL payloads.
- Create: `frontend/src/components/mobile/MobileQrScanner.vue`
  - Shared mobile scanner UI and `Html5Qrcode` lifecycle wrapper.
- Modify: `frontend/src/views/mobile/MobileInboundView.vue`
  - Replace `模拟扫码` with scanner component and handle decoded inventory tag code.
- Modify: `frontend/src/views/mobile/MobileInventoryTagQueryView.vue`
  - Replace `模拟扫码` with scanner component and auto-query decoded inventory tag code.
- Modify: `frontend/src/views/mobile/MobileOutboundView.vue`
  - Add two-phase with-order scanning: outbound order QR before load, inventory tag QR after load; no-order scans inventory tags.
- Modify: `docs/tests/acceptence-tests/iter4/week4-fr-acceptance-test-steps.md`
  - Update FR-05 mobile acceptance steps to require real camera/image scan with manual fallback.

---

## Task 1: Scan Payload Normalization

**Files:**
- Create: `frontend/src/utils/scanPayload.js`
- Create: `frontend/src/utils/scanPayload.test.js`

**Interfaces:**
- Produces: `normalizeInventoryTagCode(text: unknown): string`
- Produces: `normalizeOutboundNo(text: unknown): string`
- Later tasks consume both functions from mobile pages.

- [ ] **Step 1: Write failing tests**

Create `frontend/src/utils/scanPayload.test.js`:

```js
import { describe, expect, it } from 'vitest'
import { normalizeInventoryTagCode, normalizeOutboundNo } from './scanPayload'

describe('scan payload normalization', () => {
  it('trims raw inventory tag codes', () => {
    expect(normalizeInventoryTagCode('  IT:v1:IN-20260624:1:1  ')).toBe('IT:v1:IN-20260624:1:1')
  })

  it('trims raw outbound order numbers', () => {
    expect(normalizeOutboundNo('  OUT-20260624-001  ')).toBe('OUT-20260624-001')
  })

  it('prefers outboundNo query parameter from URLs', () => {
    expect(normalizeOutboundNo('https://wms.example/mobile/outbound?outboundNo=OUT-20260624-ABC')).toBe('OUT-20260624-ABC')
  })

  it('uses the last path segment from outbound order URLs', () => {
    expect(normalizeOutboundNo('https://wms.example/outbound/orders/OUT-20260624-XYZ')).toBe('OUT-20260624-XYZ')
  })

  it('returns trimmed text for non-URL payloads it cannot classify', () => {
    expect(normalizeOutboundNo('not a url but keep it')).toBe('not a url but keep it')
  })
})
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd frontend && npm test -- src/utils/scanPayload.test.js
```

Expected: FAIL because `frontend/src/utils/scanPayload.js` does not exist.

- [ ] **Step 3: Implement helpers**

Create `frontend/src/utils/scanPayload.js`:

```js
function trimDecodedText(text) {
  return typeof text === 'string' ? text.trim() : ''
}

function parseUrl(value) {
  try {
    return new URL(value)
  } catch {
    return null
  }
}

function lastPathSegment(url) {
  return url.pathname
    .split('/')
    .map((segment) => segment.trim())
    .filter(Boolean)
    .pop() || ''
}

export function normalizeInventoryTagCode(text) {
  return trimDecodedText(text)
}

export function normalizeOutboundNo(text) {
  const value = trimDecodedText(text)
  const url = parseUrl(value)
  if (!url) {
    return value
  }
  return url.searchParams.get('outboundNo')?.trim() || lastPathSegment(url) || value
}
```

- [ ] **Step 4: Run focused test and verify GREEN**

Run:

```bash
cd frontend && npm test -- src/utils/scanPayload.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/utils/scanPayload.js frontend/src/utils/scanPayload.test.js
git commit -m "test(frontend): add mobile scan payload normalization"
```

---

## Task 2: Shared Mobile QR Scanner Component

**Files:**
- Create: `frontend/src/components/mobile/MobileQrScanner.vue`

**Interfaces:**
- Consumes: `html5-qrcode` dependency.
- Props:
  - `readerId: string`
  - `label: string`
  - `disabled: boolean`
- Emits:
  - `decoded(text: string)`
  - `error(message: string)`

- [ ] **Step 1: Create scanner component**

Create `frontend/src/components/mobile/MobileQrScanner.vue`:

```vue
<template>
  <section class="mobile-scanner">
    <div class="scanner-frame">
      <div :id="readerDomId" class="scanner-reader" :class="{ active: cameraActive }" v-show="cameraActive"></div>
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
    // Already stopped or never fully started.
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
```

- [ ] **Step 2: Run build to verify component compiles**

Run:

```bash
cd frontend && npm run build
```

Expected: build succeeds with only the known Vite/Rollup chunk and PURE-comment warnings.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/mobile/MobileQrScanner.vue
git commit -m "feat(frontend): add mobile qr scanner component"
```

---

## Task 3: Wire Scanner Into Mobile Inbound and Query

**Files:**
- Modify: `frontend/src/views/mobile/MobileInboundView.vue`
- Modify: `frontend/src/views/mobile/MobileInventoryTagQueryView.vue`

**Interfaces:**
- Consumes: `MobileQrScanner` events.
- Consumes: `normalizeInventoryTagCode(text)`.
- Produces: scan success fills `inventoryTagCode`; query page auto-runs `queryInventoryTag()`.

- [ ] **Step 1: Replace inbound simulated scan**

In `MobileInboundView.vue`:

- Import `MobileQrScanner` and `normalizeInventoryTagCode`.
- Replace the `模拟扫码` button with:

```vue
<MobileQrScanner
  reader-id="inbound"
  label="扫描库存标签码"
  :disabled="submitting"
  @decoded="handleInventoryTagScan"
/>
```

- Add:

```js
function handleInventoryTagScan(text) {
  inventoryTagCode.value = normalizeInventoryTagCode(text)
}
```

- Remove `applyDemoCode`.

- [ ] **Step 2: Replace inventory tag query simulated scan**

In `MobileInventoryTagQueryView.vue`:

- Import `MobileQrScanner` and `normalizeInventoryTagCode`.
- Replace the `模拟扫码` button with:

```vue
<MobileQrScanner
  reader-id="inventory-tag-query"
  label="扫描库存标签码"
  :disabled="loading"
  @decoded="handleInventoryTagScan"
/>
```

- Add:

```js
async function handleInventoryTagScan(text) {
  inventoryTagCode.value = normalizeInventoryTagCode(text)
  await queryInventoryTag()
}
```

- Remove `applyDemoCode`.

- [ ] **Step 3: Run frontend tests and build**

Run:

```bash
cd frontend && npm test
cd frontend && npm run build
```

Expected: tests pass and build succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/mobile/MobileInboundView.vue frontend/src/views/mobile/MobileInventoryTagQueryView.vue
git commit -m "feat(frontend): enable mobile inbound and tag scan"
```

---

## Task 4: Wire Scanner Into Mobile Outbound

**Files:**
- Modify: `frontend/src/views/mobile/MobileOutboundView.vue`

**Interfaces:**
- Consumes: `MobileQrScanner`.
- Consumes: `normalizeInventoryTagCode(text)` and `normalizeOutboundNo(text)`.
- Produces: with-order mode scans outbound order before order load and inventory tags after order load; no-order mode scans inventory tags.

- [ ] **Step 1: Add scanner to outbound page**

In `MobileOutboundView.vue`:

- Import `MobileQrScanner`, `normalizeInventoryTagCode`, and `normalizeOutboundNo`.
- Add computed values:

```js
const scannerLabel = computed(() => {
  if (mode.value === 'with-order' && !orderInfo.value?.id) {
    return '扫描出库单二维码'
  }
  return '扫描库存标签码'
})
```

- Add decoded handler:

```js
async function handleOutboundScan(text) {
  if (mode.value === 'with-order' && !orderInfo.value?.id) {
    outboundNo.value = normalizeOutboundNo(text)
    await loadOrder()
    return
  }
  inventoryTagCode.value = normalizeInventoryTagCode(text)
}
```

- Insert the scanner in the first panel above manual fields:

```vue
<MobileQrScanner
  reader-id="outbound"
  :label="scannerLabel"
  :disabled="loadingOrder || submitting"
  @decoded="handleOutboundScan"
/>
```

- Remove `applyDemoCode` and its `模拟扫码` button.

- [ ] **Step 2: Run frontend tests and build**

Run:

```bash
cd frontend && npm test
cd frontend && npm run build
```

Expected: tests pass and build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/mobile/MobileOutboundView.vue
git commit -m "feat(frontend): enable mobile outbound qr scanning"
```

---

## Task 5: Acceptance Documentation

**Files:**
- Modify: `docs/tests/acceptence-tests/iter4/week4-fr-acceptance-test-steps.md`

**Interfaces:**
- Consumes: implemented mobile real scan behavior.
- Produces: FR-05 acceptance steps requiring real camera/image scan with manual fallback.

- [ ] **Step 1: Update FR-05 preconditions and steps**

In FR-05:

- Replace the old note that phone-side real camera scan is not required with:

```markdown
- 使用浏览器移动端视口，或直接访问手机端路径。
- 手机端优先使用摄像头扫描或图片识别二维码；若浏览器权限、HTTPS 或设备摄像头限制导致不可用，允许手工输入库存标签码或出库单号作为兜底，并需记录失败提示。
```

- Ensure steps include:
  - scan inventory tag QR for mobile inbound;
  - scan inventory tag QR for mobile inventory tag query;
  - scan outbound order QR before with-order outbound;
  - scan locked inventory tag QR for with-order outbound;
  - scan inventory tag QR for no-order outbound;
  - verify manual fallback after camera failure or unsupported context.

- [ ] **Step 2: Run docs check**

Run:

```bash
git diff --check -- docs/tests/acceptence-tests/iter4/week4-fr-acceptance-test-steps.md
```

Expected: no output and exit 0.

- [ ] **Step 3: Commit**

```bash
git add docs/tests/acceptence-tests/iter4/week4-fr-acceptance-test-steps.md
git commit -m "docs(tests): update mobile real scan acceptance"
```

---

## Task 6: Final Verification

**Files:**
- Verify all task-owned files.

**Interfaces:**
- Produces: final verification evidence.

- [ ] **Step 1: Run frontend tests**

Run:

```bash
cd frontend && npm test
```

Expected: all Vitest files pass.

- [ ] **Step 2: Run frontend build**

Run:

```bash
cd frontend && npm run build
```

Expected: build succeeds; known Rollup PURE-comment and chunk size warnings are acceptable.

- [ ] **Step 3: Run docs whitespace check**

Run:

```bash
git diff --check
```

Expected: no output and exit 0.

- [ ] **Step 4: Review final diff**

Run:

```bash
git status --short
git log --oneline --decorate -6
```

Expected: branch contains only mobile camera commits and no unstaged task-owned changes.
