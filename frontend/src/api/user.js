import request from './request'

export function listStudents(keyword) {
  return request.get('/user/students', { params: { keyword } })
}

export function listTeachers(keyword) {
  return request.get('/user/teachers', { params: { keyword } })
}

export function listUsers(keyword) {
  return request.get('/user/users', { params: { keyword } })
}
