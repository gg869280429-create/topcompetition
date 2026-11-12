import request from './request'

export function getStudentProfile() {
  return request.get('/student/profile')
}

export function updateStudentProfile(data) {
  return request.put('/student/profile', data)
}

export function changeStudentPassword(data) {
  return request.put('/student/profile/password', data)
}
