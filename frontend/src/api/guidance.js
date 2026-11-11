import request from './request'

export function pageQuota(params) {
  return request.get('/guidance/quota/page', { params })
}

export function saveQuota(data) {
  return request.post('/guidance/quota', data)
}

export function deleteQuota(id) {
  return request.delete(`/guidance/quota/${id}`)
}

export function listGuidance(registrationId) {
  return request.get('/guidance/list', { params: { registrationId } })
}

export function assignGuidance(data) {
  return request.post('/guidance', data)
}

export function replaceGuidance(id, data) {
  return request.put(`/guidance/${id}`, data)
}

export function pageMyGuidance(params) {
  return request.get('/guidance/mine', { params })
}

export function competitionTeachers(competitionId) {
  return request.get('/guidance/teachers', { params: { competitionId } })
}

export function pageMyCompetitions(params) {
  return request.get('/guidance/quota/mine', { params })
}
