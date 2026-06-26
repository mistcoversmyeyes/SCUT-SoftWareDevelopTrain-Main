<template>
  <section class="mobile-page">
    <div class="page-hero">
      <div>
        <h2>库存封存</h2>
        <p>扫描或输入库存标签码，按当前封存状态执行封存或解封。</p>
      </div>
      <el-tag type="danger">封存</el-tag>
    </div>

    <section class="panel">
      <MobileQrScanner
        reader-id="seal"
        label="扫描库存标签码"
        :disabled="loading || submitting"
        @decoded="handleDecoded"
      />

      <div class="field-grid">
        <div class="field-block">
          <label class="field-label" for="mobile-seal-code">库存标签码</label>
          <el-input
            id="mobile-seal-code"
            v-model="inventoryTagCode"
            placeholder="库存标签码"
            clearable
            size="large"
            @keyup.enter="loadInventoryTag"
          />
        </div>
        <el-button type="primary" size="large" :loading="loading" @click="loadInventoryTag">
          查询
        </el-button>
      </div>

      <el-alert
        v-if="errorMessage"
        type="error"
        :closable="false"
        show-icon
        :title="errorMessage"
      />
    </section>

    <section v-if="tagInfo" class="panel">
      <div class="panel-header">
        <h3>{{ tagInfo.inventoryTagCode }}</h3>
        <el-tag :type="canUnseal ? 'danger' : 'success'">{{ tagInfo.inventoryTagStatus }}</el-tag>
      </div>
      <dl class="detail-list">
        <div>
          <dt>物料</dt>
          <dd>{{ tagInfo.materialCode }} {{ tagInfo.materialName }}</dd>
        </div>
        <div>
          <dt>库位</dt>
          <dd>{{ tagInfo.locationName || '—' }}</dd>
        </div>
        <div>
          <dt>数量</dt>
          <dd>{{ formatQty(tagInfo.boardQty) }}</dd>
        </div>
        <div>
          <dt>封存状态</dt>
          <dd>{{ holdSummary(tagInfo) }}</dd>
        </div>
      </dl>

      <el-form v-if="canSeal" :model="sealForm" label-position="top" class="action-form">
        <el-form-item label="封存原因" required>
          <el-input v-model="sealForm.reason" placeholder="请输入封存原因" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="sealForm.remark" type="textarea" maxlength="128" show-word-limit />
        </el-form-item>
        <el-button type="warning" size="large" :loading="submitting" @click="sealCurrent">
          封存
        </el-button>
      </el-form>

      <el-button v-if="canUnseal" type="success" size="large" :loading="submitting" @click="unsealCurrent">
        解封
      </el-button>
    </section>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MobileQrScanner from '../../components/mobile/MobileQrScanner.vue'
import { lookupInventoryTag } from '../../api/outbound'
import { sealInventoryTag, unsealInventoryTag } from '../../api/inventoryTag'
import { useAuthStore } from '../../stores/auth'
import { normalizeInventoryTagCode } from '../../utils/scanPayload'

const auth = useAuthStore()
const inventoryTagCode = ref('')
const tagInfo = ref(null)
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')

const sealForm = reactive({
  reason: '',
  remark: ''
})

const canSeal = computed(() =>
  tagInfo.value?.inventoryTagStatus === 'RECEIVED' && tagInfo.value?.activeHoldType !== 'SEALED'
)

const canUnseal = computed(() =>
  tagInfo.value?.inventoryTagStatus === 'SEALED' || tagInfo.value?.activeHoldType === 'SEALED'
)

function operatorName() {
  return auth.user?.username || auth.user?.displayName || 'mobile'
}

async function handleDecoded(text) {
  inventoryTagCode.value = normalizeInventoryTagCode(text)
  await loadInventoryTag()
}

async function loadInventoryTag() {
  const code = inventoryTagCode.value.trim()
  if (!code) {
    errorMessage.value = '请输入库存标签码'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    tagInfo.value = await lookupInventoryTag(code)
    sealForm.reason = ''
    sealForm.remark = ''
  } catch (error) {
    tagInfo.value = null
    errorMessage.value = error.response?.data?.message || error.message || '库存标签查询失败'
  } finally {
    loading.value = false
  }
}

async function sealCurrent() {
  const reason = sealForm.reason.trim()
  if (!reason) {
    ElMessage.warning('请输入封存原因')
    return
  }
  const id = currentInventoryTagId()
  if (!id) {
    ElMessage.error('库存标签编号缺失')
    return
  }
  submitting.value = true
  try {
    await sealInventoryTag(id, {
      reason,
      remark: sealForm.remark.trim() || undefined,
      operator: operatorName()
    })
    ElMessage.success('封存成功')
    await loadInventoryTag()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '封存失败')
  } finally {
    submitting.value = false
  }
}

async function unsealCurrent() {
  const id = currentInventoryTagId()
  if (!id) {
    ElMessage.error('库存标签编号缺失')
    return
  }
  try {
    await ElMessageBox.confirm('确认解封该库存标签？', '解封确认')
  } catch {
    return
  }
  submitting.value = true
  try {
    await unsealInventoryTag(id, {
      reason: '手机端确认解封',
      operator: operatorName()
    })
    ElMessage.success('解封成功')
    await loadInventoryTag()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '解封失败')
  } finally {
    submitting.value = false
  }
}

function currentInventoryTagId() {
  return tagInfo.value?.inventoryTagId || tagInfo.value?.id
}

function holdSummary(context) {
  if (context.activeHoldType) {
    return `${context.activeHoldType}${context.activeHoldReason ? `：${context.activeHoldReason}` : ''}`
  }
  if (context.inventoryTagStatus === 'SEALED') return '已封存'
  return '未封存'
}

function formatQty(value) {
  return value == null ? '-' : Number(value)
}
</script>

<style scoped>
.mobile-page {
  display: grid;
  gap: 16px;
}

.page-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.page-hero h2,
.panel-header h3 {
  margin: 0;
  font-size: 20px;
}

.page-hero p {
  margin: 6px 0 0;
  color: #64748b;
  line-height: 1.6;
}

.panel {
  display: grid;
  gap: 16px;
  padding: 16px;
  background: #ffffff;
  border: 1px solid #dbe4ef;
  border-radius: 8px;
}

.field-grid,
.field-block,
.detail-list,
.action-form {
  display: grid;
  gap: 12px;
}

.field-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-list div {
  display: grid;
  gap: 4px;
}

.detail-list dt {
  font-size: 12px;
  color: #64748b;
}

.detail-list dd {
  margin: 0;
  color: #0f172a;
  word-break: break-word;
}
</style>
