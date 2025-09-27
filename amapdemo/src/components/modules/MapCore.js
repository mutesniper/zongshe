import { ref } from 'vue'

// 地图实例
let map = null
// 存储AMap实例的全局变量
let AMap = null
// 地图初始化状态
const mapInitState = ref('initializing')
// 存储车辆标记实例
let vehicleMarker = null

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
  
  console.error('地图核心错误:', standardizedError)
  
  // 调用错误回调函数
  if (typeof errorCallback === 'function') {
    errorCallback(standardizedError)
  }
  
  return standardizedError
}

/**
 * 初始化地图
 * @param {HTMLElement} container - 地图容器
 * @param {Object} options - 地图配置选项
 * @param {Function} onInitialized - 地图初始化完成回调
 * @param {Function} onError - 地图初始化错误回调
 */
export const initMap = (container, options, onInitialized, onError) => {
  try {
    console.log('Initializing map core')

    // 调用Loader加载地图API
    window
      .initMapLoader()
      .then((aMapInstance) => {
        try {
          console.log('AMap loaded successfully in core module')
          // 存储AMap实例到全局变量
          AMap = aMapInstance
          mapInitState.value = 'loading'

          // 初始化基础地图
          map = new AMap.Map(container, {
            center: options.center,
            zoom: options.zoom,
            viewMode: options.viewMode,
            pitch: options.pitch,
            // 优化地图渲染性能
            animateEnable: true,
            dragEnable: true,
            zoomEnable: true,
            resizeEnable: true
          })

          // 为了让地图正常显示，在开发环境中设置跨域代理
          if (import.meta.env.DEV) {
            AMap.Util.corsEnabled = true
          }

          // 先触发地图初始化完成事件，让界面快速响应
          onInitialized(map)
          mapInitState.value = 'success'
          
          // 然后异步加载地图控件，避免阻塞主线程
          loadMapPlugins(options.plugins).then(() => {
            console.log('All map plugins loaded successfully')
          }).catch(error => {
            handleError({ message: '加载地图插件失败', code: 'LOAD_PLUGINS_FAILED', originalError: error }, onError)
          })
        } catch (error) {
          handleError({ message: '地图初始化失败', code: 'MAP_INIT_FAILED', originalError: error }, onError)
          mapInitState.value = 'failed'
        }
      })
      .catch((error) => {
        handleError({ message: 'AMap加载失败', code: 'AMAP_LOAD_FAILED', originalError: error }, onError)
        mapInitState.value = 'failed'
      })
  } catch (error) {
    handleError({ message: '地图初始化过程出错', code: 'INIT_PROCESS_ERROR', originalError: error }, onError)
    mapInitState.value = 'failed'
  }
}

/**
 * 异步加载地图插件
 * @param {Array} plugins - 需要加载的插件列表
 * @returns {Promise} 加载完成的Promise
 */
const loadMapPlugins = (plugins) => {
  return new Promise((resolve, reject) => {
    try {
      // 核心控件插件优先加载
      const corePlugins = plugins.filter(plugin => 
        ['AMap.ToolBar', 'AMap.Scale', 'AMap.MapType'].includes(plugin)
      )
      
      // 非核心插件后续加载
      const otherPlugins = plugins.filter(plugin => 
        !['AMap.ToolBar', 'AMap.Scale', 'AMap.MapType'].includes(plugin)
      )
      
      // 先加载核心插件
      loadPluginGroup(corePlugins).then(() => {
        // 延迟加载其他插件，避免阻塞主线程
        setTimeout(() => {
          loadPluginGroup(otherPlugins).then(resolve).catch(reject)
        }, 100)
      }).catch(reject)
    } catch (error) {
      reject(error)
    }
  })
}

/**
 * 加载一组插件
 * @param {Array} pluginGroup - 插件组
 * @returns {Promise} 加载完成的Promise
 */
const loadPluginGroup = (pluginGroup) => {
  return Promise.all(
    pluginGroup.map(plugin => {
      return new Promise((resolve, reject) => {
        try {
          if (!AMap || !map) {
            reject(new Error('地图未初始化'))
            return
          }
          
          // 检查插件是否已经加载
          if (plugin === 'AMap.ToolBar' && AMap.ToolBar) {
            map.addControl(
              new AMap.ToolBar({
                position: 'RB', // 右下角
              }),
            )
            resolve()
            return
          }
          
          if (plugin === 'AMap.Scale' && AMap.Scale) {
            map.addControl(
              new AMap.Scale({
                position: 'RB', // 右下角
              }),
            )
            resolve()
            return
          }
          
          if (plugin === 'AMap.MapType' && AMap.MapType) {
            map.addControl(
              new AMap.MapType({
                position: 'RB', // 右下角
                defaultType: 0, // 0: 矢量地图 1: 卫星地图
              }),
            )
            resolve()
            return
          }
          
          // 如果插件未加载，则动态加载
          AMap.plugin(plugin, () => {
            try {
              if (plugin === 'AMap.ToolBar') {
                map.addControl(
                  new AMap.ToolBar({
                    position: 'RB', // 右下角
                  }),
                )
              } else if (plugin === 'AMap.Scale') {
                map.addControl(
                  new AMap.Scale({
                    position: 'RB', // 右下角
                  }),
                )
              } else if (plugin === 'AMap.MapType') {
                map.addControl(
                  new AMap.MapType({
                    position: 'RB', // 右下角
                    defaultType: 0, // 0: 矢量地图 1: 卫星地图
                  }),
                )
              }
              console.log(`Plugin ${plugin} loaded successfully`)
              resolve()
            } catch (error) {
              console.error(`Failed to load plugin ${plugin}:`, error)
              resolve() // 单个插件加载失败不影响整体
            }
          })
        } catch (error) {
          console.error(`Error loading plugin ${plugin}:`, error)
          resolve() // 单个插件加载失败不影响整体
        }
      })
    })
  )
}

/**
 * 获取地图实例
 * @returns {Object} 地图实例
 */
export const getMapInstance = () => {
  return map
}

/**
 * 设置地图中心点
 * @param {Array} center - 中心点坐标 [lng, lat]
 */
export const setCenter = (center) => {
  try {
    if (map) {
      map.setCenter(center)
    }
  } catch (error) {
    console.error('设置地图中心点失败:', error)
  }
}

/**
 * 设置地图缩放级别
 * @param {Number} zoom - 缩放级别
 */
export const setZoom = (zoom) => {
  try {
    if (map) {
      map.setZoom(zoom)
    }
  } catch (error) {
    console.error('设置地图缩放级别失败:', error)
  }
}

/**
 * 销毁地图实例
 */
export const destroyMap = () => {
  try {
    if (map) {
      map.destroy()
      map = null
      console.log('地图实例已销毁')
    }
  } catch (error) {
    console.error('销毁地图实例失败:', error)
  }
}

/**
 * 获取AMap全局实例
 * @returns {Object} AMap全局实例
 */
export const getAMap = () => {
  return AMap
}

/**
 * 获取地图初始化状态
 * @returns {String} 初始化状态
 */
export const getMapInitState = () => {
  return mapInitState.value
}

/**
 * 创建或更新车辆标记
 * @param {Array} position - 车辆位置坐标 [lng, lat]
 * @param {Object} options - 标记配置选项
 * @returns {Object} 车辆标记实例
 */
export const createVehicleMarker = (position, options = {}) => {
  try {
    if (!AMap || !map) {
      throw new Error('地图未初始化，无法创建车辆标记')
    }

    // 如果标记已存在，更新位置
    if (vehicleMarker) {
      try {
        vehicleMarker.setPosition(position)
        // 更新其他属性
        if (options.rotation !== undefined) {
          if (vehicleMarker.setRotation) {
            vehicleMarker.setRotation(options.rotation)
          } else {
            console.warn('当前高德地图API版本不支持setRotation方法')
          }
        }
        if (options.icon) {
          vehicleMarker.setIcon(options.icon)
        }
      } catch (error) {
        console.error('更新车辆标记属性失败:', error)
      }
      return vehicleMarker
    }

    // 创建新的车辆标记
    // 使用高德地图默认图标
    const defaultIcon = 'https://webapi.amap.com/theme/v1.3/markers/n/mark_r.png'

    // 调试信息
    console.log('Using default AMap marker icon')

    console.log('创建车辆标记，位置:', position)
    vehicleMarker = new AMap.Marker({
      map: map,
      position: position,
      icon: options.icon || defaultIcon,
      offset: options.offset || new AMap.Pixel(-12, -12), // 图标居中偏移 (尺寸的一半)
      draggable: options.draggable || true, // 默认设为可拖拽，方便测试
      zIndex: 9999, // 提高层级，确保显示在最上层
      // 可以添加更多配置项
    })

    // 设置旋转角度（如果支持）
    if (options.rotation !== undefined && vehicleMarker.setRotation) {
      vehicleMarker.setRotation(options.rotation)
    } else if (options.rotation !== undefined) {
      console.warn('当前高德地图API版本不支持setRotation方法')
    }

    // 添加标记点击事件，用于调试
    vehicleMarker.on('click', function () {
      try {
        console.log('车辆标记被点击，当前位置:', vehicleMarker.getPosition())
        alert('车辆标记位置: ' + vehicleMarker.getPosition())
      } catch (error) {
        console.error('处理车辆标记点击事件失败:', error)
      }
    })

    console.log('车辆标记已创建')
    return vehicleMarker
  } catch (error) {
    console.error('创建车辆标记失败:', error)
    return null
  }
}

/**
 * 清除车辆标记
 */
export const clearVehicleMarker = () => {
  try {
    if (vehicleMarker) {
      vehicleMarker.setMap(null)
      vehicleMarker = null
      console.log('车辆标记已清除')
    }
  } catch (error) {
    console.error('清除车辆标记失败:', error)
  }
}

/**
 * 获取车辆标记实例
 * @returns {Object} 车辆标记实例
 */
export const getVehicleMarker = () => {
  return vehicleMarker
}
