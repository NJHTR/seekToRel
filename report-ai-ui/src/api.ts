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

export type TaskResponse = { taskId: string; status: string; version: number; manifestDigest?: string | null }

export type AuthTokenResponse = {
  accessToken: string
  accessTokenExpiresInSeconds: number
  refreshToken: string
  refreshTokenExpiresInSeconds: number
  userId: string
  tenantId: string
  email: string
  displayName: string
}

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

export async function request<T>(path: string, token = '', init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (token.trim()) headers.set('Authorization', `Bearer ${token.trim()}`)
  const response = await fetch(path, { ...init, headers })
  const body = await response.json() as ApiResult<T>
  if (!response.ok || !body.success || body.data === null) throw new Error(body.message || `Request failed (${response.status})`)
  return body.data
}

export function register(input: { email: string; password: string; displayName: string; tenantName: string }) {
  return request<AuthTokenResponse>('/api/v1/auth/register', '', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) })
}
export function login(input: { email: string; password: string }) {
  return request<AuthTokenResponse>('/api/v1/auth/login', '', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) })
}
export function refresh(refreshToken: string) {
  return request<AuthTokenResponse>('/api/v1/auth/refresh', '', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken }) })
}
export function logout(refreshToken: string) {
  return request<void>('/api/v1/auth/logout', '', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken }) })
}
export function upload(file: File, token: string, idempotencyKey: string) {
  const form = new FormData(); form.append('file', file)
  return request<UploadResponse>('/api/v1/documents', token, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: form })
}
export function createTask(documentId: string, token: string, idempotencyKey: string) {
  return request<TaskResponse>('/api/v1/tasks', token, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey }, body: JSON.stringify({ documentId }) })
}
export function createTaskWithInput(documentId: string, inputPath: string, inputFile: File, token: string, idempotencyKey: string) {
  const form = new FormData()
  form.append('documentId', documentId)
  form.append('path', inputPath)
  form.append('file', inputFile)
  return request<TaskResponse>('/api/v1/tasks/with-input', token, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: form })
}
export async function streamProgress(taskId: string, token: string, onEvent: (event: ProgressEvent) => void) {
  const response = await fetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/events`, { headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' } })
  if (!response.ok || !response.body) throw new Error(`Progress connection failed (${response.status})`)
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
  let buffer = ''; let data = ''
  while (true) {
    const { value, done } = await reader.read(); if (done) break
    buffer += value
    const lines = buffer.split(/\r?\n/); buffer = lines.pop() ?? ''
    for (const line of lines) {
      if (line.startsWith('data:')) data += line.slice(5).trim()
      if (line === '' && data) {
        try {
          const event = JSON.parse(data) as ProgressEvent
          onEvent(event)
          if (['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(event.type)) {
            await reader.cancel()
            return
          }
        } catch { /* keepalive or non-json event */ }
        data = ''
      }
    }
  }
}
export type ArtifactMetadata = { id: string; objectKey: string; mediaType: string; size: number; sha256: string }
export function listArtifacts(taskId: string, token: string) {
  return request<ArtifactMetadata[]>(`/api/v1/tasks/${encodeURIComponent(taskId)}/artifacts`, token)
}
export async function downloadTask(taskId: string, token: string) {
  const response = await fetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/export`, { headers: { Authorization: `Bearer ${token}` } })
  if (!response.ok) throw new Error(`Export failed (${response.status})`)
  const blob = await response.blob(); const url = URL.createObjectURL(blob); const anchor = document.createElement('a')
  anchor.href = url; anchor.download = `report-${taskId}.docx`; anchor.click(); URL.revokeObjectURL(url)
}
