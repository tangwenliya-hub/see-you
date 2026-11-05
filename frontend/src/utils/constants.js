/**
 * 全站公共字典 —— 情绪相关常量的唯一真源
 *
 * 背景：原先 Main / Diary / WriteDiary / Assistant / Profile / DataBoard 各写了一份
 * emotions 数组（且 Profile 那份少了 bg 字段、后端又有一份 Java 版），一旦要改一个
 * 情绪的图标或配色就得改 7 处，非常容易漏。现在统一收在这里，前后端共用同一套编号：
 *
 *   1 开心  2 平静  3 疲惫  4 难过  5 生气  6 焦虑  7 兴奋  8 满足  9 其他
 *
 * ⚠️ 后端唯一真源是 see_you/.../algorithm/EmotionConst.java（NAMES / INDEX / POSITIVE /
 *    NEGATIVE，以及给 SQL 用的 SQL_INDEX / SQL_POSITIVE / SQL_NEGATIVE 片段）。
 *    StatisticsController 与 StatisticsMapper 都已改为直接引用它，不再各存一份。
 *    本文件与 EmotionConst 的一致性由后端测试 EmotionDictConsistencyTest 自动校验——
 *    改这里而忘了改那里（或反之），测试会失败。
 */

export const EMOTIONS = [
  { val: 1, label: '开心', icon: '/img/开心.png', bg: '#b8d49a', color: '#FF6B6B' },
  { val: 7, label: '兴奋', icon: '/img/兴奋.png', bg: '#ffd09a', color: '#FD79A8' },
  { val: 8, label: '满足', icon: '/img/满足.png', bg: '#ffc9d8', color: '#FFEAA7' },
  { val: 2, label: '平静', icon: '/img/平静.png', bg: '#a8d4f0', color: '#4ECDC4' },
  { val: 3, label: '疲惫', icon: '/img/疲惫.png', bg: '#d4c4f0', color: '#95E1D3' },
  { val: 6, label: '焦虑', icon: '/img/焦虑.png', bg: '#f0e8b8', color: '#A29BFE' },
  { val: 4, label: '难过', icon: '/img/难过.png', bg: '#f8c9d8', color: '#74B9FF' },
  { val: 5, label: '生气', icon: '/img/生气.png', bg: '#f0b98c', color: '#FDCB6E' },
  { val: 9, label: '其他', icon: '/img/其他.png', bg: '#e0e0e0', color: '#B2BEC3' },
]

/** 情绪指数（0~100）：与后端 EMOTION_INDEX 完全一致 */
export const EMOTION_INDEX = { 1: 100, 2: 70, 3: 50, 4: 20, 5: 25, 6: 40, 7: 90, 8: 85, 9: 55 }

/** 取情绪指数，未知值兜底 50 */
export const emotionScore = (v) => EMOTION_INDEX[v] ?? 50

/** 积极 / 消极情绪（与后端 POSITIVE / NEGATIVE 一致） */
export const POSITIVE_EMOTIONS = [1, 2, 7, 8]
export const NEGATIVE_EMOTIONS = [3, 4, 5, 6]

/** 按编号取情绪对象（找不到时返回「其他」） */
export const emotionInfo = (v) => EMOTIONS.find(e => e.val === v) || EMOTIONS[EMOTIONS.length - 1]

/** 按编号取情绪图标 */
export const emotionIcon = (v) => EMOTIONS.find(e => e.val === v)?.icon || '/img/其他.png'

/** 按标签取情绪对象（后端返回的是名字，图表配色要反查） */
export const emotionByLabel = (label) => EMOTIONS.find(e => e.label === label)
