// 业务字典常量，值与后端保持一致

// 赛制
export const COMPETITION_TYPE = {
  PERSONAL: '个人赛',
  TEAM: '团队赛'
}

// 竞赛级别
export const COMPETITION_LEVEL = {
  SCHOOL: '校级',
  CITY: '市级',
  PROVINCE: '省级',
  NATIONAL: '国家级',
  INTERNATIONAL: '国际级'
}

// 竞赛状态
export const COMPETITION_STATUS = {
  DRAFT: '草稿',
  REGISTERING: '报名中',
  IN_PROGRESS: '进行中',
  ENDED: '已结束',
  CANCELLED: '已取消'
}

// 报名状态
export const REGISTRATION_STATUS = {
  DRAFT: '待成员确认',
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
  WITHDRAWN: '已退赛'
}

// 奖项等级
export const AWARD_LEVEL = {
  SPECIAL: '特等奖',
  FIRST: '一等奖',
  SECOND: '二等奖',
  THIRD: '三等奖',
  EXCELLENT: '优秀奖'
}

// 内容分类
export const CONTENT_CATEGORY = {
  NOTICE: '通知',
  NEWS: '新闻',
  ANNOUNCEMENT: '公告'
}

// 内容状态
export const CONTENT_STATUS = {
  DRAFT: '草稿',
  PUBLISHED: '已发布'
}

// 消息类型
export const MESSAGE_TYPE = {
  SYSTEM: '系统',
  NOTICE: '通知',
  AUDIT: '审核'
}
