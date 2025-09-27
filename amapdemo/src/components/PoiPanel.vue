<template>
  <div class="poi-panel" :class="{ 'poi-panel-open': isOpen }">
    <div class="panel-title">
      POI查询
      <button class="close-button" @click="handleClose">
        <i class="fas fa-times"></i>
      </button>
    </div>
    <div class="panel-content">
      <div class="form-group">
        <label>关键词:</label>
        <input
          type="text"
          v-model="localKeyword"
          placeholder="输入POI名称"
          class="form-input"
          id="poi-search-input"
        />

        <label>城市:</label>
        <input
          type="text"
          v-model="localCity"
          placeholder="输入城市名称"
          class="form-input"
        />
      </div>

      <div class="button-group">
        <button @click="handleSearch" class="control-button primary" :disabled="loading">
          <i v-if="!loading" class="fas fa-search"></i>
          <i v-if="loading" class="fas fa-spinner fa-spin"></i>
          {{ loading ? '搜索中...' : '搜索POI' }}
        </button>
        <button @click="handleClearResults" class="control-button secondary">
          <i class="fas fa-eraser"></i> 清除结果
        </button>
      </div>

      <div v-if="error" class="alert error">
        <i class="fas fa-exclamation-circle"></i> {{ error.message }}
      </div>

      <div v-if="results.length > 0" class="search-results">
        <h4 class="results-title">查询结果 ({{ results.length }})</h4>
        <ul class="results-list">
          <li
            v-for="item in results"
            :key="item.id"
            class="result-item"
            @click="handleShowPoi(item)"
          >
            <div class="result-title">{{ item.name }}</div>
            <div class="result-address">{{ item.address }}</div>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, defineProps, defineEmits, watch } from 'vue'

const props = defineProps({
  isOpen: {
    type: Boolean,
    default: false
  },
  keyword: {
    type: String,
    default: ''
  },
  city: {
    type: String,
    default: '北京'
  },
  results: {
    type: Array,
    default: () => []
  },
  error: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([
  'close',
  'search',
  'clear-results',
  'show-poi',
  'update:keyword',
  'update:city'
])

// 使用本地ref来存储输入值，避免直接修改props
const localKeyword = ref(props.keyword)
const localCity = ref(props.city)

// 监听props变化，同步到本地状态
watch(() => props.keyword, (newValue) => {
  localKeyword.value = newValue
})

watch(() => props.city, (newValue) => {
  localCity.value = newValue
})

// 监听本地状态变化，通知父组件更新
watch(localKeyword, (newValue) => {
  emit('update:keyword', newValue)
})

watch(localCity, (newValue) => {
  emit('update:city', newValue)
})

const handleClose = () => {
  emit('close')
}

const handleSearch = () => {
  emit('search')
}

const handleClearResults = () => {
  emit('clear-results')
}

const handleShowPoi = (poi) => {
  emit('show-poi', poi)
}
</script>

<style scoped>
.poi-panel {
  position: absolute;
  top: 70px;
  right: 20px;
  width: 340px;
  background-color: var(--background-white);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: 100;
  transform: translateX(120%);
  transition: transform var(--transition-normal) cubic-bezier(0.4, 0, 0.2, 1);
  max-height: calc(100vh - 100px);
  overflow-y: auto;
}

.poi-panel-open {
  transform: translateX(0);
}

.poi-panel .panel-title {
  background-color: var(--primary-dark);
  color: white;
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--border-radius-lg) var(--border-radius-lg) 0 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.poi-panel .close-button {
  background: none;
  border: none;
  color: white;
  font-size: 16px;
  cursor: pointer;
  padding: 4px;
}

.poi-panel .panel-content {
  padding: var(--spacing-sm);
}

/* POI查询面板的按钮调整 */
.poi-panel .button-group {
  margin-top: var(--spacing-md);
}

.poi-panel .control-button {
  flex: none;
  padding: 0.65rem 1.2rem;
}

.search-results {
  margin-top: var(--spacing-lg);
  background-color: var(--background-white);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--shadow-sm);
  padding: var(--spacing-md);
}

.results-title {
  font-size: var(--font-size-md);
  font-weight: 600;
  margin-bottom: var(--spacing-sm);
  color: var(--text-secondary);
}

.results-list {
  list-style: none;
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #eee;
  border-radius: 4px;
  margin-top: 10px;
}

.result-item {
  padding: var(--spacing-sm);
  border-bottom: 1px solid #e5e7eb;
  cursor: pointer;
  transition: background-color var(--transition-fast);
}

.result-item:last-child {
  border-bottom: none;
}

.result-item:hover {
  background-color: var(--secondary-color);
}

.result-title {
  font-weight: 500;
  color: #1f2937;
}

.result-address {
  font-size: 0.85rem;
  color: #6b7280;
  margin-top: 0.2rem;
  margin-bottom: 1rem;
}

.panel-content::-webkit-scrollbar {
  width: 6px;
}

.panel-content::-webkit-scrollbar-track {
  background: var(--secondary-color);
}

.panel-content::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 3px;
}

.panel-content::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}
</style>