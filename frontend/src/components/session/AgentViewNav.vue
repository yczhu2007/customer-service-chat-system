<script setup>
import { onMounted, computed, ref } from 'vue'
import { useChatStore } from '../../stores/chat'
import { AGENT_VIEW_OPTIONS } from '../../constants/session-ui'

const chat = useChatStore()
const archiveExpanded = ref(false)
const sessionViews = AGENT_VIEW_OPTIONS.filter((view) => !view.code.startsWith('MY_ARCHIVED_') && view.code !== 'MY_TICKETS')
const ticketViews = AGENT_VIEW_OPTIONS.filter((view) => view.code === 'MY_TICKETS')
const archiveViews = AGENT_VIEW_OPTIONS.filter((view) => view.code.startsWith('MY_ARCHIVED_'))

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
    <section class="view-section session-views" aria-label="会话视图">
      <span class="section-title">会话</span>
      <ul class="view-list">
      <li
        v-for="v in sessionViews"
        :key="v.code"
        class="view-item"
        :class="{ active: chat.activeAgentView === v.code }"
        @click="selectView(v.code)"
      >
        <span class="view-label">{{ v.label }}</span>
        <span v-if="countMap[v.code] != null" class="view-count" :class="{ highlight: countMap[v.code] > 0 }">
          {{ countMap[v.code] }}
        </span>
      </li>
      </ul>
    </section>
    <section class="view-section ticket-views" aria-label="工单视图">
      <span class="section-title">工单</span>
      <ul class="view-list">
        <li v-for="v in ticketViews" :key="v.code" class="view-item" :class="{ active: chat.activeAgentView === v.code }" @click="selectView(v.code)">
          <span class="view-label">{{ v.label }}</span>
          <span v-if="countMap[v.code] != null" class="view-count" :class="{ highlight: countMap[v.code] > 0 }">{{ countMap[v.code] }}</span>
        </li>
      </ul>
    </section>
    <section class="view-section archive-views" aria-label="归档会话视图">
      <button class="archive-toggle" type="button" :aria-expanded="archiveExpanded" @click="archiveExpanded = !archiveExpanded">
        <span>归档</span><span>{{ archiveExpanded ? '收起' : '展开' }}</span>
      </button>
      <ul v-if="archiveExpanded" class="view-list archive-view-list">
        <li v-for="v in archiveViews" :key="v.code" class="view-item" :class="{ active: chat.activeAgentView === v.code }" @click="selectView(v.code)">
          <span class="view-label">{{ v.label }}</span>
          <span v-if="countMap[v.code] != null" class="view-count" :class="{ highlight: countMap[v.code] > 0 }">{{ countMap[v.code] }}</span>
        </li>
      </ul>
    </section>
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
.view-section + .view-section { margin-top: 0.3rem; }
.section-title { display: block; padding: 0.25rem 1rem; color: #9ca3af; font-size: 0.68rem; }
.archive-toggle { display: flex; width: 100%; justify-content: space-between; padding: 0.5rem 1rem; border: 0; background: transparent; color: #6b7280; cursor: pointer; font-size: 0.75rem; }
.archive-toggle:hover { background: #f3f4f6; }
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
