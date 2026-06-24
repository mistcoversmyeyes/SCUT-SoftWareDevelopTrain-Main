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
