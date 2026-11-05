<template>
  <div class="fm-page">
    <!-- 顶部横幅：放在 list-view 外面，贴顶不留白边（list-view 自带 padding） -->
    <AnimeHero v-if="!showPlayer" emoji="📻" title="心理FM" sub="以前:我主动走向你，希望你来接住我;我的快乐，由你决定。现在:我可以主动走向你，也可以随时转身;我喜欢你，但我依然是完整独立的我。" />

    <!-- ========== 列表视图（卡片网格） ========== -->
    <div class="list-view" v-if="!showPlayer">
      <div class="cat-tabs">
        <span :class="['cat-tab', activeCat === '' ? 'on' : '']" @click="switchCat('')">全部</span>
        <span v-for="c in categories" :key="c" :class="['cat-tab', activeCat === c ? 'on' : '']" @click="switchCat(c)">{{ c }}</span>
        <div class="fm-search">
          <input v-model="searchKeyword" placeholder="搜索音频..." @input="onSearchInput" />
        </div>
      </div>

      <div v-if="loading" class="fm-loading">加载中...</div>
      <template v-else>
      <div class="card-grid">
        <div v-for="(a, idx) in pagedList" :key="a.audioId" class="song-card"
             :class="{ active: current?.audioId === a.audioId }"
             @click="openPlayer((pageNum-1)*pageSize + idx)">
          <div class="card-cover-wrap">
            <img :src="getCover(a.coverImg)" @error="onCoverErr" class="card-cover" />
            <div class="card-play-overlay">
              <span class="card-play-icon">{{ current?.audioId === a.audioId && playing ? '❚❚' : '▶' }}</span>
            </div>
          </div>
          <div class="card-info">
            <div class="card-title">{{ a.audioName }}</div>
            <div class="card-cat">{{ a.category }}</div>
          </div>
        </div>
      </div>

      <div v-if="filteredList.length === 0" class="empty-tip">暂无音频，请在后台管理添加~</div>

      <div v-if="filteredList.length > pageSize" class="fm-pager">
        <button class="fp-btn" :disabled="pageNum<=1" @click="pageNum--">‹</button>
        <span v-for="p in totalPages" :key="p" :class="['fp-num', { on: p === pageNum }]" @click="pageNum = p">{{ p }}</span>
        <button class="fp-btn" :disabled="pageNum>=totalPages" @click="pageNum++">›</button>
      </div>
      </template>

      <div class="mini-bar" v-if="current" @click="showPlayer = true">
        <img :src="getCover(current.coverImg)" @error="onCoverErr" class="mini-cover" :class="{ spinning: playing }" />
        <div class="mini-info">
          <div class="mini-name">{{ current.audioName }}</div>
          <div class="mini-cat">{{ current.category }}</div>
        </div>
        <div class="mini-actions">
          <button class="mini-btn" @click.stop="prev" title="上一首">
            <svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h2v12H6zM9.5 12l8.5 6V6z"/></svg>
          </button>
          <button class="mini-btn play" @click.stop="toggle" title="播放/暂停">
            {{ playing ? '❚❚' : '▶' }}
          </button>
          <button class="mini-btn" @click.stop="next" title="下一首">
            <svg viewBox="0 0 24 24" fill="currentColor"><path d="M16 6h2v12h-2zM6 18l8.5-6L6 6z"/></svg>
          </button>
        </div>
      </div>
    </div>

    <!-- ========== 全屏播放器（黑胶+歌词） ========== -->
    <div class="player-view" v-if="showPlayer && current">
      <div class="bg-blur" :style="{ backgroundImage: 'url(' + getCover(current.coverImg) + ')' }"></div>
      <div class="bg-overlay"></div>

      <div class="player-top">
        <button class="back-btn" @click="showPlayer = false">← 返回</button>
        <div class="player-title">
          <div class="pt-name">{{ current.audioName }}</div>
          <div class="pt-cat">{{ current.category }} · ▶ {{ current.playCount || 0 }}次播放</div>
        </div>
      </div>

      <div class="player-body">
        <div class="player-main">
          <!-- 左：黑胶唱片 -->
          <div class="vinyl-section">
            <div class="vinyl-wrap">
              <div class="vinyl-disc" :class="{ spinning: playing }">
                <img :src="getCover(current.coverImg)" @error="onCoverErr" class="vinyl-cover" />
                <div class="vinyl-hole"></div>
              </div>
              <div class="vinyl-tonearm" :class="{ down: playing }">
                <div class="arm-pivot"></div>
                <div class="arm-rod"></div>
                <div class="arm-head"></div>
              </div>
            </div>
          </div>

          <!-- 右：标题信息 + 简介 -->
          <div class="lyric-section">
            <div class="big-title">{{ current.audioName }}</div>
            <div class="audio-desc">{{ current.description || '戴上耳机，让声音陪你待一会儿' }}</div>
          </div>
        </div>

        <!-- 右侧栏：简介/评论 Tab + 相关推荐（学堂播放页同款布局） -->
        <aside class="player-side">
          <div class="side-tabs">
            <button class="side-tab" :class="{ on: sideTab === 'intro' }" @click="sideTab = 'intro'">简介</button>
            <button class="side-tab" :class="{ on: sideTab === 'comment' }" @click="sideTab = 'comment'">
              评论<span class="tab-num" v-if="commentTotal"> {{ commentTotal }}</span>
            </button>
          </div>
          <div class="side-scroll">
            <template v-if="sideTab === 'intro'">
              <div class="side-sec-title" v-if="relatedAudios.length">✨ 相关推荐</div>
              <div class="rel-list" v-if="relatedAudios.length">
                <div v-for="a in relatedAudios" :key="'rel-' + a.audioId" class="rel-item" @click="playRelatedAudio(a)">
                  <div class="rel-thumb">
                    <img :src="getCover(a.coverImg)" @error="onCoverErr" />
                  </div>
                  <div class="rel-info">
                    <div class="rel-name">{{ a.audioName }}</div>
                    <div class="rel-meta">{{ a.category }} · ▶ {{ a.playCount || 0 }}次播放</div>
                  </div>
                </div>
              </div>
              <div v-else class="cp-empty">暂时没有其它音频~</div>
            </template>
            <template v-else>
              <div class="cp-input">
                <input v-model="newComment" placeholder="说点什么吧..." @keyup.enter="submitComment" />
                <button class="cp-send" @click="submitComment">发送</button>
              </div>
              <div v-if="commentLoading" class="cp-loading">加载评论中...</div>
              <div v-else-if="comments.length > 0" class="cp-list">
                <div v-for="(c, i) in comments" :key="c.commentId || i" class="cp-item" :class="{ pending: c.status === 0, rejected: c.status === -1 }">
                  <div class="cp-avatar">
                    <img v-if="c.avatar" :src="getAvatarUrl(c.avatar)" />
                    <span v-else>🧑</span>
                  </div>
                  <div class="cp-body">
                    <div class="cp-user">
                      {{ c.nickname || '用户' + (c.userId || '') }}
                      <span v-if="c.status === 0" class="cp-pending" title="仅自己可见，等待管理员审核">待审核</span>
                      <span v-else-if="c.status === -1" class="cp-rejected" title="未通过管理员审核，仅自己可见">未通过审核</span>
                    </div>
                    <div class="cp-text">{{ c.commentText }}</div>
                    <div class="cp-time">{{ c.createTime ? new Date(c.createTime).toLocaleString('zh-CN', { hour12: false }) : '' }}</div>
                  </div>
                </div>
                <!-- 分页 -->
                <div v-if="commentTotal > commentPageSize" class="cp-pager">
                  <button :disabled="commentPage <= 1" @click="onCommentPageChange(commentPage - 1)">上一页</button>
                  <span class="cp-page-info">{{ commentPage }} / {{ Math.ceil(commentTotal / commentPageSize) }}</span>
                  <button :disabled="commentPage >= Math.ceil(commentTotal / commentPageSize)" @click="onCommentPageChange(commentPage + 1)">下一页</button>
                </div>
              </div>
              <div v-else class="cp-empty">还没有评论，来抢沙发吧~</div>
            </template>
          </div>
        </aside>
      </div>

      <div v-if="resumeTip" class="resume-bar">
        <span class="resume-icon">📌</span>
        <span class="resume-text">上次听到 {{ fmt(resumeTip) }}</span>
        <button class="resume-btn" @click="resumePlay">继续</button>
        <button class="resume-btn ghost" @click="fromStart">从头</button>
      </div>

      <div class="player-progress">
        <div class="time time-left">{{ fmt(currentTime) }}</div>
        <div class="bar" ref="barRef" @click="seek">
          <div class="fill" :style="{ width: progress + '%' }"></div>
          <div class="thumb" :style="{ left: progress + '%' }"></div>
        </div>
        <div class="time time-right">{{ fmt(duration) }}</div>
      </div>
<!-- 在 <div class="player-controls"> 之前插入 -->
<div class="action-row">
  <button class="action-btn" :class="{ on: liked }" @click="toggleLike">
    <span class="act-icon">{{ liked ? '❤️' : '🤍' }}</span>
    <span class="act-text">{{ liked ? '已点赞' : '点赞' }}</span>
    <span class="act-num">{{ likeCount }}</span>
  </button>
  <button class="action-btn" :class="{ on: favorited }" @click="toggleFav">
    <span class="act-icon">{{ favorited ? '⭐' : '☆' }}</span>
    <span class="act-text">{{ favorited ? '已收藏' : '收藏' }}</span>
    <span class="act-num">{{ favCount }}</span>
  </button>
  <button class="action-btn" @click="openShare">
    <span class="act-icon">🔗</span>
    <span class="act-text">分享</span>
  </button>
</div>

      <div class="player-controls">
        <button class="pctrl-btn" @click="prev">
          <svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h2v12H6zM9.5 12l8.5 6V6z"/></svg>
        </button>
        <button class="pctrl-play" @click="toggle">
          {{ playing ? '❚❚' : '▶' }}
        </button>
        <button class="pctrl-btn" @click="next">
          <svg viewBox="0 0 24 24" fill="currentColor"><path d="M16 6h2v12h-2zM6 18l8.5-6L6 6z"/></svg>
        </button>
      </div>
    </div>

    <div v-if="shareVisible" class="share-mask" @click.self="shareVisible = false">
      <div class="share-dialog">
        <h3>🔗 分享给朋友</h3>
        <p class="share-title">{{ current?.audioName }}</p>
        <div class="share-link-row">
          <input ref="shareLinkRef" :value="shareLink" readonly />
          <button @click="copyLink">{{ copied ? '✅ 已复制' : '复制' }}</button>
        </div>
        <div class="share-tips">复制链接发给好友，或点击下方平台分享</div>
        <div class="share-platforms">
          <button @click="shareTo('weibo')">🟧 微博</button>
          <button @click="shareTo('qq')">💬 QQ</button>
          <button @click="shareTo('wechat')">💚 微信</button>
          <button @click="nativeShare">📤 系统分享</button>
        </div>
        <div class="share-close" @click="shareVisible = false">关闭</div>
      </div>
    </div>

    <audio ref="audioRef" preload="metadata" @timeupdate="onTime" @loadedmetadata="onLoad" @ended="onEnd"></audio>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import request, { playAudio } from '@/api'
import AnimeHero from '@/components/AnimeHero.vue'
import { getLikeList, saveLike, cancelLike, getLikeCount, getCollectList, saveCollect, cancelCollect, getCollectCount, getCommentListByResource, saveComment, fetchAllPages } from '@/api'
import '../styles/fm.css'

const route = useRoute()
const favAudioIds = new Set()
const likedAudioIds = new Set()
const playedAudioIds = new Set() // 本次会话已算过播放量的音频，避免重复计数

const loadFavIds = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return
  try {
    // 翻页拉全（后端单页封顶 100，size:500 会被静默截断）
    const list = await fetchAllPages(p => getCollectList({ userId: info.userId, page: p, size: 100 }))
    favAudioIds.clear()
    list.forEach(c => { if (c.resourceType === 1) favAudioIds.add(c.resourceId) })
  } catch (e) { console.warn('load fav failed', e) }
}

const loadLikeIds = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return
  try {
    const list = await fetchAllPages(p => getLikeList({ userId: info.userId, page: p, size: 100 }))
    likedAudioIds.clear()
    list.forEach(l => { if (l.resourceType === 1) likedAudioIds.add(l.resourceId) })
  } catch (e) { console.warn('load likes failed', e) }
}

const audioRef = ref(null)
const barRef = ref(null)
const playing = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const progress = ref(0)
const list = ref([])
const current = ref(null)
const activeCat = ref('')
const searchKeyword = ref('')
const resumeTip = ref(0)
const liked = ref(false)
const likeCount = ref(0)
const favorited = ref(false)
const favCount = ref(0)
const shareVisible = ref(false)
const copied = ref(false)
const shareLinkRef = ref(null)
const showPlayer = ref(false)
const loading = ref(false)
// 右侧栏 Tab：简介 / 评论（学堂播放页同款）
const sideTab = ref('intro')
const newComment = ref('')
const comments = ref([])
const commentPage = ref(1)
const commentPageSize = 10
const commentTotal = ref(0)
const commentLoading = ref(false)

const pageNum = ref(1)
const pageSize = ref(12)   // 宽屏一行约 8 个卡片，12 条正好铺成两行（8+4）
const totalPages = computed(() => Math.max(1, Math.ceil(filteredList.value.length / pageSize.value)))
const pagedList = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

const LS_KEY = 'fm_data'
const loadLS = () => { try { return JSON.parse(localStorage.getItem(LS_KEY) || '{}') } catch { return {} } }
const saveLS = (data) => localStorage.setItem(LS_KEY, JSON.stringify(data))

const categories = computed(() => [...new Set(list.value.map(a => a.category).filter(c => c))])
const filteredList = computed(() => {
  let result = list.value
  if (activeCat.value) result = result.filter(a => a.category === activeCat.value)
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    result = result.filter(a => a.audioName?.toLowerCase().includes(kw))
  }
  return result
})

const getCover = (coverImg) => {
  if (!coverImg) return '/img/默认管理员头像.png'
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

const getAvatarUrl = (avatar) => {
  if (!avatar) return ''
  if (avatar.startsWith('http') || avatar.startsWith('/')) return avatar
  return '/avatar/' + avatar
}

const fmt = (s) => {
  if (!isFinite(s)) return '0:00'
  s = Math.floor(s); const m = Math.floor(s / 60); const r = s % 60
  return m + ':' + (r < 10 ? '0' : '') + r
}

const loadData = async () => {
  loading.value = true
  const start = Date.now()
  try {
    // 翻页拉全（后端单页封顶 100，size:200 会被静默截断）
    const all = await fetchAllPages(p => request.get('/audio/list', { params: { page: p, size: 100 } }))
    list.value = (Array.isArray(all) ? all : []).map(a => ({
      audioId: a.audioId || a.id,
      audioName: a.audioName || a.audio_name || '-',
      audioUrl: a.audioUrl || a.audio_url || '',
      category: a.category || '',
      coverImg: a.coverImg || a.cover_img || '',
      playCount: a.playCount || a.play_count || 0,
      description: a.description || '',
    }))
    if (list.value.length > 0 && !current.value) selectTrack(0)
  } catch (e) { console.warn('load audio failed:', e) }
  finally {
    // 最小显示 250ms，避免 loading 闪一下
    const elapsed = Date.now() - start
    if (elapsed < 250) {
      setTimeout(() => { loading.value = false }, 250 - elapsed)
    } else {
      loading.value = false
    }
  }
}

let lastHistAudioId = null
let lastSyncSec = -99

const saveHistory = async (resourceType, resourceId, playProgress = 0, duration = 0) => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return
  try {
    await request.post('/history/save', { userId: info.userId, resourceType, resourceId, playProgress, duration, isFinished: 0 })
  } catch (e) { console.warn('save history failed', e) }
}

const updateProgress = async (resourceType, resourceId, playProgress, duration, isFinished = 0) => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return
  try {
    await request.post('/history/progress', { userId: info.userId, resourceType, resourceId, playProgress, duration, isFinished })
  } catch (e) { console.warn('update progress failed', e) }
}

const backendProgMap = ref({})

const loadBackendHistory = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return
  try {
    const res = await request.get('/history/list?userId=' + info.userId + '&resourceType=1&size=200')
    const records = res.data?.records || res.data || []
    const map = {}
    records.forEach(h => {
      const rid = h.resourceId || h.resource_id
      if (rid != null && (h.playProgress || h.play_progress)) map[rid] = h.playProgress != null ? h.playProgress : h.play_progress
    })
    backendProgMap.value = map
  } catch (e) { console.warn('load history failed', e) }
}

const playAt = (idx, autoResume = false) => {
  const arr = filteredList.value
  if (!arr[idx]) return
  current.value = arr[idx]
  const audioId = current.value.audioId
  // 🔴 播放量 +1（同一会话同一音频只算一次，开始播放就计数）
  if (!playedAudioIds.has(audioId)) {
    playedAudioIds.add(audioId)
    playAudio(audioId).catch(() => {})
    const item = list.value.find(a => a.audioId === audioId)
    if (item) item.playCount = (item.playCount || 0) + 1
  }
  if (lastHistAudioId !== audioId) {
    lastHistAudioId = audioId
    saveHistory(1, audioId, 0, Math.floor(duration.value || 0))
    setTimeout(() => { lastHistAudioId = null }, 5000)
  }
  const ls = loadLS()
  const savedProg = backendProgMap.value[audioId] || ls.progress?.[audioId]
  if (!autoResume && savedProg && savedProg > 5) resumeTip.value = savedProg
  else resumeTip.value = 0
  // 点赞/收藏状态优先用后端数据
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (info && likedAudioIds.size > 0) {
    liked.value = likedAudioIds.has(audioId)
  } else {
    liked.value = !!(ls.likes && ls.likes[audioId])
  }
  favorited.value = favAudioIds.has(audioId)
  // 加载点赞数、收藏数、评论
  loadLikeFavComment(audioId)

  const a = audioRef.value
  if (a && current.value.audioUrl) {
    a.src = current.value.audioUrl
    if (autoResume && savedProg) {
      a.play().then(() => { a.currentTime = savedProg; playing.value = true }).catch(() => { playing.value = false })
    } else {
      a.play().then(() => { playing.value = true }).catch(() => { playing.value = false })
    }
  }
}

// 只把某条音频"装载"到播放器：换 src、刷新点赞收藏评论，但不自动播放、不计播放量、不写引导历史
const selectTrack = (idx) => {
  const arr = filteredList.value
  const a = audioRef.value
  playing.value = false
  currentTime.value = 0
  progress.value = 0
  resumeTip.value = 0
  duration.value = 0
  if (!arr.length) {
    current.value = null
    if (a) { a.pause(); try { a.removeAttribute('src'); a.load() } catch {} }
    return
  }
  const item = arr[Math.max(0, Math.min(idx, arr.length - 1))]
  current.value = item
  if (a) {
    a.pause()
    if (item.audioUrl) a.src = item.audioUrl
    a.currentTime = 0
  }
  // 刷新点赞 / 收藏 / 评论
  const ls = loadLS()
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (info && likedAudioIds.size > 0) liked.value = likedAudioIds.has(item.audioId)
  else liked.value = !!(ls.likes && ls.likes[item.audioId])
  favorited.value = favAudioIds.has(item.audioId)
  loadLikeFavComment(item.audioId)
}

const openPlayer = (idx) => { playAt(idx); showPlayer.value = true }
const resumePlay = () => {
  if (!audioRef.value || !resumeTip.value) return
  const safe = Math.min(resumeTip.value, (duration.value || 0) - 1)
  audioRef.value.currentTime = Math.max(0, safe)
  audioRef.value.play().then(() => { playing.value = true })
  resumeTip.value = 0
}
const fromStart = () => { resumeTip.value = 0 }

const toggle = () => {
  const a = audioRef.value
  if (!a || !current.value) return
  if (playing.value) { a.pause(); playing.value = false }
  else { a.play().then(() => { playing.value = true }).catch(() => { playing.value = false }) }
}
const prev = () => {
  const arr = filteredList.value
  if (!arr.length) return
  const i = arr.findIndex(a => a.audioId === current.value?.audioId)
  playAt(i <= 0 ? arr.length - 1 : i - 1)
}
const next = () => {
  const arr = filteredList.value
  if (!arr.length) return
  const i = arr.findIndex(a => a.audioId === current.value?.audioId)
  playAt(i >= arr.length - 1 ? 0 : i + 1)
}

// 相关推荐：除当前音频外的其它音频（最多 8 个）
const relatedAudios = computed(() => {
  const cur = current.value
  if (!cur) return []
  return list.value.filter(a => a.audioId !== cur.audioId).slice(0, 8)
})
const playRelatedAudio = (a) => {
  const idx = filteredList.value.findIndex(x => x.audioId === a.audioId)
  if (idx >= 0) playAt(idx)
}
const switchCat = (c) => {
  activeCat.value = c
  searchKeyword.value = ''
  pageNum.value = 1
  selectTrack(0)
}

// 搜索防抖
let searchTimer = null
const onSearchInput = () => {
  // 立即将页码钳位到有效范围，避免中间出现空白页
  const total = totalPages.value
  if (pageNum.value > total && total > 0) pageNum.value = 1
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pageNum.value = 1
    selectTrack(0)
  }, 400)
}

const onTime = () => {
  if (!audioRef.value) return   // 路由切换卸载瞬间可能收到最后一次 timeupdate
  currentTime.value = audioRef.value.currentTime
  if (duration.value) progress.value = currentTime.value / duration.value * 100
  if (!current.value) return
  const sec = Math.floor(currentTime.value)
  if (sec - lastSyncSec >= 10) {
    lastSyncSec = sec
    updateProgress(1, current.value.audioId, sec, Math.floor(duration.value || 0), 0)
    const ls = loadLS(); ls.progress = ls.progress || {}
    ls.progress[current.value.audioId] = sec; saveLS(ls)
  }
}
const onLoad = () => { if (audioRef.value) duration.value = audioRef.value.duration }
const onEnd = () => {
  playing.value = false
  if (current.value) {
    const ls = loadLS()
    if (ls.progress) { delete ls.progress[current.value.audioId]; saveLS(ls) }
  }
  next()
}

const toggleLike = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) { alert('请先登录哦~'); return }
  if (!current.value) return
  const audioId = current.value.audioId
  // 先乐观更新UI，让用户立刻看到反馈
  const prevLiked = liked.value
  const prevCount = likeCount.value
  liked.value = !prevLiked
  likeCount.value = prevLiked ? Math.max(0, prevCount - 1) : prevCount + 1
  if (!prevLiked) likedAudioIds.add(audioId); else likedAudioIds.delete(audioId)
  // 同步本地备份
  const ls0 = loadLS(); ls0.likes = ls0.likes || {}
  if (liked.value) ls0.likes[audioId] = true; else delete ls0.likes[audioId]
  saveLS(ls0)

  try {
    let res
    if (!prevLiked) {
      res = await saveLike({ userId: info.userId, resourceType: 1, resourceId: audioId })
    } else {
      res = await cancelLike({ userId: info.userId, resourceType: 1, resourceId: audioId })
    }
    if (res.code !== 200) {
      // 后端失败，回滚状态
      liked.value = prevLiked
      likeCount.value = prevCount
      if (prevLiked) likedAudioIds.add(audioId); else likedAudioIds.delete(audioId)
      const ls = loadLS(); ls.likes = ls.likes || {}
      if (prevLiked) ls.likes[audioId] = true; else delete ls.likes[audioId]
      saveLS(ls)
      alert(res.msg || '操作失败，请重试')
    }
  } catch (e) {
    console.warn('like failed', e)
    // 网络异常，保留本地状态即可（已在前面设置）
  }
}

const toggleFav = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) { alert('请先登录哦~'); return }
  if (!current.value) return
  const audioId = current.value.audioId
  // 先乐观更新UI
  const prevFav = favorited.value
  const prevCount = favCount.value
  favorited.value = !prevFav
  favCount.value = prevFav ? Math.max(0, prevCount - 1) : prevCount + 1
  if (!prevFav) favAudioIds.add(audioId); else favAudioIds.delete(audioId)
  // 同步本地备份
  const ls0 = loadLS(); ls0.favs = ls0.favs || {}
  if (favorited.value) ls0.favs[audioId] = true; else delete ls0.favs[audioId]
  saveLS(ls0)

  try {
    let res
    if (!prevFav) {
      res = await saveCollect({ userId: info.userId, resourceType: 1, resourceId: audioId })
    } else {
      res = await cancelCollect({ userId: info.userId, resourceType: 1, resourceId: audioId })
    }
    if (res.code !== 200) {
      // 后端失败，回滚状态
      favorited.value = prevFav
      favCount.value = prevCount
      if (prevFav) favAudioIds.add(audioId); else favAudioIds.delete(audioId)
      const ls = loadLS(); ls.favs = ls.favs || {}
      if (prevFav) ls.favs[audioId] = true; else delete ls.favs[audioId]
      saveLS(ls)
      alert(res.msg || '操作失败，请重试')
    }
  } catch (e) {
    console.warn('fav failed', e)
    // 网络异常，保留本地状态即可
  }
}

// 加载点赞数、收藏数、评论
const loadLikeFavComment = async (audioId, page = 1) => {
  const ls = loadLS()
  // 加载点赞数
  try {
    const res = await getLikeCount({ resourceType: 1, resourceId: audioId })
    if (res.code === 200) likeCount.value = res.data || 0
  } catch (e) { console.warn('加载点赞数失败', e) }
  // 加载收藏数
  try {
    const res = await getCollectCount({ resourceType: 1, resourceId: audioId })
    if (res.code === 200) favCount.value = res.data || 0
  } catch (e) { console.warn('加载收藏数失败', e) }
  // 加载评论（带用户信息，分页）
  commentLoading.value = true
  try {
    const res = await getCommentListByResource({ page, size: commentPageSize, resourceType: 1, resourceId: audioId })
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
    const allComments = ls.comments?.[audioId] || []
    const start = (page - 1) * commentPageSize
    comments.value = allComments.slice(start, start + commentPageSize)
    commentTotal.value = allComments.length
    commentPage.value = page
  } finally {
    commentLoading.value = false
  }
}

const onCommentPageChange = (p) => {
  if (current.value) loadLikeFavComment(current.value.audioId, p)
}

const submitComment = async () => {
  const text = newComment.value.trim()
  if (!text) return
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) { alert('请先登录哦~'); return }
  try {
    const res = await saveComment({
      userId: info.userId,
      resourceType: 1,
      resourceId: current.value.audioId,
      commentText: text,
    })
    if (res.code === 200) {
      newComment.value = ''
      // 系统先审：干净评论直接公开（data=1），命中可疑判据才转人工（data=0）
      if (res.data === 1) ElMessage.success('评论发布成功')
      else ElMessage.success('评论已提交，疑似包含待确认内容，审核通过后会公开显示')
      // 刷新列表（后端已支持本人可见自己的待审核评论）
      loadLikeFavComment(current.value.audioId, commentPage.value)
    } else {
      alert(res.msg || '评论提交失败，请重试')
    }
  } catch (e) {
    console.warn('评论失败', e)
    alert('网络异常，评论发送失败，请稍后再试')
  }
}

// 路由是 history 模式，分享链接必须是 /fm?audioId=xx（带 # 的 hash 不会被路由匹配到）
const shareLink = computed(() => {
  const id = current.value?.audioId
  return id ? window.location.origin + '/fm?audioId=' + id : window.location.origin + '/fm'
})
const openShare = () => { copied.value = false; shareVisible.value = true }
const copyLink = async () => {
  try { await navigator.clipboard.writeText(shareLink.value) }
  catch { shareLinkRef.value?.select(); document.execCommand('copy') }
  copied.value = true
  setTimeout(() => copied.value = false, 2000)
}
const nativeShare = async () => {
  if (navigator.share) {
    try { await navigator.share({ title: current.value?.audioName || '心理FM', text: '世界和我爱着你', url: shareLink.value }) } catch {}
  } else { copyLink() }
}
const shareTo = (platform) => {
  const title = encodeURIComponent(current.value?.audioName || '心理FM')
  const url = encodeURIComponent(shareLink.value)
  let link = ''
  if (platform === 'weibo') link = 'https://service.weibo.com/share/share.php?title=' + title + '&url=' + url
  else if (platform === 'qq') link = 'https://connect.qq.com/widget/shareqq/index.html?title=' + title + '&url=' + url
  else if (platform === 'wechat') { copyLink(); alert('链接已复制，打开微信粘贴分享给好友！'); return }
  if (link) window.open(link, '_blank', 'width=600,height=500')
}

const seek = (e) => {
  if (!barRef.value || !duration.value) return
  const rect = barRef.value.getBoundingClientRect()
  const target = (e.clientX - rect.left) / rect.width * duration.value
  audioRef.value.currentTime = Math.max(0, Math.min(duration.value, target))
}

onMounted(async () => {
  await Promise.all([loadFavIds(), loadLikeIds()])
  await loadData()
  await loadBackendHistory()
  // 深链支持：/fm?audioId=xx → 翻到对应分页并直接播放该音频（主页推荐卡点进来）
  const qId = Number(route.query.audioId)
  if (qId) {
    const idx = filteredList.value.findIndex(a => a.audioId === qId)
    if (idx >= 0) {
      pageNum.value = Math.floor(idx / pageSize.value) + 1
      playAt(idx)
    }
  }
})

const flushProgressToBackend = async (audioId) => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return
  const id = audioId || current.value?.audioId
  if (!id) return
  const sec = Math.floor(currentTime.value || 0)
  const dur = Math.floor(duration.value || 0)
  if (sec <= 0) return
  await updateProgress(1, id, sec, dur, dur > 0 && sec >= dur ? 1 : 0)
}

watch(() => current.value?.audioId, async (newId, oldId) => {
  if (oldId) await flushProgressToBackend(oldId)
  resumeTip.value = 0
})

onBeforeUnmount(async () => {
  if (current.value && currentTime.value > 3) {
    const ls = loadLS(); ls.progress = ls.progress || {}
    ls.progress[current.value.audioId] = currentTime.value; saveLS(ls)
  }
  await flushProgressToBackend()
})
</script>