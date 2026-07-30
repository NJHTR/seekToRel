<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NAlert, NButton, NCard, NDivider, NForm, NFormItem, NInput, NLayout, NLayoutContent,
  NLayoutHeader, NProgress, NSpace, NTag, NText } from 'naive-ui'
import { createTask, download, ProgressEvent, streamProgress, upload } from './api'

const token = ref(sessionStorage.getItem('report-ai-token') ?? '')
const file = ref<File | null>(null)
const documentId = ref('')
const taskId = ref('')
const status = ref('待上传')
const percent = ref(0)
const message = ref('')
const error = ref('')
const busy = ref(false)
const warnings = ref<string[]>([])

const canUpload = computed(() => Boolean(token.value.trim() && file.value && !busy.value))
const canStart = computed(() => Boolean(documentId.value && !busy.value))

onMounted(() => {
  token.value = sessionStorage.getItem('report-ai-token') ?? ''
})

function saveToken() {
  sessionStorage.setItem('report-ai-token', token.value.trim())
}

function selectFile(selected: File | null) {
  file.value = selected
  error.value = ''
}

async function handleUpload() {
  if (!file.value) return
  saveToken()
  busy.value = true
  error.value = ''
  status.value = '解析中'
  try {
    const result = await upload(file.value, token.value.trim(), crypto.randomUUID())
    documentId.value = result.documentId
    warnings.value = result.warnings
    status.value = result.status
  } catch (cause) {
    status.value = '失败'
    error.value = cause instanceof Error ? cause.message : '上传失败'
  } finally { busy.value = false }
}

async function handleStart() {
  saveToken()
  busy.value = true
  error.value = ''
  try {
    const result = await createTask(documentId.value, token.value.trim(), crypto.randomUUID())
    taskId.value = result.taskId
    status.value = result.status
    percent.value = 0
    void watchProgress(result.taskId)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '创建任务失败'
  } finally { busy.value = false }
}

async function watchProgress(id: string) {
  try {
    await streamProgress(id, token.value.trim(), (event: ProgressEvent) => {
      percent.value = event.percent
      status.value = event.type
      message.value = event.message
      if (event.type === 'SUCCEEDED') percent.value = 100
    })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '进度连接中断'
  }
}

async function handleDownload() {
  try { await download(documentId.value, token.value.trim()) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '导出失败' }
}
</script>

<template>
  <n-layout class="page">
    <n-layout-header class="header">
      <div>
        <div class="brand">ReportAI</div>
        <n-text depth="3">实验报告自动化 Agent 平台</n-text>
      </div>
      <n-tag type="info" round>Java + PostgreSQL + Docker</n-tag>
    </n-layout-header>
    <n-layout-content class="content">
      <n-card title="创建实验任务" class="card" :bordered="false">
        <n-form label-placement="top">
          <n-form-item label="访问令牌">
            <n-input v-model:value="token" type="password" show-password-on="click"
              placeholder="粘贴 JWT，令牌仅保存在当前浏览器会话" @blur="saveToken" />
          </n-form-item>
          <n-form-item label="实验报告（DOCX）">
            <input class="file-input" type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              @change="selectFile(($event.target as HTMLInputElement).files?.[0] ?? null)" />
            <n-text depth="3">{{ file?.name ?? '请选择 .docx 文件' }}</n-text>
          </n-form-item>
          <n-space>
            <n-button type="primary" :disabled="!canUpload" :loading="busy" @click="handleUpload">上传并解析</n-button>
            <n-button type="success" :disabled="!canStart" :loading="busy" @click="handleStart">启动 Agent</n-button>
            <n-button :disabled="!documentId || status !== 'SUCCEEDED'" @click="handleDownload">导出 Word</n-button>
          </n-space>
        </n-form>
      </n-card>

      <n-card v-if="documentId || taskId" title="任务进度" class="card" :bordered="false">
        <n-space vertical>
          <n-text v-if="documentId">文档 ID：{{ documentId }}</n-text>
          <n-text v-if="taskId">任务 ID：{{ taskId }}</n-text>
          <n-progress type="line" :percentage="percent" processing />
          <n-space align="center"><n-tag>{{ status }}</n-tag><n-text depth="3">{{ message || '等待任务事件…' }}</n-text></n-space>
          <n-alert v-if="warnings.length" type="warning" title="解析提示">{{ warnings.join('；') }}</n-alert>
        </n-space>
      </n-card>

      <n-alert v-if="error" type="error" title="请求失败" closable @close="error = ''">{{ error }}</n-alert>
      <n-divider />
      <n-text depth="3">所有代码执行均通过后端 Docker 沙箱；SSE 仅传输实时进度，任务状态以服务端持久化数据为准。</n-text>
    </n-layout-content>
  </n-layout>
</template>
