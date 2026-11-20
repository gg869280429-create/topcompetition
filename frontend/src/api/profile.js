import request from './request'

export function getProfile() {
  return request.get('/profile')
}

export function updateProfile(data) {
  return request.put('/profile', data)
}

export function changePassword(data) {
  return request.put('/profile/password', data)
}

export function uploadAvatar(file) {
  const data = new FormData()
  data.append('file', file)
  return request.post('/avatar', data)
}
