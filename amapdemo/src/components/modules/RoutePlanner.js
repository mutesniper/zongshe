// 定义driving实例变量
let driving = null
// 存储AMap实例
let AMap = null
// 存储地图实例
let map = null

/**
 * 初始化路线规划模块
 * @param {Object} aMapInstance - AMap全局实例
 * @param {Object} mapInstance - 地图实例
 */
export const initRoutePlanner = (aMapInstance, mapInstance) => {
  AMap = aMapInstance
  map = mapInstance
  console.log('Route planner module initialized')
}

/**
 * 统一错误处理函数
 * @param {Object} error - 错误对象
 * @param {Function} errorCallback - 错误回调函数
 */
const handleError = (error, errorCallback) => {
  // 标准化错误对象
  const standardizedError = {
    message: error.message || '操作失败',
    code: error.code || 'UNKNOWN_ERROR',
    originalError: error
  }
  
  console.error('路线规划错误:', standardizedError)
  
  // 调用错误回调函数
  if (typeof errorCallback === 'function') {
    errorCallback(standardizedError)
  }
  
  return standardizedError
}

/**
 * 货车路线规划方法
 * @param {Object|Array} start - 起点信息或坐标
 * @param {Object|Array} end - 终点信息或坐标
 * @param {Boolean} isCoordinates - 是否使用坐标规划
 * @param {String} panelId - 路线结果面板ID
 * @param {Function} onRoutePlanned - 路线规划成功回调
 * @param {Function} onRouteError - 路线规划失败回调
 */
export const planTruckRoute = (start, end, isCoordinates = false, panelId, onRoutePlanned, onRouteError) => {
  try {
    if (!AMap) {
      throw new Error('AMap尚未加载，无法进行路线规划')
    }

    // 先清除之前的路线
    clearRoute()

    // 引入和创建驾车规划插件
    AMap.plugin(['AMap.Driving'], function () {
      try {
        // 创建货车驾车规划实例
        driving = new AMap.Driving({
          map: map,
          panel: panelId,
          policy: AMap.DrivingPolicy.TRUCK,
          // 货车参数设置
          truckInfo: {
            width: 2.5, // 货车宽度，单位：米
            height: 3.5, // 货车高度，单位：米
            length: 12, // 货车长度，单位：米
            weight: 15, // 货车重量，单位：吨
          },
        })

        // 执行路线规划
        if (isCoordinates) {
          // 使用经纬度规划
          driving.search(start, end, function (status, result) {
            handleRouteResult(status, result, onRoutePlanned, onRouteError)
          })
        } else {
          // 使用关键字规划
          const points = [
            { keyword: start.keyword, city: start.city || '北京' },
            { keyword: end.keyword, city: end.city || '北京' },
          ]
          driving.search(points, function (status, result) {
            handleRouteResult(status, result, onRoutePlanned, onRouteError)
          })
        }
      } catch (error) {
        handleError(error, onRouteError)
      }
    })
  } catch (error) {
    handleError(error, onRouteError)
  }
}

/**
 * 处理路线规划结果
 * @param {String} status - 状态码
 * @param {Object} result - 结果数据
 * @param {Function} onRoutePlanned - 路线规划成功回调
 * @param {Function} onRouteError - 路线规划失败回调
 */
const handleRouteResult = (status, result, onRoutePlanned, onRouteError) => {
  if (status === 'complete') {
    console.log('路线规划成功:', result)
    onRoutePlanned(result)
  } else if (status === 'no_data') {
    console.log('未找到路线数据')
    handleError({ message: '未找到路线数据', code: 'NO_ROUTE_DATA' }, onRouteError)
  } else {
    console.error('路线规划失败:', result)
    handleError({ message: '路线规划失败', code: 'ROUTE_PLANNING_FAILED', originalError: result }, onRouteError)
  }
}

/**
 * 清除路线
 */
export const clearRoute = () => {
  try {
    if (driving) {
      driving.clear()
      console.log('路线已清除')
    }
  } catch (error) {
    console.error('清除路线失败:', error)
  }
}

/**
 * 销毁路线规划实例
 */
export const destroyRoutePlanner = () => {
  try {
    clearRoute()
    driving = null
    console.log('Route planner instance destroyed')
  } catch (error) {
    console.error('销毁路线规划实例失败:', error)
  }
}

/**
 * 全局错误捕获机制
 * 可以在应用初始化时调用此函数来启用全局错误捕获
 */
export const setupGlobalErrorHandler = () => {
  // 捕获未处理的Promise错误
  window.addEventListener('unhandledrejection', (event) => {
    console.error('未处理的Promise拒绝:', event.reason)
    // 这里可以添加错误报告逻辑
  })

  // 捕获其他未处理的错误
  window.addEventListener('error', (event) => {
    console.error('未捕获的全局错误:', event.error)
    // 这里可以添加错误报告逻辑
  })
  
  console.log('全局错误捕获机制已设置')
}
