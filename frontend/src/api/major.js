import request from './request'
export const listMajor = params => request.get('/major', { params })
export const createMajor = data => request.post('/major', data)
export const updateMajor = (id,data) => request.put(`/major/${id}`,data)
export const deleteMajor = id => request.delete(`/major/${id}`)
