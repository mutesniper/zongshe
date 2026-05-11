<template>
  <div class="vehicle-visualization">
    <div class="header">
      <h2>车辆分析</h2>
      <div class="controls">
        <select v-model="selectedType" @change="updateCharts">
          <option value="all">全部车型</option>
          <option value="refrigerated">冷藏车</option>
          <option value="van">厢式货车</option>
          <option value="flatbed">平板车</option>
          <option value="highBar">高栏车</option>
          <option value="special">其他特种车辆</option>
        </select>
        <select v-model="timeRange" @change="updateCharts">
          <option value="day">日</option>
          <option value="week">周</option>
          <option value="month">月</option>
        </select>
      </div>
    </div>
    
    <div class="charts-grid">
      <!-- 车辆总数 -->
      <div class="chart-item">
        <h3>车辆总数</h3>
        <div ref="totalVehiclesChart" class="chart"></div>
      </div>
      
      <!-- 车辆类型分布 -->
      <div class="chart-item">
        <h3>车辆类型分布</h3>
        <div ref="vehicleTypeChart" class="chart"></div>
      </div>
      
      <!-- 车辆位置分布 -->
      <div class="chart-item">
        <h3>车辆位置分布</h3>
        <div ref="locationDistributionChart" class="chart"></div>
      </div>
      
      <!-- 车辆利用率 -->
      <div class="chart-item">
        <h3>车辆利用率</h3>
        <div ref="utilizationChart" class="chart"></div>
      </div>
      
      <!-- 等待时间分析 -->
      <div class="chart-item">
        <h3>等待时间分析</h3>
        <div ref="waitingTimeChart" class="chart"></div>
      </div>
      
      <!-- 空驶分析 -->
      <div class="chart-item">
        <h3>空驶分析</h3>
        <div ref="emptyMileageChart" class="chart"></div>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'

export default {
  name: 'VehicleVisualization',
  data() {
    return {
      selectedType: 'all',
      timeRange: 'day',
      charts: {},
      // 模拟数据
      vehicleData: {
        totalVehicles: {
          refrigerated: 15,
          van: 45,
          flatbed: 20,
          highBar: 12,
          special: 8
        },
        locationDistribution: {
          urban: 60,
          suburban: 30,
          intercity: 10
        },
        utilization: {
          overall: 78,
          refrigerated: 85,
          van: 75,
          flatbed: 70,
          highBar: 80
        },
        waitingTime: {
          totalWaitingSeconds: 0,
          totalWaitingMinutes: 0,
          totalWaitingHours: 0,
          averageWaitingMinutesPerTruck: 0
        },
        emptyMileage: {
          daily: 150, // 公里
          weekly: 1050,
          monthly: 4500
        }
      }
    }
  },
  mounted() {
    this.initCharts()
    this.fetchTruckStatistics()
  },
  beforeUnmount() {
    Object.values(this.charts).forEach(chart => chart.dispose())
  },
  methods: {
    async fetchTruckStatistics() {
      try {
        const response = await fetch('http://localhost:8080/truck/waiting-time')
        const data = await response.json()
        
        if (data.success && data.data) {
          this.vehicleData.waitingTime = {
            totalWaitingMinutes: data.data.totalWaitingMinutes || 0,
            totalWaitingHours: data.data.totalWaitingHours || 0,
            averageWaitingMinutesPerTruck: data.data.averageWaitingMinutesPerTruck || 0
          }
        }
        
        this.updateWaitingTimeChart()
      } catch (error) {
        console.error('获取车辆等待时间数据失败:', error)
      }
    },
    initCharts() {
      this.charts.totalVehicles = echarts.init(this.$refs.totalVehiclesChart)
      this.charts.vehicleType = echarts.init(this.$refs.vehicleTypeChart)
      this.charts.locationDistribution = echarts.init(this.$refs.locationDistributionChart)
      this.charts.utilization = echarts.init(this.$refs.utilizationChart)
      this.charts.waitingTime = echarts.init(this.$refs.waitingTimeChart)
      this.charts.emptyMileage = echarts.init(this.$refs.emptyMileageChart)
      
      this.updateCharts()
      
      window.addEventListener('resize', this.handleResize)
    },
    handleResize() {
      Object.values(this.charts).forEach(chart => chart.resize())
    },
    updateCharts() {
      this.updateTotalVehiclesChart()
      this.updateVehicleTypeChart()
      this.updateLocationDistributionChart()
      this.updateUtilizationChart()
      this.updateWaitingTimeChart()
      this.updateEmptyMileageChart()
    },
    updateTotalVehiclesChart() {
      const data = this.vehicleData.totalVehicles
      const option = {
        title: {
          text: '车辆总数统计',
          left: 'center'
        },
        tooltip: {
          trigger: 'item'
        },
        legend: {
          bottom: '5%',
          left: 'center'
        },
        series: [
          {
            name: '车辆类型',
            type: 'pie',
            radius: '60%',
            data: [
              { value: data.refrigerated, name: '冷藏车' },
              { value: data.van, name: '厢式货车' },
              { value: data.flatbed, name: '平板车' },
              { value: data.highBar, name: '高栏车' },
              { value: data.special, name: '其他特种车辆' }
            ],
            emphasis: {
              itemStyle: {
                shadowBlur: 10,
                shadowOffsetX: 0,
                shadowColor: 'rgba(0, 0, 0, 0.5)'
              }
            }
          }
        ]
      }
      this.charts.totalVehicles.setOption(option)
    },
    updateVehicleTypeChart() {
      const option = {
        title: {
          text: '车辆类型分布',
          left: 'center'
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          }
        },
        legend: {
          data: ['数量']
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: ['轻型货车', '中型货车', '重型货车', '冷藏保温车', '普通货舱车', '危险品专用车']
        },
        yAxis: {
          type: 'value'
        },
        series: [
          {
            name: '数量',
            type: 'bar',
            data: [30, 45, 25, 15, 80, 5]
          }
        ]
      }
      this.charts.vehicleType.setOption(option)
    },
    updateLocationDistributionChart() {
      const data = this.vehicleData.locationDistribution
      const option = {
        title: {
          text: '车辆位置分布',
          left: 'center'
        },
        tooltip: {
          trigger: 'item'
        },
        legend: {
          bottom: '5%',
          left: 'center'
        },
        series: [
          {
            name: '区域分布',
            type: 'pie',
            radius: ['40%', '70%'],
            avoidLabelOverlap: false,
            itemStyle: {
              borderRadius: 10,
              borderColor: '#fff',
              borderWidth: 2
            },
            label: {
              show: false,
              position: 'center'
            },
            emphasis: {
              label: {
                show: true,
                fontSize: '18',
                fontWeight: 'bold'
              }
            },
            labelLine: {
              show: false
            },
            data: [
              { value: data.urban, name: '市区内' },
              { value: data.suburban, name: '郊区' },
              { value: data.intercity, name: '跨城/干线' }
            ]
          }
        ]
      }
      this.charts.locationDistribution.setOption(option)
    },
    updateUtilizationChart() {
      const data = this.vehicleData.utilization
      const option = {
        title: {
          text: '车辆利用率',
          left: 'center'
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          }
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: ['整体', '冷藏车', '厢式货车', '平板车', '高栏车']
        },
        yAxis: {
          type: 'value',
          min: 0,
          max: 100,
          axisLabel: {
            formatter: '{value}%'
          }
        },
        series: [
          {
            name: '利用率',
            type: 'bar',
            data: [
              data.overall,
              data.refrigerated,
              data.van,
              data.flatbed,
              data.highBar
            ],
            itemStyle: {
              color: function(params) {
                const colors = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de']
                return colors[params.dataIndex]
              }
            }
          }
        ]
      }
      this.charts.utilization.setOption(option)
    },
    updateWaitingTimeChart() {
      const option = {
        title: {
          text: '所有车辆等待总时间',
          left: 'center'
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          }
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: {
              type: 'category',
              data: ['总等待时间（分钟）', '总等待时间（小时）', '平均每车等待（分钟）']
            },
        yAxis: {
          type: 'value',
          axisLabel: {
            formatter: '{value}'
          }
        },
        series: [
          {
            name: '等待时间',
            type: 'bar',
            data: [
                  this.vehicleData.waitingTime.totalWaitingMinutes || 0,
                  this.vehicleData.waitingTime.totalWaitingHours || 0,
                  this.vehicleData.waitingTime.averageWaitingMinutesPerTruck || 0
                ],
            itemStyle: {
              color: '#91cc75'
            }
          }
        ]
      }
      this.charts.waitingTime.setOption(option)
    },
    updateEmptyMileageChart() {
      const data = this.vehicleData.emptyMileage
      let mileageData
      
      switch (this.timeRange) {
        case 'day':
          mileageData = data.daily
          break
        case 'week':
          mileageData = data.weekly
          break
        case 'month':
          mileageData = data.monthly
          break
      }
      
      const option = {
        title: {
          text: '空驶分析',
          left: 'center'
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          }
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: ['日空驶', '周空驶', '月空驶', '占总里程比例']
        },
        yAxis: {
          type: 'value',
          axisLabel: {
            formatter: function(value, index) {
              return index === 3 ? value + '%' : value + '公里'
            }
          }
        },
        series: [
          {
            name: '空驶数据',
            type: 'bar',
            data: [
              mileageData,
              mileageData * 7,
              mileageData * 30,
              25 // 占总里程比例
            ],
            itemStyle: {
              color: function(params) {
                return params.dataIndex === 3 ? '#ee6666' : '#5470c6'
              }
            }
          }
        ]
      }
      this.charts.emptyMileage.setOption(option)
    }
  }
}
</script>

<style scoped>
.vehicle-visualization {
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e0e0e0;
}

.header h2 {
  margin: 0;
  font-size: 24px;
  color: #333;
}

.controls {
  display: flex;
  gap: 15px;
}

.controls select {
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  background: white;
  cursor: pointer;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 30px;
}

.chart-item {
  background: #f9f9f9;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.chart-item h3 {
  margin: 0 0 20px 0;
  font-size: 16px;
  color: #666;
  text-align: center;
}

.chart {
  width: 100%;
  height: 300px;
}

@media (max-width: 768px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
  
  .header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .controls {
    width: 100%;
  }
  
  .controls select {
    flex: 1;
  }
}
</style>