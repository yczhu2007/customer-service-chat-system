<script setup>
import { ref } from 'vue'
import { searchAgentMessages } from '../../api/chat-api'
import { useChatStore } from '../../stores/chat'
const emit = defineEmits(['selected'])
const chat = useChatStore()
const keyword = ref('')
const loading = ref(false)
const error = ref('')
const results = ref([])
const total = ref(0)
const hasSearched = ref(false)
async function search() { const value = keyword.value.trim(); if (!value) return; hasSearched.value = true; loading.value = true; error.value = ''; try { const result = await searchAgentMessages({ keyword: value, pageNo: 1, pageSize: 20 }); results.value = result?.data?.records || []; total.value = result?.data?.total || 0 } catch (exception) { error.value = exception.message || '&#25628;&#32034;&#22833;&#36133;' } finally { loading.value = false } }
async function openResult(result) { await chat.openAgentSearchResult(result); emit('selected') }
function clear() { keyword.value = ''; results.value = []; total.value = 0; error.value = ''; hasSearched.value = false }
</script>
<template><section class="message-search-panel"><div class="search-row"><el-input v-model="keyword" clearable placeholder="&#25628;&#32034;&#25105;&#30340;&#21382;&#21490;&#28040;&#24687;" @keyup.enter="search"/><el-button type="primary" :loading="loading" :disabled="!keyword.trim()" @click="search">&#25628;&#32034;</el-button><el-button :disabled="!keyword && !results.length" @click="clear">&#28165;&#31354;</el-button></div><p v-if="error" class="search-error">{{ error }}</p><p v-else-if="hasSearched && !loading" class="summary">&#20849;&#21305;&#37197; {{ total }} &#26465;&#28040;&#24687;</p><div v-if="results.length" class="search-results"><button v-for="result in results" :key="result.messageId" class="search-result" @click="openResult(result)"><strong>{{ result.sessionTitle || '新咨询' }}</strong><span>{{ result.content }}</span><small>{{ result.senderRole === 'AGENT' ? '&#23458;&#26381;' : '&#29992;&#25143;' }} · {{ result.createTime }}</small></button></div></section></template>
<style scoped>.message-search-panel{padding:.25rem}.search-row{display:flex;gap:.5rem}.search-row :deep(.el-input){flex:1;min-width:0}.search-error{color:#dc2626;font-size:.85rem}.summary{margin:.5rem 0;color:var(--color-muted);font-size:.85rem}.search-results{max-height:360px;overflow-y:auto;border:1px solid var(--color-line);border-radius:8px}.search-result{display:flex;width:100%;flex-direction:column;gap:.2rem;padding:.65rem;border:0;border-bottom:1px solid var(--color-line);background:var(--color-paper);text-align:left;cursor:pointer}.search-result:hover{background:#f8fafc}.search-result:last-child{border-bottom:0}.search-result span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--color-muted);font-size:.85rem}.search-result small{color:var(--color-faint);font-size:.75rem}</style>
