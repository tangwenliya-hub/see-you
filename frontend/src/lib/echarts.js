/**
 * ECharts 按需引入（全项目唯一入口）
 *
 * 背景：项目此前 5 处 `import * as echarts from 'echarts'` 全量引入，
 * 使 useChart chunk 达 1.1MB、index chunk 1.3MB。改为 echarts/core +
 * 显式注册后，未使用的图表类型（map/gauge/funnel/tree 等）不再进 bundle。
 *
 * ⚠️ 约定：全项目任何文件都不要直接 `import ... from 'echarts'`，
 * 一律从本模块引入，否则按需注册会被全量打包绕过。
 */
import * as echarts from 'echarts/core'
import {
  LineChart,
  BarChart,
  PieChart,
  ScatterChart,
  SankeyChart,
  HeatmapChart,
  RadarChart,
} from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  VisualMapComponent,
  CalendarComponent,
  DatasetComponent,
  TransformComponent,
  GraphicComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import 'echarts-wordcloud' // 词云系列，自行注册到 echarts 实例体系

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  ScatterChart,
  SankeyChart,
  HeatmapChart,
  RadarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  VisualMapComponent,
  CalendarComponent,
  DatasetComponent,
  TransformComponent,
  GraphicComponent,
  CanvasRenderer,
])

export default echarts
export const { init, graphic, use } = echarts
