import request from './request'

export function getStatisticsOverview() {
  return request.get('/statistics/overview')
}
