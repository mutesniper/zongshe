<template>
  <div class="control-panel" :class="{ collapsed: isCollapsed }">
    <div class="panel-title">路线规划</div>
    <div class="panel-content">
      <!-- 规划方式选择 -->
      <div class="form-group">
        <label class="radio-label">
          <input type="radio" v-model="localUseCoordinates" :value="false" /> 地点名称
        </label>
        <label class="radio-label">
          <input type="radio" v-model="localUseCoordinates" :value="true" /> 经纬度
        </label>
      </div>

      <!-- 关键字模式 -->
      <div v-if="!localUseCoordinates" class="form-group">
        <label>起点:</label>
        <div class="input-group">
          <input
            type="text"
            v-model="localStartKeyword"
            placeholder="输入起点名称"
            class="form-input"
          />
          <input
            type="text"
            v-model="localStartCity"
            placeholder="输入城市"
            class="form-input city-input"
          />
        </div>

        <label>终点:</label>
        <div class="input-group">
          <input
            type="text"
            v-model="localEndKeyword"
            placeholder="输入终点名称"
            class="form-input"
          />
          <input
            type="text"
            v-model="localEndCity"
            placeholder="输入城市"
            class="form-input city-input"
          />
        </div>
      </div>

      <!-- 经纬度模式 -->
      <div v-if="localUseCoordinates" class="form-group">
        <label>起点经纬度:</label>
        <div class="input-group">
          <input type="text" v-model="localStartLng" placeholder="经度" class="form-input" />
          <input type="text" v-model="localStartLat" placeholder="纬度" class="form-input" />
        </div>

        <label>终点经纬度:</label>
        <div class="input-group">
          <input type="text" v-model="localEndLng" placeholder="经度" class="form-input" />
          <input type="text" v-model="localEndLat" placeholder="纬度" class="form-input" />
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="button-group">
        <button @click="handlePlanRoute" class="control-button primary">
          <i class="fas fa-route"></i> 开始规划
        </button>
        <button @click="handleClearRoute" class="control-button secondary">
          <i class="fas fa-eraser"></i> 清除路线
        </button>
      </div>

      <!-- 结果信息 -->
      <div v-if="error" class="alert error">
        <i class="fas fa-exclamation-circle"></i> {{ error.message }}
      </div>
      <div v-if="result" class="info-box">
        <div class="info-item">
          <span class="info-label">距离:</span>
          <span class="info-value" v-if="result.routes && result.routes[0]">
            {{ result.routes[0].distance / 1000 }} 公里
          </span>
        </div>
        <div class="info-item">
          <span class="info-label">预计时间:</span>
          <span class="info-value" v-if="result.routes && result.routes[0]">
            {{ Math.ceil(result.routes[0].time / 60) }} 分钟
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, defineProps, defineEmits, watch } from 'vue'

const props = defineProps({
  isCollapsed: {
    type: Boolean,
    default: false,
  },
  useCoordinates: {
    type: Boolean,
    default: false,
  },
  startKeyword: {
    type: String,
    default: '北京市地震局（公交站）',
  },
  startCity: {
    type: String,
    default: '北京',
  },
  endKeyword: {
    type: String,
    default: '亦庄文化园（地铁站）',
  },
  endCity: {
    type: String,
    default: '北京',
  },
  startLng: {
    type: String,
    default: '116.379028',
  },
  startLat: {
    type: String,
    default: '39.865042',
  },
  endLng: {
    type: String,
    default: '116.427281',
  },
  endLat: {
    type: String,
    default: '39.903719',
  },
  result: {
    type: Object,
    default: null,
  },
  error: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits([
  'plan-route',
  'clear-route',
  'update:useCoordinates',
  'update:startKeyword',
  'update:startCity',
  'update:endKeyword',
  'update:endCity',
  'update:startLng',
  'update:startLat',
  'update:endLng',
  'update:endLat',
])

// 使用本地ref来存储输入值，避免直接修改props
const localUseCoordinates = ref(props.useCoordinates)
const localStartKeyword = ref(props.startKeyword)
const localStartCity = ref(props.startCity)
const localEndKeyword = ref(props.endKeyword)
const localEndCity = ref(props.endCity)
const localStartLng = ref(props.startLng)
const localStartLat = ref(props.startLat)
const localEndLng = ref(props.endLng)
const localEndLat = ref(props.endLat)

// 监听props变化，同步到本地状态
watch(
  () => props.useCoordinates,
  (newValue) => {
    localUseCoordinates.value = newValue
  },
)

watch(
  () => props.startKeyword,
  (newValue) => {
    localStartKeyword.value = newValue
  },
)

watch(
  () => props.startCity,
  (newValue) => {
    localStartCity.value = newValue
  },
)

watch(
  () => props.endKeyword,
  (newValue) => {
    localEndKeyword.value = newValue
  },
)

watch(
  () => props.endCity,
  (newValue) => {
    localEndCity.value = newValue
  },
)

watch(
  () => props.startLng,
  (newValue) => {
    localStartLng.value = newValue
  },
)

watch(
  () => props.startLat,
  (newValue) => {
    localStartLat.value = newValue
  },
)

watch(
  () => props.endLng,
  (newValue) => {
    localEndLng.value = newValue
  },
)

watch(
  () => props.endLat,
  (newValue) => {
    localEndLat.value = newValue
  },
)

// 监听本地状态变化，通知父组件更新
watch(localUseCoordinates, (newValue) => {
  emit('update:useCoordinates', newValue)
})

watch(localStartKeyword, (newValue) => {
  emit('update:startKeyword', newValue)
})

watch(localStartCity, (newValue) => {
  emit('update:startCity', newValue)
})

watch(localEndKeyword, (newValue) => {
  emit('update:endKeyword', newValue)
})

watch(localEndCity, (newValue) => {
  emit('update:endCity', newValue)
})

watch(localStartLng, (newValue) => {
  emit('update:startLng', newValue)
})

watch(localStartLat, (newValue) => {
  emit('update:startLat', newValue)
})

watch(localEndLng, (newValue) => {
  emit('update:endLng', newValue)
})

watch(localEndLat, (newValue) => {
  emit('update:endLat', newValue)
})

const handlePlanRoute = () => {
  emit('plan-route')
}

const handleClearRoute = () => {
  emit('clear-route')
}
</script>

<style scoped>
.control-panel {
  background-color: var(--background-white);
  position: absolute;
  right: 0;
  top: 0;
  height: 100%;
  z-index: 20;
  transition: transform var(--transition-normal) cubic-bezier(0.4, 0, 0.2, 1);
  width: 340px;
  box-shadow: -5px 0 15px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  border-radius: var(--border-radius-lg) 0 0 var(--border-radius-lg);
  overflow: hidden;
  transition: transform var(--transition-slow);
}

.panel-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  padding: var(--spacing-lg) var(--spacing-md);
  background: linear-gradient(135deg, var(--primary-color), var(--primary-dark));
  color: white;
  border-bottom: none;
  position: relative;
}

.panel-title::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 4px;
  background: linear-gradient(90deg, var(--primary-color), var(--primary-light));
}

.panel-content {
  padding: var(--spacing-lg);
  flex: 1;
  overflow-y: auto;
  background-color: var(--background-light);
}

.form-group {
  margin-bottom: var(--spacing-lg);
}

.radio-label {
  margin-right: var(--spacing-lg);
  cursor: pointer;
  font-size: var(--font-size-md);
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.radio-label input[type='radio'] {
  width: 16px;
  height: 16px;
  accent-color: var(--primary-color);
}

label {
  display: block;
  margin-bottom: var(--spacing-xs);
  font-weight: 500;
  color: var(--text-secondary);
}

.input-group {
  display: flex;
  gap: var(--spacing-xs);
}

.city-input {
  flex: 0 0 85px;
}

.button-group {
  display: flex;
  gap: var(--spacing-sm);
  margin: var(--spacing-lg) 0;
  flex-wrap: wrap;
}

.alert {
  padding: var(--spacing-sm);
  border-radius: var(--border-radius);
  margin-bottom: var(--spacing-md);
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  animation: fadeIn var(--transition-normal);
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.alert.error {
  background-color: var(--error-background);
  color: #b91c1c;
  border-left: 4px solid var(--error-color);
}

.info-box {
  background-color: #eff6ff;
  padding: var(--spacing-md);
  border-radius: var(--border-radius-lg);
  margin-top: var(--spacing-md);
  border: 1px solid #dbeafe;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.05);
}

.info-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--spacing-sm);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px dashed #dbeafe;
}

.info-item:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.info-label {
  font-weight: 600;
  color: #1e40af;
}

.info-value {
  color: #1e3a8a;
  font-weight: 500;
}

.panel-content::-webkit-scrollbar {
  width: 6px;
}

.panel-content::-webkit-scrollbar-track {
  background: #f3f4f6;
}

.panel-content::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 3px;
}

.panel-content::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}

@media (max-width: 1024px) {
  .control-panel.collapsed {
    transform: translateX(100%);
  }
}

@media (max-width: 768px) {
  .control-panel {
    width: calc(100% - 40px);
    right: 20px;
    border-radius: var(--border-radius-lg);
    margin-top: 10px;
    height: auto;
    position: relative;
    box-shadow: 0 -5px 15px rgba(0, 0, 0, 0.05);
  }
}
</style>
