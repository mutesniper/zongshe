// 定义AutoComplete实例变量
let autoComplete = null
// 定义PlaceSearch实例变量
let placeSearch = null
// 存储POI标记的数组
let poiMarkers = []
// 存储AMap实例
let AMap = null
// 存储地图实例
let map = null
// 存储回调函数
let selectCallback = null
// 搜索结果缓存
const searchCache = new Map()
// 缓存过期时间（毫秒）
const CACHE_EXPIRY_TIME = 5 * 60 * 1000 // 5分钟
// 防抖计时器
let searchDebounceTimer = null
// 防抖延迟时间（毫秒）
const DEBOUNCE_DELAY = 300

/**
 * 初始化POI搜索模块
 * @param {Object} aMapInstance - AMap全局实例
 * @param {Object} mapInstance - 地图实例
 */
export const initPoiSearcher = (aMapInstance, mapInstance) => {
  AMap = aMapInstance
  map = mapInstance
  console.log('POI searcher module initialized')
  
  // 提前创建PlaceSearch实例，避免每次搜索都创建新实例
  if (!placeSearch && AMap) {
    AMap.plugin('AMap.PlaceSearch', function () {
      try {
        placeSearch = new AMap.PlaceSearch({
          city: '北京', // 默认城市
          pageSize: 10, // 默认每页结果数
          pageIndex: 1, // 默认页码
          extensions: 'base' // 返回基本信息
        })
        console.log('PlaceSearch实例已创建')
      } catch (error) {
        console.error('创建PlaceSearch实例失败:', error)
      }
    })
  }
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
  
  console.error('POI搜索错误:', standardizedError)
  
  // 调用错误回调函数
  if (typeof errorCallback === 'function') {
    errorCallback(standardizedError)
  }
  
  return standardizedError
}

/**
 * 初始化输入提示插件
 * @param {String} inputId - 输入框ID
 * @param {String} city - 城市名称
 * @param {Function} onSelect - 选择回调函数
 */
export const initAutoComplete = (inputId, city = '全国', onSelect = null) => {
  try {
    if (!AMap) {
      throw new Error('AMap尚未加载，无法初始化输入提示插件')
    }

    AMap.plugin('AMap.AutoComplete', function () {
      try {
        const autoOptions = {
          input: inputId, // 输入框id
          city: city, // 限定城市
        }
        autoComplete = new AMap.AutoComplete(autoOptions)
        console.log('输入提示插件初始化成功')

        // 存储选择回调
        selectCallback = onSelect

        // 添加选择事件监听器
        autoComplete.on('select', function (e) {
          console.log('AutoComplete selected:', e)
          // 触发POI选择事件，传递选中的数据
          if (typeof selectCallback === 'function') {
            selectCallback(e.poi)
          }
        })
      } catch (error) {
        handleError(error)
      }
    })
  } catch (error) {
    handleError(error)
  }
}

/**
 * POI搜索方法 - 带防抖和缓存
 * @param {String} keyword - 搜索关键词
 * @param {String} city - 城市名称
 * @param {Number} pageSize - 每页结果数
 * @param {Number} pageIndex - 页码
 * @param {Function} onPoiSearched - 搜索成功回调
 * @param {Function} onPoiError - 搜索失败回调
 */
export const searchPoi = (
  keyword,
  city = '北京',
  pageSize = 10,
  pageIndex = 1,
  onPoiSearched,
  onPoiError,
) => {
  try {
    // 清除之前的防抖计时器
    if (searchDebounceTimer) {
      clearTimeout(searchDebounceTimer)
    }

    // 防抖处理
    searchDebounceTimer = setTimeout(() => {
      performSearch(keyword, city, pageSize, pageIndex, onPoiSearched, onPoiError)
    }, DEBOUNCE_DELAY)
  } catch (error) {
    handleError(error, onPoiError)
  }
}

/**
 * 执行实际的POI搜索
 * @param {String} keyword - 搜索关键词
 * @param {String} city - 城市名称
 * @param {Number} pageSize - 每页结果数
 * @param {Number} pageIndex - 页码
 * @param {Function} onPoiSearched - 搜索成功回调
 * @param {Function} onPoiError - 搜索失败回调
 */
const performSearch = (
  keyword,
  city,
  pageSize,
  pageIndex,
  onPoiSearched,
  onPoiError
) => {
  try {
    if (!AMap) {
      throw new Error('AMap尚未加载，无法进行POI搜索')
    }

    // 构建缓存键
    const cacheKey = `${keyword}_${city}_${pageSize}_${pageIndex}`
    
    // 检查缓存中是否有有效的结果
    const cachedResult = getFromCache(cacheKey)
    if (cachedResult) {
      console.log('使用缓存的POI搜索结果')
      // 先清除之前的POI结果
      clearPoiResults()
      // 在地图上显示POI标记
      if (cachedResult.poiList && cachedResult.poiList.pois) {
        cachedResult.poiList.pois.forEach((poi) => {
          showPoi(poi)
        })
      }
      onPoiSearched(cachedResult)
      return
    }

    // 先清除之前的POI结果
    clearPoiResults()

    // 确保PlaceSearch实例已创建
    if (!placeSearch) {
      AMap.plugin('AMap.PlaceSearch', function () {
        try {
          placeSearch = new AMap.PlaceSearch({
            city: city,
            pageSize: pageSize,
            pageIndex: pageIndex,
          })
          executeSearch(keyword, city, pageSize, pageIndex, onPoiSearched, onPoiError, cacheKey)
        } catch (error) {
          handleError(error, onPoiError)
        }
      })
    } else {
      // 更新搜索参数
      placeSearch.setCity(city)
      placeSearch.setPageSize(pageSize)
      placeSearch.setPageIndex(pageIndex)
      executeSearch(keyword, city, pageSize, pageIndex, onPoiSearched, onPoiError, cacheKey)
    }
  } catch (error) {
    handleError(error, onPoiError)
  }
}

/**
 * 执行搜索请求
 * @param {String} keyword - 搜索关键词
 * @param {String} city - 城市名称
 * @param {Number} pageSize - 每页结果数
 * @param {Number} pageIndex - 页码
 * @param {Function} onPoiSearched - 搜索成功回调
 * @param {Function} onPoiError - 搜索失败回调
 * @param {String} cacheKey - 缓存键
 */
const executeSearch = (
  keyword,
  city,
  pageSize,
  pageIndex,
  onPoiSearched,
  onPoiError,
  cacheKey
) => {
  try {
    if (!placeSearch) {
      throw new Error('PlaceSearch实例未创建')
    }

    placeSearch.search(keyword, function (status, result) {
      if (status === 'complete') {
        console.log('POI搜索成功:', result)
        // 缓存搜索结果
        addToCache(cacheKey, result)
        // 在地图上显示POI标记
        if (result.poiList && result.poiList.pois) {
          result.poiList.pois.forEach((poi) => {
            showPoi(poi)
          })
        }
        onPoiSearched(result)
      } else {
        console.error('POI搜索失败:', result)
        handleError({ message: 'POI搜索失败', code: 'POI_SEARCH_FAILED', originalError: result }, onPoiError)
      }
    })
  } catch (error) {
    handleError(error, onPoiError)
  }
}

/**
 * 将搜索结果添加到缓存
 * @param {String} key - 缓存键
 * @param {Object} data - 搜索结果数据
 */
const addToCache = (key, data) => {
  const cacheItem = {
    data: data,
    timestamp: Date.now()
  }
  searchCache.set(key, cacheItem)
  
  // 限制缓存大小，防止内存泄漏
  if (searchCache.size > 50) {
    // 删除最早的缓存项
    const firstKey = searchCache.keys().next().value
    searchCache.delete(firstKey)
  }
}

/**
 * 从缓存中获取搜索结果
 * @param {String} key - 缓存键
 * @returns {Object|null} 缓存的搜索结果或null
 */
const getFromCache = (key) => {
  const cacheItem = searchCache.get(key)
  if (!cacheItem) {
    return null
  }
  
  // 检查缓存是否过期
  const now = Date.now()
  if (now - cacheItem.timestamp > CACHE_EXPIRY_TIME) {
    searchCache.delete(key)
    return null
  }
  
  return cacheItem.data
}

/**
 * 在地图上显示POI点
 * @param {Object} poi - POI数据对象
 */
export const showPoi = (poi) => {
  try {
    if (!map || !poi.location) {
      return
    }

    const position = [poi.location.lng, poi.location.lat]
    const marker = new AMap.Marker({
      position: position,
      title: poi.name,
      map: map,
    })

    poiMarkers.push(marker)

    // 设置地图中心和缩放级别
    map.setCenter(position)
    map.setZoom(16)

    // 弹出信息窗口
    const infoWindow = new AMap.InfoWindow({
      content: `<div style="padding: 10px;"><h3>${poi.name}</h3><p>${poi.address}</p></div>`,
      offset: new AMap.Pixel(0, -30),
    })

    infoWindow.open(map, position)
  } catch (error) {
    console.error('显示POI失败:', error)
  }
}

/**
 * 清除POI搜索结果
 */
export const clearPoiResults = () => {
  try {
    // 移除所有POI标记
    poiMarkers.forEach((marker) => {
      if (marker && marker.setMap) {
        marker.setMap(null)
      }
    })
    poiMarkers = []
    console.log('POI结果已清除')
  } catch (error) {
    console.error('清除POI结果失败:', error)
  }
}

/**
 * 销毁POI搜索模块
 */
export const destroyPoiSearcher = () => {
  try {
    clearPoiResults()
    autoComplete = null
    placeSearch = null
    console.log('POI searcher instance destroyed')
  } catch (error) {
    console.error('销毁POI搜索模块失败:', error)
  }
}
