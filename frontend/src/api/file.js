import request from './request'

export function uploadFile(file) {
  const data = new FormData()
  data.append('file', file)
  return request.post('/file', data)
}

export async function downloadCompetitionDocument(id, filename) {
  const res = await request.get(`/competition/${id}/document`, { responseType: 'blob' })
  const url = URL.createObjectURL(res)
  const link = document.createElement('a')
  link.href = url
  link.download = filename || '竞赛文件'
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
