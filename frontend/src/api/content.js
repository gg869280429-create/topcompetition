import request from './request'

export function pageContent(params) {
  return request.get('/content/page', { params })
}

export function getContent(id) {
  return request.get(`/content/${id}`)
}

export function createContent(data) {
  return request.post('/content', data)
}

export function updateContent(id, data) {
  return request.put(`/content/${id}`, data)
}

export function deleteContent(id) {
  return request.delete(`/content/${id}`)
}

export function updateContentStatus(id, status) {
  return request.put(`/content/${id}/status`, null, { params: { status } })
}
