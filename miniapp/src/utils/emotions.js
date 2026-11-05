/**
 * 情绪字典 —— 与主项目 frontend/src/utils/constants.js 严格同步
 *
 * ⚠️ 这是全站公认的「情绪字典唯一真源」的第 N 份拷贝。
 * 后端有 EmotionConst，前端有 constants.js，小程序这里是第三处。
 * 后端已有 EmotionDictConsistencyTest 在做前后端比对（自动读前端文件校验），
 * **改这里之前请先改前端 constants.js，否则后端测试会失败。**
 *
 *   1 开心  2 平静  3 疲惫  4 难过  5 生气  6 焦虑  7 兴奋  8 满足  9 其他
 *
 * icon：与 H5 同款表情贴纸（src/static/emo，按情绪编号命名）；
 * soft / ring / shadow：H5 mood-btn「今日已打卡」态的颜色，
 * 即 color-mix(in srgb, bg 45%/60%, white) 的预计算值（WXSS 不支持 color-mix）。
 */
export const EMOTIONS = [
  { val: 1, label: '开心', emoji: '😄', bg: '#b8d49a', icon: '/static/emo/e1.png', soft: '#DFECD2', ring: '#D4E5C2', shadow: 'rgba(184, 212, 170, 0.55)' },
  { val: 7, label: '兴奋', emoji: '🤩', bg: '#ffd09a', icon: '/static/emo/e7.png', soft: '#FFEAD2', ring: '#FFE3C2', shadow: 'rgba(255, 208, 154, 0.55)' },
  { val: 8, label: '满足', emoji: '😌', bg: '#ffc9d8', icon: '/static/emo/e8.png', soft: '#FFE7ED', ring: '#FFDFE8', shadow: 'rgba(255, 201, 216, 0.55)' },
  { val: 2, label: '平静', emoji: '😊', bg: '#a8d4f0', icon: '/static/emo/e2.png', soft: '#D8ECF8', ring: '#CBE5F6', shadow: 'rgba(168, 212, 240, 0.55)' },
  { val: 3, label: '疲惫', emoji: '😪', bg: '#d4c4f0', icon: '/static/emo/e3.png', soft: '#ECE4F8', ring: '#E5DCF6', shadow: 'rgba(212, 196, 240, 0.55)' },
  { val: 6, label: '焦虑', emoji: '😰', bg: '#f0e8b8', icon: '/static/emo/e6.png', soft: '#F8F5DF', ring: '#F6F1D4', shadow: 'rgba(240, 232, 184, 0.55)' },
  { val: 4, label: '难过', emoji: '😢', bg: '#f8c9d8', icon: '/static/emo/e4.png', soft: '#FCE7ED', ring: '#FBDFE8', shadow: 'rgba(248, 201, 216, 0.55)' },
  { val: 5, label: '生气', emoji: '😠', bg: '#f0b98c', icon: '/static/emo/e5.png', soft: '#F8E0CB', ring: '#F6D5BA', shadow: 'rgba(240, 185, 140, 0.55)' },
  { val: 9, label: '其他', emoji: '😐', bg: '#e0e0e0', icon: '/static/emo/e9.png', soft: '#F1F1F1', ring: '#ECECEC', shadow: 'rgba(224, 224, 224, 0.55)' },
]

export const EMOTION_INDEX = { 1: 100, 2: 70, 3: 50, 4: 20, 5: 25, 6: 40, 7: 90, 8: 85, 9: 55 }

/** 按编号取情绪对象（找不到时返回「其他」） */
export const emotionInfo = (v) => EMOTIONS.find((e) => e.val === v) || EMOTIONS[EMOTIONS.length - 1]

/** 强度：1 轻度 / 2 中度 / 3 重度（与 H5 主页强度选择一致） */
export const INTENSITY = [
  { val: 1, label: '轻度', desc: '有一点点', bg: '#b8d49a' },
  { val: 2, label: '中度', desc: '比较明显', bg: '#ffd09a' },
  { val: 3, label: '重度', desc: '非常强烈', bg: '#f0b98c' },
]
