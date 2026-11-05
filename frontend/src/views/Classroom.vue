<template>
  <div class="classroom-page">
    <!-- 顶部标题（动漫风横幅，颜色跟随个人中心主题色） -->
    <AnimeHero emoji="🎬" title="绪见学堂" sub="心理疗愈视频播放" />

    <!-- ========== 列表视图（封面卡片网格，与心理FM同构） ========== -->
    <div class="list-view" v-if="!showPlayer">
    <!-- 分类标签 -->
    <div class="cat-tabs">
      <span :class="['cat-tab', activeCat === '' ? 'on' : '']" @click="switchCat('')">全部</span>
      <span v-for="c in categories" :key="c" :class="['cat-tab', activeCat === c ? 'on' : '']" @click="switchCat(c)">{{ c }}</span>
      <div class="search-box">
        <input v-model="searchKeyword" placeholder="搜索视频..." @input="onSearchInput" />
      </div>
    </div>

      <div v-if="loading" class="list-loading"><CowLoading size="sm" text="加载中" /></div>
      <template v-else>
        <div class="card-grid">
          <div v-for="(v, idx) in list" :key="v.videoId" class="video-card" @click="openPlayer(idx)">
            <div class="card-cover-wrap">
              <img v-if="v.coverImg" :src="getCover(v.coverImg)" @error="onCoverErr" class="card-cover" />
              <div v-else class="card-cover card-ph">🎬</div>
              <div class="card-play-overlay"><span class="card-play-icon">▶</span></div>
              <span class="card-dur" v-if="v.duration">⏱ {{ fmtDuration(v.duration) }}</span>
            </div>
            <div class="card-info">
              <div class="card-title">{{ v.videoName }}</div>
              <div class="card-cat">
                {{ v.category }} · ▶ {{ v.playCount || 0 }}次
                <span v-if="getSavedProgress(v.videoId)" class="card-progress">· 上次看到 {{ fmtTime(getSavedProgress(v.videoId)) }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="list.length === 0" class="empty-tip">暂无视频，请到后台添加~</div>
      </template>
    </div>

    <!-- ========== 播放视图（全屏沉浸式，与心理FM播放器同构） ========== -->
    <div class="player-view" v-if="showPlayer && current">
      <div class="bg-blur" :style="{ backgroundImage: 'url(' + (current.coverImg ? getCover(current.coverImg) : '') + ')' }"></div>
      <div class="bg-overlay"></div>

      <!-- 顶栏：返回 + 页面名（播放控制在视频下方，优酷式） -->
      <div class="player-top">
        <button class="back-btn" @click="closePlayer" title="返回列表">← 返回</button>
        <div class="player-title">
          <div class="pt-name">绪见学堂</div>
          <div class="pt-cat">正在播放 · 共 {{ list.length }} 个视频</div>
        </div>
      </div>

      <!-- 两栏布局：左视频区 + 右信息栏（优酷式） -->
      <div class="player-body">
        <div class="player-layout">
          <div class="player-main">
            <div class="player-card">
              <video ref="videoRef" :src="getVideoUrl(current.videoUrl)" :poster="getCover(current.coverImg)" controls
                     class="video-player"
                     @timeupdate="saveProgress"
                     @ended="onEnded"
                     @loadedmetadata="onVideoLoad"
                     @error="onVideoError"></video>
              <div class="resume-bar" v-if="savedProgress > 0 && !resumed">
                <span>📌 上次看到 {{ fmtTime(savedProgress) }}</span>
                <button @click="resumePlay">继续观看</button>
                <button class="skip" @click="skipResume">从开头</button>
              </div>
            </div>
          </div>

          <!-- 单行信息条：标题+元信息 居左 ｜ 播放控制居中 ｜ 点赞收藏分享 居右 -->
          <div class="video-head">
            <div class="vh-row">
                <div class="vh-left">
                  <h1 class="vh-name">{{ current.videoName }}</h1>
                  <span class="tag">{{ current.category }}</span>
                  <span class="vh-plays">▶ {{ current.playCount || 0 }}次播放</span>
                </div>

                <div class="video-ctrl">
              <button class="ctrl-btn" title="上一个" @click="prev">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M19 20L9 12l10-8v16z" fill="currentColor" stroke="none"/>
                  <path d="M5 19V5"/>
                </svg>
              </button>
              <button class="ctrl-btn main" :title="playing ? '暂停' : '播放'" @click="togglePlay">
                <svg v-if="playing" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                  <rect x="6.5" y="5" width="4" height="14" rx="1.6"/>
                  <rect x="13.5" y="5" width="4" height="14" rx="1.6"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="currentColor" stroke="none">
                  <path d="M8 5.5c0-1.1 1.2-1.8 2.2-1.2l10 6.5c.9.6.9 1.9 0 2.5l-10 6.5c-1 .6-2.2-.1-2.2-1.2V5.5z"/>
                </svg>
              </button>
              <button class="ctrl-btn" title="下一个" @click="next">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M5 4l10 8-10 8V4z" fill="currentColor" stroke="none"/>
                  <path d="M19 5v14"/>
                </svg>
              </button>
              <!-- 倍速选择 -->
              <div class="rate-wrap">
                <button class="rate-btn" :class="{ on: playRate !== 1 }" @click.stop="rateMenu = !rateMenu">
                  {{ fmtRate(playRate) }}
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M6 9l6 6 6-6"/></svg>
                </button>
                <transition name="rate-fade">
                  <div v-if="rateMenu" class="rate-menu" @click.stop>
                    <button v-for="r in RATES" :key="r" :class="{ on: playRate === r }" @click="setRate(r)">
                      {{ fmtRate(r) }}<span v-if="playRate === r" class="rate-check">✓</span>
                    </button>
                  </div>
                </transition>
              </div>
              </div>
                <div class="info-actions">
                  <button class="act-btn" :class="{ on: liked }" @click="toggleLike">
                    {{ liked ? '❤️' : '🤍' }} 点赞 <span class="act-num">{{ likeCount }}</span>
                  </button>
                  <button class="act-btn" :class="{ on: favorited }" @click="toggleFav">
                    {{ favorited ? '⭐' : '☆' }} 收藏 <span class="act-num">{{ favCount }}</span>
                  </button>
                  <button class="act-btn" @click="openShare">🔗 分享</button>
                </div>
              </div>
          </div>

          <!-- 右：简介 / 评论 Tab + 相关推荐列表（优酷式侧栏，与视频等高） -->
          <aside class="player-side">
          <div class="side-tabs">
            <button class="side-tab" :class="{ on: sideTab === 'intro' }" @click="sideTab = 'intro'">简介</button>
            <button class="side-tab" :class="{ on: sideTab === 'comment' }" @click="sideTab = 'comment'">
              评论<span class="tab-num" v-if="commentTotal"> {{ commentTotal }}</span>
            </button>
          </div>
          <div class="side-scroll">
            <template v-if="sideTab === 'intro'">
              <div class="video-desc" v-if="current.description">
                <div class="desc-label">📖 视频简介</div>
                <div class="desc-text" :class="{ collapsed: !descExpanded && shouldCollapseDesc }">{{ current.description }}</div>
                <button v-if="shouldCollapseDesc" class="desc-toggle" @click="descExpanded = !descExpanded">
                  {{ descExpanded ? '收起 ▲' : '展开 ▼' }}
                </button>
              </div>
              <div class="side-sec-title" v-if="relatedVideos.length">✨ 相关推荐</div>
              <div class="rel-list" v-if="relatedVideos.length">
                <div v-for="v in relatedVideos" :key="'rel-' + v.videoId" class="rel-item" @click="playRelated(v)">
                  <div class="rel-thumb">
                    <img v-if="v.coverImg" :src="getCover(v.coverImg)" @error="onCoverErr" />
                    <span v-else class="rel-ph">🎬</span>
                    <span class="rel-dur" v-if="v.duration">{{ fmtDuration(v.duration) }}</span>
                  </div>
                  <div class="rel-info">
                    <div class="rel-name">{{ v.videoName }}</div>
                    <div class="rel-meta">{{ v.category }} · ▶ {{ v.playCount || 0 }}次播放</div>
                  </div>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="comment-input">
                <input v-model="newComment" placeholder="说点什么吧..." @keyup.enter="submitComment" />
                <button class="send-btn" @click="submitComment">发送</button>
              </div>
              <div v-if="commentLoading" class="comment-loading">加载评论中...</div>
              <div v-else-if="comments.length > 0" class="comment-list">
                <div v-for="(c, i) in comments" :key="c.commentId || i" class="comment-item" :class="{ pending: c.status === 0, rejected: c.status === -1 }">
                  <div class="c-avatar">
                    <img v-if="c.avatar" :src="getAvatarUrl(c.avatar)" />
                    <span v-else>🧑</span>
                  </div>
                  <div class="c-body">
                    <div class="c-user">
                      {{ c.nickname || '用户' + (c.userId || '') }}
                      <span v-if="c.status === 0" class="c-pending" title="仅自己可见，等待管理员审核">待审核</span>
                      <span v-else-if="c.status === -1" class="c-rejected" title="未通过管理员审核，仅自己可见">未通过审核</span>
                    </div>
                    <div class="c-text">{{ c.commentText }}</div>
                    <div class="c-time">{{ c.createTime ? new Date(c.createTime).toLocaleString('zh-CN', { hour12: false }) : '' }}</div>
                  </div>
                </div>
                <!-- 分页 -->
                <div v-if="commentTotal > commentPageSize" class="comment-pager">
                  <button :disabled="commentPage <= 1" @click="onCommentPageChange(commentPage - 1)">上一页</button>
                  <span class="page-info">{{ commentPage }} / {{ Math.ceil(commentTotal / commentPageSize) }}</span>
                  <button :disabled="commentPage >= Math.ceil(commentTotal / commentPageSize)" @click="onCommentPageChange(commentPage + 1)">下一页</button>
                </div>
              </div>
              <div v-else class="comment-empty">还没有评论，来抢沙发吧~</div>
            </template>
          </div>
        </aside>
        </div>

      </div>
    </div>

    <!-- 分享弹窗 -->
    <div v-if="shareVisible" class="share-mask" @click.self="shareVisible = false">
      <div class="share-dialog">
        <h3>🔗 分享给朋友</h3>
        <p class="share-title">{{ current?.videoName }}</p>
        <div class="share-link-row">
          <input ref="shareLinkRef" :value="shareLink" readonly />
          <button @click="copyLink">{{ copied ? '✅ 已复制' : '复制' }}</button>
        </div>
        <div class="share-platforms">
          <button @click="shareTo('weibo')">🟧 微博</button>
          <button @click="shareTo('qq')">💬 QQ</button>
          <button @click="shareTo('wechat')">💚 微信</button>
          <button @click="nativeShare">📤 系统分享</button>
        </div>
        <div class="share-close" @click="shareVisible = false">关闭</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import request, { playVideo } from '@/api'
import AnimeHero from '@/components/AnimeHero.vue'
import { getCommentListByResource, saveComment, getLikeList, saveLike, cancelLike, getLikeCount, getCollectList, saveCollect, cancelCollect, getCollectCount, fetchAllPages } from '@/api'
import '../styles/classroom.css'
import CowLoading from '@/components/CowLoading.vue'

// 页面加载：预拉取收藏和点赞，然后加载视频列表（同时提取全部分类）
const route = useRoute()
onMounted(async () => {
  await Promise.all([loadFavIds(), loadLikeIds()])
  await loadData(true) // true = 是首次加载，需要提取分类（await：深链定位需要列表就绪）
  // 深链支持：/classroom?videoId=xx → 直接播放对应视频（主页推荐卡点进来）
  const qId = Number(route.query.videoId)
  if (qId) {
    const idx = list.value.findIndex(v => v.videoId === qId)
    if (idx >= 0) openPlayer(idx)
  }
})

const videoRef = ref(null)
const playing = ref(false)
const showPlayer = ref(false)   // FM 同构：false=封面卡片列表，true=全屏播放视图
const list = ref([])
const current = ref(null)
const activeCat = ref('')
const searchKeyword = ref('')
const savedProgress = ref(0)
const resumed = ref(false)
const allCategories = ref([]) // 全部分类（从完整列表提取，不因搜索而变化）

// 点赞收藏状态（用 current.videoId 做 key）
const liked = ref(false)
const likeCount = ref(0)
const favorited = ref(false)
const newComment = ref('')
const comments = ref([])
const commentPage = ref(1)
const commentPageSize = 10
const commentTotal = ref(0)
const commentLoading = ref(false)
const loading = ref(false)
const descExpanded = ref(false)
const favCount = ref(0)
// 倍速播放
const RATES = [0.5, 0.75, 1, 1.25, 1.5, 2]
const playRate = ref(1)
const rateMenu = ref(false)
// 右侧栏 Tab：简介 / 评论（优酷式）
const sideTab = ref('intro')
const fmtRate = (r) => (r === 1 ? '倍速 1.0x' : r + 'x')
const setRate = (r) => {
  playRate.value = r
  rateMenu.value = false
  if (videoRef.value) videoRef.value.playbackRate = r
}
// 点击空白处收起倍速菜单
const onDocClick = () => { rateMenu.value = false }
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

// 相关推荐：除当前视频外的其它视频（管理员上传的均为独立单集，最多 8 个）
const relatedVideos = computed(() => {
  const cur = current.value
  if (!cur) return []
  return list.value.filter(v => v.videoId !== cur.videoId).slice(0, 8)
})
// 播放指定视频（推荐条共用）
const playEp = (v) => {
  const idx = list.value.findIndex(x => x.videoId === v.videoId)
  if (idx >= 0) playAt(idx)
}
const playRelated = (v) => playEp(v)
const playedVideoIds = new Set() // 本次会话已算过播放量的视频，避免重复计数

// 分类从完整数据中提取（不因搜索而变化）
const categories = computed(() => allCategories.value)

// 数据已由后端过滤，直接用 list
const currentIndex = computed(() => {
  if (!current.value) return 0
  return list.value.findIndex(v => v.videoId === current.value.videoId)
})

// 简介超过80字才显示展开按钮
const shouldCollapseDesc = computed(() => {
  return current.value?.description?.length > 80
})

const getCover = (coverImg) => {
  if (!coverImg) return ''
  if (coverImg.startsWith('http') || coverImg.startsWith('/')) return coverImg
  return '/cover/' + coverImg
}

// 封面加载失败兜底（路径迁移/文件丢失时显示默认图，避免破图）
const onCoverErr = (e) => {
  const img = e.target
  if (!img || img.dataset.fallback) return
  img.dataset.fallback = '1'
  img.src = '/img/注册1.jpg'
}

const getVideoUrl = (videoUrl) => {
  if (!videoUrl) return ''
  if (videoUrl.startsWith('http') || videoUrl.startsWith('/')) return videoUrl
  return '/video/' + videoUrl
}

const onVideoError = () => {
  console.warn('视频加载失败')
}

const getAvatarUrl = (avatar) => {
  if (!avatar) return ''
  if (avatar.startsWith('http') || avatar.startsWith('/')) return avatar
  return '/avatar/' + avatar
}

const fmtTime = (s) => {
  if (!s || !isFinite(s)) return '0:00'
  s = Math.floor(s); const m = Math.floor(s / 60); const r = s % 60
  return m + ':' + (r < 10 ? '0' : '') + r
}

const fmtDuration = (s) => {
  if (!s) return '0:00'
  // 如果是字符串且包含冒号（如 "05:30"、"1:23:45"），直接返回
  if (typeof s === 'string' && s.includes(':')) return s
  // 如果是数字秒数，格式化
  const num = Number(s)
  if (!isFinite(num) || num <= 0) return '0:00'
  const h = Math.floor(num / 3600)
  const m = Math.floor((num % 3600) / 60)
  const r = Math.floor(num % 60)
  if (h > 0) return h + ':' + (m < 10 ? '0' : '') + m + ':' + (r < 10 ? '0' : '') + r
  return m + ':' + (r < 10 ? '0' : '') + r
}

// ===== localStorage 存取 =====
const STORAGE_KEY = 'classroom_data'
const loadStorage = () => {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') }
  catch { return {} }
}
const saveStorage = (data) => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
}

const getSavedProgress = (vid) => {
  const all = loadStorage()
  return all.progress?.[vid] || 0
}
let lastSyncSec = -99
const saveProgressLocal = () => {
  if (!current.value || !videoRef.value) return
  const sec = Math.floor(videoRef.value.currentTime)
  const all = loadStorage(); all.progress = all.progress || {}
  all.progress[current.value.videoId] = sec; saveStorage(all)
  // 每 10 秒同步后端（防抖）
  if (sec - lastSyncSec >= 10) {
    lastSyncSec = sec
    const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
    if (info) request.post('/history/progress', {
      userId: info.userId, resourceType: 2, resourceId: current.value.videoId,
      playProgress: sec, duration: Math.floor(videoRef.value.duration || 0), isFinished: 0,
    }).catch(() => {})
  }
}

const toggleLike = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) { alert('请先登录哦~'); return }
  if (!current.value) return
  const videoId = current.value.videoId
  // 先乐观更新UI，让用户立刻看到反馈
  const prevLiked = liked.value
  const prevCount = likeCount.value
  liked.value = !prevLiked
  likeCount.value = prevLiked ? Math.max(0, prevCount - 1) : prevCount + 1
  if (!prevLiked) likedVideoIds.add(videoId); else likedVideoIds.delete(videoId)
  // 同步本地备份
  const all0 = loadStorage(); all0.likes = all0.likes || {}
  if (liked.value) all0.likes[videoId] = true; else delete all0.likes[videoId]
  saveStorage(all0)

  try {
    let res
    if (!prevLiked) {
      res = await saveLike({ userId: info.userId, resourceType: 2, resourceId: videoId })
    } else {
      res = await cancelLike({ userId: info.userId, resourceType: 2, resourceId: videoId })
    }
    if (res.code !== 200) {
      // 后端失败，回滚状态
      liked.value = prevLiked
      likeCount.value = prevCount
      if (prevLiked) likedVideoIds.add(videoId); else likedVideoIds.delete(videoId)
      const all = loadStorage(); all.likes = all.likes || {}
      if (prevLiked) all.likes[videoId] = true; else delete all.likes[videoId]
      saveStorage(all)
      alert(res.msg || '操作失败，请重试')
    }
  } catch (e) {
    console.warn('点赞操作失败', e)
    // 网络异常，保留本地状态即可
  }
}
// 视频收藏状态缓存 Set（从后端 collect 表查出来的 videoId 集合）
const favVideoIds = new Set()
// 视频点赞状态缓存 Set（从后端 like 表查出来的 videoId 集合）
const likedVideoIds = new Set()

const loadFavIds = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return
  try {
    // 翻页拉全（后端单页封顶 100）
    const list = await fetchAllPages(p => getCollectList({ userId: info.userId, page: p, size: 100 }))
    favVideoIds.clear()
    list.forEach(c => { if (c.resourceType === 2) favVideoIds.add(c.resourceId) })
  } catch (e) { console.warn('加载收藏列表失败', e) }
}

const loadLikeIds = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return
  try {
    const list = await fetchAllPages(p => getLikeList({ userId: info.userId, resourceType: 2, page: p, size: 100 }))
    likedVideoIds.clear()
    list.forEach(l => { if (l.resourceType === 2) likedVideoIds.add(l.resourceId) })
  } catch (e) { console.warn('加载点赞列表失败', e) }
}

const toggleFav = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) { alert('请先登录哦~'); return }
  if (!current.value) return
  const videoId = current.value.videoId
  // 先乐观更新UI
  const prevFav = favorited.value
  const prevCount = favCount.value
  favorited.value = !prevFav
  favCount.value = prevFav ? Math.max(0, prevCount - 1) : prevCount + 1
  if (!prevFav) favVideoIds.add(videoId); else favVideoIds.delete(videoId)
  // 同步本地备份
  const all0 = loadStorage(); all0.favs = all0.favs || {}
  if (favorited.value) all0.favs[videoId] = true; else delete all0.favs[videoId]
  saveStorage(all0)

  try {
    let res
    if (!prevFav) {
      res = await saveCollect({ userId: info.userId, resourceType: 2, resourceId: videoId })
    } else {
      res = await cancelCollect({ userId: info.userId, resourceType: 2, resourceId: videoId })
    }
    if (res.code !== 200) {
      // 后端失败，回滚状态
      favorited.value = prevFav
      favCount.value = prevCount
      if (prevFav) favVideoIds.add(videoId); else favVideoIds.delete(videoId)
      const all = loadStorage(); all.favs = all.favs || {}
      if (prevFav) all.favs[videoId] = true; else delete all.favs[videoId]
      saveStorage(all)
      alert(res.msg || '操作失败，请重试')
    }
  } catch (e) {
    console.warn('收藏操作失败', e)
    // 网络异常，保留本地状态即可
  }
}

// ===== 分享功能 =====
const shareVisible = ref(false)
const copied = ref(false)
const shareLinkRef = ref(null)
// 路由是 history 模式，分享链接必须是 /classroom?videoId=xx
const shareLink = computed(() => {
  const id = current.value?.videoId
  return id ? window.location.origin + '/classroom?videoId=' + id : window.location.origin + '/classroom'
})
const openShare = () => { copied.value = false; shareVisible.value = true }
const copyLink = async () => {
  try { await navigator.clipboard.writeText(shareLink.value) }
  catch { shareLinkRef.value?.select(); document.execCommand('copy') }
  copied.value = true
  later(() => copied.value = false, 2000)
}
const nativeShare = async () => {
  if (navigator.share) {
    try { await navigator.share({ title: current.value?.videoName, url: shareLink.value }) } catch {}
  } else { copyLink() }
}
const shareTo = (platform) => {
  const title = encodeURIComponent(current.value?.videoName || '绪见学堂')
  const url = encodeURIComponent(shareLink.value)
  let link = ''
  if (platform === 'weibo') link = `https://service.weibo.com/share/share.php?title=${title}&url=${url}`
  else if (platform === 'qq') link = `https://connect.qq.com/widget/shareqq/index.html?title=${title}&url=${url}`
  else if (platform === 'wechat') { copyLink(); alert('链接已复制，打开微信粘贴分享给好友！'); return }
  if (link) window.open(link, '_blank', 'width=600,height=500')
}

const loadLikeFav = async (vid, page = 1) => {
  const all = loadStorage()
  // 切换视频时重置简介展开状态
  descExpanded.value = false
  // 点赞优先用后端数据，没有则用本地
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (info && likedVideoIds.size > 0) {
    liked.value = likedVideoIds.has(vid)
  } else {
    liked.value = !!all.likes?.[vid]
  }
  favorited.value = favVideoIds.has(vid)
  // 加载点赞数
  try {
    const res = await getLikeCount({ resourceType: 2, resourceId: vid })
    if (res.code === 200) likeCount.value = res.data || 0
  } catch (e) { console.warn('加载点赞数失败', e) }
  // 加载收藏数
  try {
    const res = await getCollectCount({ resourceType: 2, resourceId: vid })
    if (res.code === 200) favCount.value = res.data || 0
  } catch (e) { console.warn('加载收藏数失败', e) }
  // 从后端加载评论（带用户信息，分页）
  commentLoading.value = true
  try {
    const res = await getCommentListByResource({ page, size: commentPageSize, resourceType: 2, resourceId: vid })
    if (res.code === 200) {
      comments.value = res.data?.records || []
      commentTotal.value = res.data?.total || 0
      commentPage.value = page
    } else {
      throw new Error(res.msg)
    }
  } catch (e) {
    console.warn('加载评论失败', e)
    // 失败则用本地评论
    const allComments = all.comments?.[vid] || []
    const start = (page - 1) * commentPageSize
    comments.value = allComments.slice(start, start + commentPageSize)
    commentTotal.value = allComments.length
    commentPage.value = page
  } finally {
    commentLoading.value = false
  }
}

const onCommentPageChange = (p) => {
  if (current.value) loadLikeFav(current.value.videoId, p)
}

const submitComment = async () => {
  const text = newComment.value.trim()
  if (!text) return
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) { alert('请先登录哦~'); return }
  try {
    const res = await saveComment({
      userId: info.userId,
      resourceType: 2,
      resourceId: current.value.videoId,
      commentText: text,
      status: 0,
    })
    if (res.code === 200) {
      newComment.value = ''
      ElMessage.success('评论已提交，审核通过后会公开显示')
      // 刷新列表（后端已支持本人可见自己的待审核评论）
      loadLikeFav(current.value.videoId, commentPage.value)
    } else {
      alert(res.msg || '评论提交失败，请重试')
    }
  } catch (e) {
    console.warn('评论失败', e)
    alert('网络异常，评论发送失败，请稍后再试')
  }
}

// ===== 视频进度 =====
const saveProgress = () => {
  if (videoRef.value) {
    playing.value = !videoRef.value.paused
    saveProgressLocal()
  }
}
const onVideoLoad = () => {
  if (current.value) {
    savedProgress.value = getSavedProgress(current.value.videoId)
    resumed.value = false
    // 切换视频后浏览器会把倍速重置为 1，这里恢复用户选择的倍速
    if (videoRef.value && videoRef.value.playbackRate !== playRate.value) {
      videoRef.value.playbackRate = playRate.value
    }
  }
}
const resumePlay = () => {
  if (videoRef.value && savedProgress.value > 0) {
    videoRef.value.currentTime = savedProgress.value
    videoRef.value.play().then(() => {
      playing.value = true
    }).catch(() => {
      // 浏览器阻止了自动播放，保持暂停状态，用户手动点播放
      playing.value = false
    })
    resumed.value = true
  }
}
const skipResume = () => {
  resumed.value = true
  videoRef.value?.play().then(() => {
    playing.value = true
  }).catch(() => {
    playing.value = false
  })
}
const onEnded = () => {
  playing.value = false
  if (current.value) {
    // 🔴 标记播放完
    const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
    if (info) request.post('/history/progress', {
      userId: info.userId, resourceType: 2, resourceId: current.value.videoId,
      playProgress: Math.floor(videoRef.value.duration || 0), duration: Math.floor(videoRef.value.duration || 0), isFinished: 1,
    }).catch(() => {})
    const all = loadStorage()
    if (all.progress) delete all.progress[current.value.videoId]
    saveStorage(all)
  }
  // 自动连播下一集
  later(() => { next() }, 500)
}

// ===== 播放逻辑 =====
const loadData = async (isFirst = false) => {
  loading.value = true
  const start = Date.now()
  try {
    // 首次加载拉全部（用于提取分类），之后拉 200 条够用
    const params = { page: 1, size: isFirst ? 500 : 200 }
    if (activeCat.value) params.category = activeCat.value
    if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
    const res = await request.get('/video/list', { params })
    if (res.code === 200) {
      const records = Array.isArray(res.data) ? res.data : (res.data?.records || [])
      list.value = records.map(v => ({
        videoId: v.videoId || v.id,
        videoName: v.videoName || v.video_name || '-',
        videoUrl: v.videoUrl || v.video_url || '',
        category: v.category || '',
        coverImg: v.coverImg || v.cover_img || '',
        playCount: v.playCount || v.play_count || 0,
        description: v.description || '',
        duration: v.duration || 0,
      }))
      // 首次加载顺便提取全部分类
      if (isFirst) {
        allCategories.value = [...new Set(records.map(v => v.category).filter(c => c))]
      }
      // 卡片网格列表页不自动进播放器；仅当当前视频已不在列表里时清空
      if (current.value) {
        const exists = list.value.some(v => v.videoId === current.value.videoId)
        if (!exists) current.value = null
      }
    }
  } catch (e) { console.warn('加载视频列表失败:', e) }
  finally {
    // 最小显示 250ms，避免 loading 闪一下
    const elapsed = Date.now() - start
    if (elapsed < 250) {
      later(() => { loading.value = false }, 250 - elapsed)
    } else {
      loading.value = false
    }
  }
}

// 定时器统一登记：组件卸载时全部清理（防止内存泄漏与卸载后执行）
const pageTimers = new Set()
const later = (fn, ms) => {
  const id = setTimeout(() => { pageTimers.delete(id); fn() }, ms)
  pageTimers.add(id)
  return id
}
onBeforeUnmount(() => { pageTimers.forEach(clearTimeout); pageTimers.clear() })

// 离开学堂页面：保存进度、暂停视频、复位播放器状态（防止评论/播放状态跨页面残留）
onBeforeRouteLeave(() => {
  if (showPlayer.value) closePlayer()
})

// 搜索防抖
let searchTimer = null
const onSearchInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pageTimers.delete(searchTimer)
    loadData()
  }, 400)
  pageTimers.add(searchTimer)
}

// 写历史（resourceType: 1=音频 2=视频）
let lastHistVideoId = null
const saveHistory = async (resourceType, resourceId) => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return
  try {
    await request.post('/history/save', { userId: info.userId, resourceType, resourceId })
  } catch (e) { console.warn('保存历史失败', e) }
}

/* 列表卡片 → 打开全屏播放视图 */
const openPlayer = (idx) => {
  playAt(idx)
  showPlayer.value = true
}
/* 播放视图 → 返回列表（保存进度并暂停，不退出页面） */
const closePlayer = () => {
  saveProgressLocal()
  videoRef.value?.pause()
  playing.value = false
  showPlayer.value = false
}

const playAt = async (idx) => {
  const arr = list.value
  if (!arr[idx]) return
  saveProgressLocal()
  // 切换视频：立即清空上一个视频的评论/输入/分页/简介展开，避免旧内容残留
  comments.value = []
  commentTotal.value = 0
  commentPage.value = 1
  newComment.value = ''
  descExpanded.value = false
  commentLoading.value = true
  const vid = arr[idx].videoId
  current.value = arr[idx]
  // 🔴 写历史
  if (lastHistVideoId !== vid) {
    lastHistVideoId = vid
    saveHistory(2, vid)
    later(() => { lastHistVideoId = null }, 5000)
  }
  // 🔴 播放量 +1（同一会话同一视频只算一次）
  if (!playedVideoIds.has(vid)) {
    playedVideoIds.add(vid)
    playVideo(vid).catch(() => {})
    const item = list.value.find(v => v.videoId === vid)
    if (item) item.playCount = (item.playCount || 0) + 1
  }
  savedProgress.value = getSavedProgress(vid)
  resumed.value = false
  loadLikeFav(vid)
  // 自动播放
  nextTick(() => {
    if (videoRef.value) {
      videoRef.value.play().then(() => {
        playing.value = true
      }).catch(() => {
        playing.value = false
      })
    }
  })
}

const togglePlay = () => {
  const v = videoRef.value
  if (!v) return
  if (playing.value) { v.pause(); playing.value = false }
  else { v.play(); playing.value = true }
}
const prev = () => {
  const arr = list.value
  if (!arr.length) return
  const i = arr.findIndex(v => current.value && v.videoId === current.value.videoId)
  playEp(arr[i <= 0 ? arr.length - 1 : i - 1])
}
const next = () => {
  const arr = list.value
  if (!arr.length) return
  const i = arr.findIndex(v => current.value && v.videoId === current.value.videoId)
  playEp(arr[i >= arr.length - 1 ? 0 : i + 1])
}
const switchCat = (c) => {
  activeCat.value = c
  searchKeyword.value = ''
  playing.value = false
  loadData()
}
</script>