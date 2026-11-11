import request from './request'

export const updateCompetitionMajor = (id, majorId) => request.put(`/competition/${id}/major`, null, { params: { majorId } })

export function pageCompetition(params) {
  return request.get('/competition/page', { params })
}

export function getCompetition(id) {
  return request.get(`/competition/${id}`)
}

export function createCompetition(data) {
  return request.post('/competition', data)
}

export function updateCompetition(id, data) {
  return request.put(`/competition/${id}`, data)
}

export function deleteCompetition(id) {
  return request.delete(`/competition/${id}`)
}

export function updateCompetitionStatus(id, status, reason) {
  return request.put(`/competition/${id}/status`, null, { params: { status, reason } })
}
