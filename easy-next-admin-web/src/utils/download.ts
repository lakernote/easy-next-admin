export function resolveDownloadFileName(contentDisposition?: string) {
  if (!contentDisposition) {
    return ''
  }
  const encoded = contentDisposition.match(/filename\*=UTF-8''"?([^";]+)"?/i)?.[1]
  if (encoded) {
    return decodeFileName(encoded)
  }
  const plain = contentDisposition.match(/filename="?([^";]+)"?/i)?.[1]
  return plain ? decodeFileName(plain) : ''
}

export function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => {
    URL.revokeObjectURL(url)
  }, 1000)
}

function decodeFileName(value: string) {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}
