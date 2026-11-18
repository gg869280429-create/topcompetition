<template>
  <span class="major-label"><img v-if="logo" :src="logo" alt="" class="major-logo" /><el-icon v-else aria-hidden="true" :data-major-icon="icon.name"><component :is="icon.component" /></el-icon><span>{{ name || placeholder }}</span></span>
</template>

<script setup>
import { computed, ref, watch, onUnmounted } from 'vue'
import request from '@/api/request'
import { Monitor, Cpu, Tools, SetUp, Coin, Brush, House, FirstAidKit, Reading, ChatDotRound, Bowl, Sunny, Collection } from '@element-plus/icons-vue'
const props = defineProps({ name: String, majorId: Number, logoFileId: Number, iconCode: String, placeholder: { type: String, default: '待补录专业' } })
const rules = [
  [/电子商务|电商|金融|经济|财务|会计|工商|市场营销|国际贸易/, 'Coin', Coin],
  [/机械|机电|制造|汽车|车辆|数控|模具/, 'Tools', Tools],
  [/自动化|机器人|控制工程/, 'SetUp', SetUp],
  [/电子|电气|通信|微电子|集成电路/, 'Cpu', Cpu],
  [/计算机|软件|网络|数据|人工智能|信息|物联网|数字媒体技术/, 'Monitor', Monitor],
  [/艺术|美术|设计|音乐|舞蹈|影视|动画|摄影/, 'Brush', Brush],
  [/建筑|土木|工程造价|城乡规划|工程管理/, 'House', House],
  [/医|护理|药|康复|健康/, 'FirstAidKit', FirstAidKit],
  [/教育|师范|学前/, 'Reading', Reading],
  [/语言|英语|日语|汉语|翻译|外语/, 'ChatDotRound', ChatDotRound],
  [/食品|烹饪|餐饮|酒店|旅游/, 'Bowl', Bowl],
  [/农|林|园艺|园林|畜牧|兽医/, 'Sunny', Sunny]
]
const icons={Monitor,Cpu,Tools,SetUp,Coin,Brush,House,FirstAidKit,Reading,ChatDotRound,Bowl,Sunny,Collection}
const logo=ref('')
let version=0
watch(()=>[props.majorId,props.logoFileId],async()=>{
 const current=++version
 if(logo.value) URL.revokeObjectURL(logo.value)
 logo.value=''
 if(!props.majorId || !props.logoFileId)return
 try{const blob=await request.get(`/major/${props.majorId}/logo`,{responseType:'blob'});if(current===version)logo.value=URL.createObjectURL(blob)}catch{}
},{immediate:true})
onUnmounted(()=>{version++;if(logo.value)URL.revokeObjectURL(logo.value)})
const icon = computed(() => {
  if(icons[props.iconCode]) return {name:props.iconCode,component:icons[props.iconCode]}
  const match = rules.find(([pattern]) => pattern.test(props.name || ''))
  return match ? { name: match[1], component: match[2] } : { name: 'Collection', component: Collection }
})
</script>

<style scoped>
.major-label { display:inline-flex; align-items:center; gap:6px; max-width:100%; vertical-align:middle; }
.major-logo { width:22px; height:22px; object-fit:contain; flex-shrink:0; }
.major-label > .el-icon { flex-shrink:0; font-size:15px; }
.major-label > span { min-width:0; overflow-wrap:anywhere; }
</style>
