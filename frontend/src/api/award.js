import request from './request'

export function pageAward(params) {
  return request.get('/award/page', { params })
}

export function getAward(id) {
  return request.get(`/award/${id}`)
}

export function createAward(data) {
  return request.post('/award', data)
}

export function updateAward(id, data) {
  return request.put(`/award/${id}`, data)
}

export function deleteAward(id) {
  return request.delete(`/award/${id}`)
}

export function reviewAward(id, data) {
  return request.put(`/award/${id}/review`, data)
}

export function correctAward(id, data) { return request.put(`/award/${id}/correction`, data) }
export function revokeAward(id, reason) { return request.put(`/award/${id}/revoke`, { reason }) }
export function awardHistory(id) { return request.get(`/award/${id}/history`) }
export function uploadAwardCertificate(file) {
  const data = new FormData()
  data.append('file', file)
  return request.post('/file/award-certificate', data)
}
export async function downloadAwardCertificate(id) {
  const blob = await request.get(`/award/${id}/certificate`, { responseType: 'blob' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '获奖证明'
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
