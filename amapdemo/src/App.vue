<script setup>
import { ref, reactive, onMounted } from 'vue'
import MapComponent from './components/MapComponent.vue'
import AppNavbar from './components/AppNavbar.vue'
import MobileMenu from './components/MobileMenu.vue'
import PoiPanel from './components/PoiPanel.vue'
import ControlPanel from './components/ControlPanel.vue'
import PanelToggle from './components/PanelToggle.vue'
import { setupGlobalErrorHandler } from './components/modules/RoutePlanner.js'
import './assets/global.css'

// POI查询状态
const poiSearch = reactive({
  keyword: '',
  city: '北京',
  results: [],
  error: null,
  loading: false,
})

// 路线规划状态
const routePlanning = reactive({
  useCoordinates: false,
  keywordMode: {
    start: { keyword: '北京市地震局（公交站）', city: '北京' },
    end: { keyword: '亦庄文化园（地铁站）', city: '北京' },
  },
  coordinateMode: {
    start: [116.379028, 39.865042],
    end: [116.427281, 39.903719],
  },
  result: null,
  error: null,
})

// UI状态
const isMenuOpen = ref(false)
const controlPanelCollapsed = ref(false)
const isPoiPanelOpen = ref(false)

// 引用地图组件
const mapComponentRef = ref(null)

// 执行POI查询
const searchPOI = () => {
  if (!mapComponentRef.value) {
    console.error('地图组件未加载')
    return
  }

  // 重置结果和错误
  poiSearch.results = []
  poiSearch.error = null
  poiSearch.loading = true

  mapComponentRef.value.searchPoi(poiSearch.keyword, poiSearch.city)
}

// 处理POI查询结果
const handlePoiFound = (results) => {
  console.log('POI查询结果:', results)
  poiSearch.results = results
  poiSearch.loading = false
}

// 处理POI查询错误
const handlePoiError = (error) => {
  console.error('POI查询错误:', error)
  poiSearch.error = error
  poiSearch.loading = false
}

// 处理自动完成选择事件
const handlePoiSelected = (poi) => {
  console.log('Selected POI:', poi)
  // 更新POI搜索输入
  poiSearch.keyword = poi.name
  poiSearch.city = poi.city
  // 在地图上显示选中的POI
  showPoiOnMap(poi)
}

// 在地图上显示选中的POI
const showPoiOnMap = (poiItem) => {
  if (!mapComponentRef.value) {
    console.error('地图组件未加载')
    return
  }
  mapComponentRef.value.showPoi(poiItem)
}

// 地图初始化完成事件处理
const handleMapInitialized = (mapInstance) => {
  console.log('Map initialized in App.vue:', mapInstance)
  // 可以在这里进行地图实例的操作
}

// 应用初始化
onMounted(() => {
  // 设置全局错误捕获机制
  setupGlobalErrorHandler()
  console.log('应用已初始化')
})

// 切换POI查询窗口
const togglePoiPanel = () => {
  isPoiPanelOpen.value = !isPoiPanelOpen.value
}

// 模拟车辆调度功能
const dispatchVehicle = () => {
  alert('车辆调度功能将在后续实现')
}

// 添加车辆标记
const addVehicleMarker = () => {
  if (!mapComponentRef.value) {
    console.error('地图组件未加载')
    return
  }

  // 通过地图组件提供的方法创建车辆标记
  const position = [116.397428, 39.90923]
  const marker = mapComponentRef.value.createVehicleMarker(position, {
    draggable: true,
  })

  if (marker) {
    console.log('车辆标记已添加:', marker)
    alert('车辆标记已添加到地图中央')
  } else {
    console.error('创建车辆标记失败')
    alert('创建车辆标记失败，请检查地图是否已初始化')
  }
}

// 清除车辆标记
const clearVehicleMarker = () => {
  if (!mapComponentRef.value) {
    console.error('地图组件未加载')
    return
  }

  mapComponentRef.value.clearVehicleMarker()
  alert('车辆标记已清除')
}

// 查看路线规划
const viewRoutes = () => {
  // 关闭POI查询窗口
  isPoiPanelOpen.value = false
  // 展开路线规划面板
  controlPanelCollapsed.value = false
}

// 执行路线规划
const planRoute = () => {
  if (!mapComponentRef.value) {
    console.error('地图组件未加载')
    return
  }

  // 重置结果和错误
  routePlanning.result = null
  routePlanning.error = null

  if (routePlanning.useCoordinates) {
    // 使用经纬度规划
    mapComponentRef.value.planTruckRoute(
      routePlanning.coordinateMode.start,
      routePlanning.coordinateMode.end,
      true,
    )
  } else {
    // 使用关键字规划
    mapComponentRef.value.planTruckRoute(
      routePlanning.keywordMode.start,
      routePlanning.keywordMode.end,
    )
  }
}

// 清除路线
const clearRoute = () => {
  if (mapComponentRef.value) {
    mapComponentRef.value.clearRoute()
  }
  routePlanning.result = null
  routePlanning.error = null
}

// 处理路线规划结果
const handleRoutePlanned = (result) => {
  console.log('路线规划结果:', result)
  routePlanning.result = result
}

// 处理路线规划错误
const handleRouteError = (error) => {
  console.error('路线规划错误:', error)
  routePlanning.error = error
}

// 查看统计数据
const viewStatistics = () => {
  alert('统计数据功能将在后续实现')
}

// 清除POI查询结果
const clearPoiResults = () => {
  // 清空本地结果数组
  poiSearch.results = []
  // 清除地图上的POI标记
  if (mapComponentRef.value) {
    mapComponentRef.value.clearPoiResults()
  }
}
</script>

<template>
  <div class="app-container">
    <!-- 导航栏 -->
    <AppNavbar
      @toggle-menu="isMenuOpen = !isMenuOpen"
      @dispatch-vehicle="dispatchVehicle"
      @view-routes="viewRoutes"
      @view-statistics="viewStatistics"
      @toggle-poi-panel="togglePoiPanel"
      @add-vehicle-marker="addVehicleMarker"
      @clear-vehicle-marker="clearVehicleMarker"
    />

    <!-- 移动端菜单 -->
    <MobileMenu
      :isOpen="isMenuOpen"
      @dispatch-vehicle="dispatchVehicle"
      @view-routes="viewRoutes"
      @view-statistics="viewStatistics"
      @toggle-poi-panel="togglePoiPanel"
      @add-vehicle-marker="addVehicleMarker"
      @clear-vehicle-marker="clearVehicleMarker"
    />

    <!-- 主内容区 -->
    <main class="main-content">
      <div class="map-container">
        <MapComponent
          ref="mapComponentRef"
          :center="[116.397428, 39.90923]"
          :zoom="13"
          :viewMode="'3D'"
          :pitch="30"
          :plugins="[
            'AMap.ToolBar',
            'AMap.Scale',
            'AMap.MapType',
            'AMap.Driving',
            'AMap.AutoComplete',
            'AMap.PlaceSearch',
          ]"
          :route-panel-id="'route-panel'"
          @map-initialized="handleMapInitialized"
          @map-error="handleMapError"
          @route-planned="handleRoutePlanned"
          @route-error="handleRouteError"
          @poi-searched="handlePoiFound"
          @poi-error="handlePoiError"
          @poi-selected="handlePoiSelected"
        />
      </div>
      <div id="route-panel" class="route-panel"></div>

      <!-- POI查询面板 -->
      <PoiPanel
        :isOpen="isPoiPanelOpen"
        :keyword="poiSearch.keyword"
        :city="poiSearch.city"
        :results="poiSearch.results"
        :error="poiSearch.error"
        :loading="poiSearch.loading"
        @close="togglePoiPanel"
        @search="searchPOI"
        @clear-results="clearPoiResults"
        @show-poi="showPoiOnMap"
      />
    </main>

    <!-- 功能栏 -->
    <PanelToggle @toggle="controlPanelCollapsed = !controlPanelCollapsed" />
    <ControlPanel
      :isCollapsed="controlPanelCollapsed"
      v-model:useCoordinates="routePlanning.useCoordinates"
      v-model:startKeyword="routePlanning.keywordMode.start.keyword"
      v-model:startCity="routePlanning.keywordMode.start.city"
      v-model:endKeyword="routePlanning.keywordMode.end.keyword"
      v-model:endCity="routePlanning.keywordMode.end.city"
      v-model:startLng="routePlanning.coordinateMode.start[0]"
      v-model:startLat="routePlanning.coordinateMode.start[1]"
      v-model:endLng="routePlanning.coordinateMode.end[0]"
      v-model:endLat="routePlanning.coordinateMode.end[1]"
      :result="routePlanning.result"
      :error="routePlanning.error"
      @plan-route="planRoute"
      @clear-route="clearRoute"
    />
  </div>
</template>
