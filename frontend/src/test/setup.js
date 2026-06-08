const storage = new Map()

Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: {
    clear() {
      storage.clear()
    },
    getItem(key) {
      return storage.has(key) ? storage.get(key) : null
    },
    removeItem(key) {
      storage.delete(key)
    },
    setItem(key, value) {
      storage.set(key, String(value))
    }
  }
})
