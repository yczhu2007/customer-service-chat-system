<script setup>
import { ref } from 'vue'
import AdminDashboard from '../components/admin/AdminDashboard.vue'
import UserManagementPanel from '../components/admin/UserManagementPanel.vue'
import RoleManagementPanel from '../components/admin/RoleManagementPanel.vue'
import SessionAuditPanel from '../components/admin/SessionAuditPanel.vue'
import ArchiveStatsPanel from '../components/admin/ArchiveStatsPanel.vue'
import DeadLetterPanel from '../components/admin/DeadLetterPanel.vue'
import VipSkillPanel from '../components/admin/VipSkillPanel.vue'
import AdminMessageSearchPanel from '../components/admin/AdminMessageSearchPanel.vue'

const activeTab = ref('dashboard')

const tabs = [
  { key: 'dashboard', label: '管理仪表盘' },
  { key: 'users', label: '用户管理' },
  { key: 'roles', label: '角色管理' },
  { key: 'sessions', label: '会话审计' },
  { key: 'messages', label: '消息搜索' },
  { key: 'archive', label: '归档统计' },
  { key: 'deadletters', label: '死信管理' },
  { key: 'vip', label: 'VIP 技能组' },
]

function switchTab(key) {
  activeTab.value = key
}
</script>

<template>
  <el-container class="admin-workspace">
    <el-header class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        :class="['tab', { active: activeTab === tab.key }]"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </el-header>

    <el-main class="tab-content">
      <AdminDashboard v-if="activeTab === 'dashboard'" />
      <UserManagementPanel v-if="activeTab === 'users'" />
      <RoleManagementPanel v-if="activeTab === 'roles'" />
      <SessionAuditPanel v-if="activeTab === 'sessions'" />
      <AdminMessageSearchPanel v-if="activeTab === 'messages'" />
      <ArchiveStatsPanel v-if="activeTab === 'archive'" />
      <DeadLetterPanel v-if="activeTab === 'deadletters'" />
      <VipSkillPanel v-if="activeTab === 'vip'" />
    </el-main>
  </el-container>
</template>

<style scoped>
.admin-workspace {
  --el-header-padding: 0;
  --el-main-padding: 0;
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 54px);
  background: var(--color-bg);
}
.tab-bar {
  height: auto;
  line-height: normal;
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--color-line);
  background: var(--color-paper);
  padding: 0 20px;
  flex-shrink: 0;
  overflow-x: auto;
}
.tab {
  padding: 14px 16px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 0.95rem;
  color: var(--color-muted);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  white-space: nowrap;
  transition: color 0.2s, border-color 0.2s;
}
.tab:hover {
  color: var(--color-ink);
}
.tab.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: 600;
}
.tab-content {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  padding: 18px 24px;
}
:deep(.data-table) { background: var(--color-paper); border: 1px solid var(--color-line); border-radius: 8px; overflow: hidden; }
:deep(.data-table thead) { background: #f8f9fb; }
:deep(.data-table th) { color: var(--color-muted); font-size: 12px; font-weight: 600; }
:deep(.data-table th), :deep(.data-table td) { border-bottom-color: var(--color-line); }
:deep(.panel-header h2), :deep(.tab-content h2) { color: var(--color-ink); font-size: 19px; }
:deep(.btn-primary) { border-color: var(--color-primary); background: var(--color-primary); color: #fff; }
:deep(.btn-primary:hover:not(:disabled)) { border-color: var(--color-primary-hover); background: var(--color-primary-hover); color: #fff; }
:deep(.btn-danger) { border-color: var(--color-danger); background: transparent; color: var(--color-danger); }
:deep(.dialog) { border: 1px solid var(--color-line); border-radius: 10px; box-shadow: 0 12px 32px rgba(24, 29, 38, .14); }
:deep(.filters input), :deep(.filters select) { border-color: var(--color-line-strong); border-radius: 8px; }
</style>
