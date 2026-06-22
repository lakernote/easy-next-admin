export const repairImageAccept = '.jpg,.jpeg,.png,.webp'
export const repairImageMaxSize = 5 * 1024 * 1024

const allowedImageTypes = new Set(['image/jpeg', 'image/png', 'image/webp'])
const allowedImageExtensions = new Set(['jpg', 'jpeg', 'png', 'webp'])

export function validateRepairImageFile(file: Pick<File, 'name' | 'size' | 'type'>) {
  if (file.size > repairImageMaxSize) {
    return '图片大小不能超过 5MB'
  }
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  if (!allowedImageTypes.has(file.type) || !allowedImageExtensions.has(extension)) {
    return '仅支持 JPG、PNG、WEBP 图片'
  }
  return undefined
}
