import html2canvas from 'html2canvas'

export async function saveAsImage(el, filename) {
  const canvas = await html2canvas(el, { scale: 2, useCORS: true, backgroundColor: '#ffffff' })
  const link = document.createElement('a')
  link.download = (filename || 'image') + '.png'
  link.href = canvas.toDataURL('image/png')
  link.click()
}
