import request from './request'

export function pageRegistration(params) {
  return request.get('/registration/page', { params })
}

export function pageMyRegistration(params) {
  return request.get('/registration/mine', { params })
}

export function getRegistration(id) {
  return request.get(`/registration/${id}`)
}

export function createRegistration(data) {
  return request.post('/registration', data)
}

export function updateRegistration(id, data) {
  return request.put(`/registration/${id}`, data)
}

export function cancelRegistration(id) {
  return request.delete(`/registration/${id}`)
}

export function reviewRegistration(id, data) {
  return request.put(`/registration/${id}/review`, data)
}
export const submitRegistration = id => request.post(`/registration/${id}/submit`)
export const myInvitations = () => request.get('/registration/invitations')
export const respondInvitation = (id, accepted) => request.put(`/registration/invitations/${id}/response`, { accepted })
export const revokeInvitation = id => request.delete(`/registration/invitations/${id}`)
export const registrationHistory = id => request.get(`/registration/${id}/history`)
export const adjustRegistration = (id, reason) => request.post(`/registration/${id}/adjustment`, { reason })
export const reapplyRegistration = (id, reason) => request.post(`/registration/${id}/reapply`, { reason })
export const requestWithdrawal = (id, reason) => request.post(`/registration/${id}/withdrawals`, { reason })
export const reviewWithdrawal = (id, result, comment) => request.put(`/registration/withdrawals/${id}/review`, { result, comment })
