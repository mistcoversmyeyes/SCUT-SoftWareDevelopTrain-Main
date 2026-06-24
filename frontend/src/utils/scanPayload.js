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
