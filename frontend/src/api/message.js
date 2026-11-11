import request from './request'

export function pageMyMessage(params) {
  return request.get('/message/mine', { params })
}

export function unreadCount() {
  return request.get('/message/unread-count')
}

export function getMessage(id) {
  return request.get(`/message/${id}`)
}

export function markRead(id) {
  return request.put(`/message/${id}/read`)
}

export function markAllRead() {
  return request.put('/message/read-all')
}

export function deleteMessage(id) {
  return request.delete(`/message/${id}`)
}

export function sendMessage(data) {
  return request.post('/message', data)
}
