import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MobileQrScanner from './MobileQrScanner.vue'

const startMock = vi.fn()
const stopMock = vi.fn()
const scanFileMock = vi.fn()

vi.mock('html5-qrcode', () => ({
  Html5Qrcode: vi.fn(() => ({
    start: startMock,
    stop: stopMock,
    scanFile: scanFileMock
  }))
}))

function mountScanner(props = {}) {
  return mount(MobileQrScanner, {
    props: {
      readerId: 'test-reader',
      label: '扫描库存标签码',
      ...props
    },
    global: {
      stubs: {
        'el-button': {
          props: ['disabled', 'icon'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\', $event)"><slot /></button>'
        },
        'el-alert': {
          props: ['title'],
          template: '<div role="alert">{{ title }}</div>'
        },
        'el-icon': {
          template: '<span><slot /></span>'
        }
      }
    }
  })
}

describe('MobileQrScanner', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    startMock.mockResolvedValue(undefined)
    stopMock.mockResolvedValue(undefined)
    scanFileMock.mockResolvedValue('IT:v1:FILE:1:1')
  })

  it('renders camera and image actions with the supplied label', () => {
    const wrapper = mountScanner({ label: '扫描出库单二维码' })

    expect(wrapper.text()).toContain('扫描出库单二维码')
    expect(wrapper.text()).toContain('启动摄像头')
    expect(wrapper.text()).toContain('选择图片')
  })

  it('emits decoded text from camera scan and stops the camera', async () => {
    const wrapper = mountScanner()

    await wrapper.findAll('button')[0].trigger('click')
    await flushPromises()
    const onSuccess = startMock.mock.calls[0][2]
    await onSuccess('IT:v1:IN-20260624:1:1')
    await flushPromises()

    expect(wrapper.emitted('decoded')[0]).toEqual(['IT:v1:IN-20260624:1:1'])
    expect(stopMock).toHaveBeenCalledTimes(1)
  })
})
