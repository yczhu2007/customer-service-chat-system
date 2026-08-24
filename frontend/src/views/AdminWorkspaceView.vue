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
    <aside class="admin-sidebar" aria-label="管理员功能导航">
      <div class="sidebar-title">管理中心</div>
      <nav class="sidebar-nav">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="['sidebar-item', { active: activeTab === tab.key }]"
          @click="switchTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </nav>
    </aside>

    <section class="admin-content">
      <AdminDashboard v-if="activeTab === 'dashboard'" />
      <UserManagementPanel v-if="activeTab === 'users'" />
      <RoleManagementPanel v-if="activeTab === 'roles'" />
      <SessionAuditPanel v-if="activeTab === 'sessions'" />
      <AdminMessageSearchPanel v-if="activeTab === 'messages'" />
      <ArchiveStatsPanel v-if="activeTab === 'archive'" />
      <DeadLetterPanel v-if="activeTab === 'deadletters'" />
      <VipSkillPanel v-if="activeTab === 'vip'" />
    </section>
  </el-container>
</template>

<style scoped>
.admin-workspace {
  display: flex;
  flex-direction: row;
  height: calc(100vh - 54px);
  min-height: 0;
  background: var(--color-bg);
  overflow: hidden;
}
.admin-sidebar {
  width: 210px;
  flex: 0 0 210px;
  background: var(--color-paper);
  border-right: 1px solid var(--color-line);
  padding: 18px 12px;
  overflow-y: auto;
}
.sidebar-title {
  padding: 0 12px 14px;
  color: var(--color-ink);
  font-size: 0.82rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.sidebar-item {
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: transparent;
  border-radius: 7px;
  cursor: pointer;
  text-align: left;
  font-size: 0.9rem;
  color: var(--color-muted);
  white-space: nowrap;
  transition: color 0.15s, background 0.15s;
}
.sidebar-item:hover {
  color: var(--color-ink);
  background: var(--color-bg);
}
.sidebar-item.active {
  color: var(--color-primary);
  background: #eef5ff;
  font-weight: 600;
}
.admin-content {
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
@media (max-width: 820px) {
  .admin-sidebar {
    width: 154px;
    flex-basis: 154px;
    padding-inline: 8px;
  }
  .sidebar-title { padding-inline: 8px; }
  .sidebar-item { padding-inline: 8px; font-size: 0.82rem; }
  .admin-content { padding: 14px; }
}
</style>
