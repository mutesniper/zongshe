<script setup>
import { onMounted, ref, watchEffect, onUnmounted } from "vue";
import AMapLoader from "@amap/amap-jsapi-loader";
import { AMAP_CONFIG } from "./config";

// 地图容器引用
const mapContainer = ref(null);
let map = null;
let AMap = null;

// WebSocket连接
let ws = null;

// 车辆数据存储
const vehicles = ref([]);
const connectionStatus = ref("disconnected");

// WebSocket配置
const WS_CONFIG = {
  url: "ws://192.168.1.102:8080/vehicle-simulation",
  reconnectInterval: 5000, // 重连间隔时间（毫秒）
};

// API配置
const API_CONFIG = {
  baseUrl: "http://192.168.1.102:8080",
};

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
        console.log("接收到车辆数据:", data);
        // 添加详细的单条车辆数据结构日志，用于调试
        if (Array.isArray(data) && data.length > 0) {
          console.log("车辆数据结构示例:", JSON.stringify(data[0], null, 2));
        }
        vehicles.value = Array.isArray(data) ? data : [];
        updateVehicleMarkers();

        // 如果车辆数据包含任务ID，自动加载对应路线
        if (Array.isArray(data) && data.length > 0) {
          data.forEach((vehicle) => {
            if (vehicle.taskId) {
              fetchRouteData(vehicle.taskId).then((routeData) => {
                if (routeData) {
                  drawRoute(routeData, vehicle.taskId);
                }
              });
            }
          });
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

// 车辆标记缓存
const vehicleMarkers = new Map();

// 路线数据存储
const routes = ref([]);
const routePolylines = new Map(); // 存储路线的折线对象
const routeMarkers = new Map(); // 存储路线的起点终点标记
const routeCount = ref(0); // 路线数量计数器

// 地图类型切换状态
const currentMapType = ref("standard"); // 'standard' 或 'satellite'

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
      const [lng, lat] = point.pointLocation
        .split(",")
        .map((coord) => parseFloat(coord));
      return [lng, lat];
    });

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
    const id = vehicle.id || vehicle.truckId;
    if (!id) return;

    // 尝试多种可能的坐标字段名
    let lng, lat;

    // 检查常见的坐标字段名
    // 1. 基础经纬度字段
    if (vehicle.lon !== undefined && vehicle.lat !== undefined) {
      lng = parseFloat(vehicle.lon);
      lat = parseFloat(vehicle.lat);
    }
    // 2. x/y坐标
    else if (vehicle.x !== undefined && vehicle.y !== undefined) {
      lng = parseFloat(vehicle.x);
      lat = parseFloat(vehicle.y);
    }
    // 3. 大写首字母经纬度
    else if (
      vehicle.Longitude !== undefined &&
      vehicle.Latitude !== undefined
    ) {
      lng = parseFloat(vehicle.Longitude);
      lat = parseFloat(vehicle.Latitude);
    }
    // 4. 简短大写经纬度
    else if (vehicle.Lon !== undefined && vehicle.Lat !== undefined) {
      lng = parseFloat(vehicle.Lon);
      lat = parseFloat(vehicle.Lat);
    }
    // 5. 位置对象或数组
    else if (
      vehicle.position &&
      Array.isArray(vehicle.position) &&
      vehicle.position.length >= 2
    ) {
      lng = parseFloat(vehicle.position[0]);
      lat = parseFloat(vehicle.position[1]);
    }
    // 6. 位置对象（包含lng/lat属性）
    else if (
      vehicle.position &&
      vehicle.position.lng !== undefined &&
      vehicle.position.lat !== undefined
    ) {
      lng = parseFloat(vehicle.position.lng);
      lat = parseFloat(vehicle.position.lat);
    }
    // 7. 位置对象（包含longitude/latitude属性）
    else if (
      vehicle.position &&
      vehicle.position.longitude !== undefined &&
      vehicle.position.latitude !== undefined
    ) {
      lng = parseFloat(vehicle.position.longitude);
      lat = parseFloat(vehicle.position.latitude);
    }
    // 8. location对象
    else if (
      vehicle.location &&
      Array.isArray(vehicle.location) &&
      vehicle.location.length >= 2
    ) {
      lng = parseFloat(vehicle.location[0]);
      lat = parseFloat(vehicle.location[1]);
    }
    // 9. coordinates数组
    else if (
      vehicle.coordinates &&
      Array.isArray(vehicle.coordinates) &&
      vehicle.coordinates.length >= 2
    ) {
      lng = parseFloat(vehicle.coordinates[0]);
      lat = parseFloat(vehicle.coordinates[1]);
    }
    // 10. location字符串格式 "lng,lat"
    else if (vehicle.location && typeof vehicle.location === "string") {
      const coords = vehicle.location.split(",");
      if (coords.length === 2) {
        lng = parseFloat(coords[0]);
        lat = parseFloat(coords[1]);
      }
    }
    // 11. 可能的中文字段名
    else if (vehicle.经度 !== undefined && vehicle.纬度 !== undefined) {
      lng = parseFloat(vehicle.经度);
      lat = parseFloat(vehicle.纬度);
    }
    // 12. 检查完整字段名
    else if (
      vehicle.longitude !== undefined &&
      vehicle.latitude !== undefined
    ) {
      lng = parseFloat(vehicle.longitude);
      lat = parseFloat(vehicle.latitude);
    } else {
      // 如果找不到坐标，使用默认坐标（北京市中心附近的随机位置）
      console.warn(`车辆${id}没有找到坐标信息，使用默认位置`, {
        vehicle: vehicle,
        availableFields: Object.keys(vehicle),
        locationValue: vehicle.location,
        locationType: typeof vehicle.location,
      });
      // 在北京市中心附近生成随机坐标，并为不同车辆分配不同位置，避免重叠
      const angle = (id * 36 * Math.PI) / 180; // 为10辆车分配不同角度
      lng = 116.397428 + Math.cos(angle) * 0.05;
      lat = 39.90923 + Math.sin(angle) * 0.05;
    }

    // 确保坐标有效
    lng = isNaN(lng) ? 116.397428 + (Math.random() - 0.5) * 0.1 : lng;
    lat = isNaN(lat) ? 39.90923 + (Math.random() - 0.5) * 0.1 : lat;

    const position = [lng, lat];

    // 记录成功解析的坐标信息
    console.log(`车辆${id}坐标解析成功:`, {
      originalLocation: vehicle.location,
      parsedPosition: position,
      lng: lng,
      lat: lat,
    });

    if (vehicleMarkers.has(id)) {
      // 更新现有标记位置和状态
      const marker = vehicleMarkers.get(id);
      marker.setPosition(position);

      // 更新标记标题以反映最新状态
      marker.setTitle(
        `${vehicle.plateNumber || `车辆${id}`} - 状态: ${
          vehicle.status || "未知"
        }`
      );

      // 如果需要，也可以更新图标（根据状态变化）
      let carIcon = "https://webapi.amap.com/images/car.png";
      if (vehicle.status === "运输中") {
        carIcon = "https://webapi.amap.com/images/car.png";
      } else if (vehicle.status === "空闲" || vehicle.status === "online") {
        carIcon = "https://webapi.amap.com/images/car.png";
      } else if (vehicle.status === "离线" || vehicle.status === "offline") {
        carIcon = "https://webapi.amap.com/images/car.png";
      }

      // 使用AMap.Icon对象来确保图标完整显示
      const icon = new AMap.Icon({
        size: new AMap.Size(52, 26), // 图标尺寸
        image: carIcon, // 图标图片URL
        imageSize: new AMap.Size(52, 26), // 图片显示尺寸
        imageOffset: new AMap.Pixel(0, 0), // 图片偏移
      });

      marker.setIcon(icon);
      marker.setOffset(new AMap.Pixel(-26, -13)); // 车辆图标52x26，以中心底部为原点偏移(-26, -13)
      marker.setAutoRotation(true); // 启用自动旋转
      marker.setAngle(-90); // 设置初始角度
    } else {
      // 创建新标记
      // 使用指定的车辆图标
      let carIcon = "https://webapi.amap.com/images/car.png";

      // 根据车辆状态可以调整图标（如果有的话）
      if (vehicle.status === "运输中") {
        carIcon = "https://webapi.amap.com/images/car.png"; // 运输中车辆
      } else if (vehicle.status === "空闲" || vehicle.status === "online") {
        carIcon = "https://webapi.amap.com/images/car.png"; // 空闲车辆
      } else if (vehicle.status === "离线" || vehicle.status === "offline") {
        carIcon = "https://webapi.amap.com/images/car.png"; // 离线车辆
      }

      // 使用AMap.Icon对象来确保图标完整显示
      const carIconObj = new AMap.Icon({
        size: new AMap.Size(52, 26), // 图标尺寸
        image: carIcon, // 图标图片URL
        imageSize: new AMap.Size(52, 26), // 图片显示尺寸
        imageOffset: new AMap.Pixel(0, 0), // 图片偏移
      });

      const marker = new AMap.Marker({
        map: map, // 直接指定map
        position: position,
        title: `${vehicle.plateNumber || `车辆${id}`} - 状态: ${
          vehicle.status || "未知"
        }`,
        icon: carIconObj,
        // 点标记显示位置偏移量，默认值为Pixel(-10,-34)。因为图片都是矩形的放到地图上可能位置不太对通过这个属性可以调一调位置
        offset: new AMap.Pixel(-26, -13), // 车辆图标52x26，以中心底部为原点偏移(-26, -13)
        // 是否自动旋转 点标记在使用moveAlong动画时，路径方向若有变化，点标记是否自动调整角度，默认为false。广泛用于自动调节车辆行驶方向。
        autoRotation: true,
        // 点标记的旋转角度，广泛用于改变车辆行驶方向
        // 因为图片可能方向不太对通过这个旋转一下图片，但是这个不要和autoRotation混淆了哦，这个angle是图片刚加载出来之后的旋转角度，autoRotation是在angle基础上进行旋转哦
        angle: -90,
        label: {
          content: vehicle.plateNumber || `车辆${id}`,
          direction: "top",
          offset: new AMap.Pixel(0, -10), // 调整标签位置避免遮挡
        },
        // 添加动画效果增强视觉体验
        animation: "AMAP_ANIMATION_DROP",
      });

      // 添加标记到地图
      marker.setMap(map);
      vehicleMarkers.set(id, marker);

      // 添加点击事件
      marker.on("click", () => {
        // 显示车辆信息弹窗
        try {
          const infoWindow = new AMap.InfoWindow({
            content: `<div style="padding: 10px;">
              <p><strong>车辆ID:</strong> ${id}</p>
              <p><strong>车牌号:</strong> ${vehicle.plateNumber || "未知"}</p>
              <p><strong>位置:</strong> ${position[1].toFixed(
                6
              )}, ${position[0].toFixed(6)}</p>
              <p><strong>状态:</strong> ${vehicle.status || "正常"}</p>
            </div>`,
            // 使用数字数组代替AMap.Pixel对象
            offset: [0, -30],
          });
          infoWindow.open(map, position);
        } catch (e) {
          console.error(`打开车辆${id}信息窗口失败:`, e);
        }
      });
    }
  });
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
      plugins: ["AMap.ToolBar", "AMap.Scale", "AMap.HawkEye"],
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

          // 自动加载所有路线数据
          setTimeout(() => {
            loadAllRoutes();
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
});
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
      <div class="route-count">路线数量: {{ routeCount }}/10</div>
      <button
        class="load-routes-btn"
        @click="loadAllRoutes"
        title="重新加载所有路线"
      >
        重新加载路线
      </button>
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
</style>
