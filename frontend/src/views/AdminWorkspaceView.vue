<script setup>
import { ref } from 'vue'
import AdminDashboard from '../components/admin/AdminDashboard.vue'
import UserManagementPanel from '../components/admin/UserManagementPanel.vue'
import RoleManagementPanel from '../components/admin/RoleManagementPanel.vue'
import SessionAuditPanel from '../components/admin/SessionAuditPanel.vue'
import ArchiveStatsPanel from '../components/admin/ArchiveStatsPanel.vue'
import DeadLetterPanel from '../components/admin/DeadLetterPanel.vue'
import VipSkillPanel from '../components/admin/VipSkillPanel.vue'

const activeTab = ref('dashboard')

const tabs = [
  { key: 'dashboard', label: '管理仪表盘' },
  { key: 'users', label: '用户管理' },
  { key: 'roles', label: '角色管理' },
  { key: 'sessions', label: '会话审计' },
  { key: 'archive', label: '归档统计' },
  { key: 'deadletters', label: '死信管理' },
  { key: 'vip', label: 'VIP 技能组' },
]

function switchTab(key) {
  activeTab.value = key
}
</script>

<template>
  <div class="admin-workspace">
    <nav class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        :class="['tab', { active: activeTab === tab.key }]"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <main class="tab-content">
      <AdminDashboard v-if="activeTab === 'dashboard'" @navigate="switchTab" />
      <UserManagementPanel v-if="activeTab === 'users'" />
      <RoleManagementPanel v-if="activeTab === 'roles'" />
      <SessionAuditPanel v-if="activeTab === 'sessions'" />
      <ArchiveStatsPanel v-if="activeTab === 'archive'" />
      <DeadLetterPanel v-if="activeTab === 'deadletters'" />
      <VipSkillPanel v-if="activeTab === 'vip'" />
    </main>
  </div>
</template>

<style scoped>
.admin-workspace {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.tab-bar {
  display: flex;
  gap: 0;
  border-bottom: 2px solid #e5e7eb;
  background: #f9fafb;
  padding: 0 0.5rem;
  flex-shrink: 0;
  overflow-x: auto;
}
.tab {
  padding: 0.75rem 1.25rem;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 0.95rem;
  color: #64748b;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  white-space: nowrap;
  transition: color 0.2s, border-color 0.2s;
}
.tab:hover {
  color: #1e293b;
}
.tab.active {
  color: #2563eb;
  border-bottom-color: #2563eb;
  font-weight: 600;
}
.tab-content {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}
</style>
