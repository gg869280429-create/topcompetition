import request from './request'
export const pageStudent = params => request.get('/student/page', { params })
export const createStudent = data => request.post('/student', data)
export const updateStudent = (id,data) => request.put(`/student/${id}`,data)
export const deleteStudent = id => request.delete(`/student/${id}`)
