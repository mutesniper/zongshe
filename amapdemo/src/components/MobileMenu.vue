<template>
  <div class="mobile-menu" v-if="isOpen">
    <button @click="handleDispatchVehicle" class="mobile-button">车辆调度</button>
    <button @click="handleViewRoutes" class="mobile-button">路线规划</button>
    <button @click="handleViewStatistics" class="mobile-button">统计分析</button>
    <button @click="handleTogglePoiPanel" class="mobile-button">POI查询</button>
    <button @click="handleAddVehicleMarker" class="mobile-button">添加车辆标记</button>
    <button @click="handleClearVehicleMarker" class="mobile-button">清除车辆标记</button>
  </div>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue'

defineProps({
  isOpen: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([
  'dispatch-vehicle',
  'view-routes',
  'view-statistics',
  'toggle-poi-panel',
  'add-vehicle-marker',
  'clear-vehicle-marker'
])

const handleDispatchVehicle = () => {
  emit('dispatch-vehicle')
}

const handleViewRoutes = () => {
  emit('view-routes')
}

const handleViewStatistics = () => {
  emit('view-statistics')
}

const handleTogglePoiPanel = () => {
  emit('toggle-poi-panel')
}

const handleAddVehicleMarker = () => {
  emit('add-vehicle-marker')
}

const handleClearVehicleMarker = () => {
  emit('clear-vehicle-marker')
}
</script>

<style scoped>
.mobile-menu {
  background-color: var(--primary-dark);
  padding: var(--spacing-md);
  box-shadow: var(--shadow-md);
  animation: slideDown var(--transition-normal) ease forwards;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.mobile-button {
  display: block;
  width: 100%;
  padding: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
  background: none;
  border: none;
  color: white;
  text-align: left;
  font-size: var(--font-size-md);
  cursor: pointer;
  border-radius: var(--border-radius);
  transition: background-color var(--transition-normal);
}

.mobile-button:hover {
  background-color: rgba(255, 255, 255, 0.1);
}
</style>