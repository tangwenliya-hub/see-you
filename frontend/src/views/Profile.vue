﻿﻿﻿﻿﻿﻿<template>
  <div class="profile-page" :class="{ 'dark-mode': darkMode }">
    <!-- 顶部横幅（Steam 风格：背景大图 + 头像 + 等级徽章） -->
    <div class="profile-hero">
      <div class="hero-bg" :class="{ adjusting: adjustingBg, dragging: !!dragState }" :style="heroBgStyle"
           @pointerdown="onBgPointerDown" @pointermove="onBgPointerMove" @pointerup="onBgPointerUp" @pointercancel="onBgPointerUp">
        <!-- 动漫风装饰层：闪烁星星 + 漂浮云朵（pointer-events:none，不影响拖动调整） -->
        <div class="hero-anime-deco" aria-hidden="true">
          <span class="had-star s1"><AppIcon name="sparkles" /></span>
          <span class="had-star s2"><AppIcon name="sparkles" /></span>
          <span class="had-star s3"><AppIcon name="sparkles" /></span>
          <span class="had-star s4"><AppIcon name="sparkles" /></span>
          <span class="had-cloud c1"><AppIcon name="cloud" /></span>
          <span class="had-cloud c2"><AppIcon name="cloud" /></span>
        </div>
        <span v-if="adjustingBg && ripple" :key="rippleKey" class="bg-ripple" :style="{ left: ripple.x + '%', top: ripple.y + '%' }"></span>
        <div v-if="adjustingBg" class="bg-adjust-tip" @click.stop>
          <span><AppIcon name="arrows-horizontal" /> 按住拖动，调整图片展示位置（当前 {{ Math.round(posXY[0]) }}% / {{ Math.round(posXY[1]) }}%）</span>
          <button class="bg-adjust-done" @pointerdown.stop.prevent="adjustingBg = false" @click.stop="adjustingBg = false"><AppIcon name="check" /> 完成</button>
        </div>
      </div>
      <button class="hero-bg-set" @click="showBgPicker = true" title="设置背景图"><AppIcon name="image" /> 背景</button>
      <div class="hero-content">
        <div class="avatar-wrap" :class="['frame-' + userFrame]" @click="showAvatarPicker = true">
          <img :src="avatarUrl" @error="onAvatarError" class="avatar-img-inner" />
          <img v-if="currentFrameImg" :src="currentFrameImg" class="frame-ring-img" :style="{ width: currentFrameScale + '%', height: currentFrameScale + '%' }" @error="$event.target.style.display='none'" />
          <input ref="avatarUploadRef" type="file" accept="image/*" style="display:none" @change="onAvatarUpload" />
        </div>
        <div class="hero-info">
          <h1>{{ user.nickname || user.username || '未登录' }}<span class="role-tag">{{ user.role == 1 ? ' 管理员' : ' 普通用户' }}</span></h1>
          <!-- 个性签名（Steam 状态位，点击编辑） -->
          <p v-if="!editingStatus" class="hero-sign" @click="startEditStatus" title="点击编辑个性签名">
            <span class="sign-text">{{ userStatus || '点击这里，写一句介绍自己的签名吧~' }}</span>
            <span class="sign-edit"><AppIcon name="pen" /></span>
          </p>
          <div v-else class="hero-sign-edit">
            <input v-model="statusDraft" maxlength="40" placeholder="写一句签名，让大家认识你（最多40字）"
                   @keyup.enter="saveStatus" @keyup.esc="editingStatus = false" />
            <button class="sign-save" @click="saveStatus">保存</button>
            <button class="sign-cancel" @click="editingStatus = false">取消</button>
          </div>
          <p class="hero-meta">
            <span v-if="user.email"><AppIcon name="mail" /> {{ user.email }}</span>
            <span class="online-tag"><span class="online-dot"></span>在线 · 最近活跃 {{ lastActiveText }}</span>
          </p>
          <!-- 个性化设置（主题色/深色/装饰框）已收入「个人中心」弹窗，横幅只保留展示信息 -->
        </div>
      </div>
    </div>

    <!-- 背景图选择弹窗 -->
    <div v-if="showBgPicker" class="mask" @click.self="showBgPicker = false">
      <div class="avatar-picker" style="width:500px">
        <h3><AppIcon name="image" /> 设置顶部背景图</h3>
        <p class="modal-desc" style="margin:-6px 0 12px;font-size:12px">选择一张喜欢的图片作为个人中心的顶栏背景~</p>
        <div class="bg-preview" :class="{ clickable: heroBg }" :style="heroBgStyle" :title="heroBg ? '点击去顶部横幅调整显示区域' : ''" @click="heroBg && startBgAdjust()">
          <span v-if="heroBg" class="bg-preview-tip"><AppIcon name="target" /> 点击调整显示区域</span>
        </div>
        <p class="bg-pos-hint" v-if="heroBg"><AppIcon name="bulb" /> <b>怎么调整？</b>点上面的预览图 <AppIcon name="arrow-right" /> 弹窗关闭 <AppIcon name="arrow-right" /> <b>按住页面顶部的横幅拖动</b>（和微信设置聊天背景一样），调好后点「<AppIcon name="check" /> 完成」。</p>
        <div class="bg-default-row">
          <button class="primary-btn small" v-if="heroBg" @click="startBgAdjust"><AppIcon name="target" /> 在顶部横幅上调整展示区域</button>
          <button class="cancel" @click="setBg('')"><AppIcon name="palette" /> 恢复主题渐变色</button>
        </div>
        <input type="file" accept="image/*" ref="bgUploadRef" style="display:none" @change="onBgUpload" />
        <div class="picker-actions">
          <button class="cancel" @click="showBgPicker = false">取消</button>
          <button class="primary-btn small" @click="bgUploadRef?.click()"><AppIcon name="upload" /> 上传自定义图片</button>
        </div>
      </div>
    </div>

    <!-- 主体两栏：左侧内容 + 右侧信息栏（Steam 式） -->
    <div class="steam-body">
    <!-- 内容区（右侧栏即导航，点击切换） -->
    <div class="tab-content steam-main">

      <!-- ========== 最新动态（默认页，Steam 式动态流） ========== -->
      <div v-if="activeTab === 'activity'" class="activity-area">
        <div class="act-section">
          <div class="act-head">
            <span class="act-title">最新动态</span>
            <span class="act-sub">共 {{ historyCount }} 条收听/观看记录</span>
            <button class="act-more" @click="switchTab('history')">查看所有动态</button>
          </div>
          <div v-if="recentHistory.length === 0" class="empty"><AppIcon name="headphones" /> 还没有收听或观看记录~ 去绪见 FM 或绪见学堂逛逛吧！</div>
          <div v-for="h in recentHistory" :key="'act' + h.historyId" class="act-card" @click="goPlay(h)">
            <img :src="h.cover" @error="onCoverError" class="act-cover" />
            <div class="act-body">
              <div class="act-name"><AppIcon :name="h.type === 'video' ? 'video' : 'music'" /> {{ h.name }}</div>
              <div class="act-meta">总时长 {{ fmt(h.duration) }} · 上次{{ h.type === 'video' ? '观看' : '收听' }} {{ shortDate(h.createTime) }}</div>
              <div class="act-prog">
                <div class="prog-bar"><div class="prog-fill" :style="{ width: h.progress + '%' }"></div></div>
                <span class="act-prog-text"><template v-if="h.finished"><AppIcon name="check-circle" /> 已看完</template><template v-else>进度 {{ h.progress }}%</template></span>
              </div>
            </div>
            <span :class="['act-type', h.type]">{{ h.type === 'video' ? '视频' : '音频' }}</span>
          </div>
        </div>

        <div class="act-grid">
          <div class="act-section">
            <div class="act-head"><span class="act-title">最近日记</span><button class="act-more" @click="switchTab('diary')">全部日记</button></div>
            <div v-if="recentDiaries.length === 0" class="empty"><AppIcon name="edit" /> 还没有写过日记~</div>
            <div v-for="d in recentDiaries" :key="'ad' + d.diaryId" class="mini-diary" @click="switchTab('diary')">
              <img :src="getEmotionIcon(d.emotion)" class="md-emo" alt="" />
              <div class="md-body">
                <div class="md-text">{{ d.diaryContent }}</div>
                <div class="md-date">{{ formatDate(d.diaryDate) }} · {{ getWeekday(d.diaryDate) }}</div>
              </div>
            </div>
          </div>
          <div class="act-section">
            <div class="act-head"><span class="act-title">最近测评</span><button class="act-more" @click="switchTab('test')">全部报告</button></div>
            <div v-if="recentTests.length === 0" class="empty"><AppIcon name="clipboard" /> 还没有测评记录~</div>
            <div v-for="t in recentTests" :key="'at' + t.recordId" class="mini-test" @click="openTestDetail(t)">
              <div class="mt-score">{{ t.score ?? t.totalScore }}</div>
              <div class="mt-body">
                <div class="mt-name">{{ t.testTitle || t.paperName || '心理测评' }}</div>
                <div class="mt-meta">{{ t.level || getEmotionText(t.emotionLevel) }} · {{ formatDate(t.createTime) }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="act-section">
          <div class="act-head">
            <span class="act-title">留言板</span>
            <span class="act-sub">收到 {{ fbCount }} 条反馈</span>
            <button class="act-more" @click="switchTab('feedback')">查看留言</button>
          </div>
          <div v-if="fbList.length === 0" class="empty"><AppIcon name="message" /> 还没有留言~ 去反馈页说点什么吧！</div>
          <div v-for="f in fbList.slice(0, 2)" :key="'afb' + f.feedbackId" class="mini-fb" @click="openFbDetail(f)">
            <div class="mf-content">{{ (f.content || f.feedbackContent || '').slice(0, 80) }}</div>
            <div v-if="f.replyText" class="mf-reply"><AppIcon name="corner-down-right" /> 管理员：{{ f.replyText.slice(0, 60) }}</div>
            <div v-else class="mf-reply pending"><AppIcon name="corner-down-right" /> 等待管理员回复...</div>
          </div>
        </div>
      </div>

      <!-- ========== 点赞 ========== -->
      <div v-if="activeTab === 'like'" class="list-area">
        <div class="list-toolbar">
          <div class="lt-row">
            <span class="lt-label"><img src="/img/搜索.png" class="lt-icon" />搜索：</span>
            <input v-model="likeKw" class="lt-input" placeholder="输入名称或分类关键词" />
          </div>
          <div class="lt-row">
            <span class="lt-label"><AppIcon name="calendar" /> 时间：</span>
            <input type="date" v-model="likeStart" class="lt-date" />
            <span class="lt-sep">~</span>
            <input type="date" v-model="likeEnd" class="lt-date" />
          </div>
          <div class="lt-row">
            <button :class="['lt-quick', { on: likeQuick==='all' }]" @click="likeQuick='all'; likeStart=''; likeEnd=''">全部</button>
            <button :class="['lt-quick', { on: likeQuick==='7' }]" @click="setLikeQuick(7)">近7天</button>
            <button :class="['lt-quick', { on: likeQuick==='30' }]" @click="setLikeQuick(30)">近30天</button>
            <button class="lt-reset" @click="resetLikeFilter"><AppIcon name="rotate-ccw" /> 重置</button>
          </div>
        </div>
        <div v-if="filteredLikeList.length === 0" class="empty"><AppIcon name="heart" /> 还没有点赞内容~ 去 FM 或绪见学堂点个赞吧！</div>
        <div v-for="item in filteredLikeList" :key="'l'+item.type+item.id" class="item-card" @click="goPlay(item)">
          <img :src="item.cover" @error="onCoverError" />
          <div class="item-info">
            <div class="item-head">
              <span class="type-badge" :class="item.type"><AppIcon :name="item.type === 'video' ? 'video' : 'music'" /></span>
              <span class="item-name">{{ item.name }}</span>
              <span v-if="item.missing" class="gone-badge">已下架</span>
            </div>
            <div class="item-cat">{{ item.category }}</div>
          </div>
          <button class="del-btn like-del" @click.stop="removeLike(item)" title="取消点赞"><AppIcon name="heart" /></button>
        </div>
      </div>

      <!-- ========== 收藏 ========== -->
      <div v-if="activeTab === 'fav'" class="list-area">
        <div class="list-toolbar">
          <div class="lt-row">
            <span class="lt-label"><img src="/img/搜索.png" class="lt-icon" />搜索：</span>
            <input v-model="favKw" class="lt-input" placeholder="输入名称或分类关键词" />
          </div>
          <div class="lt-row">
            <span class="lt-label"><AppIcon name="calendar" /> 时间：</span>
            <input type="date" v-model="favStart" class="lt-date" />
            <span class="lt-sep">~</span>
            <input type="date" v-model="favEnd" class="lt-date" />
          </div>
          <div class="lt-row">
            <button :class="['lt-quick', { on: favQuick==='all' }]" @click="favQuick='all'; favStart=''; favEnd=''">全部</button>
            <button :class="['lt-quick', { on: favQuick==='7' }]" @click="setFavQuick(7)">近7天</button>
            <button :class="['lt-quick', { on: favQuick==='30' }]" @click="setFavQuick(30)">近30天</button>
            <button class="lt-reset" @click="resetFavFilter"><AppIcon name="rotate-ccw" /> 重置</button>
          </div>
        </div>
        <div v-if="filteredFavList.length === 0" class="empty"><AppIcon name="star-solid" /> 还没有收藏内容~ 去 FM 或绪见学堂逛逛吧！</div>
        <div v-for="item in filteredFavList" :key="'f'+item.type+item.id" class="item-card" @click="goPlay(item)">
          <img :src="item.cover" @error="onCoverError" />
                    <div class="item-info">
            <div class="item-head">
              <span class="type-badge" :class="item.type"><AppIcon :name="item.type === 'video' ? 'video' : 'music'" /></span>
              <span class="item-name">{{ item.name }}</span>
              <span v-if="item.missing" class="gone-badge">已下架</span>
            </div>
            <div class="item-cat">{{ item.category }}</div>
          </div>
          <button class="del-btn fav-del" @click.stop="removeFav(item)" title="取消收藏"><AppIcon name="star-solid" /></button>
        </div>
      </div>

      <!-- ========== 观看历史 ========== -->
      <div v-if="activeTab === 'history'" class="list-area">
        <div class="list-toolbar">
          <div class="lt-row">
            <span class="lt-label"><img src="/img/搜索.png" class="lt-icon" />搜索：</span>
            <input v-model="histKw" class="lt-input" placeholder="输入名称或分类关键词" />
          </div>
          <div class="lt-row">
            <span class="lt-label"><AppIcon name="calendar" /> 时间：</span>
            <input type="date" v-model="histStart" class="lt-date" />
            <span class="lt-sep">~</span>
            <input type="date" v-model="histEnd" class="lt-date" />
          </div>
          <div class="lt-row">
            <button :class="['lt-quick', { on: histQuick==='all' }]" @click="histQuick='all'; histStart=''; histEnd=''">全部</button>
            <button :class="['lt-quick', { on: histQuick==='7' }]" @click="setHistQuick(7)">近7天</button>
            <button :class="['lt-quick', { on: histQuick==='30' }]" @click="setHistQuick(30)">近30天</button>
            <button class="lt-reset" @click="resetHistFilter"><AppIcon name="rotate-ccw" /> 重置</button>
          </div>
        </div>
        <div v-if="groupedHistory.length === 0" class="empty"><AppIcon name="book" /> 还没有观看/收听记录~</div>
        <div v-for="grp in groupedHistory" :key="grp.label" class="hist-group">
          <div class="hist-group-title">{{ grp.label }}（{{ grp.items.length }}）</div>
          <div v-for="item in grp.items" :key="'h'+item.type+item.id" class="item-card" @click="goPlay(item)">
            <img :src="item.cover" @error="onCoverError" />
            <div class="item-info">
              <div class="item-head">
                <span class="type-badge" :class="item.type"><AppIcon :name="item.type === 'video' ? 'video' : 'music'" /></span>
                <span class="item-name">{{ item.name }}</span>
                <span v-if="item.missing" class="gone-badge">已下架</span>
                <span v-else-if="item.finished" class="finished-badge"><AppIcon name="check" /> 已看完</span>
              </div>
              <div class="item-cat">{{ item.category }}</div>
              <div class="prog-row">
                <div class="prog-bar"><div class="prog-fill" :style="{ width: item.progress + '%' }"></div></div>
                <span class="prog-text"><template v-if="item.finished"><AppIcon name="check-circle" /> 已看完</template><template v-else>上次听到 {{ fmt(item.currentTime) }}</template></span>
              </div>
              <div class="hist-meta"><AppIcon name="clock" /> {{ relativeTime(item.createTime) }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== 绪见日记（只读查看，写日记跳 /diary/write）========== -->
      <div v-if="activeTab === 'diary'" class="diary-area">
        <div class="diary-toolbar">
          <div class="diary-toolbar-left">
            <span class="diary-toolbar-title"><AppIcon name="notebook" /> 我的日记本</span>
            <span class="diary-toolbar-count">共 {{ diaryServerTotal }} 篇</span>
          </div>
          <router-link to="/diary/write" class="primary-btn small" style="text-decoration:none"><AppIcon name="pen" /> 去写一篇</router-link>
        </div>
        <div class="list-toolbar">
          <div class="lt-row">
            <span class="lt-label"><img src="/img/搜索.png" class="lt-icon" />搜索：</span>
            <input v-model="diaryKw" class="lt-input" placeholder="输入日记内容关键词" />
          </div>
          <div class="lt-row">
            <span class="lt-label"><AppIcon name="calendar" /> 时间：</span>
            <input type="date" v-model="diaryStart" class="lt-date" />
            <span class="lt-sep">~</span>
            <input type="date" v-model="diaryEnd" class="lt-date" />
          </div>
          <div class="lt-row">
            <button :class="['lt-quick', { on: diaryQuick==='all' }]" @click="diaryQuick='all'; diaryStart=''; diaryEnd=''">全部</button>
            <button :class="['lt-quick', { on: diaryQuick==='7' }]" @click="setDiaryQuick(7)">近7天</button>
            <button :class="['lt-quick', { on: diaryQuick==='30' }]" @click="setDiaryQuick(30)">近30天</button>
            <button class="lt-reset" @click="resetDiaryFilter"><AppIcon name="rotate-ccw" /> 重置</button>
          </div>
        </div>
        <div v-if="diaryLoading" class="empty"><AppIcon name="loader" /> 加载中...</div>
        <div v-else-if="diaryServerTotal === 0" class="empty">
          <span v-if="diaryKw"><img src="/img/搜索.png" class="lt-icon" style="vertical-align:middle;margin-right:4px" />没有找到匹配的日记~</span>
          <span v-else>
            <AppIcon name="edit" /> 还没有写过日记~<br />
            <router-link to="/diary/write" class="link-btn">去写第一篇日记 <AppIcon name="arrow-right" /></router-link>
          </span>
        </div>

        <!-- 时间线日记卡片 -->
        <div v-else class="timeline-wrap">
          <div v-for="(d, idx) in pagedDiaryList" :key="d.diaryId" class="timeline-item">
            <!-- 左侧时间 + 时间线 -->
            <div class="timeline-time">
              <span class="time-text">{{ formatTime(d.createTime || d.diaryDate) }}</span>
              <span class="time-date">{{ formatDate(d.diaryDate) }}</span>
              <span class="time-weekday">{{ getWeekday(d.diaryDate) }}</span>
            </div>
            <div class="timeline-line">
              <div class="timeline-dot" :style="{ background: getEmotionColor(d.emotion) }"></div>
            </div>
            <!-- 右侧卡片 -->
            <div class="timeline-card" :style="{ borderLeftColor: getEmotionColor(d.emotion) }">
              <div class="card-head">
                <div class="card-emo">
                  <img :src="getEmotionIcon(d.emotion)" class="card-emo-img" alt="" />
                  <span class="card-emo-label">{{ emotions.find(e => e.val === d.emotion)?.label || '未知' }}</span>
                </div>
                <div class="card-actions">
                  <button class="diary-del" @click="deleteDiary(d.diaryId)"><AppIcon name="trash" /></button>
                </div>
              </div>
              <div class="card-content">{{ d.diaryContent }}</div>
              <img v-if="d.imageUrl" :src="d.imageUrl" class="card-img" alt="日记配图" />
            </div>
          </div>

          <!-- 分页 -->
          <div v-if="diaryServerTotal > diaryPageSize" class="diary-pager">
            <button class="pager-btn" :disabled="diaryPage === 1 || diaryLoading" @click="loadDiaryPage(diaryPage - 1)"><AppIcon name="chevron-left" /> 上一页</button>
            <span class="pager-info">第 {{ diaryPage }} / {{ diaryTotalPages }} 页</span>
            <button class="pager-btn" :disabled="diaryPage >= diaryTotalPages || diaryLoading" @click="loadDiaryPage(diaryPage + 1)">下一页 <AppIcon name="play" /></button>
          </div>
        </div>
      </div>

      <!-- ========== AI对话历史 ========== -->
      <div v-if="activeTab === 'ai'" class="ai-area">
        <!-- 工具栏：搜索 + 时间筛选 -->
        <div class="list-toolbar">
          <div class="lt-row">
            <span class="lt-label"><img src="/img/搜索.png" class="lt-icon" />搜索：</span>
            <input v-model="aiKw" class="lt-input" placeholder="输入关键词（你说的 / AI 回复）" />
          </div>
          <div class="lt-row">
            <span class="lt-label"><AppIcon name="calendar" /> 时间：</span>
            <input type="date" v-model="aiStart" class="lt-date" />
            <span class="lt-sep">~</span>
            <input type="date" v-model="aiEnd" class="lt-date" />
          </div>
          <div class="lt-row">
            <button :class="['lt-quick', { on: aiQuick==='all' }]" @click="aiQuick='all'; aiStart=''; aiEnd=''">全部</button>
            <button :class="['lt-quick', { on: aiQuick==='7' }]" @click="setAiQuick(7)">近7天</button>
            <button :class="['lt-quick', { on: aiQuick==='30' }]" @click="setAiQuick(30)">近30天</button>
            <button class="lt-reset" @click="resetAiFilter"><AppIcon name="rotate-ccw" /> 重置</button>
          </div>
        </div>
        <div v-if="filteredAiList.length === 0" class="empty"><AppIcon name="bot" /> 没有匹配的聊天记录~</div>
        <div v-for="ai in filteredAiList" :key="ai.chatId" class="ai-card clickable" @click="openAiDetail(ai)">
          <div class="ai-user-row">
            <span class="ai-tag user">你</span>
            <span class="ai-time">{{ formatDateTime(ai.createTime) }}</span>
          </div>
          <div class="ai-bubble user"><span v-html="highlight(ai.userMsg ?? ai.userMessage, aiKw)"></span></div>
          <div class="ai-user-row">
            <span class="ai-tag ai">AI</span>
          </div>
          <div class="ai-bubble ai"><span v-html="highlight(ai.aiReply ?? ai.aiResponse, aiKw)"></span></div>
          <div class="ai-card-hint"><AppIcon name="arrow-up" /> 点击查看详情</div>
        </div>
      </div>

      <!-- ========== 测评报告 ========== -->
      <div v-if="activeTab === 'test'" class="test-area">
        <div class="chart-box" ref="testChartRef"></div>
        <div class="list-toolbar">
          <div class="lt-row">
            <span class="lt-label"><img src="/img/搜索.png" class="lt-icon" />搜索：</span>
            <input v-model="testKw" class="lt-input" placeholder="输入量表名称关键词" />
          </div>
          <div class="lt-row">
            <span class="lt-label"><AppIcon name="calendar" /> 时间：</span>
            <input type="date" v-model="testStart" class="lt-date" />
            <span class="lt-sep">~</span>
            <input type="date" v-model="testEnd" class="lt-date" />
          </div>
          <div class="lt-row">
            <button :class="['lt-quick', { on: testQuick==='all' }]" @click="testQuick='all'; testStart=''; testEnd=''">全部</button>
            <button :class="['lt-quick', { on: testQuick==='7' }]" @click="setTestQuick(7)">近7天</button>
            <button :class="['lt-quick', { on: testQuick==='30' }]" @click="setTestQuick(30)">近30天</button>
            <button class="lt-reset" @click="resetTestFilter"><AppIcon name="rotate-ccw" /> 重置</button>
          </div>
        </div>
        <div v-if="filteredTestList.length === 0" class="empty"><AppIcon name="clipboard" /> 还没有测评记录~ 去绪见测试页面看看吧！</div>
        <div class="test-list">
          <div v-for="t in filteredTestList" :key="t.recordId" class="test-card clickable" @click="openTestDetail(t)">
            <div class="test-head">
              <span class="test-score">分数 {{ t.score ?? t.totalScore }}</span>
              <span class="test-emotion" :class="'emo-' + (t.emotionLevel || 2)">{{ t.level || getEmotionText(t.emotionLevel) }}</span>
              <span class="test-time">{{ formatDateTime(t.createTime) }}</span>
            </div>
            <div class="test-tag">{{ t.testTitle || t.paperName || '综合测试' }}</div>
            <div class="test-tip" v-if="t.tip">{{ t.tip }}</div>
            <div class="test-card-actions" @click.stop>
              <button class="test-action-btn" @click="openTestDetail(t)"><AppIcon name="search" /> 查看详情</button>
              <button class="test-action-btn primary" @click="showTestShare(t)"><AppIcon name="camera" /> 分享卡片</button>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== 情绪周报 ========== -->
      <div v-if="activeTab === 'weekReport'" class="week-area">
        <div class="list-toolbar">
          <div class="lt-row">
            <span class="lt-label"><AppIcon name="calendar" /> 时间范围：</span>
            <button v-for="opt in weekRangeOptions" :key="opt.key" :class="['lt-quick', { on: weekRange === opt.key }]" @click="weekRange = opt.key">{{ opt.label }}</button>
          </div>
        </div>
        <div class="week-summary">
          <div class="summary-item">
            <div class="summary-num main">
              <img :src="weekStat.topEmotionIcon" class="week-top-emo-img" alt="" />
            </div>
            <div class="summary-label">本周主导情绪</div>
            <div class="week-top-emo-label">{{ weekStat.topEmotionLabel }}</div>
          </div>
          <div class="summary-item">
            <div class="summary-num">
              <img src="/img/我的日记本.png" class="week-stat-img" alt="" />
            </div>
            <div class="summary-label">记录天数</div>
            <div class="week-stat-value">{{ weekStat.total }}</div>
          </div>
          <div class="summary-item">
            <div class="summary-num happy">
              <img src="/img/开心.png" class="week-stat-img" alt="" />
            </div>
            <div class="summary-label">积极天数</div>
            <div class="week-stat-value">{{ weekStat.happyDays }}</div>
          </div>
        </div>
        <div class="report-text">
          <h4><AppIcon name="bulb" /> 本周小结</h4>
          <p>{{ weekStat.summary }}</p>
        </div>
        <div class="chart-box-sm" ref="lineChartRef"></div>
        <div class="chart-box-sm" ref="pieChartRef"></div>
      </div>

      <!-- ========== 我的反馈 ========== -->
      <div v-if="activeTab === 'feedback'" class="fb-area">
        <div class="diary-toolbar">
          <div class="diary-toolbar-left">
            <span class="diary-toolbar-title"><AppIcon name="message" /> 我的反馈</span>
            <span class="diary-toolbar-count">共 {{ filteredFbList.length }} 条</span>
          </div>
          <router-link to="/feedback" class="primary-btn small" style="text-decoration:none"><AppIcon name="pen" /> 去提交反馈</router-link>
        </div>
        <!-- 工具栏：搜索 + 时间筛选 -->
        <div class="list-toolbar">
          <div class="lt-row">
            <span class="lt-label"><img src="/img/搜索.png" class="lt-icon" />搜索：</span>
            <input v-model="fbKw" class="lt-input" placeholder="输入关键词（反馈内容 / 管理员回复）" />
          </div>
          <div class="lt-row">
            <span class="lt-label"><AppIcon name="calendar" /> 时间：</span>
            <input type="date" v-model="fbStart" class="lt-date" />
            <span class="lt-sep">~</span>
            <input type="date" v-model="fbEnd" class="lt-date" />
          </div>
          <div class="lt-row">
            <button :class="['lt-quick', { on: fbQuick==='all' }]" @click="fbQuick='all'; fbStart=''; fbEnd=''">全部</button>
            <button :class="['lt-quick', { on: fbQuick==='7' }]" @click="setFbQuick(7)">近7天</button>
            <button :class="['lt-quick', { on: fbQuick==='30' }]" @click="setFbQuick(30)">近30天</button>
            <button class="lt-reset" @click="resetFbFilter"><AppIcon name="rotate-ccw" /> 重置</button>
          </div>
        </div>
        <div v-if="filteredFbList.length === 0" class="empty">
          <AppIcon name="message" /> 没有匹配的反馈记录~<br />
          <router-link to="/feedback" class="link-btn">去提交第一条反馈 <AppIcon name="arrow-right" /></router-link>
        </div>
        <div class="fb-list">
          <div v-for="f in filteredFbList" :key="f.feedbackId" class="fb-card clickable" @click="openFbDetail(f)">
            <div class="fb-head">
              <span class="fb-time">{{ formatDateTime(f.createTime) }}</span>
              <span v-if="f.replyText" class="fb-status replied"><AppIcon name="check" /> 已回复</span>
              <span v-else class="fb-status pending"><AppIcon name="loader" /> 待回复</span>
            </div>
            <div class="fb-content"><span v-html="highlight(f.content || f.feedbackContent, fbKw)"></span></div>
            <div v-if="f.replyText" class="fb-reply">
              <div class="reply-label"><AppIcon name="check" /> 管理员回复：</div>
              <div class="reply-text"><span v-html="highlight(f.replyText, fbKw)"></span></div>
            </div>
            <div v-else class="fb-pending"><AppIcon name="loader" /> 等待管理员回复...</div>
            <div class="fb-card-hint"><AppIcon name="arrow-up" /> 点击查看完整对话</div>
          </div>
        </div>
      </div>

      <!-- ========== 我的评论 ========== -->
      <div v-if="activeTab === 'comments'" class="cmt-area">
        <div class="diary-toolbar">
          <div class="diary-toolbar-left">
            <span class="diary-toolbar-title"><AppIcon name="message" /> 我的评论</span>
            <span class="diary-toolbar-count">共 {{ cmtCounts.all }} 条</span>
          </div>
          <button class="primary-btn small" @click="loadCmtAll"><AppIcon name="rotate-cw" /> 刷新</button>
        </div>

        <!-- 四态统计卡，点击即筛选 -->
        <div class="cmt-stats">
          <div v-for="s in cmtStatCards" :key="s.label"
               :class="['cmt-stat', s.cls, { on: cmtStatus === s.val }]"
               @click="switchCmtFilter(s.val)">
            <span class="cs-num">{{ s.num }}</span>
            <span class="cs-label">{{ s.label }}</span>
          </div>
        </div>

        <div class="cmt-tip">
          <b>审核说明</b>：评论提交后需管理员审核才会对其他人可见。
          <span class="ct-item"><span class="dot wait"></span>待审核 —— 仅自己可见</span>
          <span class="ct-item"><span class="dot ok"></span>已通过 —— 所有人可见</span>
          <span class="ct-item"><span class="dot no"></span>未通过 —— 仅自己可见</span>
        </div>

        <div v-if="cmtLoading" class="empty">加载中...</div>

        <template v-else-if="cmtList.length > 0">
          <div class="cmt-list">
            <div v-for="c in cmtList" :key="c.commentId" class="cmt-card" :class="{ pending: c.status === 0, rejected: c.status === -1 }">
              <img class="cmt-cover" :src="cmtCoverOf(c.resourceCover)" @error="onCmtCoverErr" alt="" />
              <div class="cmt-body">
                <div class="cmt-line1">
                  <span :class="['cmt-type', c.resourceType === 1 ? 'audio' : 'video']">
                    {{ c.resourceType === 1 ? ' 音频' : ' 视频' }}
                  </span>
                  <span class="cmt-name">{{ c.resourceName || '资源已下架' }}</span>
                  <span :class="['cmt-badge', cmtBadgeCls(c.status)]">{{ cmtStatusText(c.status) }}</span>
                </div>
                <div class="cmt-text">{{ c.commentText }}</div>
                <div class="cmt-time">{{ formatDateTime(c.createTime) }}</div>
              </div>
              <button class="cmt-go" @click="goCommentResource(c)">去看看 <AppIcon name="arrow-right" /></button>
            </div>
          </div>

          <div v-if="cmtTotal > cmtPageSize" class="cmt-pager">
            <button :disabled="cmtPage <= 1" @click="goCmtPage(cmtPage - 1)">上一页</button>
            <span class="cmt-page-info">{{ cmtPage }} / {{ Math.ceil(cmtTotal / cmtPageSize) }}</span>
            <button :disabled="cmtPage >= Math.ceil(cmtTotal / cmtPageSize)" @click="goCmtPage(cmtPage + 1)">下一页</button>
          </div>
        </template>

        <div v-else class="empty">{{ cmtEmptyText }}</div>
      </div>

      <!-- ========== 账号设置 ========== -->
      <div v-if="activeTab === 'setting'" class="setting-area">
        <div class="sec-card">
          <h3><AppIcon name="device" /> 登录设备</h3>
          <p class="sec-desc">下面是你账号的登录设备（30 分钟内有活动算「在线」），可以移除不认识的设备（移除后该设备需要重新登录）</p>
          <div v-if="visibleSessions.length" class="session-list">
            <div v-for="s in visibleSessions" :key="s.sessionId" class="session-item" :class="{ current: s.current, off: s.status === 0 }">
              <span class="se-icon">{{ deviceIcon(s.device) }}</span>
              <div class="se-body">
                <p class="se-device">
                  {{ s.device }}
                  <span v-if="s.current" class="se-tag">当前</span>
                  <span :class="['se-state', isOnline(s) ? 'on' : 'idle']">{{ isOnline(s) ? '在线' : (s.status === 0 ? '已移除' : '空闲') }}</span>
                </p>
                <p class="se-meta">IP {{ s.ip }} · 登录 {{ formatDateTime(s.loginTime) }} · 活跃 {{ relativeTime(s.lastActiveTime || s.loginTime) }}</p>
              </div>
              <button v-if="s.status === 1 && !s.current" class="se-kick" @click="kick(s.sessionId)">移除</button>
              <span v-else-if="s.status === 0" class="se-off">已下线</span>
            </div>
          </div>
          <p v-else class="sec-desc">暂无有效会话记录</p>
          <div class="se-actions">
            <button class="link-btn" @click="loadSessions"><AppIcon name="refresh" /> 刷新</button>
            <button class="link-btn danger" @click="kickAllOthers"><AppIcon name="ban" /> 移除其他所有设备</button>
            <button v-if="kickedCount" class="link-btn" @click="showKicked = !showKicked">
              <template v-if="showKicked">隐藏已移除的 {{ kickedCount }} 台 <AppIcon name="chevron-up" /></template><template v-else>显示已移除的 {{ kickedCount }} 台 <AppIcon name="chevron-down" /></template>
            </button>
          </div>
        </div>
        <div class="sec-card">
          <!-- 基本资料 / 修改密码 已移入「个人中心」弹窗（右上角编辑个人资料按钮打开） -->
          <h3><AppIcon name="lock" /> 安全中心</h3>
          <p class="sec-desc">资料编辑与密码修改请点击右上角「编辑个人资料」按钮，在弹窗中操作。</p>
          <div class="se-actions">
            <button class="link-btn" @click="openUserCenter"><AppIcon name="user" /> 打开个人中心</button>
            <button class="link-btn danger-link" @click="deactivateAccountFlow"><AppIcon name="alert-triangle" /> 注销账号</button>
          </div>
          <p class="sec-desc" style="font-size:12px;margin-top:6px">注销后账号将匿名化并无法登录，此操作不可恢复。</p>
        </div>
        <button class="logout-btn" @click="logout"><AppIcon name="door" /> 退出登录</button>
      </div>

      <!-- ========== 数据看板（内嵌，不再跳转 /databoard） ========== -->
      <div v-if="activeTab === 'board'" class="board-tab">
        <div class="diary-toolbar">
          <div class="diary-toolbar-left">
            <span class="diary-toolbar-title"><AppIcon name="chart-bar" /> 我的数据看板</span>
            <span class="diary-toolbar-count">记录 {{ diaryCount }} 篇 · 测评 {{ testCount }} 次</span>
          </div>
          <router-link to="/databoard" class="primary-btn small" style="text-decoration:none"><AppIcon name="arrow-up-right" /> 全屏查看</router-link>
        </div>
        <DataBoard embedded />
      </div>

      <!-- ========== 轻量无感记录（隐私总控台） ========== -->
      <div v-if="activeTab === 'sense'" class="sense-area">
        <div class="sec-card">
          <h3><AppIcon name="cloud" /> 无感记录开关</h3>
          <p class="sec-desc">
            默认关闭。<b>只有你主动打开，系统才会采集</b>；随时可以关掉并清空全部数据。
          </p>

          <div v-if="!senseCfg.featureEnabled" class="sense-off-tip">
            后端当前未启用无感模块（<code>app.sense.enabled=false</code>），开关暂不可用。
          </div>

          <div class="sw-row" :class="{ dim: !senseCfg.featureEnabled }">
            <div class="sw-info">
              <p class="sw-name">总开关</p>
              <p class="sw-sub">关闭后立即停止全部采集（急停）；子开关的选择会被记住，重新打开时按原样恢复</p>
            </div>
            <el-switch v-model="senseForm.enabled" :disabled="!senseCfg.featureEnabled || senseSaving" @change="saveSense" />
          </div>

          <p v-if="senseForm.enabled && senseCfg.featureEnabled" class="sense-auto-tip">
            <AppIcon name="moon" /> <b>自动代记已生效</b>：白天你照常用 App 就行，不用刻意打卡。当天 23:30 如果还没写日记，
            系统会按使用痕迹替你记一条，标记为「系统代记」；连续天数照常计算，但<b>不计经验值</b>。
            想变成自己的记录，去日记页补一句话即可。
          </p>

          <div class="sw-row sub" :class="{ dim: !senseCfg.featureEnabled || !senseForm.enabled }">
            <div class="sw-info">
              <p class="sw-name">页面行为采集</p>
              <p class="sw-sub">仅统计停留秒数与点击次数，<b>不记录点击了什么、不读取页面文字</b></p>
            </div>
            <el-switch v-model="senseForm.behavior" :disabled="!senseCfg.featureEnabled || !senseForm.enabled || senseSaving" @change="saveSense" />
          </div>

          <div class="sw-row sub" :class="{ dim: !senseCfg.featureEnabled || !senseForm.enabled }">
            <div class="sw-info">
              <p class="sw-name">随笔情感分析</p>
              <p class="sw-sub">只在你主动点击时分析一次，<b>原文不落库</b>，只保留命中的词典词</p>
            </div>
            <el-switch v-model="senseForm.text" :disabled="!senseCfg.featureEnabled || !senseForm.enabled || senseSaving" @change="saveSense" />
          </div>
        </div>

        <div class="sec-card">
          <h3><AppIcon name="package" /> 我的数据概况</h3>
          <div class="sense-stat-grid">
            <div class="ssg-item"><span class="ssg-num">{{ senseCfg.behaviorRows ?? 0 }}</span><span class="ssg-lb">行为特征行数</span></div>
            <div class="ssg-item"><span class="ssg-num">{{ senseCfg.pendingCount ?? 0 }}</span><span class="ssg-lb">待确认推测</span></div>
            <div class="ssg-item"><span class="ssg-num">{{ senseCfg.retentionDays ?? '-' }}</span><span class="ssg-lb">保留天数</span></div>
            <div class="ssg-item"><span class="ssg-num">{{ pctOf(senseCfg.minConfidence) }}%</span><span class="ssg-lb">最低置信度</span></div>
          </div>
          <p class="sec-desc">
            采集区间：{{ senseCfg.firstDate || '—' }} ~ {{ senseCfg.lastDate || '—' }}
            <template v-if="senseCfg.grantAt"> · 授权于 {{ fmtSenseTime(senseCfg.grantAt) }}</template>
            <template v-if="senseCfg.revokeAt"> · 最近一次撤销 {{ fmtSenseTime(senseCfg.revokeAt) }}</template>
          </p>
          <div class="se-actions">
            <button class="link-btn" @click="loadSense"><AppIcon name="refresh" /> 刷新</button>
            <button class="link-btn" @click="exportSense"><AppIcon name="download" /> 导出我的数据</button>
            <button class="link-btn danger" @click="clearSense"><AppIcon name="trash" /> 一键清空行为数据</button>
          </div>
        </div>

        <div class="sec-card">
          <h3><AppIcon name="target" /> 推测命中率</h3>
          <p class="sec-desc">「采用」时你最终选的情绪与系统推荐一致才算命中 —— 这是论文里可以直接用的实验数据。</p>
          <div v-if="accuracy.total" class="sense-stat-grid">
            <div class="ssg-item"><span class="ssg-num">{{ accuracy.total }}</span><span class="ssg-lb">已处理推测</span></div>
            <div class="ssg-item"><span class="ssg-num">{{ accuracy.adopted }}</span><span class="ssg-lb">采用次数</span></div>
            <div class="ssg-item"><span class="ssg-num">{{ accuracy.adoptRate }}%</span><span class="ssg-lb">采纳率</span></div>
            <div class="ssg-item"><span class="ssg-num">{{ accuracy.hitRate }}%</span><span class="ssg-lb">完全一致率</span></div>
            <div class="ssg-item"><span class="ssg-num">{{ accuracy.rejected }}</span><span class="ssg-lb">忽略次数</span></div>
          </div>
          <p v-else class="sec-desc">还没有处理过推测记录。</p>
        </div>

        <div class="sec-card">
          <h3><AppIcon name="clock" /> 推测历史</h3>
          <div v-if="predHistory.length" class="ph-list">
            <div v-for="p in predHistory" :key="p.predictionId" class="ph-item">
              <span class="ph-date">{{ p.targetDate }}</span>
              <span class="ph-emo" :style="{ background: emotionBg(p.emotion) }">{{ p.emotionName }}</span>
              <span class="ph-conf">{{ pctOf(p.confidence) }}%</span>
              <span :class="['ph-status', 's' + p.status]">{{ statusText(p.status) }}</span>
              <!-- 「待确认」只是状态徽章、点不了，处理动作（采用/忽略）在写日记页完成。
                   以前用户看到「待确认」不知道去哪处理，这里给一个明确的入口。 -->
              <button v-if="p.status === 0" class="ph-handle" @click="handlePrediction(p)">去处理 ›</button>
            </div>
          </div>
          <p v-else class="sec-desc">还没有推测记录。可以在写日记时点「帮我猜一下」。</p>
          <p v-if="pendingCount" class="sec-desc ph-tip">
            有 {{ pendingCount }} 条推测还在等你确认 —— 点「去处理」到写日记页，选「采用并预填」或「不用了」即可。
          </p>
          <div class="se-actions">
            <button class="link-btn" :disabled="senseBusy" @click="genToday">
              <template v-if="senseBusy">生成中…</template><template v-else><AppIcon name="zap" /> 立即生成一条今日推测</template>
            </button>
          </div>
        </div>

        <div class="sec-card">
          <h3><AppIcon name="lock" /> 隐私操作日志</h3>
          <p class="sec-desc">每一次开关变更、推测生成、数据清空都会留痕，且只对本人可见。</p>
          <div v-if="privacyLogs.length" class="pl-list">
            <div v-for="(l, i) in privacyLogs" :key="i" class="pl-item">
              <span class="pl-act">{{ l.action }}</span>
              <span class="pl-detail">{{ l.detail }}</span>
              <span class="pl-time">{{ fmtSenseTime(l.createTime) }}</span>
            </div>
          </div>
          <p v-else class="sec-desc">暂无日志。</p>
        </div>

        <div class="sec-card sense-privacy">
          <h3><AppIcon name="shield" /> 隐私承诺</h3>
          <ul class="sp-list">
            <li>不使用摄像头、麦克风、屏幕录制，不采集键盘输入明文；</li>
            <li>采集字段只有停留毫秒数与点击次数，前端先聚合成「天 × 场景」再上传；</li>
            <li>数据库表内不含任何文本列，管理员也无法查看他人行为数据（物理隔离，而非逻辑判断）；</li>
            <li>数据保留 {{ senseCfg.retentionDays ?? 180 }} 天后自动清理，可随时手动清空；</li>
            <li>推测结果只是推荐，<b>只有你点了保存才会变成正式日记</b>，且会永久标记来源。</li>
          </ul>
        </div>
      </div>

      <!-- ========== 情绪风险预警历史（非医疗诊断） ========== -->
      <div v-if="activeTab === 'risk'" class="risk-area">
        <div class="risk-disclaimer">
          <AppIcon name="alert-triangle" /> 本模块是<b>统计意义上的情绪关怀提示</b>，不构成任何医疗诊断，<b>不能作为抑郁症等疾病的诊断依据</b>。
          如持续不适，请以专业医疗机构意见为准。
        </div>

        <div class="sec-card">
          <h3><AppIcon name="chart-bar" /> 预警概览</h3>
          <div class="sense-stat-grid">
            <div class="ssg-item"><span class="ssg-num">{{ riskOv.unread ?? 0 }}</span><span class="ssg-lb">未读</span></div>
            <div class="ssg-item"><span class="ssg-num">{{ riskOv.total ?? 0 }}</span><span class="ssg-lb">累计预警</span></div>
            <div class="ssg-item"><span class="ssg-num">{{ riskOv.maxLevelName || '正常' }}</span><span class="ssg-lb">历史最高等级</span></div>
            <div class="ssg-item"><span class="ssg-num">{{ riskOv.lastDate || '—' }}</span><span class="ssg-lb">最近预警日</span></div>
          </div>
        </div>

        <div class="sec-card">
          <h3><AppIcon name="sliders" /> 当前判定阈值（后端可配）</h3>
          <p class="sec-desc">这些值全部来自 <code>application.yml <AppIcon name="arrow-right" /> app.alert.*</code>，修改后重启后端即可生效，无需改代码。危机词库同样在该配置的 <code>crisis-words</code> 下维护。</p>
          <div class="th-grid">
            <div v-for="t in thresholdRows" :key="t.k" class="th-item">
              <span class="th-k">{{ t.label }}</span><span class="th-v">{{ t.v }}</span>
            </div>
          </div>
          <div class="se-actions">
            <button class="link-btn" :disabled="riskBusy" @click="runEvaluate">
              {{ riskBusy ? '判定中…' : ' 手动触发一次判定' }}
            </button>
            <button class="link-btn" @click="loadRisk"><AppIcon name="refresh" /> 刷新</button>
          </div>
        </div>

        <div class="sec-card">
          <h3><AppIcon name="clock" /> 历史预警记录</h3>
          <div v-if="riskList.length" class="rl-list">
            <div v-for="a in riskList" :key="a.alertId" class="rl-item" :class="['lv' + a.level, { unread: !a.isRead }]">
              <div class="rl-head">
                <span class="rl-level">{{ a.levelName }}</span>
                <span class="rl-date">{{ a.alertDate }}</span>
                <span class="rl-rule">{{ a.crisis ? '危机词命中（即时关怀）' : (a.triggerRuleName || a.triggerRule) }}</span>
                <span v-if="a.crisis" class="rl-tag crisis-tag">即时</span>
                <span v-else-if="a.misreport" class="rl-tag misreport-tag">已标记误报</span>
                <span v-else-if="!a.ackStatus" class="rl-tag">未确认</span>
              </div>
              <p class="rl-detail">{{ a.triggerDetail }}</p>
              <div v-if="openRiskId === a.alertId" class="rl-advice pre">{{ a.advice }}</div>
              <div class="rl-actions">
                <button class="link-btn" @click="toggleAdvice(a)">
                  <template v-if="openRiskId === a.alertId">收起建议 <AppIcon name="chevron-up" /></template><template v-else>查看建议 <AppIcon name="chevron-down" /></template>
                </button>
                <!-- 误报反馈：内容与真实状态不符时补标「这不是我」（危机通道不允许，后端也会拒绝） -->
                <button v-if="!a.crisis && !a.misreport" class="link-btn misreport-link" @click="misreportOne(a)">这不是我（误报）</button>
                <button v-if="!a.ackStatus" class="link-btn primary-link" @click="ackOne(a)">我知道了</button>
              </div>
            </div>
          </div>
          <p v-else class="sec-desc">
            还没有预警记录。趋势提示需要连续多天的负面数据才会触发，偶尔一次难过不会打扰你；
            但如果日记里写下了需要被认真对待的表达，系统会在保存的那一刻立即给出危机关怀提示。
          </p>

          <div v-if="riskTotal > riskList.length" class="rl-more">
            <button class="link-btn" :disabled="riskBusy" @click="loadRisk(riskPage + 1)">加载更多（{{ riskList.length }}/{{ riskTotal }}）</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧信息栏（Steam 式：等级框 → 当前在线 → 数据统计 → 本周情绪） -->
    <aside class="steam-side">
      <div class="side-card level-card">
        <div class="lc-head">
          <span class="lc-head-t"><AppIcon name="award" /> 绪见等级</span>
          <span class="lc-head-exp">{{ expTotal }} EXP</span>
        </div>
        <div class="lc-main">
          <div class="lc-badge"><span>{{ userLevel }}</span></div>
          <span class="lc-ji">级</span>
          <div class="lc-exp-wrap">
            <div class="lc-name">距离下一级还差 {{ nextLevelNeed }} 经验</div>
            <div class="lc-bar-row">
              <div class="lc-bar"><div class="lc-fill" :style="{ width: levelProgress + '%' }"></div></div>
              <span class="lc-progress-text">{{ levelProgress }}%</span>
            </div>
          </div>
        </div>
        <button class="lc-detail-toggle" @click="showExpDetail = !showExpDetail">
          <template v-if="showExpDetail">收起经验明细 <AppIcon name="chevron-up" /></template><template v-else>经验怎么来的？明细 <AppIcon name="chevron-down" /></template>
        </button>
        <transition name="fade">
          <div v-if="showExpDetail" class="lc-detail">
            <div v-for="r in expBreakdown" :key="r.label" class="lc-detail-row">
              <span class="lc-d-label"><img v-if="r.icon" :src="r.icon" class="lc-d-icon" alt="" />{{ r.label }}</span>
              <span class="lc-d-count">{{ r.count }} 次 × {{ r.unit }}</span>
              <span class="lc-d-exp">+{{ r.count * r.unit }}</span>
            </div>
            <div class="lc-detail-row total">
              <span class="lc-d-label">合计</span>
              <span class="lc-d-count"></span>
              <span class="lc-d-exp">{{ expTotal }} EXP · {{ userLevel }} 级</span>
            </div>
            <p class="lc-d-tip">每满 50 经验升 1 级；再写 {{ Math.ceil(nextLevelNeed / 3) }} 篇日记即可升级</p>
            <p class="lc-d-tip">只有你亲笔写下（含把系统代记补成自己的）才计入经验；系统代记只保打卡、不给经验。</p>
          </div>
        </transition>
        <p class="lc-desc">写日记 +3、完成测评 +5、AI 对话 +2、收藏/点赞/观看/反馈各 +1。</p>
        <button class="side-btn" @click="openUserCenter">编辑个人资料</button>
      </div>

      <div class="side-card online-card">
        <h4 class="side-title"><i class="online-light"></i> 当前在线</h4>
        <p class="side-online"><span class="dot"></span>最近活跃 {{ lastActiveText }}</p>
        <div class="side-devices">
          <template v-if="hasSessionData">
            <span class="sd-num">{{ onlineDeviceCount }}</span> 台设备在线<span class="sd-sub">（共 {{ totalDeviceCount }} 台）</span>
          </template>
          <span v-else class="sd-sub">加载中…</span>
          <button class="link-btn" @click="switchTab('setting')">管理设备</button>
        </div>
      </div>

      <div class="side-card stat-card">
        <template v-for="g in sideGroups" :key="g.title">
          <div class="sg-title">{{ g.title }}</div>
          <div v-for="s in g.items" :key="s.key" :class="['side-stat', { active: activeTab === s.key }]" @click="s.action ? s.action() : switchTab(s.key)">
            <span class="ss-label"><img v-if="s.icon" :src="s.icon" class="ss-icon" alt="" />{{ s.label }}</span>
            <span class="ss-num"><AppIcon v-if="s.glyph" :name="s.glyph" /><template v-else>{{ s.num }}</template></span>
          </div>
        </template>
      </div>

      <!-- 情绪预警：独立警示条（长期连续负面数据才触发，非医疗诊断） -->
      <button class="risk-cta" @click="switchTab('risk')" title="查看情绪预警记录">
        <span class="rc-left"><img src="/img/情绪预警.png" class="rc-icon" alt="" /><span class="rc-dot"></span>情绪预警</span>
        <span class="rc-right">{{ riskCtaText }}</span>
      </button>

      <div class="side-card">
        <h4 class="side-title">本周情绪</h4>
        <div class="side-emo">
          <img :src="weekStat.topEmotionIcon" class="se-img" alt="" />
          <div>
            <div class="se-label">{{ weekStat.topEmotionLabel }}</div>
            <div class="se-sub">记录 {{ weekStat.total }} 天 · 积极 {{ weekStat.happyDays }} 天</div>
          </div>
        </div>
        <p class="se-summary">{{ weekStat.summary }}</p>
        <button class="side-btn ghost" @click="switchTab('weekReport')">查看情绪周报 <AppIcon name="arrow-right" /></button>
      </div>
    </aside>
    </div>

    <!-- AI 对话详情弹窗 -->
    <div v-if="aiDetail" class="mask" @click.self="aiDetail = null">
      <div class="detail-modal">
        <div class="detail-header">
          <h3><AppIcon name="bot" /> AI 对话详情</h3>
          <button class="detail-close" @click="aiDetail = null"><AppIcon name="close" /></button>
        </div>
        <div class="detail-meta"><AppIcon name="clock" /> {{ formatDateTime(aiDetail.createTime) }}</div>
        <div class="detail-body">
          <div class="ai-user-row"><span class="ai-tag user">你</span></div>
          <div class="ai-bubble user full">{{ aiDetail.userMsg ?? aiDetail.userMessage }}</div>
          <div class="ai-user-row"><span class="ai-tag ai">AI</span></div>
          <div class="ai-bubble ai full">{{ aiDetail.aiReply ?? aiDetail.aiResponse }}</div>
        </div>
      </div>
    </div>

    <!-- 反馈完整对话弹窗 -->
    <div v-if="fbDetail" class="mask" @click.self="fbDetail = null">
      <div class="detail-modal">
        <div class="detail-header">
          <h3><AppIcon name="message" /> 反馈对话详情</h3>
          <button class="detail-close" @click="fbDetail = null"><AppIcon name="close" /></button>
        </div>
        <div class="detail-meta"><AppIcon name="clock" /> {{ formatDateTime(fbDetail.createTime) }}
          <span v-if="fbDetail.replyText" class="fb-status replied" style="margin-left:10px"><AppIcon name="check" /> 已回复</span>
          <span v-else class="fb-status pending" style="margin-left:10px"><AppIcon name="loader" /> 待回复</span>
        </div>
        <div class="detail-body">
          <div v-if="fbLoading" class="empty" style="padding:30px">加载中...</div>
          <template v-else>
            <div v-for="(m, idx) in fbMessages" :key="idx" class="fb-msg-row">
              <div :class="['fb-msg-bubble', m.senderType === 'user' ? 'user' : 'admin']">
                <div class="fb-msg-tag"><template v-if="m.senderType === 'user'"><AppIcon name="user" /> 我</template><template v-else><AppIcon name="shield" /> 管理员</template></div>
                <div class="fb-msg-content">{{ m.content }}</div>
                <div class="fb-msg-time">{{ formatDateTime(m.createTime) }}</div>
              </div>
            </div>
            <div v-if="fbMessages.length === 0" class="empty" style="padding:30px">暂无消息记录</div>
          </template>
        </div>
      </div>
    </div>

    <!-- 头像选择弹窗 -->
    <div v-if="showAvatarPicker" class="mask" @click.self="showAvatarPicker = false">
      <div class="avatar-picker">
        <h3><AppIcon name="image" /> 设置头像</h3>
        <div class="avatar-upload-area" @click="$refs.avatarUploadRef.click()">
          <div class="upload-big-icon"><AppIcon name="upload" /></div>
          <p>点击上传头像</p>
          <p class="upload-hint">支持 JPG / PNG 格式</p>
        </div>
        <div class="picker-actions">
          <button class="cancel" @click="showAvatarPicker = false">取消</button>
        </div>
      </div>
    </div>

    <!-- 测评详情弹窗 -->
    <div v-if="testDetail" class="mask" @click.self="testDetail = null">
      <div class="detail-modal" style="max-width:480px">
        <div class="detail-header"><h3><AppIcon name="clipboard" /> 测评报告详情</h3><button class="detail-close" @click="testDetail = null"><AppIcon name="close" /></button></div>
        <div class="test-detail-body">
          <div class="test-detail-name">{{ testDetail.testTitle || testDetail.paperName || '综合测试' }}</div>
          <div class="test-detail-circle" :class="testLevelClass(testDetail)"><span class="test-detail-score">{{ testDetail.score ?? testDetail.totalScore }}</span><span class="test-detail-total">/ {{ testDetail.totalScore || '--' }}</span></div>
          <div class="test-detail-level" :class="testLevelClass(testDetail)">{{ testDetail.level || getEmotionText(testDetail.emotionLevel) || '状态未知' }}</div>
          <div class="test-detail-meta"><AppIcon name="clock" /> {{ formatDateTime(testDetail.createTime) }}</div>
          <div class="test-detail-tip">{{ testDetail.tip || '暂无建议，请重新测评 ' }}</div>
          <div class="test-detail-actions"><button class="test-action-btn" @click="testDetail = null">关闭</button><button class="test-action-btn primary" @click="showTestShare(testDetail); testDetail = null"><AppIcon name="camera" /> 生成分享卡片</button></div>
        </div>
      </div>
    </div>

    <!-- 测评分享卡片弹窗 -->
    <div v-if="showTestShareCard" class="mask" @click.self="showTestShareCard = false">
      <div class="detail-modal" style="max-width:440px">
        <div class="detail-header"><h3><AppIcon name="camera" /> 生成分享卡片</h3><button class="detail-close" @click="showTestShareCard = false"><AppIcon name="close" /></button></div>
        <div class="share-card-preview" v-if="currentShareTest">
          <div class="sc-header"><div class="sc-logo"><AppIcon name="leaf" /> 绪见测试</div><div class="sc-date">{{ today }}</div></div>
          <div class="sc-body">
            <div class="sc-test-name">{{ currentShareTest.testTitle || currentShareTest.paperName || '心理测评' }}</div>
            <div class="sc-circle" :class="testLevelClass(currentShareTest)"><span class="sc-score">{{ currentShareTest.score ?? currentShareTest.totalScore }}</span><span class="sc-total">/ {{ currentShareTest.totalScore || '--' }}</span></div>
            <div class="sc-level" :class="testLevelClass(currentShareTest)">{{ currentShareTest.level || getEmotionText(currentShareTest.emotionLevel) || '状态未知' }}</div>
            <p class="sc-tip">{{ currentShareTest.tip || '继续关注自己的心理状态 ' }}</p>
          </div>
          <div class="sc-footer"><span class="sc-slogan">科学的心理测评，温柔了解自己 <AppIcon name="heart" /></span><span class="sc-brand">绪见</span></div>
        </div>
        <div class="picker-actions"><button class="cancel" @click="showTestShareCard = false">取消</button><button class="primary-btn small" @click="saveTestShareCard"><AppIcon name="save" /> 保存图片</button></div>
      </div>
    </div>

    <!-- 头像装饰框弹窗 -->
    <div v-if="showFramePicker" class="mask" @click.self="showFramePicker = false">
      <div class="avatar-picker" style="width:440px">
        <h3><AppIcon name="image" /> 头像装饰框</h3>
        <p class="modal-desc" style="margin:-8px 0 14px;font-size:12px"><AppIcon name="sparkles" /> 本周情绪推荐：{{ weekStat.topEmotionIcon }} {{ weekStat.summary?.split('！')[0]?.split('？')[0] || '' }}</p>
        <div class="frame-grid">
          <div v-for="f in avatarFrames" :key="f.name"
               :class="['frame-opt', { on: userFrame === f.name }]"
               @click="pickFrame(f.name)">
            <div class="frame-preview" :class="['frame-' + f.name]">
              <img :src="avatarUrl" @error="onAvatarError" class="frame-avatar-inner" />
              <img v-if="f.img" :src="f.img" class="frame-ring-img" :style="{ width: (f.scale || 120) + '%', height: (f.scale || 120) + '%' }" @error="$event.target.style.display='none'" />
            </div>
            <div class="frame-name">{{ f.icon }} {{ f.label }}</div>
            <div v-if="f.recommend" class="frame-rec"><AppIcon name="sparkles" /> 推荐</div>
          </div>
        </div>
        <div class="picker-actions">
          <button class="cancel" @click="showFramePicker = false">取消</button>
        </div>
      </div>
    </div>

    <!-- ========== 个人中心弹窗（基本资料 / 个性化设置 / 修改密码） ========== -->
    <div v-if="showUserCenter" class="mask uc-mask" @click.self="showUserCenter = false">
      <div class="uc-modal">
        <button class="uc-close" @click="showUserCenter = false" title="关闭"><AppIcon name="close" /></button>
        <div class="uc-head"><AppIcon name="user" /> 个人中心</div>
        <div class="uc-tabs">
          <button v-for="t in ucTabs" :key="t.key" :class="['uc-tab', { on: ucTab === t.key }]" @click="ucTab = t.key"><AppIcon :name="t.icon" /> {{ t.label }}</button>
        </div>

        <!-- 基本资料 -->
        <div v-if="ucTab === 'basic'" class="uc-body">
          <div class="uc-avatar-card">
            <div class="avatar-wrap uc-avatar" :class="['frame-' + userFrame]" @click="showAvatarPicker = true" title="点击更换头像">
              <img :src="avatarUrl" @error="onAvatarError" class="avatar-img-inner" />
            </div>
            <div class="uc-avatar-info">
              <div class="uc-avatar-name">{{ editForm.nickname || user.username }}</div>
              <button class="uc-upload-btn" @click="avatarUploadRef?.click()"><AppIcon name="upload" /> 上传头像</button>
            </div>
          </div>
          <div class="uc-grid">
            <div class="form-item"><label>昵称（展示名）</label><input v-model="editForm.nickname" placeholder="给自己起个好听的名字" /><p class="uc-uname-hint">登录账号：{{ user.username }}（不可修改）</p></div>
            <div class="form-item uc-full"><label>邮箱</label><input v-model="editForm.email" placeholder="xxx@qq.com" /></div>
          </div>
        </div>

        <!-- 个性化设置 -->
        <div v-else-if="ucTab === 'personal'" class="uc-body">
          <div class="uc-sec-title"><AppIcon name="palette" /> 主题颜色</div>
          <div class="uc-dots">
            <button v-for="c in themeColors" :key="c.name"
                    :class="['color-dot', { on: themeColor.name === c.name }]"
                    :style="{ background: c.hex }"
                    @click="setTheme(c)" :title="c.label"></button>
          </div>
          <div class="uc-sec-title"><AppIcon name="moon" /> 深色模式</div>
          <button class="uc-switch" @click="toggleDark"><template v-if="darkMode"><AppIcon name="moon" /> 当前为深色模式，点击切换浅色</template><template v-else><AppIcon name="sun" /> 当前为浅色模式，点击切换深色</template></button>
          <div class="uc-sec-title"><AppIcon name="sparkles" /> 个性化装饰</div>
          <div class="uc-deco-row">
            <button class="cancel" @click="showBgPicker = true"><AppIcon name="image" /> 顶部背景图</button>
            <button class="cancel" @click="showFramePicker = true"><AppIcon name="gift" /> 头像装饰框</button>
            <button class="cancel" @click="showAvatarPicker = true"><AppIcon name="face-happy" /> 预设头像</button>
          </div>
        </div>

        <!-- 修改密码 -->
        <div v-else class="uc-body">
          <div class="uc-grid">
            <div class="form-item"><label>旧密码</label><input v-model="pwdForm.oldPassword" type="password" placeholder="输入当前密码" /></div>
            <div class="form-item"><label>新密码</label><input v-model="pwdForm.newPassword" type="password" placeholder="至少6位" /></div>
            <div class="form-item uc-full"><label>确认新密码</label><input v-model="pwdForm.confirm" type="password" placeholder="再输入一次" /></div>
          </div>
          <p class="uc-tip"><AppIcon name="lock" /> 修改成功后会退出登录，请使用新密码重新登录。</p>
        </div>

        <div class="uc-footer">
          <template v-if="ucTab === 'basic'">
            <button class="cancel" @click="showUserCenter = false">取消</button>
            <button class="primary-btn small" @click="saveProfile"><AppIcon name="save" /> 保存资料</button>
          </template>
          <template v-else-if="ucTab === 'personal'">
            <button class="primary-btn small" @click="showUserCenter = false"><AppIcon name="check" /> 完成</button>
          </template>
          <template v-else>
            <button class="danger-btn small" :disabled="pwdSaving" @click="savePassword">{{ pwdSaving ? '提交中…' : ' 确认修改密码' }}</button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch, onUnmounted } from 'vue'
import { EMOTIONS as emotions } from '@/utils/constants'  // 情绪字典唯一真源（utils/constants.js）
import { getUserById, updateUser, changePassword, cancelLike, cancelCollect, getSessions, kickSession as apiKickSession, kickOtherSessions, deactivateAccount, getMyComments, getMyOverview } from '@/api'
import { jellyConfirm } from '@/utils/confirm'
import { AVATAR_FRAMES, FRAME_SCALES } from '@/lib/avatarFrames'
import { clearUserPrefs } from '../utils/userPrefs'
import { useUserStore } from '../stores/user'
import { feedbackWS } from '../utils/ws'
import { useRouter, useRoute } from 'vue-router'
import { resetAdminCheck } from '../router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useChart } from '@/composables/useChart'
import DataBoard from './DataBoard.vue'

const router = useRouter()
const route = useRoute()
import request from '@/api'

// ===== 用户 =====
const user = ref({})
const editForm = ref({ nickname: '', email: '', avatar: '' })
const pwdForm = ref({ oldPassword: '', newPassword: '', confirm: '' })
const TAB_KEYS = ['activity','like','fav','history','diary','ai','test','weekReport','feedback','comments','setting','board','sense','risk']
const activeTab = ref((route.query.tab && TAB_KEYS.includes(route.query.tab)) ? route.query.tab : 'activity')
watch(() => route.query.tab, (t) => {
  if (t && TAB_KEYS.includes(t)) {
    activeTab.value = t
    if (t === 'setting') loadSessions()
    if (t === 'sense') loadSense()
    if (t === 'risk') loadRisk(1)
  }
})
watch(activeTab, (t) => { if (t === 'setting') loadSessions() })
const switchTab = (key) => {
  activeTab.value = key
  router.replace({ path: '/profile', query: { tab: key } })
  nextTick(() => {
    if (key === 'test') initTestChart()
    if (key === 'weekReport') initWeekCharts()
    // 导航胶囊在页面顶部，点击后平滑滚到内容区
    document.querySelector('.tab-content')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}
const showAvatarPicker = ref(false)
const showFramePicker = ref(false)
const showBgPicker = ref(false)
const avatarUploadRef = ref(null)

// ===== 个人中心弹窗（编辑个人资料按钮打开；基本资料/个性化设置/修改密码 三合一） =====
const showUserCenter = ref(false)
const ucTab = ref('basic')
const ucTabs = [
  { key: 'basic', icon: 'user', label: '基本资料' },
  { key: 'personal', icon: 'palette', label: '个性化设置' },
  { key: 'pwd', icon: 'lock', label: '修改密码' },
]
const openUserCenter = () => {
  // 每次打开都用当前用户信息重置表单，避免上次未保存的改动残留
  editForm.value.nickname = user.value.nickname || ''
  editForm.value.email = user.value.email || ''
  editForm.value.avatar = user.value.avatar || ''
  ucTab.value = 'basic'
  showUserCenter.value = true
}
const bgUploadRef = ref(null)

// ===== 顶部背景图（自定义）=====
const heroBg = ref(localStorage.getItem('profile_bg') || '')
// 展示焦点（background-position 百分比），在背景弹窗里点击预览图调整
const heroBgPos = ref(localStorage.getItem('profile_bg_pos') || '50% 50%')
const posXY = computed(() => (heroBgPos.value || '50% 50%').split(/\s+/).map(v => parseFloat(v) || 50))
const heroBgStyle = computed(() => {
  if (heroBg.value) {
    return {
      backgroundImage: `url(${heroBg.value})`,
      backgroundSize: 'cover',
      backgroundPosition: heroBgPos.value,
    }
  }
  // 默认：跟随主题渐变色
  return {
    background: 'linear-gradient(135deg, var(--c-main) 0%, var(--c-hex) 55%, var(--c-main) 100%)',
  }
})
// 微信式拖动调整：按住横幅拖动  实时更新展示位置（pointer events 兼容鼠标/触摸）
const adjustingBg = ref(false)
const dragState = ref(null)
const ripple = ref(null)
const rippleKey = ref(0)
let rippleTimer = null
const startBgAdjust = () => {
  showBgPicker.value = false
  adjustingBg.value = true
}
const showRipple = (x, y) => {
  ripple.value = { x, y }
  rippleKey.value++
  clearTimeout(rippleTimer)
  rippleTimer = setTimeout(() => { ripple.value = null }, 700)
}
const onBgPointerDown = (e) => {
  if (!adjustingBg.value || !heroBg.value) return
  // 落在提示条/按钮上时不进入拖动（否则 setPointerCapture 会把 click 重定向到横幅，按钮点不动）
  if (e.target.closest?.('.bg-adjust-tip')) return
  e.preventDefault()
  e.currentTarget.setPointerCapture?.(e.pointerId)
  dragState.value = { startX: e.clientX, startY: e.clientY, startPosX: posXY.value[0], startPosY: posXY.value[1], moved: false }
}
const onBgPointerMove = (e) => {
  const d = dragState.value
  if (!d) return
  const dx = e.clientX - d.startX
  const dy = e.clientY - d.startY
  if (!d.moved && Math.abs(dx) < 3 && Math.abs(dy) < 3) return
  d.moved = true
  const rect = e.currentTarget.getBoundingClientRect()
  const nx = Math.min(100, Math.max(0, Math.round(d.startPosX - (dx / rect.width) * 100)))
  const ny = Math.min(100, Math.max(0, Math.round(d.startPosY - (dy / rect.height) * 100)))
  heroBgPos.value = `${nx}% ${ny}%`
}
const onBgPointerUp = (e) => {
  const d = dragState.value
  if (!d) return
  dragState.value = null
  const rect = e.currentTarget.getBoundingClientRect()
  if (!d.moved) {
    // 没怎么动 = 单击：把点击处设为焦点
    const x = Math.round(((e.clientX - rect.left) / rect.width) * 100)
    const y = Math.round(((e.clientY - rect.top) / rect.height) * 100)
    heroBgPos.value = `${x}% ${y}%`
    showRipple(x, y)
  }
  localStorage.setItem('profile_bg_pos', heroBgPos.value)
}
// Esc 退出调整模式
const onAdjustKey = (e) => { if (e.key === 'Escape') adjustingBg.value = false }
window.addEventListener('keydown', onAdjustKey)
onUnmounted(() => window.removeEventListener('keydown', onAdjustKey))
const setBg = (url) => {
  heroBg.value = url
  if (url) localStorage.setItem('profile_bg', url)
  else localStorage.removeItem('profile_bg')
  showBgPicker.value = false
  saveSettingsToBackend({ profileBg: url || '' })
}
const onBgUpload = async (e) => {
  const file = e.target.files?.[0]; if (!file) return
  const fd = new FormData(); fd.append('file', file); fd.append('dir', 'bg')
  try {
    const data = await request.post('/upload/file', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (data.code === 200) setBg(data.data.url)
    else ElMessage.error(data.msg || '上传失败')
  } catch (err) { ElMessage.error('上传失败：' + err.message) }
  e.target.value = ''
}

// ===== 主题色（8种） =====
const themeColors = [
  { name: 'green',  label: '薄荷绿', hex: '#7bc47b', main: '#90db90', bg1: '#f6f9f6', bg2: '#f0f7f0' },
  { name: 'blue',   label: '天空蓝', hex: '#5b9bd5', main: '#7cb8ea', bg1: '#f2f7fc', bg2: '#eaf2fa' },
  { name: 'purple', label: '浪漫紫', hex: '#9b7bd5', main: '#b8a0e6', bg1: '#f6f3fb', bg2: '#ede8f7' },
  { name: 'pink',   label: '少女粉', hex: '#e59bb3', main: '#f2b8cd', bg1: '#fcf4f7', bg2: '#fae8ef' },
  { name: 'orange', label: '暖阳橙', hex: '#e69b5b', main: '#f2b87c', bg1: '#fdf6ef', bg2: '#fae8d9' },
  { name: 'teal',   label: '青碧',   hex: '#5bb8a6', main: '#7cd4c3', bg1: '#f2faf8', bg2: '#eaf5f2' },
  { name: 'rose',   label: '玫瑰红', hex: '#d96b7b', main: '#ec8c9a', bg1: '#fcf2f4', bg2: '#fae6e9' },
  { name: 'indigo', label: '靛青',   hex: '#6b7bd9', main: '#8c9aec', bg1: '#f2f4fc', bg2: '#e6e9fa' },
]
const themeColor = ref(JSON.parse(localStorage.getItem('profile_theme') || JSON.stringify(themeColors[0])))
// 同标签页内保存后立即通知顶栏（AppLayout）刷新，storage 事件只在跨标签页触发
const notifyProfileUpdated = () => window.dispatchEvent(new Event('profile-updated'))
const setTheme = (c) => {
  themeColor.value = c
  localStorage.setItem('profile_theme', JSON.stringify(c))
  saveSettingsToBackend({ theme: c.name })
  notifyProfileUpdated()
}

// ===== 深色模式 =====
const darkMode = ref(localStorage.getItem('profile_dark') === '1')
const toggleDark = () => {
  darkMode.value = !darkMode.value
  localStorage.setItem('profile_dark', darkMode.value ? '1' : '0')
  saveSettingsToBackend({ darkMode: darkMode.value ? 1 : 0 })
  notifyProfileUpdated()
}

// ===== 头像装饰框（名单与名字统一从 lib/avatarFrames.js 取，勿在此处再写死） =====
const frameNameMap = { frame1: 'flower', frame2: 'leaf', frame3: 'crown', frame4: 'star', frame5: 'heart', frame6: 'halo', frame7: 'cloud' }
const _rawFrame = localStorage.getItem('profile_frame') || 'none'
const userFrame = ref(frameNameMap[_rawFrame] || _rawFrame)
if (frameNameMap[_rawFrame]) localStorage.setItem('profile_frame', frameNameMap[_rawFrame])
const avatarFrames = computed(() => {
  const topEmo = weekStat.value.topEmotionIcon
  const recMap = { flower: topEmo === '', leaf: topEmo === '', crown: topEmo === '', star: topEmo === '', heart: topEmo === '', halo: topEmo === '', cloud: topEmo === '' }
  return AVATAR_FRAMES.map(f => ({ ...f, scale: FRAME_SCALES[f.name], recommend: !!recMap[f.name] }))
})
const pickFrame = (name) => {
  userFrame.value = name
  localStorage.setItem('profile_frame', name)
  showFramePicker.value = false
  saveSettingsToBackend({ avatarFrame: name })
  notifyProfileUpdated()
}
const currentFrameImg = computed(() => {
  const f = avatarFrames.value.find(f => f.name === userFrame.value)
  return f?.img || ''
})
const currentFrameScale = computed(() => {
  const f = avatarFrames.value.find(f => f.name === userFrame.value)
  return f?.scale || 120
})

// ===== 未读反馈 =====
const unreadFbCount = computed(() => fbList.value.filter(f => f.replyText && !f._seen).length)

// ===== 历史分组 =====

const hfGroup = ref('all')
const groupedHistory = computed(() => {
  if (filteredHistList.value.length === 0) return []
  return [{ label: '全部历史', items: filteredHistList.value }]
})

// 相对时间格式化
const relativeTime = (t) => {
  if (!t) return ''
  const diff = Math.floor((Date.now() - new Date(t).getTime()) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 604800) return `${Math.floor(diff / 86400)} 天前`
  return new Date(t).toLocaleDateString('zh-CN')
}

// ===== 用户 =====
const defaultAvatar = '/img/默认管理员头像.png'
const avatarUrl = computed(() => {
  const a = editForm.value.avatar
  if (!a) return defaultAvatar
  if (a.startsWith('http') || a.startsWith('/') || a.startsWith('data:')) return a
  return '/' + a
})
const onAvatarError = (e) => e.target.src = defaultAvatar
const onCoverError = (e) => e.target.src = defaultAvatar

// ===== 状态 =====
const diaryCount = ref(0)

// ===== localStorage =====
const loadStorage = (key) => { try { return JSON.parse(localStorage.getItem(key) || '{}') } catch { return {} } }
const fmt = (s) => {
  if (!isFinite(s) || !s) return '0:00'
  s = Math.floor(s); const m = Math.floor(s / 60); const r = s % 60
  return m + ':' + (r < 10 ? '0' : '') + r
}

// ===== 日期工具 =====
const formatDate = (v) => v ? String(v).substring(0, 10) : ''
const formatDateTime = (v) => v ? String(v).substring(0, 16).replace('T', ' ') : ''

// ===== 情绪 =====
const getWeekday = (v) => {
  const days = ['日','一','二','三','四','五','六']
  const d = new Date(formatDate(v))
  return '星期' + days[d.getDay()]
}

// ===== 登录设备（多设备会话管理） =====
const sessions = ref([])
const loadSessions = async () => {
  try {
    const res = await getSessions()
    if (res?.code === 200) sessions.value = res.data?.list || []
  } catch (e) { sessions.value = [] }
}
const kick = async (sessionId) => {
  try {
    const res = await apiKickSession(sessionId)
    if (res?.code === 200) { ElMessage.success('已移除该设备'); loadSessions() }
    else ElMessage.error(res?.msg || '移除失败')
  } catch (e) { ElMessage.error('网络错误') }
}
const kickAllOthers = async () => {
  try {
    const res = await kickOtherSessions()
    if (res?.code === 200) { ElMessage.success(`已移除 ${res.data?.count || 0} 台设备`); loadSessions() }
    else ElMessage.error(res?.msg || '操作失败')
  } catch (e) { ElMessage.error('网络错误') }
}
const deviceIcon = (d = '') => {
  const s = String(d)
  if (s.includes('iOS') || s.includes('Android')) return ''
  if (s.includes('Mac')) return ''
  return ''
}

// ===== 情绪 =====
const getEmotionIcon = (v) => emotions.find(e => e.val === v)?.icon || '/img/其他.png'
const getEmotionText = (v) => ({ 1:'积极', 2:'平和', 3:'低迷', 4:'低落', 5:'易怒', 6:'焦虑', 7:'兴奋', 8:'满足', 9:'其他' }[v] || '未知')

// ===== 列表 =====
const favList = ref([])
const likeList = ref([])
const historyList = ref([])
// 计数用服务端 total（列表接口有 100 条/页的上限，records.length 会封顶变"死数据"）
const favCount = ref(0)
const likeCount = ref(0)
const historyCount = ref(0)

// 搜索关键词
const favKw = ref('')
const likeKw = ref('')
const histKw = ref('')
const diaryKw = ref('')
const testKw = ref('')

// ===== 收藏 时间筛选 =====
const favStart = ref('')
const favEnd = ref('')
const favQuick = ref('all')
const setFavQuick = (days) => {
  favQuick.value = String(days)
  const now = new Date()
  const s = new Date(); s.setDate(now.getDate() - days + 1); s.setHours(0,0,0,0)
  favStart.value = `${s.getFullYear()}-${String(s.getMonth()+1).padStart(2,'0')}-${String(s.getDate()).padStart(2,'0')}`
  favEnd.value = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`
}
const resetFavFilter = () => { favKw.value=''; favStart.value=''; favEnd.value=''; favQuick.value='all' }

// ===== 点赞 时间筛选 =====
const likeStart = ref('')
const likeEnd = ref('')
const likeQuick = ref('all')
const setLikeQuick = (days) => {
  likeQuick.value = String(days)
  const now = new Date()
  const s = new Date(); s.setDate(now.getDate() - days + 1); s.setHours(0,0,0,0)
  likeStart.value = `${s.getFullYear()}-${String(s.getMonth()+1).padStart(2,'0')}-${String(s.getDate()).padStart(2,'0')}`
  likeEnd.value = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`
}
const resetLikeFilter = () => { likeKw.value=''; likeStart.value=''; likeEnd.value=''; likeQuick.value='all' }

// ===== 观看历史 时间筛选 =====
const histStart = ref('')
const histEnd = ref('')
const histQuick = ref('all')
const setHistQuick = (days) => {
  histQuick.value = String(days)
  const now = new Date()
  const s = new Date(); s.setDate(now.getDate() - days + 1); s.setHours(0,0,0,0)
  histStart.value = `${s.getFullYear()}-${String(s.getMonth()+1).padStart(2,'0')}-${String(s.getDate()).padStart(2,'0')}`
  histEnd.value = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`
}
const resetHistFilter = () => { histKw.value=''; histStart.value=''; histEnd.value=''; histQuick.value='all' }

// ===== 日记 时间筛选 =====
const diaryStart = ref('')
const diaryEnd = ref('')
const diaryQuick = ref('all')
const setDiaryQuick = (days) => {
  diaryQuick.value = String(days)
  const now = new Date()
  const s = new Date(); s.setDate(now.getDate() - days + 1); s.setHours(0,0,0,0)
  diaryStart.value = `${s.getFullYear()}-${String(s.getMonth()+1).padStart(2,'0')}-${String(s.getDate()).padStart(2,'0')}`
  diaryEnd.value = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`
}
const resetDiaryFilter = () => { diaryKw.value=''; diaryStart.value=''; diaryEnd.value=''; diaryQuick.value='all' }

// ===== 测评报告 时间筛选 =====
const testStart = ref('')
const testEnd = ref('')
const testQuick = ref('all')
const setTestQuick = (days) => {
  testQuick.value = String(days)
  const now = new Date()
  const s = new Date(); s.setDate(now.getDate() - days + 1); s.setHours(0,0,0,0)
  testStart.value = `${s.getFullYear()}-${String(s.getMonth()+1).padStart(2,'0')}-${String(s.getDate()).padStart(2,'0')}`
  testEnd.value = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`
}
const resetTestFilter = () => { testKw.value=''; testStart.value=''; testEnd.value=''; testQuick.value='all' }

// 过滤后的列表
const filteredFavList = computed(() => {
  let list = [...favList.value]
  if (favKw.value.trim()) {
    const kw = favKw.value.trim().toLowerCase()
    list = list.filter(i => i.name?.toLowerCase().includes(kw) || i.category?.toLowerCase().includes(kw))
  }
  if (favStart.value) {
    const s = new Date(favStart.value + 'T00:00:00').getTime()
    list = list.filter(i => i.createTime && new Date(i.createTime).getTime() >= s)
  }
  if (favEnd.value) {
    const e = new Date(favEnd.value + 'T23:59:59').getTime()
    list = list.filter(i => i.createTime && new Date(i.createTime).getTime() <= e)
  }
  return list
})
const filteredLikeList = computed(() => {
  let list = [...likeList.value]
  if (likeKw.value.trim()) {
    const kw = likeKw.value.trim().toLowerCase()
    list = list.filter(i => i.name?.toLowerCase().includes(kw) || i.category?.toLowerCase().includes(kw))
  }
  // 点赞列表来自后端，带 createTime 时间戳
  if (likeStart.value) {
    const s = new Date(likeStart.value + 'T00:00:00').getTime()
    list = list.filter(i => i.createTime && new Date(i.createTime).getTime() >= s)
  }
  if (likeEnd.value) {
    const e = new Date(likeEnd.value + 'T23:59:59').getTime()
    list = list.filter(i => i.createTime && new Date(i.createTime).getTime() <= e)
  }
  return list
})
const filteredHistList = computed(() => {
  let list = [...historyList.value]
  if (histKw.value.trim()) {
    const kw = histKw.value.trim().toLowerCase()
    list = list.filter(i => i.name?.toLowerCase().includes(kw) || i.category?.toLowerCase().includes(kw))
  }
  if (histStart.value) {
    const s = new Date(histStart.value + 'T00:00:00').getTime()
    list = list.filter(i => i.createTime && new Date(i.createTime).getTime() >= s)
  }
  if (histEnd.value) {
    const e = new Date(histEnd.value + 'T23:59:59').getTime()
    list = list.filter(i => i.createTime && new Date(i.createTime).getTime() <= e)
  }
  return list
})
const filteredTestList = computed(() => {
  let list = [...testList.value]
  if (testKw.value.trim()) {
    const kw = testKw.value.trim().toLowerCase()
    list = list.filter(t => (t.testTitle || '').toLowerCase().includes(kw))
  }
  if (testStart.value) {
    const s = new Date(testStart.value + 'T00:00:00').getTime()
    list = list.filter(t => t.createTime && new Date(t.createTime).getTime() >= s)
  }
  if (testEnd.value) {
    const e = new Date(testEnd.value + 'T23:59:59').getTime()
    list = list.filter(t => t.createTime && new Date(t.createTime).getTime() <= e)
  }
  return list
})
const diaryList = ref([]) // 完整列表（用于统计、周报）
const diaryPage = ref(1)
const diaryPageSize = 5
const diaryServerList = ref([]) // 当前页的日记（服务端分页）
const diaryServerTotal = ref(0)
const diaryLoading = ref(false)
const diaryTotalPages = computed(() => Math.max(1, Math.ceil(diaryServerTotal.value / diaryPageSize)))
const pagedDiaryList = computed(() => diaryServerList.value)

// 加载日记分页（服务端）
const loadDiaryPage = async (p) => {
  if (typeof p === 'number') diaryPage.value = p
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return
  diaryLoading.value = true
  try {
    let url = `/diary/list?userId=${info.userId}&page=${diaryPage.value}&size=${diaryPageSize}`
    if (diaryKw.value.trim()) url += `&keyword=${encodeURIComponent(diaryKw.value.trim())}`
    if (diaryStart.value) url += `&startDate=${diaryStart.value}`
    if (diaryEnd.value) url += `&endDate=${diaryEnd.value}`
    const res = await apiFetch(url)
    if (res.ok) {
      const records = res.data?.records || res.data || []
      diaryServerList.value = records.map(d => ({
        diaryId: d.diaryId ?? d.diary_id,
        userId: d.userId ?? d.user_id,
        diaryContent: d.diaryContent ?? d.diary_content ?? '',
        emotion: d.emotion ?? 9,
        diaryDate: d.diaryDate ?? d.diary_date,
        imageUrl: d.imageUrl ?? d.image_url ?? null,
        createTime: d.createTime ?? d.create_time,
      }))
      diaryServerTotal.value = res.data?.total ?? records.length
    }
  } catch (e) { console.warn('加载日记失败', e) }
  finally { diaryLoading.value = false }
}

// 筛选变化时回到第一页重新加载
watch([diaryKw, diaryStart, diaryEnd], () => {
  diaryPage.value = 1
  if (activeTab.value === 'diary') loadDiaryPage(1)
})

// 切换到日记 tab 时加载
let diaryLastFilterKey = ''
const getDiaryFilterKey = () => `${diaryKw.value}|${diaryStart.value}|${diaryEnd.value}`
watch(activeTab, (t) => {
  if (t === 'diary') {
    const key = getDiaryFilterKey()
    // 首次进入 或 筛选条件有变化时重新加载
    if (diaryServerList.value.length === 0 || key !== diaryLastFilterKey) {
      loadDiaryPage(1)
      diaryLastFilterKey = key
    }
  }
  if (t === 'comments' && !cmtLoaded.value) {
    cmtLoaded.value = true
    loadCmtAll()
  }
  if (t === 'sense') loadSense()
  if (t === 'risk') loadRisk(1)
})
const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return ''
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}
// 情绪色板：emotion 是数字 1-9，必须与 DataBoard.vue / StatisticsController 保持一致
const EMOTION_COLORS = { 1:'#FF6B6B', 2:'#4ECDC4', 3:'#95E1D3', 4:'#74B9FF', 5:'#FDCB6E', 6:'#A29BFE', 7:'#FD79A8', 8:'#FFEAA7', 9:'#B2BEC3' }
const getEmotionColor = (val) => EMOTION_COLORS[Number(val)] || '#B2BEC3'
const aiList = ref([])
const aiCount = ref(0)
const testList = ref([])
const testCount = ref(0)
// 图表实例统一托管（useChart）：生命周期 + resize + dispose 全自动
const testChartRef = ref(null)
const lineChartRef = ref(null)
const pieChartRef = ref(null)
const testChart = useChart(testChartRef, 'profile-test')
const lineChart = useChart(lineChartRef, 'profile-line')
const pieChart = useChart(pieChartRef, 'profile-pie')

// 情绪周报时间范围
const weekRange = ref('week') // week=近7天, lastWeek=上周, month=近30天, all=全部
const weekRangeOptions = [
  { key: 'week', label: '近7天' },
  { key: 'lastWeek', label: '上周' },
  { key: 'month', label: '近30天' },
  { key: 'all', label: '全部' },
]
// 根据选择的范围获取日记
const weekRangeDiaries = computed(() => {
  const now = new Date(); now.setHours(23,59,59,999)
  let start = new Date(now); start.setHours(0,0,0,0)
  if (weekRange.value === 'week') {
    start.setDate(now.getDate() - 6)
  } else if (weekRange.value === 'lastWeek') {
    const day = now.getDay() || 7 // 周日为7
    start.setDate(now.getDate() - day - 6) // 上周一
    now.setDate(now.getDate() - day) // 上周日
    now.setHours(23,59,59,999)
  } else if (weekRange.value === 'month') {
    start.setDate(now.getDate() - 29)
  } else {
    return diaryList.value // 全部
  }
  return diaryList.value.filter(d => {
    const dt = new Date(formatDate(d.diaryDate))
    return dt >= start && dt <= now
  })
})

const weekStat = computed(() => {
  const weekDiaries = weekRangeDiaries.value
  const total = weekDiaries.length; const emoCount = {}
  weekDiaries.forEach(d => { emoCount[d.emotion] = (emoCount[d.emotion] || 0) + 1 })
  let topEmo = 0, topCount = 0
  Object.entries(emoCount).forEach(([k, v]) => { if (v > topCount) { topCount = v; topEmo = Number(k) } })
  const happyDays = weekDiaries.filter(d => d.emotion === 1 || d.emotion === 2).length
  const rangeText = { week: '本周', lastWeek: '上周', month: '本月', all: '累计' }[weekRange.value] || '本周'
  let summary = total === 0 ? `${rangeText}还没有记录哦~ 快写一篇日记吧！` :
    topEmo === 1 ? ` 太棒了！${rangeText}以「开心」为主，继续保持！` :
    topEmo === 2 ? ` ${rangeText}你很平静，情绪稳定，这是很好的状态~` :
    topEmo === 3 ? ` ${rangeText}有些疲惫，记得多休息，照顾好自己。` :
    topEmo === 4 ? ` ${rangeText}有些低落，别忘了难过的日子总会过去的 ` :
    topEmo === 5 ? ` ${rangeText}情绪有些激动，试试深呼吸~` :
    topEmo === 6 ? ` ${rangeText}有些焦虑，试试做几次冥想或呼吸练习。` : ''
  const topEmotionLabel = emotions.find(e => e.val === topEmo)?.label || (total === 0 ? '暂无记录' : '其他')
  return { total, topEmotionIcon: getEmotionIcon(topEmo), topEmotionLabel, happyDays, summary, rangeText }
})

const fbList = ref([])
const fbCount = ref(0)

// ===== 我的评论（个人中心内的「我的评论」标签页）=====
const cmtList = ref([])
const cmtTotal = ref(0)
const cmtPage = ref(1)
const cmtPageSize = 10
const cmtLoading = ref(false)
const cmtLoaded = ref(false)         // 首次进入标签页才拉明细，避免拖慢个人中心首屏
const cmtStatus = ref(null)          // null=全部 0=待审核 1=已通过 -1=未通过
const cmtCounts = reactive({ all: 0, approved: 0, pending: 0, rejected: 0 })
const cmtStatCards = computed(() => [
  { val: null, label: '全部', cls: '', num: cmtCounts.all },
  { val: 1, label: '已通过', cls: 'ok', num: cmtCounts.approved },
  { val: 0, label: '待审核', cls: 'wait', num: cmtCounts.pending },
  { val: -1, label: '未通过', cls: 'no', num: cmtCounts.rejected },
])
const myCommentCount = computed(() => cmtCounts.all)

const cmtEmptyText = computed(() => {
  if (cmtStatus.value === 0) return '没有待审核的评论~'
  if (cmtStatus.value === 1) return '还没有评论通过审核，去 FM 或学堂说两句吧~'
  if (cmtStatus.value === -1) return '没有被拒绝的评论，你很棒 '
  return '还没有发表过评论，去绪见 FM 或绪见学堂逛逛吧~'
})

// 封面：库里存的是纯文件名时补 /cover/ 前缀（与 FM 页保持一致）
const cmtCoverOf = (cover) => {
  if (!cover) return '/img/默认管理员头像.png'
  return (cover.startsWith('http') || cover.startsWith('/')) ? cover : '/cover/' + cover
}
const onCmtCoverErr = (e) => { e.target.src = '/img/默认管理员头像.png' }
const cmtStatusText = (s) => (s === 1 ? '已通过' : s === -1 ? '未通过审核' : '待审核')
const cmtBadgeCls = (s) => (s === 1 ? 'ok' : s === -1 ? 'no' : 'wait')

const loadCmtList = async () => {
  cmtLoading.value = true
  try {
    const params = { page: cmtPage.value, size: cmtPageSize }
    if (cmtStatus.value !== null) params.status = cmtStatus.value
    const res = await getMyComments(params)
    const d = res?.data
    cmtList.value = Array.isArray(d?.records) ? d.records : []
    cmtTotal.value = d?.total || 0
  } catch (e) {
    console.warn('加载我的评论失败', e)
    cmtList.value = []
    cmtTotal.value = 0
  } finally {
    cmtLoading.value = false
  }
}

// 四态条数：各发一个 size=1 的轻量请求取 total（一次并行，不额外拖慢页面）
const loadCmtCounts = async () => {
  const fetchTotal = async (status) => {
    try {
      const p = { page: 1, size: 1 }
      if (status !== null) p.status = status
      const r = await getMyComments(p)
      return r?.data?.total || 0
    } catch { return 0 }
  }
  const [all, approved, pending, rejected] = await Promise.all([
    fetchTotal(null), fetchTotal(1), fetchTotal(0), fetchTotal(-1)
  ])
  cmtCounts.all = all
  cmtCounts.approved = approved
  cmtCounts.pending = pending
  cmtCounts.rejected = rejected
}

const loadCmtAll = async () => {
  await Promise.all([loadCmtList(), loadCmtCounts()])
}

const switchCmtFilter = (v) => {
  cmtStatus.value = v
  cmtPage.value = 1
  loadCmtList()
}
const goCmtPage = (p) => {
  cmtPage.value = p
  loadCmtList()
}
const goCommentResource = (c) => {
  if (!c?.resourceId) return
  // 深链直达：音频进 FM、视频进学堂，并自动播放对应资源
  router.push(c.resourceType === 1 ? `/fm?audioId=${c.resourceId}` : `/classroom?videoId=${c.resourceId}`)
}

// ===== AI 筛选 =====
const aiKw = ref('')
const aiStart = ref('')
const aiEnd = ref('')
const aiQuick = ref('all')
const filteredAiList = computed(() => {
  let list = [...aiList.value]
  if (aiKw.value.trim()) {
    const kw = aiKw.value.trim().toLowerCase()
    list = list.filter(a => ((a.userMsg ?? a.userMessage) || '').toLowerCase().includes(kw) || ((a.aiReply ?? a.aiResponse) || '').toLowerCase().includes(kw))
  }
  if (aiStart.value) {
    const s = new Date(aiStart.value + 'T00:00:00').getTime()
    list = list.filter(a => new Date(a.createTime).getTime() >= s)
  }
  if (aiEnd.value) {
    const e = new Date(aiEnd.value + 'T23:59:59').getTime()
    list = list.filter(a => new Date(a.createTime).getTime() <= e)
  }
  return list
})
const setAiQuick = (days) => {
  aiQuick.value = String(days)
  const now = new Date()
  const s = new Date(); s.setDate(now.getDate() - days + 1); s.setHours(0,0,0,0)
  aiStart.value = `${s.getFullYear()}-${String(s.getMonth()+1).padStart(2,'0')}-${String(s.getDate()).padStart(2,'0')}`
  aiEnd.value = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`
}
const resetAiFilter = () => { aiKw.value=''; aiStart.value=''; aiEnd.value=''; aiQuick.value='all' }

// ===== 反馈 筛选 =====
const fbKw = ref('')
const fbStart = ref('')
const fbEnd = ref('')
const fbQuick = ref('all')
const filteredFbList = computed(() => {
  let list = [...fbList.value]
  if (fbKw.value.trim()) {
    const kw = fbKw.value.trim().toLowerCase()
    list = list.filter(f => {
      const content = (f.content || f.feedbackContent || '').toLowerCase()
      const reply = (f.replyText || '').toLowerCase()
      const supp = (f.userSupplement || '').toLowerCase()
      return content.includes(kw) || reply.includes(kw) || supp.includes(kw)
    })
  }
  if (fbStart.value) {
    const s = new Date(fbStart.value + 'T00:00:00').getTime()
    list = list.filter(f => new Date(f.createTime).getTime() >= s)
  }
  if (fbEnd.value) {
    const e = new Date(fbEnd.value + 'T23:59:59').getTime()
    list = list.filter(f => new Date(f.createTime).getTime() <= e)
  }
  return list
})
const setFbQuick = (days) => {
  fbQuick.value = String(days)
  const now = new Date()
  const s = new Date(); s.setDate(now.getDate() - days + 1); s.setHours(0,0,0,0)
  fbStart.value = `${s.getFullYear()}-${String(s.getMonth()+1).padStart(2,'0')}-${String(s.getDate()).padStart(2,'0')}`
  fbEnd.value = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`
}
const resetFbFilter = () => { fbKw.value=''; fbStart.value=''; fbEnd.value=''; fbQuick.value='all' }

// ===== 详情弹窗 =====
const aiDetail = ref(null)
const openAiDetail = (ai) => { aiDetail.value = ai }
const fbDetail = ref(null)
const fbMessages = ref([])
const fbLoading = ref(false)
const testDetail = ref(null)
const showTestShareCard = ref(false)
const currentShareTest = ref(null)
const shareCardRef = ref(null)
const today = computed(() => new Date().toLocaleDateString('zh-CN'))
const openTestDetail = (t) => { testDetail.value = t }
const showTestShare = (t) => { currentShareTest.value = t; showTestShareCard.value = true }
const testLevelClass = (t) => {
  if (!t) return 'lv-low'
  const score = t.score ?? t.totalScore ?? 0
  const ratio = score / (t.totalScore || 1)
  const lt = (t.level || '').toString()
  if (lt.includes('干预') || lt.includes('高') || ratio >= 0.7) return 'lv-high'
  if (lt.includes('中度') || lt.includes('关注') || ratio >= 0.45) return 'lv-mid'
  return 'lv-low'
}
const saveTestShareCard = () => {
  const t = currentShareTest.value; if (!t) return
  const c = document.createElement('canvas'), x = c.getContext('2d')
  c.width = 600; c.height = 800
  const lv = testLevelClass(t)
  const s = t.score ?? t.totalScore ?? 0, tot = t.totalScore || '--'
  const nm = (t.testTitle || t.paperName || '心理测评').slice(0, 18)
  const lvTxt = t.level || getEmotionText(t.emotionLevel) || '状态未知'
  const tip = t.tip || '继续关注自己的心理状态 '
  const g = x.createLinearGradient(0, 0, 0, 800)
  g.addColorStop(0,'#e8f5e0'); g.addColorStop(0.5,'#f5faf0'); g.addColorStop(1,'#fef9f0')
  x.fillStyle = g; x.fillRect(0, 0, 600, 800)
  x.fillStyle = 'rgba(255,255,255,0.6)'
  for (let i = 0; i < 20; i++) { x.beginPath(); x.arc(Math.random()*600, Math.random()*100, Math.random()*15+5, 0, Math.PI*2); x.fill() }
  x.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--c-hex').trim() || '#7bc47b'; x.fillRect(0, 60, 600, 60)
  x.fillStyle = '#fff'; x.font = 'bold 28px "Microsoft YaHei", sans-serif'
  x.fillText(' 绪见测试', 40, 100)
  x.font = '14px "Microsoft YaHei", sans-serif'; x.fillText(today.value, 480, 100)
  x.fillStyle = '#2d5a2d'; x.font = 'bold 22px "Microsoft YaHei", sans-serif'; x.textAlign = 'center'
  x.fillText(nm, 300, 200)
  x.beginPath(); x.arc(300, 340, 100, 0, Math.PI*2); x.fillStyle = '#e8f0e0'; x.fill()
  x.fillStyle = '#5a9a5a'; x.font = 'bold 64px "Microsoft YaHei", sans-serif'; x.textAlign = 'center'
  x.fillText(s, 300, 355)
  x.fillStyle = '#888'; x.font = '18px "Microsoft YaHei", sans-serif'; x.fillText('/ '+tot, 370, 355)
  x.font = 'bold 22px "Microsoft YaHei", sans-serif'
  x.fillStyle = lv==='lv-high'?'#e06060':lv==='lv-mid'?'#e0a040':'#60b060'
  x.fillText(lvTxt, 300, 430)
  x.fillStyle = '#555'; x.font = '16px "Microsoft YaHei", sans-serif'; x.textAlign = 'center'
  let ln='', y=490
  for (let i = 0; i < tip.length; i++) {
    const tl = ln+tip[i]
    if (x.measureText(tl).width>480 && i>0) { x.fillText(ln, 300, y); ln=tip[i]; y+=28 }
    else ln=tl
  }
  x.fillText(ln, 300, Math.min(y, 640))
  x.fillStyle = '#8ab88a'; x.font = '14px "Microsoft YaHei", sans-serif'
  x.fillText('科学的心理测评，温柔了解自己 ', 300, 700)
  x.fillStyle = '#6b9b6b'; x.font = 'bold 18px "Microsoft YaHei", sans-serif'
  x.fillText('— 绪见 —', 300, 740)
  const a = document.createElement('a')
  a.download = `绪见测评报告_${today.value}.png`; a.href = c.toDataURL('image/png'); a.click()
}

const openFbDetail = async (f) => {
  fbDetail.value = f
  fbMessages.value = []
  fbLoading.value = true
  try {
    const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
    const res = await apiFetch(`/feedback/${f.feedbackId}/messages`)
    if (res.ok && res.data) {
      fbMessages.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
    }
  } catch (e) { console.warn('加载反馈消息失败', e) }
  fbLoading.value = false
}
// 高亮关键词
const highlight = (text, kw) => {
  if (!text) return ''
  if (!kw || !kw.trim()) return text
  const k = kw.trim()
  const safe = (s) => s.replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','\'':'&#39;'}[c]))
  const escaped = k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const re = new RegExp('(' + escaped + ')', 'gi')
  return safe(text).replace(re, '<mark class="hl">$1</mark>')
}
// ===== 保存个性化设置到后端 =====
const saveSettingsToBackend = async (settings) => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info || !info.userId) return
  try {
    await updateUser({ userId: info.userId, ...settings })
    // 同步登录态（store 负责合并 + 持久化）
    useUserStore().patchUserInfo(settings)
    // 同步更新 user 对象
    Object.assign(user.value, settings)
  } catch (e) {
    console.warn('保存设置到后端失败:', e)
  }
}

// ===== 从用户信息应用设置 =====
const applyUserSettings = (u) => {
  // 主题色
  if (u.theme) {
    const found = themeColors.find(c => c.name === u.theme)
    if (found) { themeColor.value = found; localStorage.setItem('profile_theme', JSON.stringify(found)) }
  }
  // 深色模式
  if (u.darkMode !== undefined && u.darkMode !== null) {
    const isDark = u.darkMode === 1 || u.darkMode === '1' || u.darkMode === true
    darkMode.value = isDark
    localStorage.setItem('profile_dark', isDark ? '1' : '0')
  }
  // 头像框
  if (u.avatarFrame) {
    const mapped = frameNameMap[u.avatarFrame] || u.avatarFrame
    userFrame.value = mapped
    localStorage.setItem('profile_frame', mapped)
  }
  // 背景图
  if (u.profileBg !== undefined) {
    heroBg.value = u.profileBg || ''
    if (u.profileBg) localStorage.setItem('profile_bg', u.profileBg)
    else localStorage.removeItem('profile_bg')
  }
}

// ===== 用户加载 =====
const loadUser = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) { router.replace('/login'); return }
  user.value = info
  // 先从 localStorage 应用设置（快速显示）
  applyUserSettings(info)
  try {
    const res = await getUserById(info.userId)
    if (res.code === 200 && res.data) {
      Object.assign(user.value, res.data)
      // 从后端完整数据再应用一次设置（以后端为准）
      applyUserSettings(res.data)
      // 同步登录态（以后端返回的完整数据为准）
      useUserStore().patchUserInfo(res.data)
    }
  } catch {}
  editForm.value = { nickname: user.value.nickname || '', email: user.value.email || '', avatar: user.value.avatar || '' }
}

// 用 axios request 实例（跟 FM.vue 保持一致）
const apiFetch = async (path, options = {}) => {
  try {
    const method = (options.method || 'GET').toLowerCase()
    let resp
    if (method === 'get') {
      resp = await request.get(path, options)
    } else if (method === 'delete') {
      resp = await request.delete(path)
    } else if (method === 'put') {
      resp = await request.put(path, options.body, { headers: { 'Content-Type': 'application/json' } })
    } else {
      resp = await request.post(path, options.body, { headers: { 'Content-Type': 'application/json' } })
    }
    return { ok: true, ...resp }
  } catch (e) {
    return { ok: false, code: e.response?.status, msg: e.message, data: null }
  }
}

const buildCover = (c) => {
  if (!c) return defaultAvatar
  if (c.startsWith('http') || c.startsWith('/')) return c
  return '/cover/' + c
}
// 资源已被删除时的兜底卡片：不再整条丢掉，避免收藏/点赞/历史列表莫名空白
const goneItem = (type, rid, extra = {}) => ({
  id: rid, type, name: '内容已下架', category: '原内容已被移除',
  cover: '/img/其他.png', missing: true, ...extra
})

const loadAll = async () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  if (!info) return
  try {
    // 同时从后端加载：视频、音频、日记、AI对话、测评、反馈、收藏、点赞、历史
    const [vRes, aRes, dRes, aiRes, tRes, fRes, cRes, lRes, hRes] = await Promise.allSettled([
      apiFetch(`/video/list?page=1&size=500`),
      apiFetch(`/audio/list?page=1&size=500`),
      apiFetch(`/diary/list?userId=${info.userId}&size=500`),
      apiFetch(`/aiChat/list?userId=${info.userId}&size=500`),
      apiFetch(`/record/list?userId=${info.userId}&size=500`),
      apiFetch(`/feedback/my?page=1&size=500`),
      apiFetch(`/collect/list?page=1&size=500&userId=${info.userId}`),
      apiFetch(`/like/list?page=1&size=500&userId=${info.userId}`),
      apiFetch(`/history/list?page=1&size=500&userId=${info.userId}`),
    ])
    const videos = vRes.status==='fulfilled' ? (vRes.value?.data?.records || vRes.value?.data || []) : []
    const audios = aRes.status==='fulfilled' ? (aRes.value?.data?.records || aRes.value?.data || []) : []

    // 建立快速查找 Map
    const videoMap = new Map(videos.map(v => [v.videoId, v]))
    const audioMap = new Map(audios.map(a => [a.audioId, a]))

    // 收藏列表：后端 collect 表  resourceType(1=audio, 2=video) + resourceId
    const collects = cRes.status==='fulfilled' ? (cRes.value?.data?.records || cRes.value?.data || []) : []
    favList.value = collects.map(c => {
      const createTime = c.createTime ?? c.create_time
      const rtype = c.resourceType ?? c.resource_type
      const rid = c.resourceId ?? c.resource_id
      if (rtype === 1) {
        const a = audioMap.get(rid)
        return a ? { id: a.audioId, type: 'audio', name: a.audioName, category: a.category, cover: buildCover(a.coverImg), collectId: c.collectId, createTime }
                 : goneItem('audio', rid, { collectId: c.collectId, createTime })
      } else if (rtype === 2) {
        const v = videoMap.get(rid)
        return v ? { id: v.videoId, type: 'video', name: v.videoName, category: v.category, cover: buildCover(v.coverImg), collectId: c.collectId, createTime }
                 : goneItem('video', rid, { collectId: c.collectId, createTime })
      }
      return null
    }).filter(Boolean)

    // 历史列表（兼容 snake_case / camelCase）
    const histories = hRes.status==='fulfilled' ? (hRes.value?.data?.records || hRes.value?.data || []) : []
    historyList.value = histories.map(h => {
      const rid = h.resourceId ?? h.resource_id
      const rtype = h.resourceType ?? h.resource_type
      const currentTime = h.playProgress ?? h.play_progress ?? 0
      const duration = h.duration ?? 0
      const progress = duration > 0 ? Math.min(100, Math.round(currentTime / duration * 100)) : 0
      const finished = (h.isFinished ?? h.is_finished) === 1
      const createTime = h.createTime ?? h.create_time
      const base = { currentTime, duration, progress, finished, createTime, historyId: h.historyId ?? h.history_id, resourceId: rid, resourceType: rtype }
      if (rtype === 1) {
        const a = audioMap.get(rid)
        return a ? { ...base, id: a.audioId, type: 'audio', name: a.audioName, category: a.category, cover: buildCover(a.coverImg) }
                 : { ...base, ...goneItem('audio', rid) }
      } else if (rtype === 2) {
        const v = videoMap.get(rid)
        return v ? { ...base, id: v.videoId, type: 'video', name: v.videoName, category: v.category, cover: buildCover(v.coverImg) }
                 : { ...base, ...goneItem('video', rid) }
      }
      return null
    }).filter(Boolean)

    // 点赞列表：从后端 like 表  resourceType(1=audio, 2=video) + resourceId
    const likes = lRes.status==='fulfilled' ? (lRes.value?.data?.records || lRes.value?.data || []) : []
    likeList.value = likes.map(l => {
      const createTime = l.createTime ?? l.create_time
      const likeId = l.likeId ?? l.id ?? l.like_id
      const rtype = l.resourceType ?? l.resource_type
      const rid = l.resourceId ?? l.resource_id
      if (rtype === 1) {
        const a = audioMap.get(rid)
        return a ? { id: a.audioId, type: 'audio', name: a.audioName, category: a.category, cover: buildCover(a.coverImg), likeId, createTime }
                 : goneItem('audio', rid, { likeId, createTime })
      } else if (rtype === 2) {
        const v = videoMap.get(rid)
        return v ? { id: v.videoId, type: 'video', name: v.videoName, category: v.category, cover: buildCover(v.coverImg), likeId, createTime }
                 : goneItem('video', rid, { likeId, createTime })
      }
      return null
    }).filter(Boolean)

    // 日记列表（兼容 snake_case / camelCase）
    const diariesRaw = dRes.status==='fulfilled' ? (dRes.value?.data?.records || dRes.value?.data || []) : []
    diaryList.value = diariesRaw.map(d => ({
      diaryId: d.diaryId ?? d.diary_id,
      userId: d.userId ?? d.user_id,
      diaryContent: d.diaryContent ?? d.diary_content ?? '',
      emotion: d.emotion ?? 9,
      diaryDate: d.diaryDate ?? d.diary_date ?? d.diary_date,
      imageUrl: d.imageUrl ?? d.image_url ?? null,
      createTime: d.createTime ?? d.create_time,
    }))
    // 计数取分页 total（真实总数），不再用 records.length —— 后端单页上限 100，超出的会被"藏"掉
    const totalOf = (r, list) => {
      const d = r?.status === 'fulfilled' ? r.value?.data : null
      return (d && !Array.isArray(d) && d.total != null) ? Number(d.total) : list.length
    }
    diaryCount.value = totalOf(dRes, diaryList.value)
    // 日记服务端分页总数先用全量数据初始化，切到 diary tab 后会从服务端精确加载
    if (diaryServerTotal.value === 0) diaryServerTotal.value = diaryCount.value
    aiList.value = aiRes.status==='fulfilled' ? (aiRes.value?.data?.records || aiRes.value?.data || []) : []
    aiCount.value = totalOf(aiRes, aiList.value)
    testList.value = tRes.status==='fulfilled' ? (tRes.value?.data?.records || tRes.value?.data || []) : []
    testCount.value = totalOf(tRes, testList.value)
    fbList.value = fRes.status==='fulfilled' ? (fRes.value?.data?.records || fRes.value?.data || []) : []
    fbCount.value = totalOf(fRes, fbList.value)
    favCount.value = totalOf(cRes, favList.value)
    likeCount.value = totalOf(lRes, likeList.value)
    historyCount.value = totalOf(hRes, historyList.value)
  } catch (e) { console.warn('加载失败', e) }
}

const goPlay = (item) => {
  if (item?.missing) { ElMessage.warning('该内容已被移除'); return }
  router.push(item.type === 'video' ? '/classroom' : '/fm')
}

// 果冻风确认弹窗已抽为全站通用：见 @/utils/confirm.js + styles/jelly-confirm.css

// 取消点赞（调后端接口，同时清 localStorage 兜底）
const removeLike = async (item) => {
  if (!(await jellyConfirm(`确定取消点赞「${item.name}」吗？`, { confirmText: '取消点赞' }))) return
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  const rtype = item.type === 'video' ? 2 : 1
  const key = item.type === 'video' ? 'classroom_data' : 'fm_data'
  // 先乐观更新：从列表中移除，让用户立刻看到反馈
  const idx = likeList.value.findIndex(x => x.id === item.id && x.type === item.type)
  if (idx > -1) likeList.value.splice(idx, 1)
  // 清 localStorage 兜底
  const data = loadStorage(key); data.likes = data.likes || {}; delete data.likes[item.id]
  localStorage.setItem(key, JSON.stringify(data))

  if (info) {
    try {
      const res = await cancelLike({ userId: info.userId, resourceType: rtype, resourceId: item.id })
      if (res.code !== 200) {
        // 后端失败，回滚
        if (idx > -1) likeList.value.splice(idx, 0, item)
        data.likes[item.id] = true
        localStorage.setItem(key, JSON.stringify(data))
        ElMessage.error(res.msg || '取消点赞失败，请重试')
        return
      }
    } catch (e) {
      // 网络异常，回滚
      if (idx > -1) likeList.value.splice(idx, 0, item)
      data.likes[item.id] = true
      localStorage.setItem(key, JSON.stringify(data))
      ElMessage.error('网络异常，取消点赞失败')
      return
    }
  }
  loadAll()
}

const removeFav = async (item) => {
  if (!(await jellyConfirm(`确定取消收藏「${item.name}」吗？`, { confirmText: '取消收藏' }))) return
  const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
  const rtype = item.type === 'video' ? 2 : 1
  const key = item.type === 'video' ? 'classroom_data' : 'fm_data'
  // 先乐观更新：从列表中移除，让用户立刻看到反馈
  const idx = favList.value.findIndex(x => x.id === item.id && x.type === item.type)
  if (idx > -1) favList.value.splice(idx, 1)
  // 清 localStorage 兜底
  const data = loadStorage(key); data.favs = data.favs || {}; delete data.favs[item.id]
  localStorage.setItem(key, JSON.stringify(data))

  if (info) {
    try {
      const res = await cancelCollect({ userId: info.userId, resourceType: rtype, resourceId: item.id })
      if (res.code !== 200) {
        // 后端失败，回滚
        if (idx > -1) favList.value.splice(idx, 0, item)
        data.favs[item.id] = true
        localStorage.setItem(key, JSON.stringify(data))
        ElMessage.error(res.msg || '取消收藏失败，请重试')
        return
      }
    } catch (e) {
      // 网络异常，回滚
      if (idx > -1) favList.value.splice(idx, 0, item)
      data.favs[item.id] = true
      localStorage.setItem(key, JSON.stringify(data))
      ElMessage.error('网络异常，取消收藏失败')
      return
    }
  }
  loadAll()
}
const saveProfile = async () => {
  const res = await updateUser({ userId: user.value.userId, nickname: editForm.value.nickname, email: editForm.value.email, avatar: editForm.value.avatar })
  if (res.code === 200) {
    ElMessage.success('保存成功！')
    showUserCenter.value = false
    // 同步登录态里的用户信息
    useUserStore().patchUserInfo({
      nickname: editForm.value.nickname,
      email: editForm.value.email,
      avatar: editForm.value.avatar,
    })
    const info = useUserStore().userInfo || {}
    // 同步更新 user 对象
    Object.assign(user.value, info)
    notifyProfileUpdated()
  } else { ElMessage.error(res.msg || '保存失败') }
}

const onAvatarUpload = async (e) => {
  const file = e.target.files?.[0]; if (!file) return
  const fd = new FormData(); fd.append('file', file); fd.append('dir', 'avatar')
  try {
    const data = await request.post('/upload/file', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (data.code === 200) { editForm.value.avatar = data.data.url; saveProfile() }
    else ElMessage.error(data.msg || '上传失败')
  } catch (err) { ElMessage.error('上传失败：' + err.message) }
  e.target.value = ''
}

const pwdSaving = ref(false)
const savePassword = async () => {
  if (pwdSaving.value) return
  if (!pwdForm.value.oldPassword || !pwdForm.value.newPassword) return ElMessage.warning('请填写完整')
  if (pwdForm.value.newPassword !== pwdForm.value.confirm) return ElMessage.warning('两次新密码不一致')
  if (pwdForm.value.newPassword.length < 6) return ElMessage.warning('新密码至少6位')
  pwdSaving.value = true
  try {
    const res = await changePassword({ userId: user.value.userId, oldPassword: pwdForm.value.oldPassword, newPassword: pwdForm.value.newPassword })
    if (res.code === 200) { ElMessage.success('密码修改成功！请重新登录'); showUserCenter.value = false; logout() }
    else ElMessage.error(res.msg || '修改失败')
  } catch { ElMessage.error('网络异常，请稍后再试') }
  finally { pwdSaving.value = false }
}

// 注销账号：密码二次确认  后端匿名化 + 停用  清本地并回登录页
const deactivateAccountFlow = async () => {
  const { value } = await ElMessageBox.prompt(
    '注销后账号将永久无法登录，昵称与身份信息会被匿名化，且此操作不可恢复。请输入登录密码确认：',
    ' 注销账号',
    { confirmButtonText: '确认注销', cancelButtonText: '再想想', inputType: 'password', inputPlaceholder: '登录密码',
      inputValidator: (v) => (v && v.trim() ? true : '请输入密码') }
  ).catch(() => ({ value: null }))
  if (!value) return
  try {
    const res = await deactivateAccount(value.trim())
    if (res?.code === 200) {
      ElMessage.success('账号已注销，感谢你曾使用绪见 ')
      setTimeout(() => { logout() }, 800)
    } else {
      ElMessage.error(res?.msg || '注销失败')
    }
  } catch (e) { ElMessage.error('注销请求失败，请稍后再试') }
}

const logout = async () => {
  if (!(await jellyConfirm('确定要退出登录吗？', { title: '退出登录', confirmText: '退出', cancelText: '留下' }))) return
  // 断开 WebSocket，避免下一个账号收到上一个账号的消息通知
  try { feedbackWS.disconnect() } catch {}
  // 清掉主题色/头像框/背景/深色模式/签名等个性化设置，防止换号串号
  clearUserPrefs()
  localStorage.removeItem('profile_status')
  useUserStore().clearSession()
  resetAdminCheck()   // 清掉服务端 role 缓存，防止换号串号
  router.replace('/login')
}

const deleteDiary = async (id) => {
  if (!(await jellyConfirm('删除后无法恢复，确定删除这篇日记吗？', { title: '删除日记', confirmText: '删除', danger: true }))) return
  await apiFetch(`/diary/${id}`, { method: 'DELETE' })
  loadAll()
  // 如果当前页只剩一条，删除后跳上一页
  if (diaryServerList.value.length === 1 && diaryPage.value > 1) {
    loadDiaryPage(diaryPage.value - 1)
  } else {
    loadDiaryPage(diaryPage.value)
  }
}

const initTestChart = () => {
  if (!testChartRef.value || testList.value.length === 0) return
  testChart.set({
    title: { text: ' 测评分数变化', left: 'center', textStyle: { fontSize: 14, color: themeColor.value.hex } },
    tooltip: { trigger: 'axis' }, grid: { left: 50, right: 20, top: 50, bottom: 40 },
    xAxis: { type: 'category', data: testList.value.map(t => formatDateTime(t.createTime)), axisLabel: { rotate: 30, fontSize: 11 } },
    yAxis: { type: 'value', name: '分数', min: 0 },
    series: [{ type: 'line', data: testList.value.map(t => t.totalScore), smooth: true, itemStyle: { color: themeColor.value.hex }, areaStyle: { color: themeColor.value.hex + '26' }, symbol: 'circle', symbolSize: 8 }]
  }, { notMerge: true })
}

const initWeekCharts = () => {
  if (lineChartRef.value) {
    const days = [], emotions = []
    const dayCount = weekRange.value === 'week' ? 7 : weekRange.value === 'lastWeek' ? 7 : weekRange.value === 'month' ? 30 : 14
    const now = new Date()
    let startDate = new Date(now)
    if (weekRange.value === 'week') {
      startDate.setDate(now.getDate() - 6)
    } else if (weekRange.value === 'lastWeek') {
      const day = now.getDay() || 7
      startDate.setDate(now.getDate() - day - 6)
    } else if (weekRange.value === 'month') {
      startDate.setDate(now.getDate() - 29)
    } else {
      startDate.setDate(now.getDate() - 13)
    }
    for (let i = 0; i < dayCount; i++) {
      const d = new Date(startDate); d.setDate(d.getDate() + i)
      const ds = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
      days.push(`${d.getMonth()+1}/${d.getDate()}`)
      const f = diaryList.value.find(x => formatDate(x.diaryDate) === ds); emotions.push(f ? f.emotion : null)
    }
    const rangeTitle = { week: '近7天', lastWeek: '上周', month: '近30天', all: '最近14天' }[weekRange.value] || '近7天'
    lineChart.set({
      title: { text: ` ${rangeTitle}情绪走势`, left: 'center', textStyle: { fontSize: 14, color: themeColor.value.hex } },
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 50, bottom: 40 },
      xAxis: { type: 'category', data: days, axisLabel: { fontSize: 10, rotate: dayCount > 15 ? 40 : 0 } },
      yAxis: { type: 'value', min: 0, max: 9 },
      series: [{ type: 'line', data: emotions, smooth: true, itemStyle: { color: themeColor.value.hex }, areaStyle: { color: themeColor.value.hex + '26' }, connectNulls: false, symbol: 'circle', symbolSize: 8 }]
    }, { notMerge: true })
  }
  if (pieChartRef.value) {
    const emoCount = {}; weekRangeDiaries.value.forEach(d => { emoCount[d.emotion] = (emoCount[d.emotion] || 0) + 1 })
    const colorMap = { 1: '#FF6B6B', 2: '#4ECDC4', 3: '#95E1D3', 4: '#74B9FF', 5: '#FDCB6E', 6: '#A29BFE', 7: '#FD79A8', 8: '#FFEAA7', 9: '#B2BEC3' }
    const pieData = Object.entries(emoCount).map(([k, v]) => {
      const emo = emotions.find(e => e.val === Number(k)) || {}
      const iconPath = emo.icon || '/img/其他.png'
      const name = emo.label || getEmotionText(Number(k))
      return {
        name: name,
        value: v,
        itemStyle: { color: colorMap[Number(k)] || '#B2BEC3' },
        label: {
          formatter: `{icon|} {name|${name}}\n{val|${v}天}`,
          rich: {
            icon: { height: 18, width: 18, backgroundColor: { image: iconPath } },
            name: { fontSize: 13, color: '#374151', fontWeight: 700, padding: [0, 0, 0, 6] },
            val: { fontSize: 12, color: '#6b7280', padding: [4, 0, 0, 24] }
          }
        }
      }
    })
    pieChart.set({
      title: [
        { text: '情绪分布', left: 'center', top: 8, textStyle: { fontSize: 15, fontWeight: 700, color: '#374151' } },
        { text: `共${weekRangeDiaries.value.length}天`, left: 'center', top: 30, textStyle: { fontSize: 12, color: '#9ca3af', fontWeight: 400 } }
      ],
      tooltip: { trigger: 'item', formatter: '{b}: {c}天 ({d}%)' },
      legend: { show: false },
      series: [{
        type: 'pie',
        radius: ['40%', '65%'],
        center: ['50%', '52%'],
        data: pieData,
        label: { show: true },
        labelLine: { show: true, length: 15, length2: 20, smooth: true, lineStyle: { color: '#d1d5db' } },
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 3 }
      }]
    }, { notMerge: true })
  }
}

// resize / dispose 已由 useChart 全局托管（单例监听 + 卸载自动释放），此处无需手写

// 主题变化时更新 echarts 颜色
watch(themeColor, () => {
  if (activeTab.value === 'test') initTestChart()
  if (activeTab.value === 'weekReport') initWeekCharts()
})

// 日记数据变化时，如果在周报页面则刷新图表
watch(diaryList, () => {
  if (activeTab.value === 'weekReport') {
    nextTick(() => initWeekCharts())
  }
})

// 时间范围变化时刷新图表
watch(weekRange, () => {
  if (activeTab.value === 'weekReport') {
    nextTick(() => initWeekCharts())
  }
})

onMounted(async () => {
  loadUser()
  loadSessions()
  await loadAll()
  loadCmtCounts()          // 侧边胶囊只要条数，明细等切到标签页再懒加载
  // 直接通过 ?tab=xxx 打开页面时 watch 感知不到初始值，这里补一次对应 tab 的懒加载
  const t = activeTab.value
  if (t === 'diary') loadDiaryPage(1)
  if (t === 'test') nextTick(() => initTestChart())
  if (t === 'weekReport') nextTick(() => initWeekCharts())
  if (t === 'setting') loadSessions()
  if (t === 'comments') { cmtLoaded.value = true; nextTick(() => loadCmtAll()) }
  if (t === 'sense') loadSense()
  if (t === 'risk') loadRisk(1)
  loadManualDiaryCount()   // 经验值口径：只算亲笔日记
})

// ===== Steam 式右栏 / 等级 =====
// 经验值只认「亲笔」日记：source=1。系统代记（source=3）与确认推测（source=2）是保底打卡，
// 不能拿来刷经验 —— 否则用户完全不用写，等级也会一直涨。
const manualDiaryCount = ref(null)   // null = 还没取到，先按全量回退
const expDiaryCount = computed(() => manualDiaryCount.value == null ? diaryCount.value : manualDiaryCount.value)
const loadManualDiaryCount = async () => {
  try {
    const res = await getMyOverview()
    if (res?.code === 200 && res.data) manualDiaryCount.value = Number(res.data.manualDiaries) || 0
  } catch { /* 取不到就退回全量口径，不阻塞页面 */ }
}
const expTotal = computed(() =>
  expDiaryCount.value * 3 + testCount.value * 5 + aiCount.value * 2 + favCount.value + likeCount.value + historyCount.value + fbCount.value
)
const userLevel = computed(() => Math.max(1, Math.floor(expTotal.value / 50) + 1))
const levelProgress = computed(() => Math.round((expTotal.value % 50) / 50 * 100))
// ==================================================================
// 轻量无感记录 + 情绪风险预警
//   这两个 Tab 的共同红线：
//    接口一律不带 userId，身份只认 JWT —— 前端拿不到也传不了别人的数据；
//    开关状态以服务端为准，取不到就按「关闭」渲染（fail-safe）；
//    预警只展示关怀文案与求助渠道，不出现任何结论性疾病判断。
// ==================================================================
import {
  getSenseConfig, updateSenseConfig, clearBehavior, exportBehavior, getPrivacyLogs,
  getPredictionHistory, getSenseAccuracy, generatePrediction,
  getRiskOverview, getRiskList, ackRisk, evaluateRisk, readRisk, misreportRisk,
} from '@/api/sense'

const pctOf = (v) => Math.round(Number(v || 0) * 100)
const fmtSenseTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  return isNaN(d.getTime()) ? String(t) : d.toLocaleString('zh-CN', { hour12: false })
}

// ---------- 无感：开关与概况 ----------
const senseCfg = ref({})
const senseForm = ref({ enabled: false, behavior: false, text: false })
const senseSaving = ref(false)
const senseBusy = ref(false)
const privacyLogs = ref([])
const accuracy = ref({ total: 0, hitRate: 0, adopted: 0, rejected: 0 })
const predHistory = ref([])

async function loadSense() {
  try {
    const res = await getSenseConfig()
    const d = res?.data || {}
    senseCfg.value = d
    senseForm.value = {
      enabled: d.enabled === 1,
      behavior: d.behavior === 1,
      text: d.text === 1,
    }
  } catch { senseCfg.value = {} }

  const [lg, ac, hs] = await Promise.allSettled([
    getPrivacyLogs(), getSenseAccuracy(), getPredictionHistory(1, 20),
  ])
  privacyLogs.value = lg.status === 'fulfilled' ? (lg.value?.data || []) : []
  if (ac.status === 'fulfilled' && ac.value?.data) {
    const a = ac.value.data
    accuracy.value = {
      total: Number(a.total || 0),
      hitRate: pctOf(a.hitRate),
      adoptRate: pctOf(a.adoptRate),
      adopted: Number(a.adopted || 0),
      rejected: Number(a.rejected || 0),
    }
  }
  if (hs.status === 'fulfilled') predHistory.value = hs.value?.data?.records || []
}

async function saveSense() {
  /*
   * 总开关只当「急停」用，不再顺手把子开关清零。
   * 旧逻辑会把 behavior/text 一起置 0 并保存，于是用户「关一次再打开」之后，
   * 两个子开关在服务端仍是 0，界面却显示总开关已开启 —— 表现为「开了没数据」。
   * 现在子开关保留用户的选择，真正的采集条件是 enabled && sub（服务端判断）。
   */
  senseSaving.value = true
  try {
    const res = await updateSenseConfig({
      enabled: senseForm.value.enabled ? 1 : 0,
      behavior: senseForm.value.behavior ? 1 : 0,
      text: senseForm.value.text ? 1 : 0,
    })
    if (res?.code !== 200) {
      ElMessage.warning({ message: res?.msg || '保存失败', grouping: true })
      return
    }
    ElMessage.success('已保存')
    window.dispatchEvent(new CustomEvent('sense-config-changed'))
    await loadSense()
  } catch (e) {
    ElMessage.warning(e?.response?.data?.msg || '保存失败')
  } finally {
    senseSaving.value = false
  }
}

async function genToday() {
  senseBusy.value = true
  try {
    const res = await generatePrediction(null)
    ElMessage[res?.data?.hasSuggestion ? 'success' : 'info'](
      res?.data?.hasSuggestion ? '已生成今日推测' : (res?.data?.reason || '线索还不够')
    )
    await loadSense()
  } catch (e) {
    ElMessage.warning(e?.response?.data?.msg || '生成失败')
  } finally {
    senseBusy.value = false
  }
}

async function exportSense() {
  try {
    const res = await exportBehavior()
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `我的无感行为数据-${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('已导出')
  } catch {
    ElMessage.warning('导出失败')
  }
}

async function clearSense() {
  const ok = await jellyConfirm(
    '将永久删除你的全部行为特征数据（推测记录与日记不受影响）。此操作不可恢复，确定继续吗？',
    { title: '清空行为数据', danger: true }
  )
  if (!ok) return
  try {
    await clearBehavior()
    ElMessage.success('已清空')
    await loadSense()
  } catch (e) { ElMessage.error(e?.message || '清空失败，请稍后重试') }
}

const emotionBg = (v) => emotions.find(e => e.val === v)?.bg || '#e0e0e0'
const statusText = (s) => ({ 0: '待确认', 1: '已采纳', 2: '已忽略', 3: '已过期' })[s] || '未知'

/** 还在等用户表态的推测条数（用于提示文案） */
const pendingCount = computed(() => predHistory.value.filter((p) => p.status === 0).length)

/**
 * 「去处理」：把这条待确认推测带到写日记页。
 *
 * 为什么跳转而不是就地弹窗：采用/忽略必须落在写日记的上下文里 ——
 * 采用会直接生成一篇日记（source=2），需要用户先填内容再保存。
 * 参数用 pred* 前缀，避开写日记页既有的 ?emotion= 预选逻辑
 * （否则情绪会被预选、保存时走普通保存而非 confirm 接口，推测状态就永远变不了）。
 */
function handlePrediction(p) {
  router.push({
    path: '/diary/write',
    query: {
      predId: p.predictionId,
      predEmotion: p.emotion,
      predName: p.emotionName,
      predConf: p.confidence,
      predDate: p.targetDate || '',
    },
  })
}

// ---------- 情绪风险预警 ----------
const riskOv = ref({})
const riskList = ref([])
const riskTotal = ref(0)
const riskPage = ref(1)
const riskBusy = ref(false)
const openRiskId = ref(null)

const thresholdRows = computed(() => {
  const c = riskOv.value?.config || {}
  return [
    { label: '观察窗口', v: `${c.windowDays ?? '-'} 天` },
    { label: '最少有效记录', v: `${c.minRecordDays ?? '-'} 天` },
    { label: '二级·低落天数', v: `${c.level2DowncastDays ?? '-'} 天` },
    { label: '三级·低落天数', v: `${c.level3DowncastDays ?? '-'} 天` },
    { label: '三级·负面占比', v: c.level3Ratio != null ? pctOf(c.level3Ratio) + '%' : '-' },
    { label: '三级·平均指数≤', v: c.level3AvgIndex ?? '-' },
    { label: '一级·负面占比', v: c.level1Ratio != null ? pctOf(c.level1Ratio) + '%' : '-' },
    { label: '一级·趋势斜率≤', v: c.level1Slope ?? '-' },
    { label: '冷静期', v: `${c.cooldownDays ?? '-'} 天` },
    // 危机通道独立于窗口统计：命中即提醒，所以只展示开关、等级与词库规模
    { label: '危机词通道', v: c.crisisEnabled === false ? '已关闭' : `开启（${c.crisisLevel ?? 3} 级）` },
    { label: '危机词库', v: `${c.crisisWordCount ?? '-'} 条` },
  ]
})

async function loadRisk(page = 1) {
  try {
    const [ov, ls] = await Promise.allSettled([getRiskOverview(), getRiskList(page, 10)])
    if (ov.status === 'fulfilled') riskOv.value = ov.value?.data || {}
    if (ls.status === 'fulfilled') {
      const d = ls.value?.data || {}
      const rows = d.records || []
      riskList.value = page === 1 ? rows : riskList.value.concat(rows)
      riskTotal.value = Number(d.total || 0)
      riskPage.value = page
    }
  } catch { /* 静默 */ }
}

async function ackOne(a) {
  try {
    await ackRisk(a.alertId)
    a.ackStatus = 1
    a.isRead = 1
    ElMessage.success('已记录')
    window.dispatchEvent(new CustomEvent('risk-alert-read'))
    await loadRisk(1)
  } catch {
    ElMessage.warning('操作失败')
  }
}

/**
 * 补标误报（「这不是我」）：预警记录页的入口，弹窗里没来得及标的可以在这里补。
 * 后端规则与弹窗一致：危机词通道拒绝、幂等、标记后同时置为已确认（不再弹窗）。
 */
async function misreportOne(a) {
  try {
    await misreportRisk(a.alertId)
    a.misreport = true
    a.ackStatus = 1
    a.isRead = 1
    ElMessage.success('已收到反馈，这条提示不会再打扰你')
    window.dispatchEvent(new CustomEvent('risk-alert-read'))
    await loadRisk(1)
  } catch (e) {
    ElMessage.warning(e?.response?.data?.msg || '标记失败')
  }
}

/**
 * 展开「查看建议」= 用户真正看过这条预警  顺手标记已读。
 * 之前已读只在 ack（我知道了）时触发，用户看了但没点确认就永远是未读，
 * 也就是「已读未触发」的问题根源；这里补上查看即读的语义。
 */
function toggleAdvice(a) {
  const opening = openRiskId.value !== a.alertId
  openRiskId.value = opening ? a.alertId : null
  if (opening && !a.isRead) {
    a.isRead = 1   // 乐观更新：不打扰用户，失败也只影响红点不影响功能
    readRisk(a.alertId).catch(() => {})
    window.dispatchEvent(new CustomEvent('risk-alert-read'))
  }
}

async function runEvaluate() {
  riskBusy.value = true
  try {
    const res = await evaluateRisk()
    const hit = res?.data?.hit
    if (hit) ElMessage.warning('检测到需要关注的状态，已生成预警')
    else ElMessage.success('当前未达到预警阈值，继续保持记录')
    await loadRisk(1)
  } catch (e) {
    ElMessage.warning(e?.response?.data?.msg || '判定失败')
  } finally {
    riskBusy.value = false
  }
}

const sideStats = computed(() => [
  { key: 'diary', label: '绪见日记', icon: '/img/我的日记本.png', num: diaryCount.value },
  { key: 'weekReport', label: '情绪周报', icon: '/img/情绪周报.png', num: weekStat.value.total },
  { key: 'test', label: '测评报告', icon: '/img/测评报告.png', num: testCount.value },
  { key: 'ai', label: 'AI 对话', icon: '/img/AI对话历史.png', num: aiCount.value },
  { key: 'fav', label: '我的收藏', icon: '/img/我的收藏.png', num: favCount.value },
  { key: 'like', label: '我的点赞', icon: '/img/我的点赞.png', num: likeCount.value },
  { key: 'history', label: '观看历史', icon: '/img/观看历史.png', num: historyCount.value },
  { key: 'feedback', label: '我的反馈', icon: '/img/我的反馈.png', num: fbCount.value },
  { key: 'comments', label: '我的评论', icon: '/img/我的评论.png', num: myCommentCount.value },
  { key: 'setting', label: '账号设置', icon: '/img/账号设置.png', num: '', glyph: 'sliders' },
  { key: 'board', label: '数据看板', icon: '/img/情绪周报.png', num: '', glyph: 'chart-bar' },
  { key: 'sense', label: '无感记录', icon: '/img/无感记录.png', num: '', glyph: 'cloud' },
])

// 参考图式分组：记录 / 我的 / 互动 / 设置；情绪预警独立成底部警示条
const sideGroups = computed(() => {
  const byKey = Object.fromEntries(sideStats.value.map(s => [s.key, s]))
  const pick = (...keys) => keys.map(k => byKey[k]).filter(Boolean)
  return [
    { title: '记录', items: pick('diary', 'weekReport', 'test', 'ai') },
    { title: '我的', items: pick('fav', 'like', 'history') },
    { title: '互动', items: pick('feedback', 'comments') },
    { title: '设置', items: pick('setting', 'board', 'sense') },
  ]
})

// 底部预警警示条的状态文字：有未读显示条数，否则显示「今日平稳」
const riskCtaText = computed(() => {
  const unread = Number(riskOv.value?.unread || 0)
  return unread > 0 ? `${unread} 条未读` : '今日平稳'
})
// 最新动态（Steam 式动态流）
const recentHistory = computed(() => historyList.value.slice(0, 4))
const recentDiaries = computed(() => diaryList.value.slice(0, 2))
const recentTests = computed(() => testList.value.slice(0, 2))
const shortDate = (t) => {
  const d = new Date(t)
  return isNaN(d.getTime()) ? '' : `${d.getMonth() + 1}月${d.getDate()}日`
}
const lastActiveText = computed(() => {
  const cur = sessions.value.find(s => s.current) || sessions.value[0]
  return cur ? relativeTime(cur.lastActiveTime || cur.loginTime) : '刚刚'
})
// 真正的「在线」判定：会话有效 且 最近 30 分钟内有活动（否则只是没清理的历史会话）
const ONLINE_WINDOW = 30 * 60 * 1000
const isOnline = (s) => {
  if (s.status !== 1) return false
  const t = new Date(s.lastActiveTime || s.loginTime).getTime()
  return !isNaN(t) && Date.now() - t < ONLINE_WINDOW
}
const onlineDeviceCount = computed(() => sessions.value.filter(isOnline).length)
const totalDeviceCount = computed(() => sessions.value.filter(s => s.status === 1).length)
const hasSessionData = computed(() => sessions.value.length > 0)
// 设备列表默认只显示有效会话，已移除的折叠起来（否则历史会话会把列表撑爆）
const showKicked = ref(false)
const visibleSessions = computed(() => showKicked.value ? sessions.value : sessions.value.filter(s => s.status === 1))
const kickedCount = computed(() => sessions.value.filter(s => s.status === 0).length)

// ===== 经验值明细（让等级规则在页面上可见） =====
const showExpDetail = ref(false)
const expRules = computed(() => [
  { label: '写日记（亲笔）', icon: '/img/写日记.png', count: expDiaryCount.value, unit: 3 },
  { label: '完成测评', icon: '/img/测评报告.png', count: testCount.value, unit: 5 },
  { label: 'AI 对话', icon: '/img/AI对话奶牛.png', count: aiCount.value, unit: 2 },
  { label: '收藏内容', icon: '/img/我的收藏.png', count: favCount.value, unit: 1 },
  { label: '点赞内容', icon: '/img/我的点赞.png', count: likeCount.value, unit: 1 },
  { label: '观看记录', icon: '/img/观看历史.png', count: historyCount.value, unit: 1 },
  { label: '提交反馈', icon: '/img/我的反馈.png', count: fbCount.value, unit: 1 },
])
const expBreakdown = computed(() => expRules.value.filter(r => r.count > 0))
const nextLevelNeed = computed(() => 50 - (expTotal.value % 50))

// 个性签名（Steam 状态位）：存数据库 sys_user.bio，跟账号走（不存 localStorage）
const userStatus = computed(() => user.value?.bio || '')
const editingStatus = ref(false)
const statusDraft = ref('')
const startEditStatus = () => { statusDraft.value = user.value?.bio || ''; editingStatus.value = true }
const saveStatus = async () => {
  const val = statusDraft.value.trim()
  if (val === (user.value?.bio || '')) { editingStatus.value = false; return }
  const res = await updateUser({ userId: user.value.userId, bio: val })
  if (res?.code !== 200) { ElMessage.error(res?.msg || '签名保存失败', { grouping: true }); return }
  user.value.bio = val
  useUserStore().patchUserInfo({ bio: val })
  editingStatus.value = false
  if (val) ElMessage.success('签名已保存')
}
</script>

<style scoped>
.profile-page {
  --c-hex: v-bind(themeColor.hex);
  --c-main: v-bind(themeColor.main);
  --c-bg1: v-bind(themeColor.bg1);
  --c-bg2: v-bind(themeColor.bg2);
  min-height: calc(100vh - 56px);
  background: linear-gradient(135deg, var(--c-bg1) 0%, var(--c-bg2) 50%, var(--c-bg1) 100%);
  padding: 30px 48px 60px;
  max-width: 100%;
  transition: background 0.3s;
}
.profile-page.dark-mode { background: linear-gradient(135deg, #1f2937, #111827, #1f2937); color: #e5e7eb; }
.profile-page.dark-mode .hero-content,
.profile-page.dark-mode .sec-card,
.profile-page.dark-mode .diary-input-card,
.profile-page.dark-mode .fb-input-card,
.profile-page.dark-mode .item-card,
.profile-page.dark-mode .diary-card,
.profile-page.dark-mode .ai-card,
.profile-page.dark-mode .test-card,
.profile-page.dark-mode .week-summary .summary-item,
.profile-page.dark-mode .report-text,
.profile-page.dark-mode .fb-card,
.profile-page.dark-mode .chart-box,
.profile-page.dark-mode .chart-box-sm,
.profile-page.dark-mode .history-filter,
.profile-page.dark-mode .avatar-picker,
.profile-page.dark-mode .modal-card { background: #1f2937 !important; }

/* Hero */
.profile-hero { width: 100%; margin: 0 0 16px; border-radius: 24px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.12); background: #fff; position: relative; }
.hero-bg { height: 230px; transition: background 0.3s; position: relative; }
.hero-bg::after { content:''; position:absolute; inset:0; background:
    linear-gradient(to bottom, rgba(255,255,255,0) 58%, rgba(255,255,255,0.5) 84%, #fff 100%),
    radial-gradient(circle at 15% 25%, rgba(255,255,255,0.35) 0%, transparent 45%),
    radial-gradient(circle at 85% 65%, rgba(255,255,255,0.25) 0%, transparent 45%); }

/* ===== 动漫风装饰层（照片上的淡淡装饰，与全站 AnimeHero 同款元素但更收敛）===== */
.hero-anime-deco { position: absolute; inset: 0; z-index: 1; pointer-events: none; overflow: hidden; }
/* 一闪一闪的小星星（不透明度低于横幅版，只在照片上轻轻点缀） */
.had-star {
  position: absolute; color: rgba(255,255,255,0.88);
  text-shadow: 0 0 8px rgba(255,255,255,0.8);
  animation: hadTwinkle 2.8s ease-in-out infinite;
}
.had-star.s1 { font-size: 15px; top: 18%; left: 11%; }
.had-star.s2 { font-size: 11px; top: 52%; left: 30%; animation-delay: 0.9s; }
.had-star.s3 { font-size: 13px; top: 14%; right: 24%; animation-delay: 1.6s; }
.had-star.s4 { font-size: 10px; top: 60%; right: 9%; animation-delay: 0.5s; }
@keyframes hadTwinkle {
  0%, 100% { opacity: 0.22; transform: scale(0.85) rotate(0deg); }
  50% { opacity: 0.92; transform: scale(1.18) rotate(18deg); }
}
/* 慢慢飘过的云（半透明白，速度放缓，更"淡"） */
.had-cloud { position: absolute; color: rgba(255,255,255,0.5); animation: hadDrift 34s linear infinite; }
.had-cloud.c1 { font-size: 30px; top: 12%; left: -50px; }
.had-cloud.c2 { font-size: 22px; top: 56%; left: -90px; animation-delay: -17s; animation-duration: 42s; }
@keyframes hadDrift {
  from { transform: translateX(0); }
  to { transform: translateX(calc(100vw + 160px)); }
}
.hero-bg-set { position: absolute; top: 12px; right: 16px; z-index: 2; padding: 5px 12px; border-radius: 999px; border: 1px solid rgba(255,255,255,0.9); background: rgba(255,255,255,0.75); backdrop-filter: blur(8px); font-size: 12px; cursor: pointer; color: #555; transition: all 0.25s; }
.hero-bg-set:hover { background: #fff; transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0,0,0,0.15); color: var(--c-hex); }
.bg-preview { position: relative; width: 100%; height: 90px; border-radius: 12px; margin: 8px 0 12px; border: 2px dashed var(--c-border); transition: all 0.3s; }
.bg-preview.clickable { cursor: pointer; }
.bg-preview.clickable:hover { border-color: var(--c-hex); transform: translateY(-1px); box-shadow: 0 6px 16px rgba(0,0,0,0.10); }
.bg-preview-tip { position: absolute; right: 8px; bottom: 8px; padding: 3px 10px; border-radius: 999px; background: rgba(0,0,0,0.45); color: #fff; font-size: 11px; backdrop-filter: blur(4px); opacity: 0; transition: opacity 0.25s; pointer-events: none; }
.bg-preview.clickable:hover .bg-preview-tip { opacity: 1; }
/* 背景展示焦点调整模式：微信式按住拖动（所见即所得） */
.hero-bg.adjusting { cursor: grab; }
.hero-bg.adjusting.dragging { cursor: grabbing; }
.hero-bg.adjusting::before { content: ''; position: absolute; inset: 0; z-index: 2; background: rgba(255,255,255,0.12); box-shadow: inset 0 0 0 3px var(--c-hex); animation: adjustPulse 1.6s ease-in-out infinite; }
@keyframes adjustPulse { 0%, 100% { opacity: 0.35; } 50% { opacity: 1; } }
.bg-ripple { position: absolute; width: 14px; height: 14px; border-radius: 50%; background: rgba(255,255,255,0.95); border: 2px solid var(--c-hex); transform: translate(-50%, -50%); pointer-events: none; z-index: 3; animation: bgRipple 0.7s ease-out forwards; }
@keyframes bgRipple {
  0% { box-shadow: 0 0 0 0 rgba(123, 196, 123, 0.55); opacity: 1; }
  100% { box-shadow: 0 0 0 20px rgba(123, 196, 123, 0); opacity: 0; }
}
.bg-adjust-tip { position: absolute; left: 50%; top: 14px; transform: translateX(-50%); z-index: 3; display: flex; align-items: center; gap: 12px; padding: 8px 10px 8px 16px; border-radius: 999px; background: rgba(255,255,255,0.94); backdrop-filter: blur(8px); box-shadow: 0 4px 16px rgba(0,0,0,0.18); font-size: 13px; color: #555; white-space: nowrap; }
.bg-adjust-done { padding: 4px 14px; border-radius: 999px; border: none; background: linear-gradient(135deg, var(--c-main), var(--c-hex)); color: #fff; font-size: 12px; cursor: pointer; }
.bg-adjust-done:hover { filter: brightness(1.06); }
.bg-pos-hint { margin: -4px 0 12px; font-size: 12px; color: var(--c-text-weak); }
.bg-pos-hint b { color: var(--c-hex); }
.bg-default-row { display: flex; justify-content: flex-start; margin-bottom: 12px; }
.bg-default-row .link-btn { font-size: 13px; }
.hero-content { position: relative; padding: 0 32px 14px; display: flex; align-items: flex-end; gap: 22px; margin-top: -54px; flex-wrap: wrap; }
.avatar-wrap { width: 96px; height: 96px; border-radius: 50%; overflow: visible; border: 4px solid #fff; position: relative; cursor: pointer; flex-shrink: 0; background: transparent; }
.avatar-wrap .avatar-img-inner { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; display: block; background: var(--c-bg2); position: relative; z-index: 1; }
.avatar-wrap .frame-ring-img { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); width: 165%; height: 165%; pointer-events: none; z-index: 3; object-fit: contain; }
.avatar-edit { position: absolute; bottom: 2px; right: 2px; background: var(--c-hex); color: #fff; width: 26px; height: 26px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 13px; border: 2px solid #fff; }
.avatar-upload-btn { position: absolute; bottom: 2px; left: 2px; background: #fff; color: var(--c-hex); width: 26px; height: 26px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 13px; border: 2px solid #fff; cursor: pointer; box-shadow: 0 2px 6px rgba(0,0,0,0.15); }
/* ===== 头像装饰框：用户要求全部去掉彩色光圈，只保留常规投影（框的大小由 frame-ring-img 贴图负责） ===== */
.avatar-wrap.frame-none,
.avatar-wrap.frame-flower,
.avatar-wrap.frame-leaf,
.avatar-wrap.frame-crown,
.avatar-wrap.frame-star,
.avatar-wrap.frame-heart,
.avatar-wrap.frame-halo,
.avatar-wrap.frame-cloud { box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18); }
.hero-info { flex: 1; padding-bottom: 6px; min-width: 200px; }
.hero-greeting { display: inline-block; padding: 6px 18px; border-radius: 14px; background: linear-gradient(135deg, var(--c-main), var(--c-hex)); color: #fff; font-size: 15px; font-weight: 600; margin-bottom: 10px; box-shadow: 0 3px 12px rgba(0,0,0,0.12); }
.hero-info h1 { margin: 0 0 8px; font-size: 22px; font-weight: 700; color: #374151; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.hero-meta { margin: 0 0 10px; font-size: 13px; color: #9ca3af; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.role-tag { padding: 2px 10px; border-radius: 999px; background: var(--c-bg2); color: var(--c-hex); font-size: 12px; font-weight: 500; }
/* 个性化工具条已移入「个人中心」弹窗，横幅不再放主题色圆点 */
.color-dot { width: 22px; height: 22px; border-radius: 50%; border: 2px solid #fff; cursor: pointer; padding: 0; box-shadow: 0 1px 4px rgba(0,0,0,0.15); transition: all 0.2s; }
.color-dot:hover { transform: scale(1.2); }
.color-dot.on { border-color: #374151; transform: scale(1.25); box-shadow: 0 0 0 2px #fff, 0 0 0 4px #374151; }
.theme-toggle { width: 30px; height: 30px; border-radius: 50%; border: 1px solid var(--c-border); background: #fff; font-size: 14px; cursor: pointer; transition: all 0.2s; display: flex; align-items: center; justify-content: center; }
.theme-toggle:hover { border-color: var(--c-hex); background: var(--c-bg2); }
/* ===== Steam 式两栏布局 ===== */
.steam-body { display: flex; gap: 20px; align-items: flex-start; }
.steam-main { flex: 1; min-width: 0; }
.steam-side { width: 280px; flex-shrink: 0; display: flex; flex-direction: column; gap: 16px; position: sticky; top: 16px; }
.side-card { background: #fff; border-radius: 16px; padding: 18px; box-shadow: 0 2px 12px rgba(0,0,0,0.05); }
.side-title { margin: 0 0 12px; font-size: 13px; font-weight: 700; color: #6b7280; letter-spacing: 1px; }
/* 在线状态灯：这里是「绿=在线」的语义色，不能用单色线性图标，单独画一个小圆点 */
.side-title .online-light {
  display: inline-block; width: 7px; height: 7px; margin-right: 6px;
  border-radius: 50%; background: #4ade80; vertical-align: 1px;
  box-shadow: 0 0 0 3px rgba(74, 222, 128, 0.18);
}

/* 在线状态标签（横幅右下） */
.online-tag { display: inline-flex; align-items: center; gap: 5px; color: #4ade80; font-size: 12px; font-weight: 600; }
.online-dot { width: 8px; height: 8px; border-radius: 50%; background: #4ade80; box-shadow: 0 0 0 3px rgba(74,222,128,0.25); animation: onlinePulse 2s ease-in-out infinite; }
@keyframes onlinePulse { 0%, 100% { box-shadow: 0 0 0 3px rgba(74,222,128,0.25); } 50% { box-shadow: 0 0 0 5px rgba(74,222,128,0.1); } }

/* 等级徽章已移至右栏；顶部新增个性签名（Steam 状态位） */
.hero-info h1 .role-tag { margin-left: 10px; vertical-align: 3px; font-size: 11px; }
.hero-sign { display: inline-flex; align-items: center; gap: 8px; margin: 0 0 10px; padding: 5px 14px; border-radius: 999px; background: rgba(255,255,255,0.7); border: 1px dashed var(--c-hex); cursor: pointer; max-width: 100%; transition: all 0.2s; }
.hero-sign:hover { background: #fff; box-shadow: 0 3px 10px rgba(0,0,0,0.1); }
.hero-sign .sign-text { font-size: 13px; color: #6b7280; font-style: italic; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.hero-sign .sign-edit { font-size: 11px; opacity: 0.45; transition: opacity 0.2s; }
.hero-sign:hover .sign-edit { opacity: 1; }
.hero-sign-edit { display: flex; align-items: center; gap: 8px; margin: 0 0 10px; }
.hero-sign-edit input { flex: 1; min-width: 220px; max-width: 420px; padding: 7px 12px; border-radius: 8px; border: 1.5px solid var(--c-hex); font-size: 13px; outline: none; background: #fff; color: #374151; }
.sign-save { padding: 7px 14px; border: none; border-radius: 8px; background: linear-gradient(135deg, var(--c-main), var(--c-hex)); color: #fff; font-size: 12.5px; cursor: pointer; }
.sign-cancel { padding: 7px 12px; border: 1px solid var(--c-border); border-radius: 8px; background: #fff; color: #9ca3af; font-size: 12.5px; cursor: pointer; }
.sign-cancel:hover { color: #6b7280; border-color: var(--c-border-strong); }

/* 等级卡片（右栏顶部：渐变色带头 + 「N 级」框 + 进度条） */
.level-card { padding: 0; overflow: hidden; }
.lc-head { display: flex; align-items: center; justify-content: space-between; padding: 10px 18px; background: linear-gradient(135deg, var(--c-main), var(--c-hex)); color: #fff; }
.lc-head-t { font-size: 13px; font-weight: 700; letter-spacing: 1px; }
.lc-head-exp { font-size: 11.5px; opacity: 0.92; font-variant-numeric: tabular-nums; }
.lc-main { display: flex; align-items: center; gap: 10px; padding: 16px 18px 0; }
.lc-badge { width: 52px; height: 52px; border-radius: 12px; background: linear-gradient(160deg, var(--c-main), var(--c-hex)); border: 2px solid #fff; box-shadow: 0 0 0 2px var(--c-bg2), 0 4px 12px rgba(0,0,0,0.15); display: flex; align-items: center; justify-content: center; color: #fff; font-size: 23px; font-weight: 800; flex-shrink: 0; }
.lc-ji { font-size: 18px; font-weight: 800; color: var(--c-hex); margin-right: 2px; }
.lc-exp-wrap { flex: 1; min-width: 0; }
.lc-name { font-size: 12px; color: #6b7280; margin-bottom: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.lc-bar-row { display: flex; align-items: center; gap: 8px; }
.lc-bar { flex: 1; height: 7px; border-radius: 7px; background: var(--c-bg2); overflow: hidden; }
.lc-fill { height: 100%; border-radius: 7px; background: linear-gradient(90deg, var(--c-main), var(--c-hex)); transition: width 0.5s; }
.lc-progress-text { font-size: 11px; color: #9ca3af; white-space: nowrap; }
.level-card .lc-desc { margin: 12px 18px 14px; font-size: 12px; color: #9ca3af; line-height: 1.6; }
.level-card .side-btn { margin: 0 18px 18px; width: calc(100% - 36px); }
/* 在线卡片 */
.online-card { padding: 16px 18px; }
.online-card .side-online { margin-bottom: 8px; }

/* 右栏数据统计（Steam 式行导航：左标签右数字，带分隔线） */
.stat-card { padding: 6px 10px; }
.side-stat { display: flex; align-items: center; justify-content: space-between; padding: 9px 12px; border-radius: 10px; cursor: pointer; transition: background 0.18s, color 0.18s; color: #6b7280; font-size: 13.5px; }
/* 分组标题（记录 / 我的 / 互动 / 设置） */
.sg-title { margin: 12px 12px 4px; font-size: 11px; font-weight: 600; letter-spacing: 3px; color: #b3bac3; }
.sg-title:first-child { margin-top: 2px; }

/* 底部情绪预警警示条 */
.risk-cta {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  width: 100%; margin-top: 12px; padding: 12px 14px;
  border: none; border-radius: 14px; cursor: pointer; font-family: inherit;
  background: linear-gradient(135deg, #e87a52, #d95b3c);
  color: #fff; box-shadow: 0 4px 12px rgba(217, 91, 60, 0.32);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.risk-cta:hover { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(217, 91, 60, 0.42); }
.risk-cta:active { transform: none; }
.risk-cta .rc-left { display: flex; align-items: center; gap: 8px; font-size: 13.5px; font-weight: 600; letter-spacing: 1px; }
.risk-cta .rc-warn { font-size: 14px; line-height: 1; }
.risk-cta .rc-icon { width: 22px; height: 22px; object-fit: contain; }
.risk-cta .rc-dot { width: 7px; height: 7px; border-radius: 50%; background: #ffd9c9; animation: rcBlink 1.6s ease-in-out infinite; }
@keyframes rcBlink { 0%, 100% { opacity: 1; } 50% { opacity: 0.35; } }
.risk-cta .rc-right { font-size: 11.5px; opacity: 0.94; white-space: nowrap; }

.profile-page.dark-mode .sg-title { color: #5f6b7a; }
.profile-page.dark-mode .risk-cta { background: linear-gradient(135deg, #b85332, #a03d24); }
/* 分组布局下不再需要行分隔线（参考图无框线） */
.side-stat:hover { background: var(--c-bg2); color: var(--c-hex); }
.side-stat.active { background: linear-gradient(135deg, var(--c-main), var(--c-hex)); color: #fff; font-weight: 600; box-shadow: 0 3px 10px rgba(0,0,0,0.12); }
.side-stat.active + .side-stat { border-top-color: transparent; }
.ss-label { display: inline-flex; align-items: center; gap: 8px; }
.ss-icon { width: 16px; height: 16px; object-fit: contain; }
.ss-num { font-size: 13px; font-weight: 700; }
.profile-page.dark-mode .hero-sign { background: rgba(255,255,255,0.06); }
.profile-page.dark-mode .hero-sign:hover { background: rgba(255,255,255,0.12); }
.profile-page.dark-mode .hero-sign .sign-text { color: #d1d5db; }
.profile-page.dark-mode .hero-sign-edit input { background: rgba(255,255,255,0.08); color: #e5e7eb; }
.profile-page.dark-mode .sign-cancel { background: rgba(255,255,255,0.08); border-color: rgba(255,255,255,0.15); }

.profile-page.dark-mode .side-stat.active + .side-stat { border-top-color: transparent; }

/* 右栏在线 / 情绪卡 */
.side-online { display: flex; align-items: center; gap: 8px; margin: 0 0 10px; font-size: 13px; color: #4b5563; }
.side-online .dot { width: 9px; height: 9px; border-radius: 50%; background: #4ade80; box-shadow: 0 0 0 3px rgba(74,222,128,0.22); flex-shrink: 0; }
.side-devices { font-size: 12px; color: #9ca3af; }
.side-devices .sd-num { font-size: 16px; font-weight: 700; color: var(--c-hex); }
.side-devices .link-btn { margin-top: 0; }
.side-devices .sd-sub { font-size: 11.5px; color: #b0b8b0; }
.se-state { padding: 1px 8px; font-size: 11px; font-weight: 500; border-radius: 999px; margin-left: 4px; }
.se-state.on { background: var(--c-bg2); color: var(--c-hex); }
.se-state.idle { background: #f1f3f1; color: #9ca3af; }
/* 经验明细（等级卡展开） */
.lc-detail-toggle { width: 100%; margin: 2px 0 10px; padding: 6px 0; border: none; background: none; color: var(--c-hex); font-size: 12px; cursor: pointer; opacity: 0.9; }
.lc-detail-toggle:hover { opacity: 1; text-decoration: underline; }
.lc-detail { background: var(--c-bg2); border-radius: 10px; padding: 10px 12px; margin-bottom: 10px; }
.lc-detail-row { display: flex; align-items: center; gap: 8px; font-size: 12px; padding: 3px 0; color: #6b7280; }
.lc-detail-row .lc-d-label { flex: 1; display: flex; align-items: center; gap: 6px; }
.lc-d-icon { width: 16px; height: 16px; object-fit: contain; flex-shrink: 0; }
.lc-detail-row .lc-d-count { color: #9ca3af; }
.lc-detail-row .lc-d-exp { font-weight: 700; color: var(--c-hex); min-width: 52px; text-align: right; }
.lc-detail-row.total { border-top: 1px dashed #d8ded8; margin-top: 4px; padding-top: 6px; color: #374151; font-weight: 600; }
.lc-d-tip { margin: 6px 0 0; font-size: 11.5px; color: #9ca3af; line-height: 1.5; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.side-emo { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.se-img { width: 44px; height: 44px; border-radius: 50%; object-fit: cover; box-shadow: 0 2px 8px rgba(0,0,0,0.12); }
.se-label { font-size: 15px; font-weight: 700; color: var(--c-hex); }
.se-sub { font-size: 11.5px; color: #9ca3af; margin-top: 2px; }
.se-summary { margin: 0 0 12px; font-size: 12.5px; color: #6b7280; line-height: 1.65; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.side-btn { width: 100%; padding: 10px; border: none; border-radius: 10px; background: linear-gradient(135deg, var(--c-main), var(--c-hex)); color: #fff; font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.2s; box-shadow: 0 3px 10px rgba(0,0,0,0.1); }
.side-btn:hover { filter: brightness(1.06); transform: translateY(-1px); }
.side-btn.ghost { background: var(--c-bg2); color: var(--c-hex); box-shadow: none; }
.side-btn.ghost:hover { filter: none; background: var(--c-hex); color: #fff; }

/* ===== 最新动态（Steam 式动态流） ===== */
.activity-area { display: flex; flex-direction: column; gap: 18px; }
.act-section { display: flex; flex-direction: column; gap: 10px; }
.act-head { display: flex; align-items: baseline; gap: 12px; padding: 0 4px; }
.act-title { font-size: 16px; font-weight: 700; color: #374151; }
.act-sub { font-size: 12px; color: #9ca3af; }
.act-more { margin-left: auto; background: none; border: none; color: var(--c-hex); font-size: 12.5px; cursor: pointer; padding: 0; }
.act-more:hover { text-decoration: underline; }
.act-card { display: flex; align-items: center; gap: 14px; background: #fff; border-radius: 14px; padding: 12px 16px 12px 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.04); cursor: pointer; transition: all 0.2s; }
.act-card:hover { transform: translateX(4px); box-shadow: 0 6px 18px rgba(0,0,0,0.08); }
.act-cover { width: 120px; height: 66px; border-radius: 10px; object-fit: cover; flex-shrink: 0; background: var(--c-bg2); }
.act-body { flex: 1; min-width: 0; }
.act-name { font-size: 14.5px; font-weight: 600; color: #374151; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.act-meta { font-size: 12px; color: #9ca3af; margin: 4px 0 8px; }
.act-prog { display: flex; align-items: center; gap: 10px; }
.act-prog .prog-bar { height: 6px; }
.act-prog-text { font-size: 11px; color: #9ca3af; white-space: nowrap; }
.act-type { flex-shrink: 0; font-size: 11px; font-weight: 600; padding: 3px 10px; border-radius: 999px; }
.act-type.audio { background: #ecf5ff; color: #409eff; }
.act-type.video { background: #fef0f0; color: #f56c6c; }
.act-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.mini-diary { display: flex; gap: 10px; background: #fff; border-radius: 12px; padding: 12px 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); cursor: pointer; transition: all 0.2s; }
.mini-diary:hover { transform: translateY(-2px); box-shadow: 0 5px 14px rgba(0,0,0,0.08); }
.md-emo { width: 30px; height: 30px; object-fit: contain; flex-shrink: 0; }
.md-body { flex: 1; min-width: 0; }
.md-text { font-size: 13px; color: #4b5563; line-height: 1.6; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.md-date { font-size: 11px; color: #b0b7c3; margin-top: 5px; }
.mini-test { display: flex; align-items: center; gap: 12px; background: #fff; border-radius: 12px; padding: 12px 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); cursor: pointer; transition: all 0.2s; }
.mini-test:hover { transform: translateY(-2px); box-shadow: 0 5px 14px rgba(0,0,0,0.08); }
.mt-score { width: 44px; height: 44px; border-radius: 12px; background: var(--c-bg2); color: var(--c-hex); font-size: 17px; font-weight: 800; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.mt-body { flex: 1; min-width: 0; }
.mt-name { font-size: 13.5px; font-weight: 600; color: #374151; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mt-meta { font-size: 11.5px; color: #9ca3af; margin-top: 3px; }
.mini-fb { background: #fff; border-radius: 12px; padding: 12px 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); cursor: pointer; transition: all 0.2s; }
.mini-fb:hover { transform: translateY(-2px); box-shadow: 0 5px 14px rgba(0,0,0,0.08); }
.mf-content { font-size: 13px; color: #4b5563; line-height: 1.6; }
.mf-reply { margin-top: 8px; font-size: 12.5px; color: var(--c-hex); padding: 8px 12px; background: var(--c-bg2); border-radius: 8px; line-height: 1.55; }
.mf-reply.pending { color: #d97706; }

/* 深色模式：新布局适配 */
.profile-page.dark-mode .side-card,
.profile-page.dark-mode .act-card,
.profile-page.dark-mode .mini-diary,
.profile-page.dark-mode .mini-test,
.profile-page.dark-mode .mini-fb,
.profile-page.dark-mode .empty { background: #1f2937; }
.profile-page.dark-mode .act-title,
.profile-page.dark-mode .lc-name,
.profile-page.dark-mode .act-name,
.profile-page.dark-mode .mt-name { color: #e5e7eb; }
.profile-page.dark-mode .side-stat { color: #9ca3af; }
.profile-page.dark-mode .side-stat:hover { background: rgba(255,255,255,0.07); color: var(--c-main); }
.profile-page.dark-mode .lc-bar { background: rgba(255,255,255,0.1); }
.profile-page.dark-mode .side-online,
.profile-page.dark-mode .se-summary,
.profile-page.dark-mode .md-text,
.profile-page.dark-mode .mf-content { color: #cbd5e1; }
.profile-page.dark-mode .act-meta,
.profile-page.dark-mode .md-date,
.profile-page.dark-mode .mt-meta { color: #6b7280; }
.profile-page.dark-mode .act-card:hover,
.profile-page.dark-mode .mini-diary:hover,
.profile-page.dark-mode .mini-test:hover,
.profile-page.dark-mode .mini-fb:hover { box-shadow: 0 6px 18px rgba(0,0,0,0.4); }
.profile-page.dark-mode .online-tag { color: #4ade80; }
.profile-page.dark-mode .hero-info h1 { color: #f3f4f6; }
.profile-page.dark-mode .profile-hero { background: #1f2937; }
.profile-page.dark-mode .hero-bg::after { background: linear-gradient(to bottom, rgba(31,41,55,0) 58%, rgba(31,41,55,0.55) 84%, #1f2937 100%); }
.profile-page.dark-mode .role-tag { background: rgba(255,255,255,0.08); }
.profile-page.dark-mode .hero-bg-set { background: rgba(31,41,55,0.65); border-color: rgba(255,255,255,0.18); color: #cbd5e1; }
.profile-page.dark-mode .theme-toggle { background: rgba(255,255,255,0.08); border-color: rgba(255,255,255,0.15); }
.profile-page.dark-mode .bg-adjust-tip { background: rgba(31,41,55,0.92); color: #e5e7eb; }
.profile-page.dark-mode .hero-meta { color: #9ca3af; }

/* Tab */
.tab-content { padding: 18px 0 0; width: 100%; margin: 0; scroll-margin-top: 12px; }
.empty { text-align: center; padding: 60px 20px; color: #aaa; font-size: 14px; background: #fff; border-radius: 16px; }
.list-area, .diary-area, .ai-area, .test-area, .week-area, .fb-area, .setting-area { display: flex; flex-direction: column; gap: 14px; }
/* 无感记录 / 情绪预警 两个新 Tab 复用同一套纵向卡片流 */
.sense-area, .risk-area { display: flex; flex-direction: column; gap: 14px; }

/* ===== 无感记录 Tab ===== */
.sw-row {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  padding: 12px 14px; margin-bottom: 10px;
  background: #fff; border: 1px solid var(--c-border); border-radius: 14px;
  box-sizing: border-box;
}
.sw-row.sub { margin-left: 18px; background: #fbfcfd; }
.sw-row.dim { opacity: 0.5; }
.sw-info { flex: 1; min-width: 0; }
.sw-name { margin: 0; font-size: 14px; font-weight: 600; color: #3c4d5f; }
.sw-sub  { margin: 3px 0 0; font-size: 12px; line-height: 1.65; color: #8a9bab; overflow-wrap: anywhere; }
.sw-sub b { color: #6b7f93; }
.sense-off-tip {
  padding: 10px 12px; margin-bottom: 10px; border-radius: 12px;
  background: #fdf6f6; color: #a8493a; font-size: 12.5px; line-height: 1.7;
}
.sense-off-tip code { background: rgba(0,0,0,0.05); padding: 1px 5px; border-radius: 5px; }
.sense-auto-tip {
  margin: 10px 0 0; padding: 11px 13px; border-radius: 12px;
  background: #f4f0ff; border: 1px dashed rgba(150,128,205,0.45);
  color: #6b5aa8; font-size: 12.5px; line-height: 1.75;
}
.sense-auto-tip b { color: #57489a; }

.sense-stat-grid {
  display: grid; gap: 10px; margin-bottom: 10px;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
}
.ssg-item {
  background: #fff; border: 1px solid var(--c-border); border-radius: 14px;
  padding: 12px 10px; text-align: center; box-sizing: border-box;
}
.ssg-num {
  display: block; font-size: 20px; font-weight: 700; color: #4a6b8a;
  overflow-wrap: anywhere; line-height: 1.3;
}
.ssg-lb { display: block; margin-top: 4px; font-size: 11.5px; color: #9aabbd; }

.ph-list { display: flex; flex-direction: column; gap: 8px; margin-bottom: 10px; }
.ph-item {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  background: #fff; border: 1px solid var(--c-border); border-radius: 12px; padding: 9px 12px;
}
.ph-date { font-size: 12.5px; color: #8a9bab; flex: 0 0 auto; }
.ph-emo {
  font-size: 12px; padding: 2px 9px; border-radius: 9px; color: #3c4d5f; flex: 0 0 auto;
}
.ph-conf { font-size: 12px; color: #7fb3d5; flex: 0 0 auto; }
.ph-status { margin-left: auto; font-size: 11.5px; padding: 1px 8px; border-radius: 8px; background: #f2f5f8; color: #8a9bab; }
.ph-status.s1 { background: #eaf5e9; color: #3f6f20; }
.ph-status.s2 { background: #f4f4f4; color: #888; }
.ph-status.s3 { background: #fdf3e3; color: #8a5b12; }
/* 「去处理」：待确认推测的处理入口 */
.ph-handle {
  flex: 0 0 auto; font-size: 11.5px; padding: 3px 10px; border-radius: 9px;
  border: 1px solid #bcd9ee; background: #f2f8fd; color: #3d7ba8; cursor: pointer;
  transition: all .18s ease;
}
.ph-handle:hover { background: #3d7ba8; border-color: #3d7ba8; color: #fff; }
.ph-tip { color: #7b8fa3; }

.pl-list { display: flex; flex-direction: column; gap: 6px; }
.pl-item {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  font-size: 12px; padding: 7px 10px; background: #fbfcfd; border-radius: 10px;
}
.pl-act { font-weight: 700; color: #5d7186; flex: 0 0 auto; }
.pl-detail { flex: 1; min-width: 0; color: #8a9bab; overflow-wrap: anywhere; }
.pl-time { flex: 0 0 auto; color: #b3c0cd; }

.sense-privacy .sp-list { margin: 0; padding-left: 20px; }
.sense-privacy .sp-list li { font-size: 12.5px; line-height: 1.9; color: #6b7f93; }
.sense-privacy .sp-list b { color: #4a6b8a; }

/* ===== 情绪风险预警 Tab（非医疗诊断） ===== */
.risk-disclaimer {
  padding: 11px 14px; border-radius: 14px; font-size: 12.5px; line-height: 1.8;
  background: #fdf3edd9; color: #a8493a; border: 1px solid rgba(224, 150, 120, 0.35);
  box-sizing: border-box; overflow-wrap: anywhere;
}
.risk-disclaimer b { color: #93392b; }

.th-grid { display: grid; gap: 8px; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); margin-bottom: 10px; }
.th-item {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  background: #fff; border: 1px solid var(--c-border); border-radius: 11px; padding: 8px 11px;
}
.th-k { font-size: 12px; color: #8a9bab; }
.th-v { font-size: 13px; font-weight: 700; color: #4a6b8a; }

.rl-list { display: flex; flex-direction: column; gap: 10px; }
.rl-item {
  background: #fff; border: 1px solid var(--c-border); border-radius: 14px; padding: 12px 14px;
  box-sizing: border-box;
}
.rl-item.lv2 { border-color: rgba(238, 143, 106, 0.45); background: #fffaf6; }
.rl-item.lv3 { border-color: rgba(221, 111, 111, 0.5);  background: #fff6f6; }
.rl-item.unread { box-shadow: 0 0 0 3px rgba(240, 161, 92, 0.13); }
.rl-head { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; margin-bottom: 6px; }
.rl-level { font-size: 12px; font-weight: 700; color: #fff; background: #f0a15c; padding: 2px 9px; border-radius: 9px; }
.rl-item.lv2 .rl-level { background: #ee8f6a; }
.rl-item.lv3 .rl-level { background: #dd6f6f; }
.rl-date { font-size: 12.5px; color: #6b7f93; }
.rl-rule { font-size: 11px; color: #fff; background: #c9d6e2; padding: 1px 7px; border-radius: 7px; }
.rl-tag { margin-left: auto; font-size: 11px; color: #fff; background: #f0a15c; padding: 1px 7px; border-radius: 7px; }
.rl-tag.crisis-tag { background: #d65757; }
/* 已标记误报：灰紫调，和橙色未确认/红色危机都区分开 */
.rl-tag.misreport-tag { background: #9d8ab5; }
.rl-detail { margin: 0 0 8px; font-size: 12.5px; line-height: 1.75; color: #6b7f93; overflow-wrap: anywhere; }
.rl-advice.pre {
  white-space: pre-wrap; font-size: 13px; line-height: 1.85; color: #44576b;
  background: #f6f8fa; border-radius: 12px; padding: 11px 13px; margin-bottom: 8px;
  overflow-wrap: anywhere;
}
.rl-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.rl-more { margin-top: 12px; text-align: center; }

/* 移动端：窄屏时一行一个，杜绝横向溢出 */
@media (max-width: 768px) {
  .sw-row.sub { margin-left: 0; }
  .sw-row { flex-wrap: wrap; }
  .ph-status { margin-left: 0; }
}
.diary-toolbar { display: flex; align-items: center; justify-content: space-between; background: #fff; border-radius: 14px; padding: 14px 20px; box-shadow: 0 2px 10px rgba(0,0,0,0.04); }
.board-tab .diary-toolbar { margin-bottom: 16px; }
.diary-toolbar-left { display: flex; align-items: center; gap: 12px; }
.diary-toolbar-title { font-size: 16px; font-weight: 600; color: var(--c-hex); }
.diary-toolbar-count { font-size: 12px; color: #9ca3af; background: var(--c-bg2); padding: 2px 10px; border-radius: 999px; }
.history-filter { display: flex; gap: 8px; align-items: center; background: #fff; padding: 14px 18px; border-radius: 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); flex-wrap: wrap; }
.hf-label { font-size: 13px; color: #6b7280; font-weight: 500; }
.hf-btn { padding: 5px 14px; border-radius: 999px; border: 1px solid var(--c-border); background: #fff; font-size: 12px; cursor: pointer; color: #6b7280; transition: all 0.2s; }
.hf-btn.on { background: var(--c-bg2); color: var(--c-hex); border-color: var(--c-hex); }
.hist-group .hist-group-title { font-size: 13px; color: #9ca3af; font-weight: 600; margin: 8px 4px 6px; }
.item-card { background: #fff; border-radius: 16px; padding: 14px; display: flex; gap: 16px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); cursor: pointer; transition: all 0.25s; position: relative; }
.item-card:hover { transform: translateY(-2px); box-shadow: 0 6px 22px rgba(0,0,0,0.08); }
.item-card > img { width: 76px; height: 76px; border-radius: 12px; object-fit: cover; flex-shrink: 0; background: var(--c-bg2); }
.item-info { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; }
.item-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.type-badge { padding: 2px 10px; border-radius: 999px; font-size: 11px; font-weight: 600; }
.type-badge.video { background: #fef0f0; color: #f56c6c; }
.type-badge.audio { background: #ecf5ff; color: #409eff; }
.item-name { font-size: 15px; font-weight: 600; color: #374151; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.item-cat { font-size: 12px; color: #9ca3af; }
.prog-row { margin-top: 8px; display: flex; align-items: center; gap: 10px; }
.prog-bar { flex: 1; height: 5px; background: #f0f0f0; border-radius: 5px; overflow: hidden; }
.prog-fill { height: 100%; background: linear-gradient(90deg, var(--c-main), var(--c-hex)); border-radius: 5px; }
.prog-text { font-size: 11px; color: #9ca3af; white-space: nowrap; }
.finished-badge { font-size: 11px; color: #52c41a; background: rgba(82,196,26,.1); padding: 2px 8px; border-radius: 10px; margin-left: auto; }
.gone-badge { font-size: 11px; color: #9ca3af; background: rgba(156,163,175,.14); padding: 2px 8px; border-radius: 10px; margin-left: auto; }
.item-card:has(.gone-badge) { opacity: .72; }
.hist-meta { font-size: 11px; color: #bbb; margin-top: 4px; }
.del-btn { position: absolute; top: 10px; right: 10px; width: 26px; height: 26px; border-radius: 50%; border: none; background: var(--c-bg2); color: #bbb; font-size: 12px; cursor: pointer; opacity: 0; transition: all 0.2s; display: flex; align-items: center; justify-content: center; }
.item-card:hover .del-btn { opacity: 1; }
.del-btn:hover { background: #fee2e2; color: #ef4444; }

.diary-input-card, .fb-input-card { background: #fff; border-radius: 18px; padding: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); }
.diary-input-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
.emotion-picker { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.emotion-label { font-size: 13px; color: #6b7280; }
.emo-btn { width: 40px; height: 40px; border-radius: 50%; border: 2px solid transparent; background: var(--c-bg1); cursor: pointer; transition: all 0.2s; padding: 0; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.emo-btn img { width: 28px; height: 28px; object-fit: contain; pointer-events: none; }
.emo-btn:hover { background: #f0f0f0; transform: scale(1.08); }
.emo-btn.on { border-color: var(--c-hex); background: var(--c-bg2); transform: scale(1.15); box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.diary-date { font-size: 13px; color: #9ca3af; }
.diary-input-card textarea, .fb-input-card textarea { width: 100%; padding: 12px; border: 1px solid var(--c-border); border-radius: 10px; font-size: 14px; font-family: inherit; resize: vertical; background: var(--c-bg1); transition: all 0.2s; box-sizing: border-box; }
.diary-input-card textarea:focus, .fb-input-card textarea:focus { outline: none; border-color: var(--c-hex); background: #fff; box-shadow: 0 0 0 3px rgba(144,219,144,0.12); }
.diary-input-bottom, .fb-input-bottom { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; }
.char-count { font-size: 12px; color: #9ca3af; }
.primary-btn, .danger-btn { padding: 13px; border: none; border-radius: 12px; font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.25s; }
.primary-btn { background: linear-gradient(135deg, var(--c-main), var(--c-hex)); color: #fff; box-shadow: 0 3px 12px rgba(144,219,144,0.35); }
.primary-btn:hover { box-shadow: 0 6px 20px rgba(144,219,144,0.5); transform: translateY(-1px); }
.primary-btn.small { padding: 8px 22px; font-size: 13px; }
.danger-btn { background: linear-gradient(135deg, #f87171, #ef4444); color: #fff; box-shadow: 0 3px 12px rgba(239,68,68,0.35); }
.danger-btn.small { padding: 8px 22px; font-size: 13px; }
.diary-list { display: flex; flex-direction: column; gap: 12px; }
.diary-card { background: #fff; border-radius: 16px; padding: 18px 20px; box-shadow: 0 2px 10px rgba(0,0,0,0.04); border-left: 4px solid var(--c-hex); }
.diary-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.diary-emo-img { width: 26px; height: 26px; object-fit: contain; }
.diary-emo-label { font-size: 13px; color: var(--c-hex); font-weight: 600; }
.diary-card-img { margin-top: 10px; max-width: 100%; max-height: 280px; border-radius: 12px; object-fit: cover; display: block; }

/* ===== 时间线日记卡片 ===== */
.timeline-wrap { display: flex; flex-direction: column; gap: 18px; padding: 10px 0; }
.timeline-item { display: flex; align-items: flex-start; gap: 0; }
.timeline-time { width: 80px; flex-shrink: 0; text-align: right; padding-right: 12px; padding-top: 10px; display: flex; flex-direction: column; gap: 1px; }
.time-text { font-size: 16px; font-weight: 800; color: #1f2937; font-family: 'Courier New', monospace; text-shadow: 0 1px 2px rgba(255,255,255,0.8); line-height: 1.2; }
.time-date { font-size: 12px; color: #4b5563; font-weight: 600; line-height: 1.2; }
.time-weekday { font-size: 12px; color: #6b7280; font-weight: 500; line-height: 1.2; }
.timeline-line { width: 24px; flex-shrink: 0; display: flex; justify-content: center; position: relative; padding-top: 14px; }
.timeline-line::before { content: ''; position: absolute; left: 50%; top: 0; bottom: -18px; width: 2px; background: var(--c-border-strong); transform: translateX(-50%); }
.timeline-item:last-child .timeline-line::before { display: none; }
.timeline-dot { width: 12px; height: 12px; border-radius: 50%; border: 3px solid #fff; box-shadow: 0 0 0 2px var(--c-border-strong); position: relative; z-index: 1; flex-shrink: 0; }
.timeline-card { flex: 1; background: #fff; border-radius: 16px; padding: 16px 18px; border-left: 5px solid #FFB74D; box-shadow: 0 2px 12px rgba(0,0,0,0.06); transition: all 0.2s; }
.timeline-card:hover { box-shadow: 0 4px 20px rgba(0,0,0,0.1); transform: translateY(-2px); }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.card-emo { display: flex; align-items: center; gap: 8px; }
.card-emo-img { width: 28px; height: 28px; object-fit: contain; }
.card-emo-label { font-size: 18px; font-weight: 700; color: #374151; }
.card-actions { display: flex; align-items: center; gap: 12px; }
.card-heart { font-size: 14px; color: #f43f5e; font-weight: 600; }
.card-content { font-size: 15px; line-height: 1.8; color: #4b5563; white-space: pre-wrap; }
.card-img { margin-top: 10px; max-width: 100%; max-height: 260px; border-radius: 10px; object-fit: cover; }

/* ===== 日记分页 ===== */
.diary-pager { display: flex; justify-content: center; align-items: center; gap: 16px; margin-top: 24px; padding: 16px 0; }
.pager-btn { padding: 10px 22px; border: none; border-radius: 25px; background: linear-gradient(135deg, #FFB74D, #FF9800); color: #fff; font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.2s; box-shadow: 0 2px 8px rgba(255, 152, 0, 0.3); }
.pager-btn:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(255, 152, 0, 0.4); }
.pager-btn:disabled { background: var(--c-border-strong); cursor: not-allowed; box-shadow: none; opacity: 0.6; }
.pager-info { font-size: 15px; font-weight: 600; color: #4b5563; background: var(--c-bg2); padding: 8px 18px; border-radius: 20px; }
.diary-img-row { display: flex; align-items: center; gap: 10px; margin-top: 10px; padding: 8px 12px; background: var(--c-bg2, #f6f9f6); border-radius: 10px; }
.diary-img-preview { width: 60px; height: 60px; object-fit: cover; border-radius: 8px; }
.diary-img-remove { background: none; border: none; color: #ef4444; font-size: 12px; cursor: pointer; padding: 4px 8px; }
.diary-img-remove:hover { text-decoration: underline; }
.diary-input-actions { display: flex; gap: 8px; align-items: center; }
.link-btn.small { font-size: 13px; padding: 6px 12px; border: 1px solid var(--c-border-strong); border-radius: 8px; background: #fff; cursor: pointer; color: #6b7280; }
.link-btn.small:hover { color: var(--c-hex); border-color: var(--c-hex); }
.link-btn.danger-link { color: #dc2626; }
.link-btn.danger-link:hover { text-decoration: underline; }
.diary-week { font-size: 12px; color: #9ca3af; }
.diary-del { margin-left: auto; font-size: 12px; color: #ef4444; background: none; border: none; cursor: pointer; }
.diary-content { font-size: 14px; color: #4b5563; line-height: 1.7; white-space: pre-wrap; }

.ai-card { background: #fff; border-radius: 16px; padding: 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.04); }
.ai-user-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.ai-tag { font-size: 12px; padding: 2px 8px; border-radius: 4px; font-weight: 600; }
.ai-tag.user { background: #e0f2fe; color: #0284c7; }
.ai-tag.ai { background: var(--c-bg2); color: var(--c-hex); }
.ai-time { margin-left: auto; font-size: 11px; color: #9ca3af; }
.ai-bubble { padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.6; margin-bottom: 10px; white-space: pre-wrap; word-break: break-word; }
.ai-bubble.user { background: #eff6ff; color: #1e40af; margin-left: 40px; border-bottom-right-radius: 4px; }
.ai-bubble.ai { background: var(--c-bg2); color: #374151; margin-right: 40px; border-bottom-left-radius: 4px; }

.chart-box { width: 100%; height: 280px; background: #fff; border-radius: 16px; padding: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.04); }
.chart-box-sm { width: 100%; height: 300px; background: #fff; border-radius: 16px; padding: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.04); }
.test-list { display: flex; flex-direction: column; gap: 10px; }
.test-card { background: #fff; border-radius: 14px; padding: 14px 18px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); }
.test-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.test-score { font-size: 20px; font-weight: 700; color: var(--c-hex); }
.test-emotion { padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.test-emotion.emo-1 { background: #fef3c7; color: #d97706; }
.test-emotion.emo-2 { background: #dcfce7; color: #16a34a; }
.test-emotion.emo-3 { background: #fee2e2; color: #dc2626; }
.test-emotion.emo-4 { background: #ede9fe; color: #7c3aed; }
.test-emotion.emo-5 { background: #fecaca; color: #b91c1c; }
.test-emotion.emo-6 { background: #ffe4e6; color: #be123c; }
.test-time { margin-left: auto; font-size: 12px; color: #9ca3af; }
.test-tag { font-size: 12px; color: #9ca3af; margin-top: 6px; }
.test-tip { font-size: 13px; color: #6b7280; margin-top: 8px; line-height: 1.6; }
.test-card-actions { display: flex; gap: 8px; margin-top: 10px; }
.test-action-btn { flex:1; padding:8px 12px; border-radius:8px; border:1px solid var(--c-border); background:#fff; color:#6b7280; font-size:13px; cursor:pointer; transition:all 0.2s; }
.test-action-btn:hover { border-color: var(--c-hex); color: var(--c-hex); }
.test-action-btn.primary { background: var(--c-hex); color:#fff; border-color: var(--c-hex); }
.test-card.clickable:hover { box-shadow:0 4px 14px rgba(0,0,0,0.08); }
.test-detail-body { padding:10px 0; text-align:center; }
.test-detail-name { font-size:18px; font-weight:700; color:#2d5a2d; margin-bottom:16px; }
.test-detail-circle { width:120px; height:120px; border-radius:50%; background:#e8f0e0; display:inline-flex; align-items:baseline; justify-content:center; padding-top:28px; margin-bottom:12px; }
.test-detail-circle.lv-low { background:#d4e8d0; }
.test-detail-circle.lv-mid { background:#f0e8b8; }
.test-detail-circle.lv-high { background:#f8c9d8; }
.test-detail-score { font-size:42px; font-weight:700; color:#2d5a2d; }
.test-detail-total { font-size:18px; color:#888; margin-left:4px; }
.test-detail-level { display:inline-block; padding:4px 16px; border-radius:999px; font-size:14px; font-weight:600; margin-bottom:10px; }
.test-detail-level.lv-low { background:#d4e8d0; color:#3d7c32; }
.test-detail-level.lv-mid { background:#f0e8b8; color:#8a7a3a; }
.test-detail-level.lv-high { background:#f8c9d8; color:#b84a5a; }
.test-detail-meta { font-size:12px; color:#9ca3af; margin-bottom:12px; }
.test-detail-tip { background:var(--c-bg1); border-radius:10px; padding:14px; font-size:14px; color:#555; line-height:1.7; text-align:left; }
.test-detail-actions { display:flex; gap:8px; margin-top:16px; }
.share-card-preview { border-radius:16px; overflow:hidden; margin-bottom:16px; }
.sc-header { background:linear-gradient(135deg,var(--c-main),var(--c-hex)); padding:16px 20px; display:flex; justify-content:space-between; align-items:center; }
.sc-logo { color:#fff; font-weight:700; font-size:15px; }
.sc-date { color:rgba(255,255,255,0.85); font-size:12px; }
.sc-body { background:linear-gradient(180deg,#f5faf0,#fef9f0); padding:24px 20px; text-align:center; }
.sc-test-name { font-size:16px; font-weight:700; color:#2d5a2d; margin-bottom:18px; }
.sc-circle { width:90px; height:90px; border-radius:50%; background:#e8f0e0; display:inline-flex; align-items:baseline; justify-content:center; padding-top:22px; margin-bottom:10px; }
.sc-circle.lv-low { background:#d4e8d0; }
.sc-circle.lv-mid { background:#f0e8b8; }
.sc-circle.lv-high { background:#f8c9d8; }
.sc-score { font-size:32px; font-weight:700; color:#5a9a5a; }
.sc-circle.lv-high .sc-score { color:#b84a5a; }
.sc-circle.lv-mid .sc-score { color:#8a7a3a; }
.sc-total { font-size:14px; color:#888; margin-left:2px; }
.sc-level { display:inline-block; padding:3px 14px; border-radius:999px; font-size:13px; font-weight:600; margin-bottom:12px; }
.sc-level.lv-low { background:#d4e8d0; color:#3d7c32; }
.sc-level.lv-mid { background:#f0e8b8; color:#8a7a3a; }
.sc-level.lv-high { background:#f8c9d8; color:#b84a5a; }
.sc-tip { font-size:13px; color:#555; line-height:1.6; margin:0; }
.sc-footer { background:#f5f5f0; padding:12px 20px; display:flex; justify-content:space-between; font-size:11px; color:#8ab88a; }
.sc-brand { font-weight:700; color:#6b9b6b; }

.week-summary { display: flex; gap: 14px; flex-wrap: wrap; }
.summary-item { flex: 1; background: #fff; border-radius: 14px; padding: 18px; text-align: center; box-shadow: 0 2px 10px rgba(0,0,0,0.04); min-width: 100px; }
.summary-num { font-size: 26px; font-weight: 700; color: var(--c-hex); }
.summary-num.main { font-size: 40px; }
.week-top-emo-img { width: 50px; height: 50px; border-radius: 50%; object-fit: cover; display: block; margin: 0 auto; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.week-stat-img { width: 42px; height: 42px; object-fit: contain; display: block; margin: 0 auto; }
.week-stat-value { font-size: 22px; font-weight: 700; color: inherit; margin-top: 2px; }
.summary-num.happy { color: #f59e0b; }
.summary-label { font-size: 12px; color: #9ca3af; margin-top: 4px; }
.week-top-emo-label { font-size: 14px; font-weight: 700; color: var(--c-hex); margin-top: 4px; }
.report-text { background: #fff; border-radius: 14px; padding: 18px 20px; box-shadow: 0 2px 10px rgba(0,0,0,0.04); }
.report-text h4 { margin: 0 0 10px; font-size: 14px; color: #374151; }
.report-text p { margin: 0; font-size: 14px; color: #4b5563; line-height: 1.7; }

.fb-input-card h4 { margin: 0 0 12px; font-size: 15px; color: #374151; }
.fb-list { display: flex; flex-direction: column; gap: 12px; }
.fb-card { background: #fff; border-radius: 16px; padding: 18px 20px; box-shadow: 0 2px 10px rgba(0,0,0,0.04); }
.fb-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.fb-time { font-size: 12px; color: #9ca3af; }
.fb-content { font-size: 14px; color: #374151; line-height: 1.6; }
.fb-reply { margin-top: 12px; padding: 12px 14px; background: var(--c-bg2); border-radius: 10px; border-left: 3px solid var(--c-hex); }
.reply-label { font-size: 12px; color: var(--c-hex); font-weight: 600; margin-bottom: 4px; }
.reply-text { font-size: 13px; color: #374151; line-height: 1.6; }
.fb-pending { margin-top: 10px; font-size: 12px; color: #f59e0b; }

.setting-area { display: flex; flex-direction: column; gap: 20px; }
.sec-card { background: #fff; border-radius: 18px; padding: 26px 28px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); }
.sec-card h3 { margin: 0 0 20px; font-size: 16px; font-weight: 600; color: #374151; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px 20px; }
.form-item { display: flex; flex-direction: column; gap: 6px; }
.uc-uname-hint { margin: 0; font-size: 11.5px; color: #9ca3af; }
.form-item label { font-size: 13px; color: #6b7280; }
.form-item input { padding: 11px 14px; border: 1px solid var(--c-border); border-radius: 10px; font-size: 14px; background: var(--c-bg1); transition: all 0.2s; outline: none; color: #374151; }
.form-item input:focus { border-color: var(--c-hex); background: #fff; box-shadow: 0 0 0 3px rgba(144,219,144,0.12); }
.form-item input:disabled { background: var(--c-bg2); color: #9ca3af; }
.link-btn { background: none; border: none; color: var(--c-hex); font-size: 13px; cursor: pointer; padding: 0; margin-top: 4px; }
.link-btn:hover { text-decoration: underline; }
/* ===== 登录设备 / 多端会话 ===== */
.sec-desc { margin: -12px 0 16px; font-size: 12.5px; color: #9ca3af; line-height: 1.6; }
.session-list { display: flex; flex-direction: column; gap: 10px; }
.session-item { display: flex; align-items: center; gap: 14px; padding: 14px 16px; border: 1px solid #eef2f0; border-radius: 14px; background: #fafdfb; transition: all 0.2s; }
.session-item:hover { border-color: #d7e8dc; background: #fff; }
.session-item.current { border-color: #b9e3c4; background: linear-gradient(135deg, rgba(158,217,168,0.10), rgba(158,217,168,0.02)); }
.session-item.off { opacity: 0.55; }
.se-icon { width: 40px; height: 40px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; font-size: 20px; border-radius: 12px; background: #eef6f1; }
.session-item.current .se-icon { background: rgba(158,217,168,0.28); }
.se-body { flex: 1; min-width: 0; }
.se-device { margin: 0 0 4px; font-size: 14px; font-weight: 600; color: #374151; display: flex; align-items: center; gap: 8px; }
.se-tag { padding: 1px 8px; font-size: 11px; font-weight: 500; border-radius: 999px; background: #6fbf8f; color: #fff; }
.se-meta { margin: 0; font-size: 12px; color: #9ca3af; }
.se-kick { flex-shrink: 0; padding: 7px 14px; border: 1px solid #fecaca; border-radius: 999px; background: #fff; color: #ef4444; font-size: 12.5px; cursor: pointer; transition: all 0.2s; }
.se-kick:hover { background: #fef2f2; border-color: #f87171; }
.se-off { flex-shrink: 0; font-size: 12px; color: #9ca3af; }
.se-actions { display: flex; gap: 18px; margin-top: 16px; padding-top: 14px; border-top: 1px dashed var(--c-border); }
.link-btn.danger { color: #ef4444; }
.link-btn.danger:hover { color: #dc2626; }
/* 无感 / 预警 Tab 用的主要操作样式（视觉上比普通 link-btn 重一点） */
.link-btn.primary-link { color: #d1783f; font-weight: 700; }
.link-btn.primary-link:hover { color: #b45f2b; }
/* 误报按钮：低调的次要操作，弱于「我知道了」 */
.link-btn.misreport-link { color: #98a2b3; }
.link-btn.misreport-link:hover { color: #7a5a9a; }
.link-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.link-btn:disabled:hover { text-decoration: none; }
.profile-page.dark-mode .session-item { background: rgba(45,55,72,0.55); border-color: rgba(120,140,170,0.2); }
.profile-page.dark-mode .session-item:hover { background: rgba(45,55,72,0.8); }
.profile-page.dark-mode .se-device { color: #cbd5e1; }
.profile-page.dark-mode .se-icon { background: rgba(120,140,170,0.22); }
.profile-page.dark-mode .se-kick { background: transparent; }
.profile-page.dark-mode .sec-desc { color: #7b8794; }

.logout-btn { width: 100%; padding: 13px; border: 1px solid #fecaca; border-radius: 12px; background: #fff; color: #ef4444; font-size: 14px; font-weight: 500; cursor: pointer; transition: all 0.2s; }
.logout-btn:hover { background: #fef2f2; border-color: #f87171; }

.mask { position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 9999; backdrop-filter: blur(4px); }
.avatar-picker, .modal-card { background: #fff; border-radius: 20px; padding: 28px 32px; width: 400px; max-width: 90vw; box-shadow: 0 20px 50px rgba(0,0,0,0.2); }
.avatar-picker h3, .modal-card h3 { margin: 0 0 20px; font-size: 16px; color: #374151; text-align: center; }
.modal-desc { font-size: 14px; color: #6b7280; text-align: center; margin: 0 0 20px; line-height: 1.6; }
.modal-desc code { background: var(--c-bg2); padding: 2px 8px; border-radius: 4px; color: var(--c-hex); font-weight: 600; }
.modal-actions { display: flex; gap: 12px; justify-content: center; }
.avatar-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 22px; }
.avatar-opt { width: 90px; height: 90px; margin: 0 auto; border-radius: 50%; overflow: hidden; cursor: pointer; border: 3px solid transparent; transition: all 0.2s; }
.avatar-opt img { width: 100%; height: 100%; object-fit: cover; display: block; }
.avatar-opt.on { border-color: var(--c-hex); transform: scale(1.06); box-shadow: 0 4px 14px rgba(123,196,123,0.4); }
.picker-actions { display: flex; justify-content: center; }
.cancel { padding: 10px 30px; border: 1px solid var(--c-border); border-radius: 10px; background: #fff; cursor: pointer; font-size: 14px; color: #6b7280; }
.cancel:hover { border-color: #ccc; color: #374151; }

.frame-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 22px; }
.frame-opt { cursor: pointer; text-align: center; transition: all 0.2s; padding: 10px 4px; border-radius: 12px; }
.frame-opt:hover { background: var(--c-bg2); }
.frame-opt.on { background: var(--c-bg2); box-shadow: inset 0 0 0 2px var(--c-hex); }
.frame-preview { width: 60px; height: 60px; margin: 0 auto 18px; border-radius: 50%; overflow: visible; position: relative; }
.frame-preview .frame-avatar-inner { width: 60px; height: 60px; border-radius: 50%; object-fit: cover; position: relative; z-index: 1; background: var(--c-bg2); }
.frame-preview .frame-ring-img { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); width: 165%; height: 165%; pointer-events: none; z-index: 3; object-fit: contain; }
.frame-name { font-size: 12px; color: #6b7280; margin-top: 6px; }
.frame-rec { font-size: 10px; color: var(--c-hex); font-weight: 600; margin-top: 2px; }
.avatar-opt.upload-opt { display: flex; align-items: center; justify-content: center; border: 2px dashed var(--c-border-strong); background: var(--c-bg1); }
.avatar-opt.upload-opt:hover { border-color: var(--c-hex); background: var(--c-bg2); }
.upload-placeholder { display: flex; flex-direction: column; align-items: center; gap: 4px; font-size: 22px; color: #9ca3af; }
.upload-placeholder span { font-size: 11px; }
.avatar-set-row { display: flex; align-items: center; gap: 10px; }
.avatar-preview-sm { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; border: 2px solid var(--c-border); flex-shrink: 0; }
.avatar-upload-area { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 36px 20px; border: 2px dashed var(--c-border-strong); border-radius: 14px; cursor: pointer; transition: all 0.2s; margin-bottom: 22px; }
.avatar-upload-area:hover { border-color: var(--c-hex); background: var(--c-bg2); }

/* ===== 个人中心弹窗（基本资料 / 个性化设置 / 修改密码） ===== */
.uc-mask { z-index: 9990; } /* 低于头像/背景/头像框选择弹窗(9999)，保证二级弹窗盖在上面 */
.uc-modal { position: relative; background: #fff; border-radius: 20px; padding: 22px 28px 20px; width: 560px; max-width: 92vw; max-height: 86vh; overflow-y: auto; box-shadow: 0 20px 50px rgba(0,0,0,0.2); }
.uc-close { position: absolute; top: 14px; right: 16px; width: 30px; height: 30px; border-radius: 50%; border: none; background: var(--c-bg2); color: #6b7280; font-size: 14px; cursor: pointer; transition: all 0.2s; display: flex; align-items: center; justify-content: center; }
.uc-close:hover { background: var(--c-hex); color: #fff; transform: rotate(90deg); }
.uc-head { font-size: 16px; font-weight: 700; color: #374151; margin-bottom: 14px; }
.uc-tabs { display: flex; gap: 10px; margin-bottom: 18px; flex-wrap: wrap; }
.uc-tab { padding: 7px 18px; border-radius: 999px; border: 1px solid var(--c-border); background: #fff; color: #6b7280; font-size: 13px; cursor: pointer; transition: all 0.2s; }
.uc-tab:hover { border-color: var(--c-hex); color: var(--c-hex); }
.uc-tab.on { background: linear-gradient(135deg, var(--c-main), var(--c-hex)); border-color: transparent; color: #fff; box-shadow: 0 3px 10px rgba(0,0,0,0.12); }
.uc-body { min-height: 230px; }
.uc-avatar-card { display: flex; align-items: center; gap: 16px; background: var(--c-bg1); border-radius: 14px; padding: 14px 18px; margin-bottom: 18px; }
.uc-avatar { width: 64px; height: 64px; border-width: 3px; }
.uc-avatar .avatar-img-inner { width: 64px; height: 64px; }
.uc-avatar-info { display: flex; flex-direction: column; gap: 8px; align-items: flex-start; }
.uc-avatar-name { font-size: 15px; font-weight: 600; color: #374151; }
.uc-upload-btn { padding: 6px 16px; border-radius: 999px; border: none; background: linear-gradient(135deg, var(--c-main), var(--c-hex)); color: #fff; font-size: 12px; cursor: pointer; transition: all 0.2s; }
.uc-upload-btn:hover { box-shadow: 0 4px 14px rgba(0,0,0,0.15); transform: translateY(-1px); }
.uc-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px 18px; }
.uc-full { grid-column: 1 / -1; }
.uc-sec-title { font-size: 13px; font-weight: 600; color: #6b7280; margin: 16px 0 10px; }
.uc-sec-title:first-child { margin-top: 2px; }
.uc-dots { display: flex; gap: 10px; flex-wrap: wrap; padding: 4px 0; }
.uc-switch { width: 100%; padding: 11px; border-radius: 10px; border: 1px solid var(--c-border); background: var(--c-bg1); font-size: 13px; color: #374151; cursor: pointer; transition: all 0.2s; }
.uc-switch:hover { border-color: var(--c-hex); background: var(--c-bg2); }
.uc-deco-row { display: flex; gap: 10px; flex-wrap: wrap; }
.uc-deco-row .cancel { flex: 1; min-width: 140px; }
.uc-tip { font-size: 12px; color: #9ca3af; margin-top: 14px; }
.uc-footer { display: flex; justify-content: flex-end; gap: 12px; margin-top: 18px; }
.upload-big-icon { font-size: 40px; margin-bottom: 10px; }
.avatar-upload-area p { margin: 2px 0; font-size: 14px; color: #6b7280; }
.avatar-upload-area .upload-hint { font-size: 12px; color: #9ca3af; }

@media (max-width: 900px) {
  .steam-body { flex-direction: column; }
  .steam-side { width: 100%; position: static; }
  .act-grid { grid-template-columns: 1fr; }
  .act-cover { width: 90px; height: 52px; }
}

@media (max-width: 700px) {
  .profile-page { padding: 20px 14px 60px; }
  .hero-content { flex-wrap: wrap; justify-content: center; text-align: center; gap: 12px; }
  .hero-bg { height: 150px; }
  .hero-sign-edit input { min-width: 0; flex: 1; }
  .week-summary { flex-wrap: wrap; }
  .summary-item { min-width: 80px; }
  .form-grid { grid-template-columns: 1fr; }
  .frame-grid { grid-template-columns: repeat(3, 1fr); }
}

/* ===== 列表工具栏 ===== */
.list-toolbar { display: flex; gap: 14px; align-items: center; background: #fff; padding: 14px 18px; border-radius: 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); flex-wrap: wrap; }
.lt-row { display: flex; align-items: center; gap: 8px; }
.lt-label { font-size: 13px; color: #6b7280; font-weight: 500; white-space: nowrap; display: inline-flex; align-items: center; gap: 4px; }
.lt-icon { width: 14px; height: 14px; object-fit: contain; }
.lt-input { padding: 7px 12px; border: 1px solid var(--c-border); border-radius: 8px; font-size: 13px; background: var(--c-bg1); outline: none; min-width: 220px; transition: all 0.2s; color: #374151; }
.lt-input:focus { border-color: var(--c-hex); background: #fff; box-shadow: 0 0 0 3px rgba(144,219,144,0.12); }
.lt-date { padding: 7px 10px; border: 1px solid var(--c-border); border-radius: 8px; font-size: 13px; background: var(--c-bg1); outline: none; transition: all 0.2s; color: #374151; }
.lt-date:focus { border-color: var(--c-hex); background: #fff; }
.lt-sep { font-size: 13px; color: #9ca3af; }
.lt-quick { padding: 6px 14px; border-radius: 999px; border: 1px solid var(--c-border); background: #fff; font-size: 12px; cursor: pointer; color: #6b7280; transition: all 0.2s; }
.lt-quick:hover { border-color: var(--c-hex); color: var(--c-hex); }
.lt-quick.on { background: var(--c-bg2); color: var(--c-hex); border-color: var(--c-hex); font-weight: 600; }
.lt-reset { padding: 6px 14px; border-radius: 999px; border: 1px dashed var(--c-border-strong); background: #fff; font-size: 12px; cursor: pointer; color: #9ca3af; transition: all 0.2s; }
.lt-reset:hover { border-color: var(--c-hex); color: var(--c-hex); }

/* 可点击卡片 */
.ai-card.clickable, .fb-card.clickable { cursor: pointer; transition: all 0.25s; }
.ai-card.clickable:hover, .fb-card.clickable:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,0.08); }
.ai-card-hint, .fb-card-hint { margin-top: 10px; font-size: 11px; color: var(--c-hex); text-align: right; opacity: 0.6; transition: opacity 0.2s; }
.ai-card.clickable:hover .ai-card-hint,
.fb-card.clickable:hover .fb-card-hint { opacity: 1; }

/* 反馈状态标签 */
.fb-status { font-size: 11px; padding: 2px 10px; border-radius: 999px; font-weight: 600; }
.fb-status.replied { background: #ecfdf5; color: #10b981; }
.fb-status.pending { background: #fef3c7; color: #d97706; }

/* 气泡 full 样式（详情弹内） */
.ai-bubble.user.full { margin-left: 0; }
.ai-bubble.ai.full { margin-right: 0; }

/* 详情弹窗 */
.detail-modal { background: #fff; border-radius: 20px; width: 560px; max-width: 92vw; max-height: 85vh; overflow: hidden; box-shadow: 0 20px 50px rgba(0,0,0,0.2); display: flex; flex-direction: column; animation: dmIn 0.2s ease; }
@keyframes dmIn { from { opacity: 0; transform: scale(0.92) translateY(10px); } to { opacity: 1; transform: scale(1) translateY(0); } }
.detail-header { display: flex; align-items: center; justify-content: space-between; padding: 18px 24px; border-bottom: 1px solid var(--c-border); background: linear-gradient(135deg, var(--c-bg1), #fff); }
.detail-header h3 { margin: 0; font-size: 16px; color: #374151; font-weight: 600; }
.detail-close { width: 32px; height: 32px; border-radius: 50%; border: none; background: var(--c-bg2); color: #6b7280; font-size: 16px; cursor: pointer; transition: all 0.2s; }
.detail-close:hover { background: #fee2e2; color: #ef4444; transform: rotate(90deg); }
.detail-meta { padding: 12px 24px; font-size: 12px; color: #9ca3af; background: #fafafa; border-bottom: 1px solid var(--c-border); }
.detail-body { padding: 20px 24px; overflow-y: auto; flex: 1; display: flex; flex-direction: column; gap: 8px; background: #fff; }

/* 反馈对话消息气泡 */
.fb-msg-row { display: flex; }
.fb-msg-row:nth-child(odd) { justify-content: flex-start; }
.fb-msg-row:nth-child(even) { justify-content: flex-end; }
.fb-msg-bubble { max-width: 80%; padding: 12px 16px; border-radius: 14px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.fb-msg-bubble.user { background: #eff6ff; color: #1e40af; border-bottom-left-radius: 4px; }
.fb-msg-bubble.admin { background: var(--c-bg2); color: #374151; border-bottom-right-radius: 4px; }
.fb-msg-tag { font-size: 11px; color: #9ca3af; margin-bottom: 4px; font-weight: 600; }
.fb-msg-content { font-size: 14px; line-height: 1.65; white-space: pre-wrap; word-break: break-word; }
.fb-msg-time { font-size: 10px; color: #bbb; margin-top: 6px; text-align: right; }

/* 关键词高亮 */
mark.hl { background: #fde68a; color: #92400e; padding: 0 2px; border-radius: 3px; font-weight: 600; }
.profile-page.dark-mode mark.hl { background: #92400e; color: #fde68a; }

/* 果冻风确认弹窗样式已抽至 styles/jelly-confirm.css（全站通用） */

/* ===== 我的评论标签页 ===== */
.cmt-area { display: flex; flex-direction: column; gap: 14px; }
.cmt-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.cmt-stat {
  background: #fff; border: 1px solid #eef2f0; border-radius: 14px;
  padding: 14px 12px; text-align: center; cursor: pointer;
  box-shadow: 0 2px 10px rgba(0,0,0,0.04); transition: all 0.2s;
}
.cmt-stat:hover { transform: translateY(-2px); box-shadow: 0 6px 18px rgba(0,0,0,0.07); }
.cmt-stat.on { border-color: var(--c-hex); background: var(--c-bg2); }
.cs-num { display: block; font-size: 22px; font-weight: 700; color: #374151; line-height: 1.2; }
.cs-label { font-size: 12px; color: #9ca3af; }
.cmt-stat.ok .cs-num { color: #4a9a5a; }
.cmt-stat.wait .cs-num { color: #b7791f; }
.cmt-stat.no .cs-num { color: #c0392b; }
.cmt-stat.ok.on { border-color: #4a9a5a; }
.cmt-stat.wait.on { border-color: #b7791f; }
.cmt-stat.no.on { border-color: #c0392b; }

.cmt-tip {
  background: #fff; border: 1px dashed #dfe7e2; border-radius: 12px;
  padding: 10px 14px; font-size: 12px; color: #6b7280; line-height: 1.8;
}
.cmt-tip b { color: #374151; }
.ct-item { margin-left: 14px; }
.dot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; margin-right: 4px; }
.dot.ok { background: #4a9a5a; }
.dot.wait { background: #b7791f; }
.dot.no { background: #c0392b; }

.cmt-list { display: flex; flex-direction: column; gap: 10px; }
.cmt-card {
  display: flex; align-items: flex-start; gap: 14px;
  background: #fff; border: 1px solid #eef2f0; border-radius: 14px;
  padding: 14px 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.04);
  transition: all 0.2s;
}
.cmt-card:hover { box-shadow: 0 6px 18px rgba(0,0,0,0.08); }
.cmt-card.pending { border-style: dashed; border-color: #f0c36d; background: #fffdf6; }
.cmt-card.rejected { border-style: dashed; border-color: #f3b0ac; background: #fffafa; }
.cmt-cover { width: 56px; height: 56px; border-radius: 10px; object-fit: cover; flex: none; background: var(--c-bg2); }
.cmt-body { flex: 1; min-width: 0; }
.cmt-line1 { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 5px; }
.cmt-type { font-size: 11px; padding: 1px 8px; border-radius: 10px; background: #eef3ef; color: #6b7280; }
.cmt-type.audio { background: #e8f4ea; color: #3f7a4a; }
.cmt-type.video { background: #eaf1fa; color: #3f6ea8; }
.cmt-name { font-size: 13px; font-weight: 600; color: #374151; }
.cmt-badge { font-size: 11px; padding: 1px 8px; border-radius: 10px; line-height: 16px; }
.cmt-badge.ok { color: #3f7a4a; background: #e8f6ea; border: 1px solid #a8d5b1; }
.cmt-badge.wait { color: #b7791f; background: #fdf3d8; border: 1px solid #f0c36d; }
.cmt-badge.no { color: #c0392b; background: #fdeceb; border: 1px solid #f3b0ac; }
.cmt-text { font-size: 13px; color: #4b5563; line-height: 1.6; word-break: break-all; }
.cmt-card.rejected .cmt-text { text-decoration: line-through; text-decoration-color: #d99b96; }
.cmt-time { font-size: 11px; color: #9ca3af; margin-top: 5px; }
.cmt-go {
  flex: none; border: 1px solid var(--c-bg2); background: #fff; color: var(--c-hex);
  font-size: 12px; padding: 6px 12px; border-radius: 999px; cursor: pointer; transition: all 0.2s;
}
.cmt-go:hover { background: var(--c-hex); border-color: var(--c-hex); color: #fff; }

.cmt-pager { display: flex; justify-content: center; align-items: center; gap: 14px; padding: 4px 0 8px; }
.cmt-pager button {
  border: 1px solid var(--c-border); background: #fff; color: #6b7280;
  font-size: 12px; padding: 6px 14px; border-radius: 999px; cursor: pointer;
}
.cmt-pager button:disabled { opacity: 0.45; cursor: not-allowed; }
.cmt-page-info { font-size: 12px; color: #9ca3af; }

.profile-page.dark-mode .cmt-stat,
.profile-page.dark-mode .cmt-tip,
.profile-page.dark-mode .cmt-card { background: rgba(31, 41, 55, 0.9); border-color: #374151; }
.profile-page.dark-mode .cmt-card.pending { background: rgba(120, 90, 30, 0.22); border-color: #b7791f; }
.profile-page.dark-mode .cmt-card.rejected { background: rgba(120, 40, 40, 0.22); border-color: #c0392b; }
.profile-page.dark-mode .cs-num { color: #e5e7eb; }
.profile-page.dark-mode .cs-label,
.profile-page.dark-mode .cmt-tip { color: #9ca3af; }
.profile-page.dark-mode .cmt-name,
.profile-page.dark-mode .cmt-text { color: #d1d5db; }
.profile-page.dark-mode .cmt-time { color: #6b7280; }
.profile-page.dark-mode .cmt-go,
.profile-page.dark-mode .cmt-pager button { background: rgba(55, 65, 81, 0.9); border-color: #4b5563; color: #d1d5db; }

@media (max-width: 700px) {
  .cmt-stats { grid-template-columns: repeat(2, 1fr); }
  .cmt-card { flex-wrap: wrap; padding: 12px; }
  .cmt-go { margin-left: 70px; }
  .cmt-cover { width: 46px; height: 46px; }
}

</style>