<script setup>
import { onMounted, computed } from 'vue'
import { useChatStore } from '../../stores/chat'

const chat = useChatStore()

/** View definitions with Chinese labels and icons */
const VIEW_DEFS = [
  { code: 'MY_ACTIVE', label: '处理中', icon: '🟢' },
  { code: 'MY_UNREAD', label: '未读', icon: '🔴' },
  { code: 'MY_HIGH_PRIORITY', label: '高优先级', icon: '⚡' },
  { code: 'MY_UNARCHIVED', label: '未归档', icon: '📂' },
  { code: 'MY_RECENT_CLOSED', label: '最近关闭', icon: '✅' },
]

/** Build a map of code -> count for quick lookup */
const countMap = computed(() => {
  const map = {}
  for (const v of chat.agentViewCounts) {
    map[v.code] = v.count
  }
  return map
})

function selectView(code) {
  chat.switchAgentView(code)
}

onMounted(() => {
  chat.loadAgentViewCounts()
})
</script>

<template>
  <nav class="agent-view-nav" role="navigation" aria-label="客服视图导航">
    <h3 class="nav-title">视图</h3>
    <ul class="view-list">
      <li
        v-for="v in VIEW_DEFS"
        :key="v.code"
        class="view-item"
        :class="{ active: chat.activeAgentView === v.code }"
        @click="selectView(v.code)"
      >
        <span class="view-icon">{{ v.icon }}</span>
        <span class="view-label">{{ v.label }}</span>
        <span v-if="countMap[v.code] != null" class="view-count" :class="{ highlight: countMap[v.code] > 0 }">
          {{ countMap[v.code] }}
        </span>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.agent-view-nav {
  padding: 0.5rem 0;
}
.nav-title {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6b7280;
  padding: 0.5rem 1rem;
  margin: 0;
}
.view-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.view-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  cursor: pointer;
  font-size: 0.875rem;
  color: #374151;
  transition: background 0.15s;
}
.view-item:hover {
  background: #f3f4f6;
}
.view-item.active {
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 600;
}
.view-icon {
  font-size: 1rem;
  width: 1.25rem;
  text-align: center;
}
.view-label {
  flex: 1;
}
.view-count {
  font-size: 0.75rem;
  min-width: 1.5rem;
  text-align: center;
  padding: 0.1rem 0.4rem;
  border-radius: 9999px;
  background: #e5e7eb;
  color: #6b7280;
}
.view-count.highlight {
  background: #dbeafe;
  color: #1d4ed8;
}
</style>
