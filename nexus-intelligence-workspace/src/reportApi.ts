export type ApiResult<T> = {
  success: boolean
  data: T | null
  code: string
  message: string
  traceId?: string
}

/**
 * The history endpoints deliberately expose only bounded, user-facing facts.
 * Keep the types here provider-neutral so the Observatory does not need to
 * know about scheduler/storage implementation details.
 */
export type TaskHistoryItem = {
  taskId: string
  documentId?: string
  documentName?: string
  status: string
  percent: number
  stage?: string
  attempt?: number
  version?: number
  manifestDigest?: string
  createdAt?: string
  updatedAt?: string
  completedAt?: string
  errorSummary?: string
  retryable?: boolean
  exportReady?: boolean
}

export type TaskHistoryPage = {
  items: TaskHistoryItem[]
  nextCursor: string | null
  hasMore: boolean
}

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

export type UploadResponse = {
  documentId: string
  status: string
  mediaType: string
  contentLength: number
  warnings: string[]
}

export type TaskResponse = { taskId: string; status: string; version: number; manifestDigest?: string | null }

export type ProgressEvent = {
  eventId: string
  taskId: string
  /** Database-assigned ordering key. Older servers may omit it during rollout. */
  eventSequence?: number
  attempt: number
  type: string
  percent: number
  message: string
  occurredAt: string
  attributes?: Record<string, string>
}

export type TaskEventHistoryPage = {
  items: ProgressEvent[]
  nextCursor: string | null
  hasMore: boolean
}

export type HistoryPageQuery = {
  cursor?: string
  after?: string
  limit?: number
}

export const DEFAULT_HISTORY_PAGE_SIZE = 20
export const MAX_HISTORY_PAGE_SIZE = 50

export class ReportApiError extends Error {
  readonly status: number
  readonly code: string
  readonly traceId?: string

  constructor(message: string, details: { status: number; code?: string; traceId?: string }) {
    super(message)
    this.name = 'ReportApiError'
    this.status = details.status
    this.code = details.code ?? 'REQUEST_FAILED'
    this.traceId = details.traceId
  }
}

const apiOrigin = (import.meta.env.VITE_REPORT_AI_API_ORIGIN ?? '').replace(/\/$/, '')

async function request<T>(path: string, token = '', init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (token.trim()) headers.set('Authorization', `Bearer ${token.trim()}`)
  const response = await fetch(`${apiOrigin}${path}`, { ...init, headers })
  let body: ApiResult<T> | null = null
  try {
    body = await response.json() as ApiResult<T>
  } catch {
    // Keep the error typed even when a reverse proxy returns an HTML/plaintext
    // error page. The Observatory uses the status to choose a safe UI state.
  }
  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new ReportApiError(body?.message || `Request failed (${response.status})`, {
      status: response.status,
      code: body?.code,
      traceId: body?.traceId
    })
  }
  return body.data
}

function boundedLimit(limit?: number) {
  if (!Number.isFinite(limit)) return DEFAULT_HISTORY_PAGE_SIZE
  return Math.max(1, Math.min(MAX_HISTORY_PAGE_SIZE, Math.floor(limit as number)))
}

function readRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' ? value as Record<string, unknown> : {}
}

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function readNumber(value: unknown, fallback = 0) {
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function readBoolean(value: unknown) {
  return typeof value === 'boolean' ? value : undefined
}

function clampPercent(value: unknown) {
  return Math.max(0, Math.min(100, Math.round(readNumber(value))))
}

function pageItems(data: unknown): unknown[] {
  if (Array.isArray(data)) return data
  const record = readRecord(data)
  for (const key of ['items', 'tasks', 'events', 'content', 'results']) {
    if (Array.isArray(record[key])) return record[key] as unknown[]
  }
  return []
}

function pageCursor(data: unknown) {
  const record = readRecord(data)
  for (const key of ['nextCursor', 'next_cursor', 'next', 'cursor']) {
    const value = readString(record[key])
    if (value) return value
  }
  return null
}

function pageHasMore(data: unknown, nextCursor: string | null) {
  const record = readRecord(data)
  return readBoolean(record.hasMore) ?? readBoolean(record.hasNext) ?? Boolean(nextCursor)
}

function normalizeTaskHistoryItem(value: unknown): TaskHistoryItem | null {
  const record = readRecord(value)
  const taskId = readString(record.taskId) ?? readString(record.id)
  if (!taskId) return null
  const status = (readString(record.status) ?? 'UNKNOWN').toUpperCase()
  const hasPercent = ['percent', 'progressPercent', 'progress', 'completion'].some((key) => record[key] !== undefined)
  return {
    taskId,
    documentId: readString(record.documentId) ?? readString(record.document_id),
    documentName: readString(record.documentName) ?? readString(record.fileName) ?? readString(record.filename),
    status,
    percent: hasPercent ? clampPercent(record.percent ?? record.progressPercent ?? record.progress ?? record.completion) : status === 'SUCCEEDED' ? 100 : 0,
    stage: readString(record.stage) ?? readString(record.currentStage) ?? readString(record.phase) ?? status,
    attempt: Math.max(0, Math.floor(readNumber(record.attempt, 0))),
    version: Math.max(0, Math.floor(readNumber(record.version, 0))),
    manifestDigest: readString(record.manifestDigest) ?? readString(record.manifest_digest),
    createdAt: readString(record.createdAt) ?? readString(record.created_at),
    updatedAt: readString(record.updatedAt) ?? readString(record.updated_at),
    completedAt: readString(record.completedAt) ?? readString(record.completed_at),
    errorSummary: readString(record.errorSummary) ?? readString(record.failureReason) ?? readString(record.error),
    retryable: readBoolean(record.retryable) ?? readBoolean(record.canRetry) ?? status === 'FAILED',
    exportReady: readBoolean(record.exportReady) ?? readBoolean(record.canExport) ?? status === 'SUCCEEDED'
  }
}

function normalizeProgressEvent(value: unknown): ProgressEvent | null {
  const record = readRecord(value)
  const eventId = readString(record.eventId) ?? readString(record.id)
  const taskId = readString(record.taskId)
  const occurredAt = readString(record.occurredAt) ?? readString(record.occurred_at) ?? readString(record.createdAt)
  const type = readString(record.type) ?? readString(record.eventType)
  if (!eventId || !taskId || !occurredAt || !type) return null
  const rawAttributes = readRecord(record.attributes)
  const attributes: Record<string, string> = {}
  // Only retain fields that are explicitly rendered by the UI. This is a
  // second line of defence if an older server sends extra attributes.
  for (const key of ['phase', 'agents', 'agent', 'errorCode', 'retryable', 'reasonCode', 'stage', 'operation']) {
    const value = readString(rawAttributes[key])
    if (value) attributes[key] = value.slice(0, 240)
  }
  return {
    eventId,
    taskId,
    eventSequence: (() => {
      const sequence = readNumber(record.eventSequence ?? record.event_sequence ?? record.sequence, 0)
      return sequence > 0 ? Math.floor(sequence) : undefined
    })(),
    attempt: Math.max(0, Math.floor(readNumber(record.attempt, 0))),
    type,
    percent: clampPercent(record.percent ?? record.progress),
    message: (readString(record.message) ?? 'Agent progress updated.').slice(0, 240),
    occurredAt,
    attributes
  }
}

function normalizeHistoryPage<T>(data: unknown, normalize: (value: unknown) => T | null) {
  const items = pageItems(data).map(normalize).filter((item): item is T => item !== null)
  const nextCursor = pageCursor(data)
  return { items, nextCursor, hasMore: pageHasMore(data, nextCursor) }
}

export async function listTaskHistory(token: string, query: HistoryPageQuery = {}): Promise<TaskHistoryPage> {
  const params = new URLSearchParams({ limit: String(boundedLimit(query.limit)) })
  if (query.cursor?.trim()) params.set('cursor', query.cursor.trim())
  const data = await request<unknown>(`/api/v1/tasks?${params.toString()}`, token)
  return normalizeHistoryPage(data, normalizeTaskHistoryItem)
}

export async function getTaskEventHistory(taskId: string, token: string, query: HistoryPageQuery = {}): Promise<TaskEventHistoryPage> {
  const params = new URLSearchParams({ limit: String(boundedLimit(query.limit)) })
  if (query.after?.trim()) params.set('after', query.after.trim())
  else if (query.cursor?.trim()) params.set('after', query.cursor.trim())
  const data = await request<unknown>(`/api/v1/tasks/${encodeURIComponent(taskId)}/events/history?${params.toString()}`, token)
  return normalizeHistoryPage(data, normalizeProgressEvent)
}

export function register(input: { email: string; password: string; displayName: string; tenantName: string }) {
  return request<AuthTokenResponse>('/api/v1/auth/register', '', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input)
  })
}

export function login(input: { email: string; password: string }) {
  return request<AuthTokenResponse>('/api/v1/auth/login', '', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input)
  })
}

export function refresh(refreshToken: string) {
  return request<AuthTokenResponse>('/api/v1/auth/refresh', '', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  })
}

// Refresh tokens are rotated by the API. A browser can have several requests
// become due at the same time (archive load, event history, and the live
// stream), but only the first request may consume the current token. Keep one
// in-flight refresh per token so concurrent callers share the rotated result
// instead of replaying the old token and invalidating the session.
const refreshInFlight = new Map<string, Promise<AuthTokenResponse>>()

export function refreshSingleFlight(refreshToken: string) {
  const key = refreshToken.trim()
  const existing = refreshInFlight.get(key)
  if (existing) return existing

  const pending = refresh(key).finally(() => {
    if (refreshInFlight.get(key) === pending) refreshInFlight.delete(key)
  })
  refreshInFlight.set(key, pending)
  return pending
}

export function logout(refreshToken: string) {
  return request<void>('/api/v1/auth/logout', '', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  })
}

export function upload(file: File, token: string, idempotencyKey: string) {
  const form = new FormData()
  form.append('file', file)
  return request<UploadResponse>('/api/v1/documents', token, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: form
  })
}

export function createTask(documentId: string, token: string, idempotencyKey: string) {
  return request<TaskResponse>('/api/v1/tasks', token, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ documentId })
  })
}

export function createTaskWithInput(documentId: string, inputPath: string, inputFile: File, token: string, idempotencyKey: string) {
  const form = new FormData()
  form.append('documentId', documentId)
  form.append('path', inputPath)
  form.append('file', inputFile)
  return request<TaskResponse>('/api/v1/tasks/with-input', token, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: form
  })
}

export async function streamProgress(
  taskId: string,
  token: string,
  onEvent: (event: ProgressEvent) => void,
  options: AbortSignal | { signal?: AbortSignal; afterSequence?: number } = {}
) {
  const legacySignal = typeof AbortSignal !== 'undefined' && options instanceof AbortSignal
  const signal = legacySignal ? options as AbortSignal : (options as { signal?: AbortSignal }).signal
  const afterSequence = legacySignal ? undefined : (options as { afterSequence?: number }).afterSequence
  const headers = new Headers({ Authorization: `Bearer ${token}`, Accept: 'text/event-stream' })
  if (afterSequence && Number.isSafeInteger(afterSequence) && afterSequence > 0) {
    headers.set('Last-Event-ID', String(afterSequence))
  }
  const response = await fetch(`${apiOrigin}/api/v1/tasks/${encodeURIComponent(taskId)}/events`, {
    headers,
    signal
  })
  if (!response.ok || !response.body) throw new Error(`Progress connection failed (${response.status})`)
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
  let buffer = ''
  let data = ''
  let eventId = ''
  let lastSequence = afterSequence ?? 0
  while (true) {
    const { value, done } = await reader.read()
    if (done) return lastSequence || undefined
    buffer += value
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      if (line.startsWith('data:')) data += line.slice(5).trim()
      if (line.startsWith('id:')) eventId = line.slice(3).trim()
      if (line === '' && data) {
        try {
          const event = normalizeProgressEvent(JSON.parse(data))
          if (!event) throw new Error('invalid progress event')
          const parsedId = Number(eventId)
          if (!event.eventSequence && Number.isSafeInteger(parsedId) && parsedId > 0) event.eventSequence = parsedId
          if (event.eventSequence && event.eventSequence > lastSequence) lastSequence = event.eventSequence
          onEvent(event)
          if (['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(event.type)) {
            await reader.cancel()
            return
          }
        } catch {
          // connected/keepalive frames are intentionally ignored
        }
        data = ''
        eventId = ''
      }
    }
  }
}

async function downloadBinary(path: string, filename: string, token: string) {
  const response = await fetch(`${apiOrigin}${path}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  if (!response.ok) throw new Error(`Export failed (${response.status})`)
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

/** Rebuilds a canonical document for preview/export compatibility. */
export function download(documentId: string, token: string) {
  return downloadBinary(`/api/v1/documents/${encodeURIComponent(documentId)}/export`,
    `report-${documentId}.docx`, token)
}

/** Downloads the immutable review-gated artifact produced by a successful task. */
export function downloadTask(taskId: string, token: string) {
  return downloadBinary(`/api/v1/tasks/${encodeURIComponent(taskId)}/export`,
    `report-task-${taskId}.docx`, token)
}
