<template>
  <nav class="navbar">
    <div class="navbar-logo">
      <i class="fas fa-truck"></i>
      <!-- 货车图标 -->
      <span>货车调度优化与仿真系统</span>
    </div>
    <div class="navbar-menu">
      <button @click="handleDispatchVehicle" class="nav-button">车辆调度</button>
      <button @click="handleViewRoutes" class="nav-button">路线规划</button>
      <button @click="handleViewStatistics" class="nav-button">统计分析</button>
      <button @click="handleTogglePoiPanel" class="nav-button">POI查询</button>
      <button @click="handleAddVehicleMarker" class="nav-button">添加车辆标记</button>
      <button @click="handleClearVehicleMarker" class="nav-button">清除车辆标记</button>
    </div>
    <div class="navbar-toggle" @click="handleToggleMenu">
      <i class="fas fa-bars"></i>
    </div>
  </nav>
</template>

<script setup>
import { defineEmits } from 'vue'

const emit = defineEmits([
  'toggle-menu',
  'dispatch-vehicle',
  'view-routes',
  'view-statistics',
  'toggle-poi-panel',
  'add-vehicle-marker',
  'clear-vehicle-marker',
])

const handleToggleMenu = () => {
  emit('toggle-menu')
}

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
.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: var(--primary-dark);
  color: white;
  padding: var(--spacing-sm) var(--spacing-xl);
  box-shadow: var(--shadow-md);
  position: sticky;
  top: 0;
  z-index: 1000;
}

.navbar-logo {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-lg);
  font-weight: bold;
}

.navbar-menu {
  display: flex;
  gap: var(--spacing-lg);
}

.nav-button {
  background: none;
  border: none;
  color: white;
  font-size: var(--font-size-md);
  cursor: pointer;
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--border-radius);
  transition: all var(--transition-normal);
  position: relative;
  overflow: hidden;
}

.nav-button:hover {
  background-color: rgba(255, 255, 255, 0.1);
  transform: translateY(-2px);
}

.navbar-toggle {
  display: none;
  font-size: 1.5rem;
  cursor: pointer;
  transition: transform var(--transition-normal);
}

.navbar-toggle:hover {
  transform: rotate(90deg);
}

@media (max-width: 768px) {
  .navbar-menu {
    display: none;
  }

  .navbar-toggle {
    display: block;
  }
}
</style>
