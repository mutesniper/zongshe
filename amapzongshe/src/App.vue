<script setup>
import { onMounted, ref, watchEffect, onUnmounted } from "vue";
import AMapLoader from "@amap/amap-jsapi-loader";
import { AMAP_CONFIG } from "./config";
import VehicleVisualization from "./components/VehicleVisualization.vue";
import GoodsVisualization from "./components/GoodsVisualization.vue";
import CostVisualization from "./components/CostVisualization.vue";

// 地图容器引用
const mapContainer = ref(null);
let map = null;
let AMap = null;

// WebSocket连接
let ws = null;

// 车辆数据存储
const vehicles = ref([]);
const vehicleMarkers = new Map(); // 车辆标记缓存
const connectionStatus = ref("disconnected");

// 车辆动画状态管理（时间驱动）
const vehicleAnimations = new Map(); // 存储每辆车的动画状态
let animationFrameId = null; // requestAnimationFrame ID

// WebSocket配置
const WS_CONFIG = {
  url: "ws://localhost:8080/vehicle-simulation",
  reconnectInterval: 5000, // 重连间隔时间（毫秒）
};

// API配置
const API_CONFIG = {
  baseUrl: "http://localhost:8080",
};

// 当前速度倍数（响应式变量）
const currentSpeed = ref(30);

// 初始化WebSocket连接
function initWebSocket() {
  try {
    // 关闭可能存在的旧连接
    if (ws) {
      ws.close();
    }

    ws = new WebSocket(WS_CONFIG.url);

    // 连接打开
    ws.onopen = () => {
      console.log("WebSocket 连接成功");
      connectionStatus.value = "connected";
    };

    // 接收消息
    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        
        // 处理事件驱动的消息
        if (data.eventType) {
          if (data.eventType === 'SPEED_CHANGED') {
            // 处理速度变更事件：更新所有动画状态中的速度
            handleSpeedChange(data);
          } else {
            handleTruckEvent(data);
          }
        } else if (Array.isArray(data)) {
          // 批量初始化数据
          vehicles.value = data;
          updateVehicleMarkers();
          
          const activeTaskIds = new Set();
          data.forEach((vehicle) => {
            if (vehicle.taskId) {
              activeTaskIds.add(vehicle.taskId);
              if (!routePolylines.has(vehicle.taskId)) {
                fetchRouteData(vehicle.taskId).then((routeData) => {
                  if (routeData) {
                    drawRoute(routeData, vehicle.taskId);
                  }
                });
              }
            }
          });
          
          routePolylines.forEach((_, taskId) => {
            if (!activeTaskIds.has(taskId)) {
              clearRoute(taskId);
            }
          });
        } else if (typeof data === 'object' && data !== null) {
          // 兼容旧的单条更新格式
          const id = data.id || data.truckId;
          if (id) {
            const index = vehicles.value.findIndex(v => (v.id || v.truckId) === id);
            if (index !== -1) {
              vehicles.value[index] = { ...vehicles.value[index], ...data };
              updateSingleVehicleMarker(vehicles.value[index]);
            } else {
              vehicles.value.push(data);
              updateVehicleMarkers();
            }
          }
        }
      } catch (e) {
        console.error("解析车辆数据失败:", e);
      }
    };

       
    // 连接错误
    ws.onerror = (err) => {
      console.error("WebSocket 错误:", err);
      connectionStatus.value = "error";
    };

    // 连接关闭
    ws.onclose = () => {
      console.log("WebSocket 连接关闭");
      connectionStatus.value = "disconnected";
      // 尝试重新连接
      setTimeout(initWebSocket, WS_CONFIG.reconnectInterval);
    };
  } catch (e) {
    console.error("初始化WebSocket失败:", e);
    connectionStatus.value = "error";
  }
}

// 辅助函数：解析位置
function parseLocation(vehicle) {
    let lng, lat;
    if (vehicle.location && typeof vehicle.location === "string") {
      const coords = vehicle.location.split(",");
      if (coords.length === 2) {
        const val1 = parseFloat(coords[0]);
        const val2 = parseFloat(coords[1]);
        
        // 智能识别经纬度（基于中国大致范围：经度73-135，纬度4-53）
        // 如果第一个值在经度范围内且第二个值在纬度范围内 -> lng,lat
        // 如果第二个值在经度范围内且第一个值在纬度范围内 -> lat,lng
        if (val1 > 70 && val1 < 140 && val2 > 0 && val2 < 60) {
           lng = val1;
           lat = val2;
        } else if (val2 > 70 && val2 < 140 && val1 > 0 && val1 < 60) {
           lng = val2;
           lat = val1;
        } else {
           // 默认回退：假设是 lat,lng (后端存的是这个)
           lng = val2;
           lat = val1;
        }

      }
    } else if (vehicle.lon !== undefined && vehicle.lat !== undefined) {
      lng = parseFloat(vehicle.lon);
      lat = parseFloat(vehicle.lat);
    }
    if (!isNaN(lng) && !isNaN(lat)) return [lng, lat];
    return null;
}

// 处理后端推送的车辆事件（时间驱动核心逻辑）
function handleTruckEvent(event) {
  const { eventType, truckId, location, status, taskId, routePoints, totalDistance, startTime, simulationSpeed } = event;
  
  // 更新车辆列表
  const index = vehicles.value.findIndex(v => (v.id || v.truckId) === truckId);
  if (index !== -1) {
    vehicles.value[index] = { 
      ...vehicles.value[index], 
      location, 
      status, 
      taskId,
      simulationSpeed 
    };
  }
  
  switch (eventType) {
    case 'TASK_ASSIGNED':
    case 'DEPARTURE':
      // 车辆出发：初始化动画状态
      if (routePoints && routePoints.length > 0) {
        const animState = {
          truckId,
          routePoints: routePoints.map(p => parseLocation({ location: p.pointLocation })),
          totalDistance,
          startTime,
          simulationSpeed: simulationSpeed || 1,
          currentSegmentIndex: 0,
          isAnimating: true
        };
        vehicleAnimations.set(truckId, animState);
        console.log(`车辆 ${truckId} 出发，开始动画，路径点数: ${routePoints.length}`);
      }
      break;
      
    case 'PICKUP_START':
      // 车辆前往取货点：初始化动画状态
      if (routePoints && routePoints.length > 0) {
        const animState = {
          truckId,
          routePoints: routePoints.map(p => parseLocation({ location: p.pointLocation })),
          totalDistance,
          startTime,
          simulationSpeed: simulationSpeed || 1,
          currentSegmentIndex: 0,
          isAnimating: true
        };
        vehicleAnimations.set(truckId, animState);
        console.log(`车辆 ${truckId} 前往取货点，路径点数: ${routePoints.length}`);
      }
      break;
      
    case 'ARRIVED_PICKUP':
      // 到达取货点：停止动画，等待装货
      vehicleAnimations.delete(truckId);
      updateSingleVehicleMarker(vehicles.value[index]);
      console.log(`车辆 ${truckId} 已到达取货点，开始装货`);
      break;
      
    case 'POSITION_UPDATE':
      // 位置更新：后端计算的插值位置，前端直接显示
      updateSingleVehicleMarker(vehicles.value[index]);
      break;
      
    case 'ARRIVED':
      // 到达终点：停止动画
      vehicleAnimations.delete(truckId);
      updateSingleVehicleMarker(vehicles.value[index]);
      console.log(`车辆 ${truckId} 已到达`);
      break;
      
    case 'TRAFFIC_JAM':
    case 'WAITING':
    case 'UNLOADING_START':
    case 'JAM_CLEARED':
      // 状态变化：更新标记显示
      updateSingleVehicleMarker(vehicles.value[index]);
      break;
  }
}

// 处理速度变更事件
function handleSpeedChange(event) {
  const { oldSpeed, newSpeed } = event;
  console.log(`收到速度变更事件: ${oldSpeed}x -> ${newSpeed}x`);
  
  // 更新所有正在动画的车辆的速度
  vehicleAnimations.forEach((animState, truckId) => {
    if (animState.isAnimating) {
      // 计算当前已行驶的进度
      const now = Date.now();
      const elapsed = now - animState.startTime;
      const oldSpeedMs = (600 * animState.simulationSpeed * 1000) / 3600;
      const traveledDistance = oldSpeedMs * elapsed;
      const progress = Math.min(traveledDistance / (animState.totalDistance * 1000), 1.0);
      
      if (progress < 1.0) {
        // 关键修复：根据已行驶距离，倒推一个新的startTime
        // 使得在新速度下，从新的startTime开始计算，车辆会处于相同的位置
        const newSpeedMs = (600 * newSpeed * 1000) / 3600;
        const timeNeededSeconds = traveledDistance / newSpeedMs;
        const newStartTime = now - (timeNeededSeconds * 1000);
        
        // 更新动画状态
        animState.startTime = newStartTime;
        animState.simulationSpeed = newSpeed;
        
        console.log(`车辆 ${truckId} 动画速度更新: ${animState.simulationSpeed}x -> ${newSpeed}x, 已行驶 ${(progress * 100).toFixed(2)}%`);
      }
    }
  });
  
  // 同时更新currentSpeed显示
  currentSpeed.value = newSpeed;
}

// 启动全局动画循环（60fps平滑渲染）
function startAnimationLoop() {
  if (animationFrameId) return; // 防止重复启动
  
  function animate() {
    const now = Date.now();
    
    // 遍历所有正在动画的车辆
    vehicleAnimations.forEach((animState, truckId) => {
      if (!animState.isAnimating) return;
      
      const elapsed = now - animState.startTime;
      const speedMs = (600 * animState.simulationSpeed * 1000) / 3600; // 米/毫秒
      const traveledDistance = speedMs * elapsed;
      const progress = Math.min(traveledDistance / (animState.totalDistance * 1000), 1.0);
      
      // 计算插值位置
      const interpolatedPos = interpolatePosition(animState.routePoints, progress, animState.totalDistance);
      
      if (interpolatedPos) {
        // 更新车辆位置（不更新数据库，只更新前端显示）
        const index = vehicles.value.findIndex(v => (v.id || v.truckId) === truckId);
        if (index !== -1) {
          const vehicle = vehicles.value[index];
          vehicle.currentLat = interpolatedPos[1];
          vehicle.currentLon = interpolatedPos[0];
          
          // 平滑更新标记位置
          const marker = vehicleMarkers.get(truckId);
          if (marker) {
            marker.setPosition(interpolatedPos);
          }
        }
      }
      
      // 到达终点
      if (progress >= 1.0) {
        animState.isAnimating = false;
      }
    });
    
    animationFrameId = requestAnimationFrame(animate);
  }
  
  animate();
}

// 前端插值计算（基于距离）
function interpolatePosition(routePoints, progress, totalDistance) {
  if (!routePoints || routePoints.length < 2) return null;
  if (progress <= 0) return routePoints[0];
  if (progress >= 1) return routePoints[routePoints.length - 1];
  
  const targetDistance = totalDistance * progress;
  let accumulatedDistance = 0;
  
  for (let i = 1; i < routePoints.length; i++) {
    const p1 = routePoints[i - 1];
    const p2 = routePoints[i];
    
    // 计算两点间距离（简化版，实际应该用Haversine公式）
    const segmentDistance = calculateSegmentDistance(p1, p2);
    
    if (accumulatedDistance + segmentDistance >= targetDistance) {
      const segmentProgress = (targetDistance - accumulatedDistance) / segmentDistance;
      
      // 线性插值
      const lng = p1[0] + (p2[0] - p1[0]) * segmentProgress;
      const lat = p1[1] + (p2[1] - p1[1]) * segmentProgress;
      
      return [lng, lat];
    }
    
    accumulatedDistance += segmentDistance;
  }
  
  return routePoints[routePoints.length - 1];
}

// 计算两点间距离（公里）
function calculateSegmentDistance(p1, p2) {
  const R = 6371; // 地球半径（公里）
  const lat1 = p1[1] * Math.PI / 180;
  const lat2 = p2[1] * Math.PI / 180;
  const deltaLat = (p2[1] - p1[1]) * Math.PI / 180;
  const deltaLon = (p2[0] - p1[0]) * Math.PI / 180;
  
  const a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  
  return R * c;
}

// 停止动画循环
function stopAnimationLoop() {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId);
    animationFrameId = null;
  }
}


// POI数据存储
const poiMarkers = new Map();
const showPois = ref(true); // 是否显示POI
const showSidebar = ref(true); // 是否显示侧边栏
const simulationRunning = ref(true); // 仿真是否正在运行

// 控制仿真状态
async function controlSimulation(action) {
  try {
    const url = `${API_CONFIG.baseUrl}/simulation/${action}`;
    const response = await fetch(url, { method: 'POST' });
    if (response.ok) {
      const data = await response.json();
      simulationRunning.value = data.running;
      console.log(`仿真${action === 'start' ? '已开始/恢复' : '已暂停'}`);
    }
  } catch (error) {
    console.error("控制仿真失败:", error);
  }
}

// 调整仿真速度
async function adjustSimulationSpeed(speed) {
  try {
    // 确保speed是数字类型
    const speedValue = parseInt(speed);
    if (isNaN(speedValue)) {
      console.error("无效的速度值:", speed);
      return;
    }
    
    // 更新本地显示的速度
    currentSpeed.value = speedValue;
    
    const url = `${API_CONFIG.baseUrl}/simulation/speed`;
    const response = await fetch(url, { 
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ speed: speedValue })
    });
    if (response.ok) {
      const data = await response.json();
      console.log(`仿真速度已调整为${speedValue}倍`);
    } else {
      console.error(`调整仿真速度失败，状态码: ${response.status}`);
    }
  } catch (error) {
    console.error("调整仿真速度失败:", error);
  }
}

// 生成随机任务
async function generateRandomTasks() {
  try {
    const count = prompt("请输入生成任务数量（1-100）:", "10");
    if (count === null) return; // 用户取消
    
    const num = parseInt(count);
    if (isNaN(num) || num < 1 || num > 100) {
      alert("请输入1-100之间的有效数字");
      return;
    }
    
    const response = await fetch(`${API_CONFIG.baseUrl}/generate/random?count=${num}`, {
      method: 'GET',
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const result = await response.text();
    alert(result);
    console.log("生成随机任务成功:", result);
  } catch (error) {
    console.error("生成随机任务失败:", error);
    alert("生成任务失败: " + error.message);
  }
}


// 获取并绘制POI
async function loadPois() {
  try {
    const response = await fetch(`${API_CONFIG.baseUrl}/point`);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const pois = await response.json();
    console.log(`获取到 ${pois.length} 个POI点`);
    
    // 绘制POI
    drawPois(pois);
  } catch (error) {
    console.error("加载POI数据失败:", error);
  }
}

// 生成Canvas标记图标
function createCanvasMarker(type, color) {
  const canvas = document.createElement('canvas');
  canvas.width = 32;
  canvas.height = 40;
  const ctx = canvas.getContext('2d');
  
  if (!ctx) return ''; // 异常保护

  // 1. 绘制水滴形状底座
  ctx.fillStyle = color;
  ctx.beginPath();
  // 绘制一个圆头
  ctx.arc(16, 16, 16, Math.PI, 0); 
  // 绘制下方的尖角
  // 贝塞尔曲线让形状更圆润
  ctx.bezierCurveTo(32, 26, 22, 32, 16, 40);
  ctx.bezierCurveTo(10, 32, 0, 26, 0, 16);
  ctx.fill();
  
  // 2. 绘制白色圆形背景
  ctx.fillStyle = 'white';
  ctx.beginPath();
  ctx.arc(16, 16, 10, 0, Math.PI * 2);
  ctx.fill();
  
  // 3. 绘制内部文字（类型首字）
  // 选取类型的第一个字符，如果是英文则取首字母
  const text = type ? type.charAt(0) : '点';
  
  ctx.fillStyle = color; // 文字颜色与底座一致
  ctx.font = 'bold 14px "Microsoft YaHei", sans-serif'; // 加粗字体
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  // 微调文字位置，使其视觉居中（y=16是圆心，但由于字体基线原因可能需要微调）
  ctx.fillText(text, 16, 17);
  
  // 返回 PNG 格式的 Data URL
  return canvas.toDataURL('image/png');
}

// POI类型颜色映射
const poiColors = {
  '仓库': '#607D8B',      // 蓝灰
  '商场': '#FF9800',      // 橙色
  '公司企业': '#2196F3',  // 蓝色
  '购物服务': '#E91E63',  // 粉色
  '科教文化服务': '#9C27B0', // 紫色
  '商务住宅': '#795548',  // 棕色
  '生活服务': '#4CAF50',  // 绿色
  '汽车服务': '#3F51B5',  // 靛蓝
  '医疗保健服务': '#F44336', // 红色
  'default': '#009688'    // 默认青色
};

// 绘制POI点
function drawPois(pois) {
  if (!map || !AMap) return;
  
  // 清除旧的POI标记
  clearPois();
  
  // 1. 准备样式数组
  // 获取所有出现的类型，加上默认类型
  const types = Object.keys(poiColors);
  const styles = types.map(type => ({
    url: createCanvasMarker(type, poiColors[type]),
    anchor: new AMap.Pixel(16, 40), // 锚点在底部中心 (32x40尺寸)
    size: new AMap.Size(32, 40)
  }));
  
  // 创建类型到样式索引的映射
  const typeStyleIndex = {};
  types.forEach((type, index) => {
    typeStyleIndex[type] = index;
  });
  const defaultStyleIndex = typeStyleIndex['default'];

  // 2. 准备数据
  const massMarksData = pois.map(poi => {
    const coords = poi.location.split(",");
    let lng, lat;
    if (coords.length === 2) {
       lat = parseFloat(coords[0]);
       lng = parseFloat(coords[1]);
       
       if (lat > 70 && lat < 140) {
           const temp = lat;
           lat = lng;
           lng = temp;
       }
    }
        
    // 确定样式索引
    let styleIndex = typeStyleIndex[poi.type];
    if (styleIndex === undefined) {
        styleIndex = defaultStyleIndex;
    }
    
    return {
      lnglat: [lng, lat],
      name: poi.name,
      type: poi.type,
      style: styleIndex // 使用对应的样式索引
    };
  }).filter(p => !isNaN(p.lnglat[0]) && !isNaN(p.lnglat[1]));


  const massMarks = new AMap.MassMarks(massMarksData, {
    zIndex: 5,
    zooms: [3, 19],
    style: styles // 传入样式数组
  });

  massMarks.setMap(map);
  
  // 点击事件
  massMarks.on('click', function (e) {
    const marker = e.data;
    const color = poiColors[marker.type] || poiColors['default'];
    const infoWindow = new AMap.InfoWindow({
        content: `<div style="padding: 5px;">
            <b style="color: ${color}">${marker.name}</b><br>
            类型: ${marker.type}<br>
            坐标: ${marker.lnglat}
        </div>`,
        offset: new AMap.Pixel(0, -40) // 调整信息窗偏移
    });
    infoWindow.open(map, marker.lnglat);
  });
  
  // 保存引用以便清除
  poiMarkers.set('massMarks', massMarks);
  console.log(`已绘制 ${massMarksData.length} 个POI点`);
}

// 清除POI
function clearPois() {
  if (poiMarkers.has('massMarks')) {
    poiMarkers.get('massMarks').setMap(null);
    poiMarkers.delete('massMarks');
  }
}

// 切换POI显示
function togglePois() {
    showPois.value = !showPois.value;
    if (showPois.value) {
        loadPois();
    } else {
        clearPois();
    }
}

// 路线数据存储
const routes = ref([]);
const routePolylines = new Map(); // 存储路线的折线对象
const routeMarkers = new Map(); // 存储路线的起点终点标记
const routeCount = ref(0); // 路线数量计数器

// 地图类型切换状态
const currentMapType = ref("standard"); // 'standard' 或 'satellite'

// 可视化页面状态
const showVisualization = ref(false);
const showGoodsVisualization = ref(false);
const showCostVisualization = ref(false);

// 地图放大
function zoomIn() {
  if (map) {
    map.zoomIn();
  }
}

// 地图缩小
function zoomOut() {
  if (map) {
    map.zoomOut();
  }
}

// 定位到当前位置
function locateMe() {
  if (map && AMap) {
    map.plugin("AMap.Geolocation", function () {
      const geolocation = new AMap.Geolocation({
        enableHighAccuracy: true, // 高精度定位
        timeout: 10000, // 超时时间
        buttonPosition: "RB", // 定位按钮位置
        buttonOffset: new AMap.Pixel(10, 20), // 定位按钮偏移
      });

      map.addControl(geolocation);
      geolocation.getCurrentPosition();

      AMap.event.addListener(geolocation, "complete", function (data) {
        const { position } = data;
        map.setCenter([position.lng, position.lat]);
        map.setZoom(15);
      });

      AMap.event.addListener(geolocation, "error", function () {
        alert("定位失败，请检查定位权限");
      });
    });
  }
}

// 刷新车辆数据
function refreshData() {
  if (ws && connectionStatus.value === "connected") {
    // 关闭并重新连接WebSocket以获取最新数据
    initWebSocket();
    console.log("车辆数据已刷新");
  } else {
    alert("WebSocket未连接，无法刷新数据");
  }
}

// 清除所有标记
function clearMarkers() {
  if (map) {
    vehicleMarkers.forEach((marker) => {
      map.remove(marker);
    });
    vehicleMarkers.clear();
    console.log("所有车辆标记已清除");
  }
}

// 切换地图类型
function toggleMapType() {
  if (map && AMap) {
    if (currentMapType.value === "standard") {
      map.setMapStyle("amap://styles/satellite");
      currentMapType.value = "satellite";
    } else {
      map.setMapStyle("amap://styles/normal");
      currentMapType.value = "standard";
    }
    console.log("地图类型已切换为:", currentMapType.value);
  }
}

// 显示所有车辆
function fitBounds() {
  if (map && vehicleMarkers.size > 0) {
    const markers = Array.from(vehicleMarkers.values());
    map.setFitView(markers, false, [50, 50, 50, 50]); // 设置边距，避免标记显示在屏幕边缘
    console.log("地图已调整以显示所有车辆");
  } else if (vehicleMarkers.size === 0) {
    alert("当前没有车辆标记");
  }
}

// 获取路线数据
async function fetchRouteData(taskId) {
  try {
    const response = await fetch(`${API_CONFIG.baseUrl}/route/${taskId}`);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const routeData = await response.json();
    console.log(`获取到任务${taskId}的路线数据:`, routeData);
    return routeData;
  } catch (error) {
    console.error(`获取任务${taskId}路线数据失败:`, error);
    return null;
  }
}

// 获取路线颜色
function getRouteColor(taskId) {
  const colors = [
    "#FF6B6B", // 红色
    "#4ECDC4", // 青色
    "#45B7D1", // 蓝色
    "#96CEB4", // 绿色
    "#FECA57", // 黄色
    "#FF9FF3", // 粉色
    "#54A0FF", // 深蓝色
    "#48DBFB", // 天蓝色
    "#A29BFE", // 紫色
    "#FD79A8", // 玫红色
  ];
  return colors[(taskId - 2) % colors.length]; // 任务2-11对应索引0-9
}

// 绘制路线
function drawRoute(routeData, taskId) {
  if (!map || !AMap || !routeData || routeData.length === 0) return;

  // 清除之前的路线
  if (routePolylines.has(taskId)) {
    map.remove(routePolylines.get(taskId));
    routePolylines.delete(taskId);
  }

  // 提取坐标点并按序列排序
  const sortedPoints = routeData
    .sort((a, b) => a.sequence - b.sequence)
    .map((point) => {
            // 数据库中存储的是 Lat,Lon (例如 39.9,116.3)
      // 需要解析并确保格式为 [Lng, Lat]
      const parts = point.pointLocation.split(",").map((coord) => parseFloat(coord));
      let lng, lat;
      
      if (parts.length >= 2) {
          const val1 = parts[0];
          const val2 = parts[1];
          // 智能识别：经度通常在 73-135 之间，纬度在 3-54 之间
          if (val1 > 70 && val1 < 140) {
              lng = val1;
              lat = val2;
          } else {
              lng = val2;
              lat = val1;
          }
      } else {
          return null;
      }
  
      return [lng, lat];
        })
    .filter(p => p !== null);

  if (sortedPoints.length < 2) {
    console.warn(`任务${taskId}的路线点数不足，无法绘制路线`);
    return;
  }

  // 获取路线颜色
  const routeColor = getRouteColor(taskId);

  // 创建路线折线
  const polyline = new AMap.Polyline({
    path: sortedPoints,
    strokeColor: routeColor, // 不同任务使用不同颜色
    strokeWeight: 5, // 线条宽度
    strokeOpacity: 0.8, // 线条透明度
    strokeStyle: "solid", // 线条样式
    lineJoin: "round", // 折线拐点连接处样式
    lineCap: "round", // 折线两端线帽样式
    zIndex: 50, // 显示层级
    showDir: true, // 显示方向
  });

  // 添加到地图
  polyline.setMap(map);
  routePolylines.set(taskId, polyline);

  // 添加起点和终点标记
  const startMarker = new AMap.Marker({
    position: sortedPoints[0],
    map: map,
    icon: new AMap.Icon({
      size: new AMap.Size(25, 35),
      image: "https://webapi.amap.com/theme/v1.3/markers/n/start.png",
      imageSize: new AMap.Size(25, 35),
      imageOffset: new AMap.Pixel(0, 0),
    }),
    offset: new AMap.Pixel(-12, -35), // 25x35图标，以底部中心为锚点
    title: `任务${taskId}起点`,
    zIndex: 100,
  });

  const endMarker = new AMap.Marker({
    position: sortedPoints[sortedPoints.length - 1],
    map: map,
    icon: new AMap.Icon({
      size: new AMap.Size(25, 35),
      image: "https://webapi.amap.com/theme/v1.3/markers/n/end.png",
      imageSize: new AMap.Size(25, 35),
      imageOffset: new AMap.Pixel(0, 0),
    }),
    offset: new AMap.Pixel(-12, -35), // 25x35图标，以底部中心为锚点
    title: `任务${taskId}终点`,
    zIndex: 100,
  });

  // 将起点终点标记存储到单独的Map中
  routeMarkers.set(`${taskId}_start`, startMarker);
  routeMarkers.set(`${taskId}_end`, endMarker);

  // 更新路线计数
  routeCount.value = routePolylines.size;

  console.log(
    `任务${taskId}路线绘制完成，共${sortedPoints.length}个路径点，颜色: ${routeColor}`
  );
}

// 清除指定任务的路线
function clearRoute(taskId) {
  // 清除路线折线
  if (routePolylines.has(taskId)) {
    map.remove(routePolylines.get(taskId));
    routePolylines.delete(taskId);
  }

  // 清除起点标记
  if (routeMarkers.has(`${taskId}_start`)) {
    map.remove(routeMarkers.get(`${taskId}_start`));
    routeMarkers.delete(`${taskId}_start`);
  }

  // 清除终点标记
  if (routeMarkers.has(`${taskId}_end`)) {
    map.remove(routeMarkers.get(`${taskId}_end`));
    routeMarkers.delete(`${taskId}_end`);
  }

  // 更新路线计数
  routeCount.value = routePolylines.size;

  console.log(`已清除任务${taskId}的路线及标记`);
}

// 清除所有路线
function clearAllRoutes() {
  // 清除所有路线折线
  routePolylines.forEach((polyline, taskId) => {
    map.remove(polyline);
  });
  routePolylines.clear();

  // 清除所有起点终点标记
  routeMarkers.forEach((marker) => {
    map.remove(marker);
  });
  routeMarkers.clear();

  // 重置路线计数
  routeCount.value = 0;

  console.log("已清除所有路线及标记");
}

// 更新车辆标记
function updateVehicleMarkers() {
  if (!map || !AMap) return;

  // 清除不再存在的车辆标记
  const currentVehicleIds = new Set(
    vehicles.value.map((v) => v.id || v.truckId)
  );
  const markersToRemove = [];

  vehicleMarkers.forEach((marker, id) => {
    if (!currentVehicleIds.has(id)) {
      markersToRemove.push(id);
      map.remove(marker);
    }
  });

  markersToRemove.forEach((id) => vehicleMarkers.delete(id));

  // 更新或添加车辆标记
  vehicles.value.forEach((vehicle) => {
    updateSingleVehicleMarker(vehicle);
  });
}

// 更新单个车辆标记
function updateSingleVehicleMarker(vehicle) {
  const id = vehicle.id || vehicle.truckId;
  if (!id) return;

  // 解析坐标
  let lng, lat;
  const parsed = parseLocation(vehicle);
  if (parsed) {
    lng = parsed[0];
    lat = parsed[1];
  } else {
    // 兼容其他字段
    if (vehicle.x !== undefined && vehicle.y !== undefined) { lng = parseFloat(vehicle.x); lat = parseFloat(vehicle.y); }
    else if (vehicle.longitude !== undefined && vehicle.latitude !== undefined) { lng = parseFloat(vehicle.longitude); lat = parseFloat(vehicle.latitude); }
    else {
        // 默认坐标
        const angle = (id * 36 * Math.PI) / 180;
        lng = 116.397428 + Math.cos(angle) * 0.05;
        lat = 39.90923 + Math.sin(angle) * 0.05;
    }
  }
  
  // 确保坐标有效
  if (isNaN(lng) || isNaN(lat)) {
      lng = 116.397428; lat = 39.90923;
  }
  
  const position = [lng, lat];

  // 确定图标和状态
  let carIcon = "https://webapi.amap.com/images/car.png";
  let isCar = true;

  if (vehicle.status === "装货") {
    carIcon = "https://a.amap.com/jsapi_demos/static/demo-center/icons/poi-marker-red.png";
    isCar = false;
  } else if (vehicle.status === "卸货") {
    carIcon = "https://a.amap.com/jsapi_demos/static/demo-center/icons/poi-marker-default.png";
    isCar = false;
  } else if (vehicle.status === "故障") {
    carIcon = "https://webapi.amap.com/theme/v1.3/markers/n/mark_r.png";
    isCar = false;
  }
  // "拥堵"、"运输中"、"空闲" 使用默认车辆图标

  // 准备图标内容
  let content = "";
  let offset;

  if (isCar) {
    // 车辆图标：旋转90度使其朝右（适配autoRotation）
    content = `<img src="${carIcon}" style="width: 52px; height: 26px; transform: rotate(90deg); display: block;">`;
    offset = new AMap.Pixel(-26, -13);
  } else {
    // 普通图标：不旋转，底部中心对齐
    content = `<img src="${carIcon}" style="width: 25px; height: 34px; display: block;">`;
    offset = new AMap.Pixel(-12, -34);
  }

  // 更新或创建标记
  if (vehicleMarkers.has(id)) {
    const marker = vehicleMarkers.get(id);
    
    // 平滑移动
    const currentPos = marker.getPosition();
    if (Math.abs(currentPos.lng - lng) > 0.000001 || Math.abs(currentPos.lat - lat) > 0.000001) {
      marker.moveTo(position, {
        duration: 5000,
        autoRotation: isCar,
      });
    }
    
    // 更新样式和标题
    marker.setContent(content);
    marker.setOffset(offset);
    marker.setTitle(`${vehicle.plateNumber || `车辆${id}`} - 状态: ${vehicle.status || "未知"}`);
    
  } else {
    // 创建新标记
    const marker = new AMap.Marker({
      map: map,
      position: position,
      title: `${vehicle.plateNumber || `车辆${id}`} - 状态: ${vehicle.status || "未知"}`,
      content: content,
      offset: offset,
      autoRotation: isCar,
      label: {
        content: vehicle.plateNumber || `车辆${id}`,
        direction: "top",
        offset: new AMap.Pixel(0, -10),
      },
      animation: "AMAP_ANIMATION_DROP",
    });
    
    vehicleMarkers.set(id, marker);
    
    // 点击事件
    marker.on("click", () => {
      const infoWindow = new AMap.InfoWindow({
        content: `<div style="padding: 10px;">
          <p><strong>车辆ID:</strong> ${id}</p>
          <p><strong>车牌号:</strong> ${vehicle.plateNumber || "未知"}</p>
          <p><strong>位置:</strong> ${lat.toFixed(6)}, ${lng.toFixed(6)}</p>
          <p><strong>状态:</strong> ${vehicle.status || "正常"}</p>
        </div>`,
        offset: [0, -30],
      });
      infoWindow.open(map, position);
    });
  }
}

// 初始化地图
onMounted(() => {
  // 确保容器存在且有尺寸
  if (mapContainer.value) {
    // 设置一些基本样式确保容器有明确尺寸
    mapContainer.value.style.width = "100%";
    mapContainer.value.style.height = "100vh";

    // 设置安全密钥（高德地图需要）
    window._AMapSecurityConfig = {
      securityJsCode: AMAP_CONFIG.key, // 注意：实际使用时，安全密钥可能需要单独配置
    };

    AMapLoader.load({
      key: AMAP_CONFIG.key,
      version: AMAP_CONFIG.version,
      plugins: ["AMap.ToolBar", "AMap.Scale", "AMap.HawkEye", "AMap.MoveAnimation"],
      AMapUI: {
        version: "1.1",
        plugins: [],
      },
      Loca: {
        version: "2.0",
      },
    })
      .then((_AMap) => {
        // 使用try-catch来捕获可能的初始化错误
        try {
          AMap = _AMap;
          // 创建地图实例
          map = new AMap.Map(mapContainer.value, {
            viewMode: "3D",
            zoom: 11,
            center: [116.397428, 39.90923], // 北京市中心坐标
            resizeEnable: true,
          });

          console.log("高德地图初始化成功");

          // 添加一些地图控件
          map.addControl(new AMap.ToolBar());
          map.addControl(new AMap.Scale());
          map.addControl(new AMap.HawkEye());

          // 地图初始化成功后，建立WebSocket连接
          initWebSocket();
          
          // 启动动画循环（60fps平滑渲染）
          startAnimationLoop();

          // 自动加载所有路线数据
          setTimeout(() => {
            loadAllRoutes();
            loadPois(); // 自动加载POI
          }, 1000); // 延迟1秒确保地图完全初始化
        } catch (err) {
          console.error("地图实例创建失败:", err);
        }
      })
      .catch((e) => {
        console.error("高德地图API加载失败:", e);
      });
  } else {
    console.error("地图容器不存在");
  }
});

// 监听窗口大小变化，调整地图大小
watchEffect(() => {
  window.addEventListener("resize", () => {
    if (map) {
      map.resize();
    }
  });
});

// 加载所有路线数据（任务2到11）
async function loadAllRoutes() {
  console.log("开始加载所有路线数据（任务2-11）...");

  const routePromises = [];
  for (let taskId = 2; taskId <= 11; taskId++) {
    routePromises.push(
      fetchRouteData(taskId).then((routeData) => {
        if (routeData) {
          drawRoute(routeData, taskId);
          return { taskId, success: true, pointCount: routeData.length };
        } else {
          return { taskId, success: false, pointCount: 0 };
        }
      })
    );
  }

  try {
    const results = await Promise.all(routePromises);
    const successCount = results.filter((r) => r.success).length;
    const totalPoints = results.reduce((sum, r) => sum + r.pointCount, 0);

    console.log(
      `路线加载完成！成功加载 ${successCount}/10 条路线，总计 ${totalPoints} 个路径点`
    );
    console.log("详细结果:", results);
  } catch (error) {
    console.error("批量加载路线时发生错误:", error);
  }
}

// 测试加载路线数据（可以在控制台调用）
window.loadTestRoute = async function (taskId = 2) {
  console.log(`开始加载任务${taskId}的路线数据...`);
  const routeData = await fetchRouteData(taskId);
  if (routeData) {
    drawRoute(routeData, taskId);
  }
};

// 清除所有路线（可以在控制台调用）
window.clearAllRoutes = clearAllRoutes;

// 组件卸载时清理资源
onUnmounted(() => {
  // 停止动画循环
  stopAnimationLoop();
  
  if (ws) {
    ws.close();
    ws = null;
  }

  if (map && vehicleMarkers.size > 0) {
    vehicleMarkers.forEach((marker) => {
      map.remove(marker);
    });
    vehicleMarkers.clear();
  }

  // 清除所有路线
  clearAllRoutes();
    
  // 清除POI
  clearPois();
  
  // 清除动画状态
  vehicleAnimations.clear();
});

// 格式化位置显示
function formatLocation(loc) {
    if (!loc) return '-';
    if (typeof loc === 'string') {
        const parts = loc.split(',');
        if (parts.length >= 2) {
            return `${parseFloat(parts[1]).toFixed(4)}, ${parseFloat(parts[0]).toFixed(4)}`;
        }
        return loc;
    }
    return '-';
}

// 获取状态样式类
function getStatusClass(status) {
    if (status === '运输中') return 'status-transit';
    if (status === '装货') return 'status-loading';
    if (status === '卸货') return 'status-unloading';
    if (status === '空闲') return 'status-idle';
    if (status === '故障' || status === '拥堵') return 'status-warning';
    return '';
}

// 聚焦车辆
function focusVehicle(vehicle) {
    const id = vehicle.id || vehicle.truckId;
    if (vehicleMarkers.has(id)) {
        const marker = vehicleMarkers.get(id);
        const pos = marker.getPosition();
        map.setZoomAndCenter(17, pos);
        // 触发点击事件显示信息窗
        marker.emit('click', { target: marker });
    }
}
</script>

<template>
  <div class="map-container">
    <div id="map-container" ref="mapContainer"></div>
    <div class="status-panel">
      <div class="connection-status" :class="connectionStatus">
        WebSocket 连接状态:
        {{
          connectionStatus === "connected"
            ? "已连接"
            : connectionStatus === "error"
            ? "错误"
            : "断开"
        }}
      </div>
      <div class="vehicle-count">车辆数量: {{ vehicles.length }}</div>
      <div class="route-count">路线数量: {{ routeCount }}</div>
      <button
        class="load-routes-btn"
        @click="loadAllRoutes"
        title="重新加载所有路线"
      >
        重新加载路线
      </button>
      <button
        class="load-routes-btn"
        @click="togglePois"
        style="margin-left: 10px; background-color: #10b981;"
        title="显示/隐藏POI"
      >
        {{ showPois ? '隐藏POI' : '显示POI' }}
      </button>
            <button
        class="load-routes-btn"
        @click="showSidebar = !showSidebar"
        style="margin-left: 10px; background-color: #f59e0b;"
        title="显示/隐藏车辆列表"
      >
        {{ showSidebar ? '隐藏列表' : '车辆列表' }}
      </button>

      <!-- 仿真控制按钮组 -->
      <div class="control-group">
        <button
          class="control-btn btn-start"
          @click="controlSimulation('start')"
          :disabled="simulationRunning"
          title="开始/恢复仿真"
        >
          ▶ 开始
        </button>
        <button
          class="control-btn btn-pause"
          @click="controlSimulation('pause')"
          :disabled="!simulationRunning"
          title="暂停仿真"
        >
          ⏸ 暂停
        </button>
      </div>
      
      <!-- 速度调节控件 -->
      <div class="speed-control">
        <label for="speed-slider">速度倍数:</label>
        <input 
          type="range" 
          id="speed-slider" 
          min="1" 
          max="100" 
          v-model.number="currentSpeed"
          step="1"
          @input="adjustSimulationSpeed(currentSpeed)"
        >
        <span id="speed-value">{{ currentSpeed }}</span>
      </div>

      <!-- 生成任务按钮 -->
      <div class="generate-tasks">
        <button 
          class="load-routes-btn"
          style="background-color: #8b5cf6; margin-top: 10px;"
          @click="generateRandomTasks"
        >
          🎲 生成任务
        </button>
      </div>
      
      <!-- 可视化按钮 -->
      <div class="visualization-btn">
        <button 
          class="load-routes-btn"
          style="background-color: #3b82f6; margin-top: 10px;"
          @click="showVisualization = true"
        >
          📊 车辆分析
        </button>
      </div>
      
      <!-- 货物分析按钮 -->
      <div class="goods-visualization-btn">
        <button 
          class="load-routes-btn"
          style="background-color: #10b981; margin-top: 10px;"
          @click="showGoodsVisualization = true"
        >
          📦 货物分析
        </button>
      </div>
      
      <!-- 成本分析按钮 -->
      <div class="cost-visualization-btn">
        <button 
          class="load-routes-btn"
          style="background-color: #f59e0b; margin-top: 10px;"
          @click="showCostVisualization = true"
        >
          💰 成本分析
        </button>
      </div>
    </div>

    <!-- 车辆信息侧边栏 -->
    <div class="vehicle-sidebar" :class="{ 'sidebar-hidden': !showSidebar }">
      <div class="sidebar-header">
        <h3>车辆监控列表</h3>
        <span class="close-btn" @click="showSidebar = false">×</span>
      </div>
      <div class="sidebar-content">
        <div v-if="vehicles.length === 0" class="no-data">
          暂无在线车辆
        </div>
        <div 
          v-else 
          v-for="vehicle in vehicles" 
          :key="vehicle.id || vehicle.truckId"
          class="vehicle-card"
          @click="focusVehicle(vehicle)"
        >
          <div class="vehicle-header">
            <span class="plate-number">{{ vehicle.plateNumber || `车辆${vehicle.id || vehicle.truckId}` }}</span>
            <span class="status-badge" :class="getStatusClass(vehicle.status)">{{ vehicle.status || '未知' }}</span>
          </div>
          <div class="vehicle-detail">
            <div class="detail-item">
              <span class="label">类型:</span>
              <span class="value">{{ vehicle.type || '普通货车' }}</span>
            </div>
            <div class="detail-item">
              <span class="label">任务:</span>
              <span class="value">{{ vehicle.taskId ? `任务#${vehicle.taskId}` : '无任务' }}</span>
            </div>
             <div class="detail-item" v-if="vehicle.location">
              <span class="label">位置:</span>
              <span class="value">{{ formatLocation(vehicle.location) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  
  <!-- 车辆分析可视化模态窗口 -->
  <div v-if="showVisualization" class="visualization-modal">
    <div class="modal-content">
      <div class="modal-header">
        <h2>车辆分析可视化</h2>
        <button class="close-btn" @click="showVisualization = false">×</button>
      </div>
      <div class="modal-body">
        <VehicleVisualization />
      </div>
    </div>
  </div>
  
  <!-- 货物分析可视化模态窗口 -->
  <div v-if="showGoodsVisualization" class="visualization-modal">
    <div class="modal-content">
      <div class="modal-header">
        <h2>货物分析可视化</h2>
        <button class="close-btn" @click="showGoodsVisualization = false">×</button>
      </div>
      <div class="modal-body">
        <GoodsVisualization />
      </div>
    </div>
  </div>
  
  <!-- 成本分析可视化模态窗口 -->
  <div v-if="showCostVisualization" class="visualization-modal">
    <div class="modal-content">
      <div class="modal-header">
        <h2>成本分析可视化</h2>
        <button class="close-btn" @click="showCostVisualization = false">×</button>
      </div>
      <div class="modal-body">
        <CostVisualization />
      </div>
    </div>
  </div>
</template>

<style scoped>
.map-container {
  width: 100%;
  height: 100vh;
  position: relative;
  overflow: hidden;
}

#map-container {
  width: 100%;
  height: 100%;
  min-width: 300px;
  min-height: 300px;
}

.status-panel {
  position: absolute;
  top: 20px;
  left: 20px;
  background: rgba(255, 255, 255, 0.9);
  padding: 10px 15px;
  border-radius: 5px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  font-size: 14px;
  line-height: 1.5;
}

.connection-status {
  font-weight: bold;
  margin-bottom: 5px;
}

.connection-status.connected {
  color: #10b981;
}

.connection-status.error {
  color: #ef4444;
}

.connection-status.disconnected {
  color: #f59e0b;
}

.vehicle-count, .route-count {
  margin: 5px 0;
  font-weight: bold;
}

.load-routes-btn {
  margin-top: 10px;
  padding: 5px 10px;
  background-color: #3b82f6;
  color: white;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  font-size: 14px;
}

.load-routes-btn:hover {
  background-color: #2563eb;
}

.control-group {
  margin-top: 10px;
  display: flex;
  gap: 10px;
}

.control-btn {
  padding: 5px 15px;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  font-size: 14px;
  font-weight: bold;
}

.control-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-start {
  background-color: #10b981;
  color: white;
}

.btn-start:hover {
  background-color: #059669;
}

.btn-pause {
  background-color: #f59e0b;
  color: white;
}

.btn-pause:hover {
  background-color: #d97706;
}

.speed-control {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.speed-control label {
  font-size: 14px;
  font-weight: bold;
}

.speed-control input[type="range"] {
  flex: 1;
  min-width: 150px;
}

.speed-control span {
  font-size: 14px;
  font-weight: bold;
  color: #3b82f6;
}

.connection-status.disconnected {
  color: #f59e0b;
}

.connection-status.error {
  color: #ef4444;
}

.vehicle-count {
  color: #374151;
}

.route-count {
  color: #374151;
  margin-top: 5px;
}

.load-routes-btn {
  margin-top: 10px;
  padding: 6px 12px;
  background: #3366ff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: background-color 0.2s;
}

.load-routes-btn:hover {
  background: #2851e0;
}

.load-routes-btn:active {
  background: #1e40af;
}

/* 仿真控制按钮组 */
.control-group {
  margin-top: 15px;
  display: flex;
  gap: 10px;
  border-top: 1px solid #eee;
  padding-top: 10px;
}

.control-btn {
  flex: 1;
  padding: 6px 12px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: white;
  transition: all 0.2s;
}

.control-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-start {
  background-color: #3b82f6;
}

.btn-start:hover:not(:disabled) {
  background-color: #2563eb;
}

.btn-pause {
  background-color: #ef4444;
}

.btn-pause:hover:not(:disabled) {
  background-color: #dc2626;
}

/* 侧边栏样式 */
.vehicle-sidebar {
  position: absolute;
  top: 0;
  right: 0;
  width: 300px;
  height: 100%;
  background: white;
  box-shadow: -2px 0 10px rgba(0,0,0,0.1);
  z-index: 1001;
  transition: transform 0.3s ease;
  display: flex;
  flex-direction: column;
}

.sidebar-hidden {
  transform: translateX(100%);
}

.sidebar-header {
  padding: 15px;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #f8f9fa;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 16px;
  color: #333;
}

.close-btn {
  cursor: pointer;
  font-size: 20px;
  color: #999;
}

.sidebar-content {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.no-data {
  text-align: center;
  color: #999;
  padding: 20px;
}

.vehicle-card {
  background: white;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}

.vehicle-card:hover {
  box-shadow: 0 4px 6px rgba(0,0,0,0.1);
  border-color: #3366ff;
}

.vehicle-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.plate-number {
  font-weight: bold;
  font-size: 15px;
  color: #333;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  background: #eee;
  color: #666;
}

.status-transit { background: #e3f2fd; color: #1976d2; }
.status-loading { background: #ffebee; color: #d32f2f; }
.status-unloading { background: #e8f5e9; color: #388e3c; }
.status-idle { background: #f5f5f5; color: #757575; }
.status-warning { background: #fff3e0; color: #f57c00; }

.vehicle-detail {
  font-size: 12px;
  color: #666;
}

.detail-item {
  display: flex;
  margin-bottom: 4px;
}

.detail-item .label {
  width: 40px;
  color: #999;
}

.detail-item .value {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

<style>
/* 全局样式重置 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

#app {
  width: 100%;
  height: 100%;
}

/* 可视化模态窗口 */
.visualization-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000;
}

.modal-content {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  width: 90%;
  max-width: 1400px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #e0e0e0;
  background: #f8f9fa;
}

.modal-header h2 {
  margin: 0;
  font-size: 20px;
  color: #333;
}

.modal-header .close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s;
}

.modal-header .close-btn:hover {
  background: #e9ecef;
  color: #333;
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

@media (max-width: 768px) {
  .modal-content {
    width: 95%;
    height: 95vh;
    max-height: 95vh;
  }
}
</style>
