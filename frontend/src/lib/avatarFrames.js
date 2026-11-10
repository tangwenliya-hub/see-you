// 头像装饰框【唯一真源】：名字/图片只在这一份里维护
// 各页面从这里导入，scale 由页面本地覆盖（不同场景头像大小不同）
export const AVATAR_FRAMES = [
  { name: 'none',   label: '无',       img: '' },
  { name: 'flower', label: '小猫咪',   img: '/img/头像框.png' },
  { name: 'leaf',   label: '生气',     img: '/img/头像框2.png' },
  { name: 'crown',  label: '冰淇淋云', img: '/img/头像框3.png' },
  { name: 'star',   label: '认识我',   img: '/img/头像框4.png' },
  { name: 'heart',  label: '雪球猫',   img: '/img/头像框5.png' },
  { name: 'halo',   label: '猫咪花环', img: '/img/头像框6.png' },
  { name: 'cloud',  label: '蔷薇花环', img: '/img/头像框7.png' },
]

// 个人中心(Profile)同款尺寸，作为全站默认；需要差异的页面可自行覆盖
export const FRAME_SCALES = { none: 0, flower: 110, leaf: 165, crown: 205, star: 150, heart: 150, halo: 145, cloud: 165 }
