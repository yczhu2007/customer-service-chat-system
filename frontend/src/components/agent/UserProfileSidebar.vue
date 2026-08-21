<script setup>
import { computed } from 'vue'
import { useChatStore } from '../../stores/chat'

const chat = useChatStore()

const profile = computed(() => chat.activeUserProfile)

/** Format datetime for display */
function formatTime(ts) {
  if (!ts) return '-'
  const d = new Date(ts)
  if (isNaN(d.getTime())) return '-'
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** VIP level badge */
function vipBadge(level) {
  if (level >= 5) return { text: `VIP ${level}`, class: 'vip-diamond' }
  if (level >= 3) return { text: `VIP ${level}`, class: 'vip-gold' }
  if (level >= 1) return { text: `VIP ${level}`, class: 'vip-silver' }
  return { text: '普通用户', class: 'vip-none' }
}
</script>

<template>
  <div class="user-profile-sidebar">
    <h4 class="sidebar-title">用户信息</h4>

    <div v-if="!profile" class="no-data">请先选择会话</div>

    <template v-else>
      <div class="profile-section">
        <div class="avatar">{{ (profile.username || '?')[0] }}</div>
        <div class="name-row">
          <span class="username">{{ profile.username || '-' }}</span>
          <span class="vip-badge" :class="vipBadge(profile.vipLevel).class">
            {{ vipBadge(profile.vipLevel).text }}
          </span>
        </div>
      </div>

      <div class="info-grid">
        <div class="info-item">
          <span class="info-label">用户ID</span>
          <span class="info-value">{{ profile.userId || '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">总会话数</span>
          <span class="info-value">{{ profile.totalSessionCount ?? '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">最近会话</span>
          <span class="info-value">{{ formatTime(profile.lastSessionTime) }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.user-profile-sidebar {
  padding: 0.75rem;
}
.sidebar-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #111827;
  margin: 0 0 0.75rem;
}
.no-data {
  color: #9ca3af;
  font-size: 0.8rem;
  text-align: center;
  padding: 1rem 0;
}
.profile-section {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}
.avatar {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 50%;
  background: #dbeafe;
  color: #1d4ed8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.1rem;
  font-weight: 600;
  flex-shrink: 0;
}
.name-row {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.username {
  font-size: 0.9rem;
  font-weight: 600;
  color: #111827;
}
.vip-badge {
  font-size: 0.65rem;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
  display: inline-block;
  width: fit-content;
}
.vip-none {
  background: #f3f4f6;
  color: #6b7280;
}
.vip-silver {
  background: #e0f2fe;
  color: #0284c7;
}
.vip-gold {
  background: #fef3c7;
  color: #d97706;
}
.vip-diamond {
  background: #ede9fe;
  color: #7c3aed;
}
.info-grid {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.8rem;
}
.info-label {
  color: #6b7280;
}
.info-value {
  color: #111827;
  font-weight: 500;
  text-align: right;
  word-break: break-all;
  max-width: 60%;
}
</style>
