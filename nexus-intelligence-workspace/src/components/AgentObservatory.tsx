import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  AlertTriangle,
  Archive,
  ArrowDown,
  CheckCircle2,
  ChevronRight,
  CircleDashed,
  Clock3,
  Download,
  FileClock,
  Loader2,
  LockKeyhole,
  RefreshCw,
  RotateCcw,
  Search,
  ShieldAlert,
  Wifi,
  WifiOff,
  XCircle
} from 'lucide-react'
import {
  getTaskEventHistory,
  listTaskHistory,
  ProgressEvent,
  ReportApiError,
  TaskEventHistoryPage,
  TaskHistoryItem,
  MAX_HISTORY_PAGE_SIZE,
  streamProgress
} from '../reportApi'

type ObservatoryProps = {
  getAccessToken: () => Promise<string>
  activeTaskId?: string
  onOpenTask?: (task: TaskHistoryItem) => void
  onRetryTask?: (task: TaskHistoryItem) => void
  onExportTask?: (task: TaskHistoryItem) => void
  onOpenForge?: () => void
  onSignOut: () => void
}

type ViewState = 'loading' | 'ready' | 'empty' | 'unauthorized' | 'forbidden' | 'error'
type EventConnection = 'idle' | 'connecting' | 'live' | 'disconnected' | 'complete'

const TERMINAL_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED'])
const statusLabels: Record<string, string> = {
  CREATED: 'Queued',
  QUEUED: 'Queued',
  STARTED: 'Starting',
  PARSING: 'Parsing',
  PLANNING: 'Planning',
  ACCEPTED: 'Accepted',
  LLM: 'Generating with LLM',
  AGENTS: 'Running agents',
  EXECUTING: 'Executing',
  REVIEWING: 'Reviewing',
  EXPORTING: 'Exporting',
  PAUSED: 'Paused',
  SUCCEEDED: 'Completed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
  UNKNOWN: 'Unavailable'
}

const eventLabels: Record<string, string> = {
  QUEUED: 'Task queued',
  STARTED: 'Worker started',
  PARSING: 'Document parsing',
  PLANNING: 'Experiment plan created',
  EXECUTING: 'Sandbox execution',
  REVIEWING: 'Report review',
  EXPORTING: 'DOCX export',
  RETRY_SCHEDULED: 'Retry scheduled',
  SUCCEEDED: 'Task completed',
  FAILED: 'Task failed',
  CANCELLED: 'Task cancelled'
}

function safeText(value: string | undefined, fallback: string) {
  if (!value) return fallback
  return value
    .replace(/(sk-[A-Za-z0-9_-]{8,}|Bearer\s+[A-Za-z0-9._-]+|(?:api[_-]?key|secret|password|token)\s*[:=]\s*[^\s,;]+)/gi, '[redacted]')
    .slice(0, 240)
}

function statusLabel(status: string) {
  return statusLabels[status.toUpperCase()] ?? statusLabels.UNKNOWN
}

function statusTone(status: string) {
  switch (status.toUpperCase()) {
    case 'SUCCEEDED': return 'success'
    case 'FAILED': return 'error'
    case 'CANCELLED': return 'muted'
    case 'PAUSED': return 'warning'
    default: return 'active'
  }
}

function eventTone(event: ProgressEvent) {
  if (event.type === 'FAILED') return 'error'
  if (event.type === 'RETRY_SCHEDULED') return 'warning'
  if (event.type === 'SUCCEEDED') return 'success'
  return 'active'
}

function eventDetail(event: ProgressEvent, fallback = 'Agent progress updated.') {
  const code = event.attributes?.errorCode || event.attributes?.reasonCode
  return safeText(code ? `Failure code: ${code}` : event.message, fallback)
}

function formatDate(value?: string, withDate = true) {
  if (!value) return 'Time unavailable'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Time unavailable'
  return new Intl.DateTimeFormat(undefined, withDate
    ? { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }
    : { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(date)
}

function eventSort(a: ProgressEvent, b: ProgressEvent) {
  if (a.eventSequence && b.eventSequence && a.eventSequence !== b.eventSequence) {
    return a.eventSequence - b.eventSequence
  }
  const time = Date.parse(a.occurredAt) - Date.parse(b.occurredAt)
  return time || a.eventId.localeCompare(b.eventId)
}

function mergeEvents(previous: ProgressEvent[], incoming: ProgressEvent[]) {
  const byKey = new Map<string, ProgressEvent>()
  previous.forEach((event) => {
    byKey.set(`id:${event.eventId}`, event)
    if (event.eventSequence) byKey.set(`sequence:${event.eventSequence}`, event)
  })
  incoming.forEach((event) => {
    const idKey = `id:${event.eventId}`
    const sequenceKey = event.eventSequence ? `sequence:${event.eventSequence}` : ''
    if (!byKey.has(idKey) && (!sequenceKey || !byKey.has(sequenceKey))) {
      byKey.set(idKey, event)
      if (sequenceKey) byKey.set(sequenceKey, event)
    }
  })
  return [...new Set(byKey.values())].sort(eventSort)
}

function connectionLabel(connection: EventConnection) {
  switch (connection) {
    case 'connecting': return 'Connecting to live updates…'
    case 'live': return 'Live updates connected'
    case 'disconnected': return 'Live updates disconnected'
    case 'complete': return 'Task stream complete'
    default: return 'History snapshot'
  }
}

function snapshotFromEvents(task: TaskHistoryItem, events: ProgressEvent[]): TaskHistoryItem {
  return events.reduce<TaskHistoryItem>((snapshot, event) => {
    const terminal = TERMINAL_STATUSES.has(event.type)
    const phase = event.attributes?.phase ?? event.attributes?.stage
    const retryable = event.attributes?.retryable
    return {
      ...snapshot,
      status: terminal ? event.type : phase ? phase.toUpperCase() : snapshot.status,
      percent: Math.max(snapshot.percent, event.percent),
      stage: phase || snapshot.stage,
      attempt: Math.max(snapshot.attempt ?? 0, event.attempt),
      updatedAt: event.occurredAt,
      errorSummary: event.type === 'FAILED' ? eventDetail(event, 'The task failed at this stage.') : snapshot.errorSummary,
      retryable: retryable === 'false' ? false : retryable === 'true' ? true : snapshot.retryable,
      exportReady: event.type === 'SUCCEEDED' ? true : snapshot.exportReady
    }
  }, { ...task })
}

function ErrorState({
  state,
  onRetry,
  onSignOut
}: { state: ViewState; onRetry: () => void; onSignOut: () => void }) {
  if (state === 'unauthorized') {
    return <div className="border border-amber-200 bg-amber-50 rounded-xl p-8 text-center max-w-xl mx-auto">
      <LockKeyhole className="mx-auto text-amber-700" size={28} aria-hidden="true" />
      <h2 className="font-headline-sm mt-4">Your session has expired</h2>
      <p className="text-sm text-amber-900/80 mt-2">Sign in again to view tenant-scoped task history. No task data was retained in this view.</p>
      <button onClick={onSignOut} className="mt-5 bg-[#3525cd] text-white rounded px-4 py-2.5 font-semibold">Sign in again</button>
    </div>
  }
  if (state === 'forbidden') {
    return <div className="border border-[#c7c4d8] bg-white rounded-xl p-8 text-center max-w-xl mx-auto">
      <ShieldAlert className="mx-auto text-[#777587]" size={28} aria-hidden="true" />
      <h2 className="font-headline-sm mt-4">This task is unavailable</h2>
      <p className="text-sm text-[#777587] mt-2">The task may have been removed or belongs to another workspace.</p>
      <button onClick={onRetry} className="mt-5 border border-[#3525cd] text-[#3525cd] rounded px-4 py-2.5 font-semibold">Refresh archive</button>
    </div>
  }
  return <div className="border border-red-200 bg-red-50 rounded-xl p-8 text-center max-w-xl mx-auto">
    <WifiOff className="mx-auto text-red-700" size={28} aria-hidden="true" />
    <h2 className="font-headline-sm mt-4">Archive could not load</h2>
    <p className="text-sm text-red-900/80 mt-2">The server did not return a task history page. You can retry without losing your live task.</p>
    <button onClick={onRetry} className="mt-5 bg-[#3525cd] text-white rounded px-4 py-2.5 font-semibold inline-flex items-center gap-2"><RefreshCw size={16} />Retry</button>
  </div>
}

function StatusBadge({ status }: { status: string }) {
  const tone = statusTone(status)
  const classes = tone === 'success'
    ? 'bg-emerald-100 text-emerald-800'
    : tone === 'error'
      ? 'bg-red-100 text-red-800'
      : tone === 'warning'
        ? 'bg-amber-100 text-amber-800'
        : tone === 'muted'
          ? 'bg-[#eeeeed] text-[#777587]'
          : 'bg-[#f0f0ff] text-[#3525cd]'
  return <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 font-label-caps text-[10px] ${classes}`}>
    {tone === 'success' ? <CheckCircle2 size={12} /> : tone === 'error' ? <XCircle size={12} /> : tone === 'active' ? <CircleDashed size={12} /> : <Clock3 size={12} />}
    {statusLabel(status)}
  </span>
}

function TaskCard({
  task,
  selected,
  onSelect
}: { task: TaskHistoryItem; selected: boolean; onSelect: () => void }) {
  const title = task.documentName || task.documentId || `Task ${task.taskId.slice(0, 12)}`
  return <button
    type="button"
    onClick={onSelect}
    aria-pressed={selected}
    className={`w-full text-left border rounded-xl p-4 transition-colors focus:outline-none focus:ring-2 focus:ring-[#3525cd] ${selected ? 'border-[#3525cd] bg-[#f0f0ff]' : 'border-[#c7c4d8] bg-white hover:border-[#9b96e8]'}`}
  >
    <div className="flex items-start gap-3">
      <span className={`mt-1 h-2.5 w-2.5 shrink-0 rounded-full ${statusTone(task.status) === 'success' ? 'bg-emerald-600' : statusTone(task.status) === 'error' ? 'bg-red-600' : 'bg-[#3525cd]'}`} />
      <span className="min-w-0 flex-1">
        <span className="flex items-start justify-between gap-3">
          <strong className="truncate text-sm text-[#1a1c1c]">{title}</strong>
          <ChevronRight size={16} className={`shrink-0 text-[#777587] transition-transform ${selected ? 'rotate-90 text-[#3525cd]' : ''}`} aria-hidden="true" />
        </span>
        <span className="mt-2 flex flex-wrap items-center gap-2"><StatusBadge status={task.status} /><span className="font-label-caps text-[10px] text-[#777587]">{task.percent}%</span></span>
         <span className="mt-3 block h-1.5 overflow-hidden rounded-full bg-[#eeeeed]"><span className={`block h-full rounded-full ${statusTone(task.status) === 'error' ? 'bg-red-500' : statusTone(task.status) === 'success' ? 'bg-emerald-500' : 'bg-[#3525cd]'}`} style={{ width: `${task.percent}%` }} /></span>
         <span className="mt-2 flex items-center justify-between gap-2 text-[11px] text-[#777587]"><span className="truncate">{task.stage || 'Stage unavailable'}</span><time className="shrink-0">{formatDate(task.updatedAt || task.createdAt)}</time></span>
         {task.status === 'FAILED' && task.errorSummary && <span className="mt-2 block truncate text-[11px] text-red-800">{safeText(task.errorSummary, 'Task failed.')}</span>}
       </span>
    </div>
  </button>
}

function EventTimeline({ events }: { events: ProgressEvent[] }) {
  if (events.length === 0) {
    return <div className="rounded-lg border border-dashed border-[#c7c4d8] py-12 text-center text-sm text-[#777587]">No public execution events have been recorded yet.</div>
  }
  return <ol className="relative ml-2 border-l border-[#d8d6e2] pl-6" aria-label="Task event timeline">
    {events.map((event) => {
      const tone = eventTone(event)
      const dot = tone === 'success' ? 'bg-emerald-600' : tone === 'error' ? 'bg-red-600' : tone === 'warning' ? 'bg-amber-500' : 'bg-[#3525cd]'
      const error = event.type === 'FAILED' || Boolean(event.attributes?.errorCode || event.attributes?.reasonCode)
      return <li key={event.eventId} className="relative pb-6 last:pb-0">
        <span className={`absolute -left-[31px] top-1 flex h-3 w-3 rounded-full border-2 border-white ${dot}`} aria-hidden="true" />
        <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1">
          <strong className="text-sm text-[#1a1c1c]">{eventLabels[event.type] ?? 'Agent update'}</strong>
          <time className="font-label-caps text-[10px] text-[#777587]" dateTime={event.occurredAt}>{formatDate(event.occurredAt, false)}</time>
        </div>
        <p className={`mt-1 text-xs leading-relaxed ${error ? 'text-red-800' : 'text-[#464555]'}`}>{eventDetail(event)}</p>
        <div className="mt-2 flex flex-wrap gap-2">
          {event.attributes?.phase && <span className="rounded-full bg-[#f0f0ff] px-2 py-0.5 font-label-caps text-[10px] text-[#3525cd]">{safeText(event.attributes.phase, 'Stage')}</span>}
          <span className="rounded-full bg-[#eeeeed] px-2 py-0.5 font-label-caps text-[10px] text-[#777587]">{event.percent}%</span>
          {event.attempt > 0 && <span className="rounded-full bg-[#eeeeed] px-2 py-0.5 font-label-caps text-[10px] text-[#777587]">Attempt {event.attempt}</span>}
        </div>
        {error && <details className="mt-2 text-xs text-red-800"><summary className="cursor-pointer font-semibold">Failure details</summary><p className="mt-1 rounded bg-red-50 p-2">{eventDetail(event, 'The task failed at this stage.')}</p></details>}
      </li>
    })}
  </ol>
}

function EmptyState({ onOpenForge }: { onOpenForge?: () => void }) {
  return <div className="rounded-xl border border-dashed border-[#c7c4d8] bg-white p-12 text-center">
    <Archive className="mx-auto text-[#777587]" size={30} aria-hidden="true" />
    <h2 className="font-headline-sm mt-4">Your execution archive is empty</h2>
    <p className="mx-auto mt-2 max-w-md text-sm leading-relaxed text-[#777587]">Completed and in-progress Agent tasks will appear here. Start with a DOCX in Forge and this view will keep the public execution record after refresh.</p>
    {onOpenForge && <button onClick={onOpenForge} className="mt-5 bg-[#3525cd] text-white rounded px-4 py-2.5 font-semibold inline-flex items-center gap-2"><Search size={16} />Open Forge</button>}
  </div>
}

export default function AgentObservatory({
  getAccessToken,
  activeTaskId,
  onOpenTask,
  onRetryTask,
  onExportTask,
  onOpenForge,
  onSignOut
}: ObservatoryProps) {
  const [viewState, setViewState] = useState<ViewState>('loading')
  const [tasks, setTasks] = useState<TaskHistoryItem[]>([])
  const [nextTaskCursor, setNextTaskCursor] = useState<string | null>(null)
  const [selectedTask, setSelectedTask] = useState<TaskHistoryItem | null>(null)
  const [events, setEvents] = useState<ProgressEvent[]>([])
  const [eventCursor, setEventCursor] = useState<string | null>(null)
  const [eventState, setEventState] = useState<ViewState>('ready')
  const [eventConnection, setEventConnection] = useState<EventConnection>('idle')
  const [eventError, setEventError] = useState('')
  const [busy, setBusy] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [selectedLoading, setSelectedLoading] = useState(false)
  const selectedIdRef = useRef('')
  const selectedTaskRef = useRef<TaskHistoryItem | null>(null)
  const latestSequenceRef = useRef(0)
  const terminalRef = useRef(false)
  const liveControllerRef = useRef<AbortController | null>(null)
  const reconnectTimerRef = useRef<number | null>(null)

  const loadTasks = useCallback(async (append = false) => {
    if (append && !nextTaskCursor) return
    append ? setLoadingMore(true) : setBusy(true)
    if (!append) setViewState('loading')
    try {
      const token = await getAccessToken()
      const page = await listTaskHistory(token, { cursor: append ? nextTaskCursor ?? undefined : undefined, limit: MAX_HISTORY_PAGE_SIZE })
      setTasks((current) => append ? [...current, ...page.items.filter((item) => !current.some((known) => known.taskId === item.taskId))] : page.items)
      setNextTaskCursor(page.nextCursor)
      setViewState(page.items.length > 0 || (append && tasks.length > 0) ? 'ready' : 'empty')
    } catch (cause) {
      const apiError = cause instanceof ReportApiError ? cause : null
      if (apiError?.status === 401) setViewState('unauthorized')
      else if (apiError?.status === 403 || apiError?.status === 404) setViewState('forbidden')
      else setViewState('error')
    } finally {
      setBusy(false)
      setLoadingMore(false)
    }
  }, [getAccessToken, nextTaskCursor, tasks.length])

  useEffect(() => {
    let disposed = false
    void (async () => {
      try {
        const token = await getAccessToken()
        const page = await listTaskHistory(token, { limit: MAX_HISTORY_PAGE_SIZE })
        if (disposed) return
        setTasks(page.items)
        setNextTaskCursor(page.nextCursor)
        setViewState(page.items.length ? 'ready' : 'empty')
      } catch (cause) {
        if (disposed) return
        const apiError = cause instanceof ReportApiError ? cause : null
        setViewState(apiError?.status === 401 ? 'unauthorized' : apiError?.status === 403 || apiError?.status === 404 ? 'forbidden' : 'error')
      }
    })()
    return () => { disposed = true }
  }, [getAccessToken])

  const mergeLiveEvent = useCallback((event: ProgressEvent) => {
    if (event.taskId !== selectedIdRef.current) return
    if (event.eventSequence && event.eventSequence <= latestSequenceRef.current) return
    if (event.eventSequence) latestSequenceRef.current = event.eventSequence
    setEventConnection('live')
    setEventState('ready')
    setEvents((current) => mergeEvents(current, [event]))
    setTasks((current) => current.map((task) => task.taskId === event.taskId ? snapshotFromEvents(task, [event]) : task))
    setSelectedTask((task) => {
      if (!task || task.taskId !== event.taskId) return task
      const next = snapshotFromEvents(task, [event])
      selectedTaskRef.current = next
      return next
    })
    if (TERMINAL_STATUSES.has(event.type)) { terminalRef.current = true; setEventConnection('complete'); setEventState('ready') }
  }, [])

  const loadEvents = useCallback(async (task: TaskHistoryItem, append = false) => {
    if (append && !eventCursor) return
    setSelectedLoading(!append)
    if (!append) { setEventState('loading'); setEventError(''); setEvents([]); setEventCursor(null) }
    try {
      const token = await getAccessToken()
      const page: TaskEventHistoryPage = await getTaskEventHistory(task.taskId, token, { after: append ? eventCursor ?? undefined : undefined, limit: MAX_HISTORY_PAGE_SIZE })
      if (selectedIdRef.current !== task.taskId) return
      page.items.forEach((event) => {
        if (event.eventSequence) latestSequenceRef.current = Math.max(latestSequenceRef.current, event.eventSequence)
      })
      setEvents((current) => append ? mergeEvents(current, page.items) : mergeEvents([], page.items))
      if (page.items.length > 0) {
        setTasks((current) => current.map((item) => item.taskId === task.taskId ? snapshotFromEvents(item, page.items) : item))
        setSelectedTask((current) => {
          if (!current || current.taskId !== task.taskId) return current
          const next = snapshotFromEvents(current, page.items)
          selectedTaskRef.current = next
          terminalRef.current = TERMINAL_STATUSES.has(next.status)
          return next
        })
      }
      setEventCursor(page.nextCursor)
      setEventState(page.items.length || append ? 'ready' : 'empty')
    } catch (cause) {
      if (selectedIdRef.current !== task.taskId) return
      const apiError = cause instanceof ReportApiError ? cause : null
      setEventError(cause instanceof Error ? cause.message : 'History request failed.')
      setEventState(apiError?.status === 401 ? 'unauthorized' : apiError?.status === 403 || apiError?.status === 404 ? 'forbidden' : 'error')
    } finally {
      setSelectedLoading(false)
    }
  }, [eventCursor, getAccessToken])

  const connectLive = useCallback(() => {
    const task = selectedTaskRef.current
    if (!task || terminalRef.current || TERMINAL_STATUSES.has(task.status)) return
    selectedIdRef.current = task.taskId
    liveControllerRef.current?.abort()
    if (reconnectTimerRef.current !== null) window.clearTimeout(reconnectTimerRef.current)
    const controller = new AbortController()
    liveControllerRef.current = controller
    setEventConnection('connecting')
    void (async () => {
      let delay = 1_000
      try {
        while (!controller.signal.aborted && !terminalRef.current) {
          try {
            await streamProgress(task.taskId, await getAccessToken(), mergeLiveEvent, {
              signal: controller.signal,
              afterSequence: latestSequenceRef.current || undefined
            })
            if (terminalRef.current) break
            setEventConnection('disconnected')
          } catch (cause) {
            if (controller.signal.aborted) break
            const apiError = cause instanceof ReportApiError ? cause : null
            if (apiError && [401, 403, 404].includes(apiError.status)) {
              setEventError(apiError.message)
              setEventConnection('disconnected')
              break
            }
            setEventError(cause instanceof Error ? cause.message : 'Live connection interrupted.')
            setEventConnection('disconnected')
          }
          if (controller.signal.aborted || terminalRef.current) break
          await new Promise<void>((resolve) => {
            reconnectTimerRef.current = window.setTimeout(resolve, delay)
            delay = Math.min(delay * 2, 15_000)
          })
          reconnectTimerRef.current = null
          setEventConnection('connecting')
        }
      } catch (cause) {
        if (!controller.signal.aborted) {
          setEventConnection('disconnected')
          setEventError(cause instanceof Error ? cause.message : 'Live connection interrupted.')
        }
      }
    })()
    return () => {
      controller.abort()
      if (reconnectTimerRef.current !== null) window.clearTimeout(reconnectTimerRef.current)
      if (liveControllerRef.current === controller) liveControllerRef.current = null
    }
  }, [getAccessToken, mergeLiveEvent])

  useEffect(() => {
    if (!selectedTask || eventState === 'loading' || terminalRef.current) return
    return connectLive()
  }, [connectLive, eventState, selectedTask?.taskId])

  const selectTask = useCallback((task: TaskHistoryItem) => {
    selectedIdRef.current = task.taskId
    selectedTaskRef.current = task
    latestSequenceRef.current = 0
    terminalRef.current = TERMINAL_STATUSES.has(task.status)
    setSelectedTask(task)
    setEvents([])
    setEventCursor(null)
    setEventConnection('idle')
    setEventError('')
    setEventState('loading')
    void loadEvents(task)
    onOpenTask?.(task)
  }, [loadEvents, onOpenTask])

  useEffect(() => {
    if (!activeTaskId || selectedTask || !tasks.length) return
    const activeTask = tasks.find((task) => task.taskId === activeTaskId)
    if (activeTask) selectTask(activeTask)
  }, [activeTaskId, selectedTask, selectTask, tasks])

  const selectedEvents = useMemo(() => events.filter((event) => event.taskId === selectedTask?.taskId), [events, selectedTask?.taskId])
  const latestEvent = selectedEvents[selectedEvents.length - 1]
  const canLoadMoreTasks = Boolean(nextTaskCursor)
  const canLoadMoreEvents = Boolean(eventCursor)

  return <section className="mx-auto w-full max-w-7xl px-4 pb-8 sm:px-8 animate-fade-in">
    <div className="mb-8 flex flex-wrap items-start justify-between gap-5">
      <div>
        <p className="font-label-caps tracking-widest text-[#3525cd]">AGENT OBSERVATORY / ARCHIVE</p>
        <h1 className="font-display-lg mt-4 max-w-3xl">Every run leaves a usable trail.</h1>
        <p className="mt-4 max-w-2xl text-[#464555]">Return after a refresh, inspect safe execution facts, and take the next allowed action. Private prompts and model reasoning never enter this timeline.</p>
      </div>
      <button onClick={() => void loadTasks(false)} disabled={busy} className="inline-flex items-center gap-2 rounded border border-[#c7c4d8] bg-white px-3 py-2 text-sm font-semibold text-[#464555] hover:border-[#3525cd] hover:text-[#3525cd] disabled:opacity-50"><RefreshCw size={15} className={busy ? 'animate-spin' : ''} />Refresh history</button>
    </div>

    {viewState === 'loading' && <div className="grid gap-6 lg:grid-cols-[minmax(250px,0.38fr)_minmax(0,1fr)]"><div className="space-y-3">{[1, 2, 3].map((item) => <div key={item} className="h-32 animate-pulse rounded-xl border border-[#e8e8e7] bg-white" />)}</div><div className="min-h-80 animate-pulse rounded-xl border border-[#e8e8e7] bg-white" /></div>}
    {viewState === 'empty' && <EmptyState onOpenForge={onOpenForge} />}
    {['error', 'unauthorized', 'forbidden'].includes(viewState) && <ErrorState state={viewState} onRetry={() => void loadTasks(false)} onSignOut={onSignOut} />}

    {viewState === 'ready' && <div className="grid gap-6 lg:grid-cols-[minmax(250px,0.38fr)_minmax(0,1fr)]">
      <aside className="min-w-0">
        <div className="mb-3 flex items-center justify-between"><div><p className="font-label-caps tracking-widest text-[#777587]">RECENT TASKS</p><p className="mt-1 text-xs text-[#777587]">Tenant-scoped execution records</p></div><FileClock size={20} className="text-[#3525cd]" aria-hidden="true" /></div>
        <div className="space-y-3">{tasks.map((task) => <div key={task.taskId}><TaskCard task={task} selected={selectedTask?.taskId === task.taskId} onSelect={() => selectTask(task)} /></div>)}</div>
        {canLoadMoreTasks && <button onClick={() => void loadTasks(true)} disabled={loadingMore} className="mt-4 w-full rounded border border-[#c7c4d8] bg-white px-3 py-2 text-sm font-semibold text-[#464555] hover:border-[#3525cd] hover:text-[#3525cd] disabled:opacity-50">{loadingMore ? <Loader2 size={15} className="mx-auto animate-spin" /> : <><ArrowDown size={15} className="mr-1 inline" />Load older tasks</>}</button>}
      </aside>

      <article className="min-w-0 rounded-xl border border-[#c7c4d8] bg-white shadow-2xs">
        {!selectedTask && <div className="flex min-h-[520px] flex-col items-center justify-center p-10 text-center"><Archive size={32} className="text-[#777587]" aria-hidden="true" /><h2 className="font-headline-sm mt-4">Select a task to inspect</h2><p className="mt-2 max-w-md text-sm text-[#777587]">The timeline shows public stage changes, percentages, attempts and bounded failure details. It never shows chain-of-thought.</p></div>}
        {selectedTask && <>
          <header className="border-b border-[#e8e8e7] p-5 sm:p-7"><div className="flex flex-wrap items-start justify-between gap-4"><div className="min-w-0"><p className="font-label-caps tracking-widest text-[#777587]">TASK {selectedTask.taskId.slice(0, 16)}</p><h2 className="mt-2 truncate font-headline-sm">{selectedTask.documentName || selectedTask.documentId || 'Untitled report'}</h2><p className="mt-2 text-xs text-[#777587]">Last updated {formatDate(selectedTask.updatedAt || selectedTask.createdAt)}</p></div><StatusBadge status={selectedTask.status} /></div><div className="mt-6 grid gap-4 sm:grid-cols-[1fr_auto] sm:items-end"><div><div className="mb-2 flex justify-between text-xs"><span className="text-[#777587]">{selectedTask.stage || 'Stage unavailable'}</span><strong className="text-[#3525cd]">{selectedTask.percent}%</strong></div><div className="h-2 overflow-hidden rounded-full bg-[#eeeeed]"><div className={`h-full rounded-full ${statusTone(selectedTask.status) === 'error' ? 'bg-red-500' : 'bg-[#3525cd]'}`} style={{ width: `${selectedTask.percent}%` }} /></div></div><div className="flex flex-wrap gap-2">{selectedTask.retryable && onRetryTask && <button onClick={() => onRetryTask(selectedTask)} className="inline-flex items-center gap-1.5 rounded border border-[#3525cd] px-3 py-2 text-xs font-semibold text-[#3525cd] hover:bg-[#f0f0ff]"><RotateCcw size={14} />Retry</button>}{selectedTask.exportReady && onExportTask && <button onClick={() => onExportTask(selectedTask)} className="inline-flex items-center gap-1.5 rounded bg-[#3525cd] px-3 py-2 text-xs font-semibold text-white hover:bg-[#4f46e5]"><Download size={14} />Export</button>}</div></div>{selectedTask.errorSummary && <div className="mt-5 flex gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-900"><AlertTriangle size={17} className="mt-0.5 shrink-0" /><div><strong>Safe failure summary</strong><p className="mt-1 text-xs leading-relaxed">{safeText(selectedTask.errorSummary, 'The task failed.')}</p></div></div>}</header>
           <div className="p-5 sm:p-7">{latestEvent && <div className="mb-5 rounded-lg border border-[#d9d6ff] bg-[#f7f6ff] p-3" aria-live="polite"><div className="flex flex-wrap items-center justify-between gap-2"><span className="font-label-caps text-[10px] tracking-widest text-[#3525cd]">CURRENT AGENT ACTIVITY</span><time className="text-[11px] text-[#777587]" dateTime={latestEvent.occurredAt}>{formatDate(latestEvent.occurredAt, false)}</time></div><strong className="mt-1 block text-sm text-[#1a1c1c]">{eventLabels[latestEvent.type] ?? 'Agent update'}</strong><p className="mt-1 text-xs leading-relaxed text-[#464555]">{eventDetail(latestEvent)}</p></div>}<div className="mb-5 flex flex-wrap items-center justify-between gap-3"><div><p className="font-label-caps tracking-widest text-[#777587]">PUBLIC EVENT TIMELINE</p><p className="mt-1 text-xs text-[#777587]">Ordered by the server-assigned event sequence</p></div><div className="flex flex-wrap items-center gap-2 text-xs"><span aria-live="polite" className={`inline-flex items-center gap-1.5 rounded-full px-2 py-1 ${eventConnection === 'live' ? 'bg-emerald-100 text-emerald-800' : eventConnection === 'disconnected' ? 'bg-amber-100 text-amber-800' : 'bg-[#eeeeed] text-[#777587]'}`}><span className={`h-1.5 w-1.5 rounded-full ${eventConnection === 'live' ? 'bg-emerald-600' : eventConnection === 'disconnected' ? 'bg-amber-600' : 'bg-[#777587]'}`} />{connectionLabel(eventConnection)}</span>{!TERMINAL_STATUSES.has(selectedTask.status) && <button onClick={() => { void loadEvents(selectedTask); connectLive() }} className="inline-flex items-center gap-1.5 rounded border border-[#c7c4d8] px-2.5 py-1.5 font-semibold text-[#464555] hover:border-[#3525cd] hover:text-[#3525cd]"><Wifi size={14} />Reconnect</button>}</div></div>{eventError && <div className="mb-4 flex items-center gap-2 rounded border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900"><WifiOff size={15} />{safeText(eventError, 'The live connection was interrupted.')}<button onClick={() => { setEventError(''); void loadEvents(selectedTask); connectLive() }} className="ml-auto font-semibold underline">Retry</button></div>}{eventState === 'loading' && <div className="space-y-4"><div className="h-16 animate-pulse rounded bg-[#f9f9f8]" /><div className="h-16 animate-pulse rounded bg-[#f9f9f8]" /></div>}{eventState === 'empty' && <div className="rounded-lg border border-dashed border-[#c7c4d8] py-12 text-center text-sm text-[#777587]">No public execution events have been recorded yet.</div>}{eventState === 'unauthorized' && <ErrorState state="unauthorized" onRetry={() => void loadEvents(selectedTask)} onSignOut={onSignOut} />}{eventState === 'forbidden' && <ErrorState state="forbidden" onRetry={() => void loadEvents(selectedTask)} onSignOut={onSignOut} />}{eventState === 'error' && <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center text-sm text-red-900"><p>Event history is temporarily unavailable.</p><button onClick={() => void loadEvents(selectedTask)} className="mt-3 inline-flex items-center gap-2 font-semibold underline"><RefreshCw size={14} />Retry timeline</button></div>}{eventState === 'ready' && <EventTimeline events={selectedEvents} />}{canLoadMoreEvents && <button onClick={() => void loadEvents(selectedTask, true)} disabled={selectedLoading} className="mt-6 w-full rounded border border-[#c7c4d8] px-3 py-2 text-xs font-semibold text-[#464555] hover:border-[#3525cd] hover:text-[#3525cd] disabled:opacity-50">{selectedLoading ? <Loader2 size={14} className="mx-auto animate-spin" /> : 'Load more events'}</button>}</div>
        </>}
      </article>
    </div>}
  </section>
}
