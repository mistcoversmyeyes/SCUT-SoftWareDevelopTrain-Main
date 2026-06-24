import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MobileInboundView from './MobileInboundView.vue'
import MobileInventoryTagQueryView from './MobileInventoryTagQueryView.vue'
import MobileOutboundView from './MobileOutboundView.vue'
import { fetchMasterDataOptions } from '../../api/masterData'
import { lookupInventoryTagInbound } from '../../api/inventory'
import { fetchInventoryTagTrace } from '../../api/inventoryTag'
import { fetchQrInfo, lookupInventoryTag } from '../../api/outbound'

vi.mock('../../api/masterData', () => ({
  fetchMasterDataOptions: vi.fn()
}))

vi.mock('../../api/inventory', () => ({
  lookupInventoryTagInbound: vi.fn(),
  scanInbound: vi.fn()
}))

vi.mock('../../api/inventoryTag', () => ({
  fetchInventoryTagTrace: vi.fn()
}))

vi.mock('../../api/outbound', () => ({
  fetchQrInfo: vi.fn(),
  lookupInventoryTag: vi.fn(),
  pickNoOrder: vi.fn(),
  pickWithOrder: vi.fn()
}))

const commonStubs = {
  MobileQrScanner: {
    props: ['readerId', 'label', 'disabled'],
    emits: ['decoded'],
    template: `
      <button
        class="scanner-stub"
        @click="$emit('decoded', label.includes('出库单') ? 'https://wms.example/outbound/orders/OUT-SCAN-1' : '  IT:v1:SCAN:1:1  ')"
      >
        {{ label }}
      </button>
    `
  },
  'el-input': {
    inheritAttrs: false,
    props: ['modelValue', 'id', 'size', 'clearable', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<input :id="id" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': {
    inheritAttrs: false,
    props: ['modelValue', 'size', 'clearable', 'filterable', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>'
  },
  'el-option': {
    template: '<option><slot /></option>'
  },
  'el-button': {
    props: ['loading', 'disabled'],
    emits: ['click'],
    template: '<button :disabled="disabled" @click="$emit(\'click\', $event)"><slot /></button>'
  },
  'el-input-number': {
    inheritAttrs: false,
    props: ['modelValue', 'id', 'size', 'min', 'step', 'precision', 'placeholder'],
    emits: ['update:modelValue'],
    template: '<input :id="id" type="number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />'
  },
  'el-alert': {
    props: ['title'],
    template: '<div role="alert">{{ title }}</div>'
  },
  'el-tag': {
    template: '<span><slot /></span>'
  }
}

describe('mobile real scan page wiring', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    fetchMasterDataOptions.mockResolvedValue({ locations: [] })
    lookupInventoryTagInbound.mockResolvedValue({
      inventoryTagStatus: 'PRINTED',
      materialCode: 'MAT-1',
      materialName: '测试物料',
      inboundNo: 'IN-1',
      locationName: 'A-01',
      boardQty: 100
    })
    fetchInventoryTagTrace.mockResolvedValue({
      inventoryTagCode: 'IT:v1:SCAN:1:1',
      inventoryTagStatus: 'RECEIVED',
      materialCode: 'MAT-1',
      materialName: '测试物料'
    })
    lookupInventoryTag.mockResolvedValue({
      inventoryTagCode: 'IT:v1:SCAN:1:1',
      boardQty: 100,
      pickedQty: 0
    })
    fetchQrInfo.mockResolvedValue({
      order: {
        id: 9,
        outboundNo: 'OUT-SCAN-1',
        status: 'RELEASED',
        supplier: { name: '8KH' },
        plannedQty: 100,
        pickedQty: 0
      },
      lockedItems: [
        {
          id: 1,
          inventoryTagCode: 'IT:v1:SCAN:1:1',
          materialCode: 'MAT-1',
          materialName: '测试物料',
          locationName: 'A-01',
          lockQty: 100
        }
      ]
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('fills scanned inventory tag code in mobile inbound and loads preview', async () => {
    const wrapper = mount(MobileInboundView, {
      global: { stubs: commonStubs }
    })

    await wrapper.find('.scanner-stub').trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    await flushPromises()

    expect(lookupInventoryTagInbound).toHaveBeenCalledWith('IT:v1:SCAN:1:1')
    expect(wrapper.text()).toContain('测试物料')
  })

  it('auto-queries inventory tag after mobile query scanner decodes a code', async () => {
    const wrapper = mount(MobileInventoryTagQueryView, {
      global: { stubs: commonStubs }
    })

    await wrapper.find('.scanner-stub').trigger('click')
    await flushPromises()

    expect(fetchInventoryTagTrace).toHaveBeenCalledWith('IT:v1:SCAN:1:1')
    expect(lookupInventoryTag).toHaveBeenCalledWith('IT:v1:SCAN:1:1')
    expect(wrapper.text()).toContain('RECEIVED')
  })

  it('loads outbound order from scanned order QR before scanning inventory tag', async () => {
    const wrapper = mount(MobileOutboundView, {
      global: { stubs: commonStubs }
    })

    await wrapper.find('.scanner-stub').trigger('click')
    await flushPromises()

    expect(fetchQrInfo).toHaveBeenCalledWith('OUT-SCAN-1')
    expect(wrapper.text()).toContain('OUT-SCAN-1')
    expect(wrapper.text()).toContain('IT:v1:SCAN:1:1')

    await wrapper.find('.scanner-stub').trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    await flushPromises()

    expect(lookupInventoryTag).toHaveBeenCalledWith('IT:v1:SCAN:1:1')
  })
})
