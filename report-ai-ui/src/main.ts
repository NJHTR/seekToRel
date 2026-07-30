import { createApp } from 'vue'
import { create, NButton, NCard, NCode, NForm, NFormItem, NInput, NLayout, NLayoutContent,
  NLayoutHeader, NProgress, NSpace, NTag, NUpload, NUploadDragger, NText, NAlert, NDivider } from 'naive-ui'
import App from './App.vue'
import './style.css'

const naive = create({
  components: [NButton, NCard, NCode, NForm, NFormItem, NInput, NLayout, NLayoutContent,
    NLayoutHeader, NProgress, NSpace, NTag, NUpload, NUploadDragger, NText, NAlert, NDivider],
})

createApp(App).use(naive).mount('#app')
