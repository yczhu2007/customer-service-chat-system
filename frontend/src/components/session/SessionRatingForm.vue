<script setup>
import { ref, watch, onMounted } from 'vue'
import { useChatStore } from '../../stores/chat'

const props = defineProps({
  sessionId: { type: String, required: true },
})

const chat = useChatStore()
const rating = ref(0)
const comment = ref('')
const existingRating = ref(null)
const loading = ref(false)
const submitting = ref(false)
const error = ref(null)
const submitted = ref(false)

async function loadRating() {
  loading.value = true
  error.value = null
  try {
    const result = await chat.getSessionRating(props.sessionId)
    if (result && result.rating) {
      existingRating.value = result
      rating.value = result.rating
      comment.value = result.comment || ''
    } else {
      existingRating.value = null
      rating.value = 0
      comment.value = ''
    }
  } catch {
    existingRating.value = null
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (rating.value < 1 || rating.value > 5) {
    error.value = '请选择评分'
    return
  }
  submitting.value = true
  error.value = null
  try {
    const result = await chat.submitRating(props.sessionId, {
      rating: rating.value,
      comment: comment.value || undefined,
    })
    existingRating.value = result
    submitted.value = true
  } catch (e) {
    error.value = e.message || '提交失败'
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.sessionId,
  () => {
    submitted.value = false
    existingRating.value = null
    rating.value = 0
    comment.value = ''
    loadRating()
  }
)

onMounted(() => {
  loadRating()
})
</script>

<template>
  <div class="rating-form">
    <h4>满意度评价</h4>

    <!-- Loading -->
    <div v-if="loading" class="loading-hint">加载中…</div>

    <!-- Already rated (read-only) -->
    <div v-else-if="existingRating && !submitted" class="rated-display">
      <div class="stars-display">
        <el-rate :model-value="existingRating.rating" disabled text-color="#f59e0b" disabled-void-color="#d1d5db" />
      </div>
      <p v-if="existingRating.comment" class="rated-comment">{{ existingRating.comment }}</p>
      <p class="rated-time">评价时间: {{ existingRating.createTime }}</p>
    </div>

    <!-- Just submitted -->
    <div v-else-if="submitted" class="submitted-hint">
      <p>感谢您的评价！</p>
    </div>

    <!-- Rating input form -->
    <div v-else class="rating-input">
      <div class="stars-input">
        <el-rate v-model="rating" :disabled="submitting" text-color="#f59e0b" void-color="#d1d5db" />
      </div>
      <el-input
        v-model="comment"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="输入评价内容（可选）"
        class="comment-input"
      />
      <div class="form-footer">
        <el-alert v-if="error" :title="error" type="error" :closable="false" class="error-msg" />
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="rating < 1"
          class="submit-btn"
          @click="submit"
        >
          {{ submitting ? '提交中…' : '提交评价' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rating-form {
  padding: 1rem;
  background: white;
  border-top: 1px solid #e5e7eb;
}
.rating-form h4 {
  margin: 0 0 0.75rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #374151;
}
.stars-display,
.stars-input {
  margin-bottom: 0.5rem;
}
.stars-display :deep(.el-rate),
.stars-input :deep(.el-rate) {
  height: 28px;
}
.comment-input {
  width: 100%;
  margin-bottom: 0.5rem;
}
.form-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.submit-btn {
  padding: 0.4rem 1rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 0.375rem;
  cursor: pointer;
  font-size: 0.85rem;
}
.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.submit-btn:not(:disabled):hover {
  background: #2563eb;
}
.error-msg {
  color: #ef4444;
  font-size: 0.8rem;
}
.rated-display {
  padding: 0.5rem 0;
}
.rated-comment {
  font-size: 0.85rem;
  color: #374151;
  margin: 0.25rem 0;
}
.rated-time {
  font-size: 0.75rem;
  color: #9ca3af;
  margin: 0;
}
.submitted-hint {
  color: #059669;
  font-size: 0.9rem;
}
.loading-hint {
  color: #9ca3af;
  font-size: 0.85rem;
}
</style>
