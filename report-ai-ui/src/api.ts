export type ApiResult<T> = {
  success: boolean
  data: T | null
  code: string
  message: string
  traceId?: string
}

export type UploadResponse = {
  documentId: string
  status: string
  mediaType: string
  contentLength: number
  warnings: string[]
}

export type TaskResponse = { taskId: string; status: string; version: number }

export type ProgressEvent = {
  eventId: string
  taskId: string
  attempt: number
  type: string
  percent: number
  message: string
  occurredAt: string
  attributes?: Record<string, string>
}

export async function request<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(path, { ...init, headers })
  const body = await response.json() as ApiResult<T>
  if (!response.ok || !body.success || body.data === null) {
    throw new Error(body.message || `请求失败 (${response.status})`)
  }
  return body.data
}

export function upload(file: File, token: string, idempotencyKey: string) {
  const form = new FormData()
  form.append('file', file)
  return request<UploadResponse>('/api/v1/documents', token, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: form,
  })
}

export function createTask(documentId: string, token: string, idempotencyKey: string) {
  return request<TaskResponse>('/api/v1/tasks', token, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ documentId }),
  })
}

export async function streamProgress(taskId: string, token: string, onEvent: (event: ProgressEvent) => void) {
  const response = await fetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/events`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
  })
  if (!response.ok || !response.body) throw new Error(`进度连接失败 (${response.status})`)
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
  let buffer = ''
  let data = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += value
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      if (line.startsWith('data:')) data += line.slice(5).trim()
      if (line === '' && data) {
        try { onEvent(JSON.parse(data) as ProgressEvent) } catch { /* ignore keepalive/non-json events */ }
        data = ''
      }
    }
  }
}

export async function download(documentId: string, token: string) {
  const response = await fetch(`/api/v1/documents/${encodeURIComponent(documentId)}/export`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) throw new Error(`导出失败 (${response.status})`)
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `report-${documentId}.docx`
  anchor.click()
  URL.revokeObjectURL(url)
}
