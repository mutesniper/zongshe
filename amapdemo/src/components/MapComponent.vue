<template>
  <div class="map-container" ref="mapContainer"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

// 引入拆分的模块
import * as MapCore from './modules/MapCore.js'
import * as RoutePlanner from './modules/RoutePlanner.js'
import * as PoiSearcher from './modules/PoiSearcher.js'

// 定义组件属性
const props = defineProps({
  // 地图中心点坐标
  center: {
    type: Array,
    default: () => [116.397428, 39.90923],
  },
  // 地图缩放级别
  zoom: {
    type: Number,
    default: 13,
  },
  // 地图视图模式 (2D/3D)
  viewMode: {
    type: String,
    default: '3D',
  },
  // 3D视图的俯仰角
  pitch: {
    type: Number,
    default: 30,
  },
  // 需要加载的地图插件
  plugins: {
    type: Array,
    default: () => ['AMap.ToolBar', 'AMap.Scale', 'AMap.MapType', 'AMap.Driving'],
  },
  // 路线规划面板ID
  routePanelId: {
    type: String,
    default: 'route-panel',
  },
})

// 定义组件事件
const emit = defineEmits([
  'map-initialized',
  'map-error',
  'route-planned',
  'route-error',
  'poi-searched',
  'poi-error',
  'poi-selected',
])

// 地图容器引用
const mapContainer = ref(null)

// 地图初始化成功回调
const onMapInitialized = (mapInstance) => {
  // 初始化路线规划模块
  RoutePlanner.initRoutePlanner(MapCore.getAMap(), mapInstance)
  // 初始化POI搜索模块
  PoiSearcher.initPoiSearcher(MapCore.getAMap(), mapInstance)

  // 延迟初始化自动完成插件，确保地图已加载
  setTimeout(() => {
    initAutoComplete('poi-search-input')
  }, 1000)

  // 触发地图初始化完成事件
  emit('map-initialized', mapInstance)
}

// 地图初始化错误回调
const onMapError = (error) => {
  emit('map-error', error)
}

// 路线规划成功回调
const onRoutePlanned = (result) => {
  emit('route-planned', result)
}

// 路线规划失败回调
const onRouteError = (error) => {
  emit('route-error', error)
}

// POI搜索成功回调
const onPoiSearched = (result) => {
  emit('poi-searched', result)
}

// POI搜索失败回调
const onPoiError = (error) => {
  emit('poi-error', error)
}

// POI选择回调
const onPoiSelected = (poi) => {
  emit('poi-selected', poi)
}

// 初始化地图
const initMap = () => {
  MapCore.initMap(
    mapContainer.value,
    {
      center: props.center,
      zoom: props.zoom,
      viewMode: props.viewMode,
      pitch: props.pitch,
      plugins: props.plugins,
    },
    onMapInitialized,
    onMapError,
  )
}

// 组件挂载时初始化地图
onMounted(() => {
  initMap()
})

// 组件卸载时销毁地图
onUnmounted(() => {
  RoutePlanner.destroyRoutePlanner()
  PoiSearcher.destroyPoiSearcher()
  MapCore.destroyMap()
})

// 货车路线规划方法
const planTruckRoute = (start, end, isCoordinates = false) => {
  RoutePlanner.planTruckRoute(
    start,
    end,
    isCoordinates,
    props.routePanelId,
    onRoutePlanned,
    onRouteError,
  )
}

// 清除路线
const clearRoute = () => {
  RoutePlanner.clearRoute()
}

// 初始化输入提示插件
const initAutoComplete = (inputId, city = '全国') => {
  PoiSearcher.initAutoComplete(inputId, city, onPoiSelected)
}

// POI搜索方法
const searchPoi = (keyword, city = '北京', pageSize = 10, pageIndex = 1) => {
  PoiSearcher.searchPoi(keyword, city, pageSize, pageIndex, onPoiSearched, onPoiError)
}

// 在地图上显示POI点
const showPoi = (poi) => {
  PoiSearcher.showPoi(poi)
}

// 清除POI搜索结果
const clearPoiResults = () => {
  PoiSearcher.clearPoiResults()
}

// 暴露地图实例的方法
defineExpose({
  getMapInstance: MapCore.getMapInstance,
  // 重新设置地图中心点
  setCenter: MapCore.setCenter,
  // 重新设置地图缩放级别
  setZoom: MapCore.setZoom,
  // 货车路线规划方法
  planTruckRoute,
  // 清除路线
  clearRoute,
  // 初始化输入提示插件
  initAutoComplete,
  // POI查询方法
  searchPoi,
  // 清除POI搜索结果
  clearPoiResults,
  // 在地图上显示POI点
  showPoi,
  // 车辆标记相关方法
  createVehicleMarker: MapCore.createVehicleMarker,
  clearVehicleMarker: MapCore.clearVehicleMarker,
  getVehicleMarker: MapCore.getVehicleMarker,
})
</script>

<style scoped>
.map-container {
  width: 100%;
  height: 100%;
  min-height: 500px;
  position: relative;
  /* 移除overflow: hidden，避免遮挡标记 */
}
</style>
