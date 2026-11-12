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
