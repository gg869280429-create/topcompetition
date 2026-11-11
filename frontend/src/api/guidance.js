import request from './request'

export const completeGuidance = id => request.post(`/guidance/${id}/complete`)
export const guidanceResults = id => request.get(`/guidance/${id}/results`)
export const saveParticipationResult = (id, studentId, data) => request.put(`/guidance/${id}/results/${studentId}`, data)
export const teacherQuotaDetails = teacherId => request.get(`/guidance/teacher/${teacherId}/quotas`)
export const increaseTeacherQuota = (teacherId, competitionId, amount) => request.post(`/guidance/teacher/${teacherId}/quotas/${competitionId}/increase`, { amount })

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
