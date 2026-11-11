import request from './request'

export const pageTeacher = (params) => request.get('/teacher/page', { params })
export const listMajors = () => request.get('/teacher/majors')
export const createTeacher = (data) => request.post('/teacher', data)
export const updateTeacher = (id, data) => request.put(`/teacher/${id}`, data)
