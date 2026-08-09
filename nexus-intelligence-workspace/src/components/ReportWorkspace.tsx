import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  Bot,
  CheckCircle2,
  ClipboardCheck,
  Code2,
  Database,
  Download,
  FileText,
  Loader2,
  LogOut,
  Play,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Terminal,
  UploadCloud,
  X
} from 'lucide-react'
import { SideNavBar } from './SideNavBar'
import { TopAppBar } from './TopAppBar'
import {
  AuthTokenResponse,
  createTask,
  createTaskWithInput,
  downloadTask,
  login,
  logout,
  ProgressEvent,
  refreshSingleFlight,
  ReportApiError,
  register,
  TaskHistoryItem,
  streamProgress,
  upload
} from '../reportApi'
import { NavigationTab } from '../types'
import AgentObservatory from './AgentObservatory'

type Session = AuthTokenResponse & { accessTokenExpiresAt: number }
type EventTone = 'info' | 'success' | 'warning' | 'error'
type ActivityItem = { id: string; title: string; detail: string; meta?: string; tone: EventTone; time: string }

type Stage = { key: string; label: string; description: string; icon: typeof UploadCloud }

const stages: Stage[] = [
  { key: 'UPLOAD', label: 'Upload report', description: 'Validate the DOCX and store the source', icon: UploadCloud },
  { key: 'PARSE', label: 'Parse document', description: 'Extract structure, tables and placeholders', icon: FileText },
  { key: 'PLAN', label: 'Plan experiment', description: 'Build a traceable execution plan', icon: ClipboardCheck },
  { key: 'CATALOG', label: 'Resolve Catalog', description: 'Lock image, dependencies and hashes', icon: Database },
  { key: 'PREFLIGHT', label: 'Preflight inputs', description: 'Verify data, permissions and resource limits', icon: ShieldCheck },
  { key: 'CODE', label: 'Generate code', description: 'Create runnable experiment code', icon: Code2 },
  { key: 'INSTALL', label: 'Install dependencies', description: 'Prepare the ephemeral environment', icon: Terminal },
  { key: 'EXECUTE', label: 'Run sandbox', description: 'Execute offline with bounded resources', icon: Terminal },
  { key: 'ARTIFACTS', label: 'Collect artifacts', description: 'Persist charts, images, logs and metrics', icon: Database },
  { key: 'CLEANUP', label: 'Cleanup runtime', description: 'Remove temporary containers and volumes', icon: X },
  { key: 'REVIEW', label: 'Review report', description: 'Check completeness and evidence', icon: ShieldCheck },
  { key: 'EXPORT', label: 'Export DOCX', description: 'Release the final reproducible report', icon: Download }
]

const stageForPhase: Record<string, string> = {
  accepted: 'UPLOAD',
  parsing: 'PARSE',
  planning: 'PLAN',
  executing: 'EXECUTE',
  agents: 'EXECUTE',
  reviewing: 'REVIEW',
  exporting: 'EXPORT',
  completed: 'EXPORT'
}

const labels: Record<string, string> = {
  QUEUED: 'Task queued',
  STARTED: 'Worker started',
  PARSING: 'Parsing document',
  PLANNING: 'Planning experiment',
  EXECUTING: 'Executing agents',
  REVIEWING: 'Reviewing output',
  EXPORTING: 'Building DOCX',
  RETRY_SCHEDULED: 'Retry scheduled',
  SUCCEEDED: 'Report completed',
  FAILED: 'Task failed',
  CANCELLED: 'Task cancelled'
}

function nowLabel() {
  return new Intl.DateTimeFormat('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date())
}

function sizeLabel(bytes: number) {
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function stageFromEvent(event: ProgressEvent) {
  const phase = event.attributes?.phase?.toLowerCase() ?? ''
  const agent = event.attributes?.agent?.toLowerCase() ?? ''
  const text = `${phase} ${event.message ?? ''} ${agent} ${event.attributes?.agents ?? ''}`.toLowerCase()
  if (event.type === 'SUCCEEDED') return 'EXPORT'
  if (agent === 'planner') return 'PLAN'
  if (agent === 'coder') return 'CODE'
  if (agent === 'preflight') return 'PREFLIGHT'
  if (agent === 'installer') return 'INSTALL'
  if (agent === 'execution' || agent === 'runtime') return 'EXECUTE'
  if (agent === 'artifact-persistence') return 'ARTIFACTS'
  if (agent === 'cleanup') return 'CLEANUP'
  if (agent === 'document' || agent === 'reviewer') return 'REVIEW'
  if (text.includes('catalog') || text.includes('resolve')) return 'CATALOG'
  if (text.includes('preflight')) return 'PREFLIGHT'
  if (text.includes('installer') || text.includes('install depend')) return 'INSTALL'
  if (text.includes('artifact') || text.includes('collect')) return 'ARTIFACTS'
  if (text.includes('cleanup') || text.includes('clean up')) return 'CLEANUP'
  if (text.includes('coder') || text.includes('generate')) return 'CODE'
  if (text.includes('runtime') || text.includes('sandbox') || text.includes('execut')) return 'EXECUTE'
  if (event.type === 'FAILED' || event.type === 'CANCELLED') return stageForPhase[phase] ?? 'EXECUTE'
  return stageForPhase[phase] ?? 'PARSE'
}

function toneForEvent(event: ProgressEvent): EventTone {
  if (event.type === 'FAILED') return 'error'
  if (event.type === 'RETRY_SCHEDULED') return 'warning'
  if (event.type === 'SUCCEEDED') return 'success'
  return 'info'
}

function toActivity(event: ProgressEvent): ActivityItem {
  const redact = (value: string) => value.replace(/(sk-[A-Za-z0-9_-]{8,}|Bearer\s+[A-Za-z0-9._-]+|(?:api[_-]?key|secret|password|token)\s*[:=]\s*[^\s,;]+)/gi, '[redacted]').slice(0, 240)
  const detail = redact(event.attributes?.errorCode ? `Failure code: ${event.attributes.errorCode}` : event.message || 'Agent emitted a progress event.')
  const meta = event.attributes?.agents ? `Agents: ${redact(event.attributes.agents)}` : event.attributes?.phase ? `Phase: ${redact(event.attributes.phase)}` : undefined
  return { id: event.eventId, title: labels[event.type] ?? event.type, detail, meta, tone: toneForEvent(event), time: new Date(event.occurredAt).toLocaleTimeString() }
}

function AuthView({ onAuthenticated }: { onAuthenticated: (session: AuthTokenResponse) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [tenantName, setTenantName] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      const result = mode === 'login'
        ? await login({ email: email.trim(), password })
        : await register({ email: email.trim(), password, displayName: displayName.trim(), tenantName: tenantName.trim() })
      onAuthenticated(result)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Authentication failed.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#f9f9f8] text-[#1a1c1c] flex flex-col">
      <div className="h-16 border-b border-[#c7c4d8] flex items-center justify-between px-8 bg-[#f9f9f8]/90 backdrop-blur">
        <div className="flex items-center gap-3"><Sparkles className="text-[#3525cd]" size={22} /><span className="font-headline-sm">Nexus</span></div>
        <span className="font-label-caps text-[#777587]">SECURE AGENT WORKSPACE</span>
      </div>
      <main className="flex-1 max-w-6xl w-full mx-auto px-8 py-16 grid lg:grid-cols-[1fr_410px] gap-16 items-center">
        <section>
          <p className="font-label-caps text-[#3525cd] tracking-[0.18em] mb-5">AUTOMATED DOCUMENT FORGE</p>
          <h1 className="font-display-lg text-[#1a1c1c] max-w-2xl">Turn one experiment into a visible, finished report.</h1>
          <p className="font-body-lg text-[#464555] max-w-xl mt-6">Nexus provides a real workspace: upload a Word report, watch every Agent stage, inspect failures, and download the review-gated DOCX.</p>
          <div className="grid grid-cols-3 gap-4 mt-10 max-w-xl">
            {[['01', 'Understand', 'Parse goals, tables and questions'], ['02', 'Execute', 'Generate code in a sandbox'], ['03', 'Deliver', 'Review and export DOCX']].map(([number, title, description]) => (
              <div key={number} className="border-t border-[#c7c4d8] pt-3"><span className="font-label-caps text-[#3525cd]">{number}</span><strong className="block font-body-md mt-2">{title}</strong><small className="block text-[#777587] mt-1 leading-relaxed">{description}</small></div>
            ))}
          </div>
        </section>
        <form onSubmit={submit} className="bg-white border border-[#c7c4d8] rounded-xl p-8 shadow-sm">
          <p className="font-label-caps text-[#777587] tracking-widest">YOUR WORKSPACE</p>
          <h2 className="font-headline-md mt-3">{mode === 'login' ? 'Sign in' : 'Create workspace'}</h2>
          <p className="text-sm text-[#777587] mt-2 mb-6">{mode === 'login' ? 'Continue your report automation task.' : 'Register a tenant and upload your first report.'}</p>
          {error && <div className="mb-4 border border-red-200 bg-red-50 text-red-800 p-3 rounded text-sm flex gap-2"><AlertTriangle size={17} className="shrink-0 mt-0.5" />{error}</div>}
          <label className="block text-xs font-semibold text-[#464555] mb-1">Email</label>
          <input className="w-full border border-[#c7c4d8] rounded px-3 py-2.5 mb-4 outline-none focus:border-[#3525cd]" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
          <label className="block text-xs font-semibold text-[#464555] mb-1">Password</label>
          <input className="w-full border border-[#c7c4d8] rounded px-3 py-2.5 mb-4 outline-none focus:border-[#3525cd]" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={12} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
          {mode === 'register' && <>
            <label className="block text-xs font-semibold text-[#464555] mb-1">Display name</label>
            <input className="w-full border border-[#c7c4d8] rounded px-3 py-2.5 mb-4 outline-none focus:border-[#3525cd]" value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
            <label className="block text-xs font-semibold text-[#464555] mb-1">Workspace name</label>
            <input className="w-full border border-[#c7c4d8] rounded px-3 py-2.5 mb-4 outline-none focus:border-[#3525cd]" value={tenantName} onChange={(e) => setTenantName(e.target.value)} required />
          </>}
          <button disabled={busy} className="w-full bg-[#3525cd] text-white rounded px-4 py-3 font-semibold flex items-center justify-center gap-2 disabled:opacity-60">{busy && <Loader2 size={17} className="animate-spin" />}{mode === 'login' ? 'Enter workspace' : 'Create workspace'}</button>
          <button type="button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }} className="w-full mt-4 text-sm text-[#3525cd] hover:underline">{mode === 'login' ? 'No account? Create a workspace' : 'Already registered? Sign in'}</button>
        </form>
      </main>
    </div>
  )
}

function StageTimeline({ activeStage, status, percent }: { activeStage: string; status: string; percent: number }) {
  const activeIndex = Math.max(0, stages.findIndex((stage) => stage.key === activeStage))
  return <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
    {stages.map((stage, index) => {
      const Icon = stage.icon
      const done = status === 'SUCCEEDED' || index < activeIndex
      const active = index === activeIndex && status !== 'SUCCEEDED'
      const failed = status === 'FAILED' && active
      return <div key={stage.key} className={`relative border rounded-lg p-3 min-h-[112px] transition-colors ${failed ? 'border-red-300 bg-red-50' : active ? 'border-[#3525cd] bg-[#f0f0ff]' : done ? 'border-emerald-200 bg-emerald-50/60' : 'border-[#c7c4d8] bg-white'}`}>
        <div className="flex items-center justify-between"><span className={`w-7 h-7 rounded flex items-center justify-center ${failed ? 'bg-red-600 text-white' : active ? 'bg-[#3525cd] text-white' : done ? 'bg-emerald-600 text-white' : 'bg-[#eeeeed] text-[#777587]'}`}>{done ? <CheckCircle2 size={15} /> : active ? <Loader2 size={15} className="animate-spin" /> : <Icon size={15} />}</span><span className="font-label-caps text-[#777587]">{String(index + 1).padStart(2, '0')}</span></div>
        <strong className="block font-body-md text-sm mt-3">{stage.label}</strong><span className="block text-xs text-[#777587] leading-relaxed mt-1">{stage.description}</span>
        {active && <div className="absolute left-3 right-3 bottom-2 h-1 bg-[#d9d6ff] rounded"><div className="h-full bg-[#3525cd] rounded transition-all" style={{ width: `${Math.max(4, Math.min(100, percent))}%` }} /></div>}
      </div>
    })}
  </div>
}

function EventFeed({ activities }: { activities: ActivityItem[] }) {
  return <div className="space-y-3 max-h-[420px] overflow-y-auto pr-1">{activities.length === 0 ? <div className="text-center border border-dashed border-[#c7c4d8] rounded-lg py-10 text-sm text-[#777587]">Agent events will appear here after the task starts.</div> : activities.map((item) => <div key={item.id} className="flex gap-3 border-b border-[#e8e8e7] pb-3"><span className={`mt-1 w-2 h-2 rounded-full shrink-0 ${item.tone === 'success' ? 'bg-emerald-600' : item.tone === 'error' ? 'bg-red-600' : item.tone === 'warning' ? 'bg-amber-500' : 'bg-[#3525cd]'}`} /><div className="min-w-0 flex-1"><div className="flex justify-between gap-3"><strong className="text-sm">{item.title}</strong><time className="font-label-caps text-[10px] text-[#777587] shrink-0">{item.time}</time></div><p className="text-xs text-[#464555] mt-1 break-words">{item.detail}</p>{item.meta && <span className="inline-block mt-1 px-2 py-0.5 rounded-full bg-[#f0f0ff] text-[#3525cd] font-label-caps text-[10px]">{item.meta}</span>}</div></div>)}</div>
}

function Inspector({ activities, status, percent, onRetry, onExport, onSignOut }: { activities: ActivityItem[]; status: string; percent: number; onRetry: () => void; onExport: () => void; onSignOut: () => void }) {
  return <aside className="bg-[#f3f4f3] fixed right-0 top-0 h-screen w-80 border-l border-[#c7c4d8] z-30 pt-16 hidden xl:flex flex-col"><div className="p-5 border-b border-[#c7c4d8] bg-[#f9f9f8]"><p className="font-label-caps text-[#777587] tracking-widest">THE INSPECTOR</p><h2 className="font-headline-sm mt-1">Execution facts</h2><p className="text-xs text-[#777587] mt-1">Visible task events, not hidden reasoning.</p></div><div className="p-4 flex-1 overflow-y-auto"><div className="bg-[#3525cd]/5 border-l-4 border-[#3525cd] rounded-r-lg p-4 mb-6"><div className="flex items-center gap-2 text-[#3525cd] font-label-caps"><Activity size={14} /> LIVE STATUS</div><strong className="block mt-2 text-sm">{labels[status] ?? 'Ready for a report'}</strong><div className="h-1.5 bg-[#d9d6ff] rounded mt-3 overflow-hidden"><div className="h-full bg-[#3525cd]" style={{ width: `${percent}%` }} /></div><span className="block text-right font-label-caps text-[#3525cd] mt-1">{percent}%</span></div><p className="font-label-caps text-[#777587] tracking-widest mb-3">EVENT STREAM</p><EventFeed activities={activities} /></div><div className="p-4 border-t border-[#c7c4d8] space-y-2">{status === 'FAILED' && <button onClick={onRetry} className="w-full border border-[#3525cd] text-[#3525cd] rounded px-3 py-2 text-sm flex items-center justify-center gap-2"><RefreshCw size={15} /> Retry task</button>}{status === 'SUCCEEDED' && <button onClick={onExport} className="w-full bg-[#3525cd] text-white rounded px-3 py-2 text-sm flex items-center justify-center gap-2"><Download size={15} /> Download final DOCX</button>}<button onClick={onSignOut} className="w-full text-[#777587] rounded px-3 py-2 text-sm flex items-center justify-center gap-2 hover:bg-[#e8e8e7]"><LogOut size={15} /> Sign out</button></div></aside>
}

function ReportWorkspace({ session, onSignOut }: { session: Session; onSignOut: () => void }) {
  const [currentSession, setCurrentSession] = useState(session)
  const [tab, setTab] = useState<NavigationTab>('forge')
  const [inspectorOpen, setInspectorOpen] = useState(true)
  const [file, setFile] = useState<File | null>(null)
  const [inputFile, setInputFile] = useState<File | null>(null)
  const [inputPath, setInputPath] = useState('data/input.bin')
  const [documentId, setDocumentId] = useState('')
  const [taskId, setTaskId] = useState('')
  const [status, setStatus] = useState('IDLE')
  const [percent, setPercent] = useState(0)
  const [message, setMessage] = useState('Upload a DOCX report to begin.')
  const [error, setError] = useState('')
  const [warnings, setWarnings] = useState<string[]>([])
  const [activities, setActivities] = useState<ActivityItem[]>([])
  const [busy, setBusy] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [lastStage, setLastStage] = useState('UPLOAD')
  const statusRef = useRef(status)
  const lastEventSequenceRef = useRef(0)
  const currentSessionRef = useRef(session)
  const onSignOutRef = useRef(onSignOut)
  const invalidatedRef = useRef(false)
  const refreshBlockedUntilRef = useRef(0)

  useEffect(() => {
    onSignOutRef.current = onSignOut
  }, [onSignOut])

  const activeStage = useMemo(() => {
    if (!documentId) return 'UPLOAD'
    if (!taskId) return 'PARSE'
    return lastStage
  }, [documentId, lastStage, taskId])

  function saveActivity(item: ActivityItem) {
    setActivities((current) => [item, ...current].slice(0, 80))
  }

  const invalidateSession = useCallback(() => {
    if (invalidatedRef.current) return
    invalidatedRef.current = true
    sessionStorage.removeItem('report-ai-auth')
    onSignOutRef.current()
  }, [])

  const accessToken = useCallback(async () => {
    const snapshot = currentSessionRef.current
    if (snapshot.accessTokenExpiresAt > Date.now() + 60_000) return snapshot.accessToken
    if (refreshBlockedUntilRef.current > Date.now()) {
      throw new ReportApiError('Token refresh is temporarily rate limited. Please sign in again shortly.', {
        status: 429,
        code: 'RATE_LIMITED'
      })
    }

    try {
      const renewed = await refreshSingleFlight(snapshot.refreshToken)
      const nextSession = { ...renewed, accessTokenExpiresAt: Date.now() + renewed.accessTokenExpiresInSeconds * 1000 }
      currentSessionRef.current = nextSession
      setCurrentSession(nextSession)
      sessionStorage.setItem('report-ai-auth', JSON.stringify(nextSession))
      refreshBlockedUntilRef.current = 0
      return renewed.accessToken
    } catch (cause) {
      const apiError = cause instanceof ReportApiError ? cause : null
      if (apiError?.status === 401) invalidateSession()
      if (apiError?.status === 429) refreshBlockedUntilRef.current = Date.now() + 10_000
      throw cause
    }
  }, [invalidateSession])

  function selectFile(value: File | null) {
    if (!value) return
    if (!value.name.toLowerCase().endsWith('.docx')) { setError('Only .docx reports are supported.'); return }
    if (value.size > 20 * 1024 * 1024) { setError('The report must be smaller than 20 MB.'); return }
    setFile(value); setError(''); setMessage('Ready to upload and parse.'); saveActivity({ id: crypto.randomUUID(), title: 'Report selected', detail: `${value.name} · ${sizeLabel(value.size)}`, tone: 'info', time: nowLabel() })
  }

  function selectInputFile(value: File | null) {
    if (!value) return
    if (value.size > 512 * 1024 * 1024) { setError('The experiment input must be smaller than 512 MB.'); return }
    setInputFile(value); setError('')
    saveActivity({ id: crypto.randomUUID(), title: 'Input selected', detail: `${value.name} · ${sizeLabel(value.size)}`, meta: 'Mounted read-only before preflight', tone: 'info', time: nowLabel() })
  }

  async function handleUpload() {
    if (!file) return
    setBusy(true); setError(''); setStatus('UPLOADING'); setPercent(4)
    try {
      const result = await upload(file, await accessToken(), crypto.randomUUID())
      setDocumentId(result.documentId); setWarnings(result.warnings ?? []); setStatus('DOCUMENT_READY'); setPercent(12); setLastStage('PARSE'); setMessage('Document parsed. Start the Agent when you are ready.')
      saveActivity({ id: crypto.randomUUID(), title: 'Document parsed', detail: `${result.documentId} · canonical structure persisted`, tone: 'success', meta: result.warnings?.length ? `${result.warnings.length} parser notes` : 'No parser warnings', time: nowLabel() })
    } catch (cause) { setStatus('FAILED'); setError(cause instanceof Error ? cause.message : 'Upload failed.'); saveActivity({ id: crypto.randomUUID(), title: 'Upload failed', detail: cause instanceof Error ? cause.message : 'Upload failed.', tone: 'error', time: nowLabel() }) } finally { setBusy(false) }
  }

  async function startAutomation() {
    if (!documentId) return
    setBusy(true); setError(''); setStatus('CREATED'); setPercent(0); setMessage('Task queued. Waiting for the Agent scheduler.'); setTab('flow')
    try {
      const token = await accessToken()
      const result = inputFile
        ? await createTaskWithInput(documentId, inputPath.trim() || 'data/input.bin', inputFile, token, crypto.randomUUID())
        : await createTask(documentId, token, crypto.randomUUID())
      setTaskId(result.taskId); setStatus(result.status || 'CREATED'); saveActivity({ id: crypto.randomUUID(), title: 'Agent task created', detail: `Task ${result.taskId}`, meta: 'Planner → Catalog → Preflight → Coder → Installer → Execution → Artifacts → Cleanup → Review → Export', tone: 'info', time: nowLabel() })
    } catch (cause) { setStatus('FAILED'); setLastStage('PLAN'); setError(cause instanceof Error ? cause.message : 'Could not create task.'); saveActivity({ id: crypto.randomUUID(), title: 'Task creation failed', detail: cause instanceof Error ? cause.message : 'Task creation failed.', tone: 'error', time: nowLabel() }) } finally { setBusy(false) }
  }

  function openArchivedTask(task: TaskHistoryItem) {
    setTaskId(task.taskId)
    setStatus(task.status || 'UNKNOWN')
    setPercent(task.percent)
    setLastStage(task.stage?.toUpperCase() || 'PARSE')
    if (task.documentId) setDocumentId(task.documentId)
    setMessage(task.errorSummary || `Loaded task ${task.taskId.slice(0, 12)} from the durable archive.`)
  }

  async function retryArchivedTask(task: TaskHistoryItem) {
    if (!task.documentId) {
      setError('This task does not expose a retryable document reference.')
      return
    }
    setDocumentId(task.documentId)
    setTaskId('')
    setStatus('CREATED')
    setPercent(0)
    setError('')
    setMessage('Retry queued. Waiting for the Agent scheduler.')
    try {
      const result = await createTask(task.documentId, await accessToken(), crypto.randomUUID())
      setTaskId(result.taskId)
      setStatus(result.status || 'CREATED')
      setTab('flow')
      saveActivity({ id: crypto.randomUUID(), title: 'Archived task retried', detail: `Created task ${result.taskId}`, tone: 'info', time: nowLabel() })
    } catch (cause) {
      setStatus('FAILED')
      setError(cause instanceof Error ? cause.message : 'Could not retry task.')
    }
  }

  async function exportArchivedTask(task: TaskHistoryItem) {
    if (!task.taskId) {
      setError('The archived task has no exportable task reference.')
      return
    }
    try {
      await downloadTask(task.taskId, await accessToken())
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Export failed.')
    }
  }

  function retry() { setTaskId(''); setStatus('DOCUMENT_READY'); setError(''); setMessage('Create a fresh task from the parsed document.'); setTab('flow'); void startAutomation() }

  async function exportReport() {
    if (!taskId || status !== 'SUCCEEDED') return
    try { await downloadTask(taskId, await accessToken()); saveActivity({ id: crypto.randomUUID(), title: 'Export downloaded', detail: 'The review-gated DOCX was sent to the browser.', tone: 'success', time: nowLabel() }) } catch (cause) { setError(cause instanceof Error ? cause.message : 'Export failed.') }
  }

  useEffect(() => {
    statusRef.current = status
  }, [status])

  useEffect(() => {
    if (!taskId) return
    const controller = new AbortController()
    lastEventSequenceRef.current = 0
    void (async () => {
      try {
        await streamProgress(taskId, await accessToken(), (event) => {
          if (event.eventSequence && event.eventSequence <= lastEventSequenceRef.current) return
          if (event.eventSequence) lastEventSequenceRef.current = event.eventSequence
          const nextStage = stageFromEvent(event)
          setLastStage(nextStage); setStatus((current) => { const next = event.type === 'PROGRESS' ? (event.attributes?.phase?.toUpperCase() ?? current) : event.type; statusRef.current = next; return next }); setPercent((current) => Math.max(current, event.percent)); setMessage(event.message); saveActivity(toActivity(event))
          if (event.type === 'FAILED') setError(event.attributes?.errorCode || event.message)
          if (event.type === 'SUCCEEDED') { setStatus('SUCCEEDED'); setPercent(100); setMessage('The report passed review and is ready to download.') }
        }, { signal: controller.signal })
      } catch (cause) {
        if ((cause as Error)?.name !== 'AbortError') setError(cause instanceof Error ? cause.message : 'Progress connection interrupted.')
      }
    })()
    return () => controller.abort()
  // The task id deliberately controls one stream; accessToken is refreshed inside the stream.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskId])

  const onExport = () => { void exportReport() }
  const title = tab === 'forge' ? 'Nexus Forge' : tab === 'flow' ? 'Execution Flow' : tab === 'editor' ? (file?.name || 'Report Editor') : tab === 'library' ? 'Agent Library' : 'Archive'

    return <div className="min-h-screen bg-[#f9f9f8] text-[#1a1c1c]">
    <SideNavBar currentTab={tab} onTabChange={setTab} />
    <TopAppBar currentTab={tab} documentTitle={file?.name} onExport={onExport}
      exportEnabled={status === 'SUCCEEDED' && Boolean(taskId)}
      onShare={() => void navigator.clipboard?.writeText(taskId || documentId)}
      toggleInspector={() => setInspectorOpen((open) => !open)} inspectorOpen={inspectorOpen} />
    <div className="fixed top-16 left-16 right-0 z-20 bg-[#f9f9f8]/90 border-b border-[#e8e8e7] px-8 py-2 flex items-center justify-between"><span className="font-label-caps text-[#777587]">{title.toUpperCase()}</span><span className="flex items-center gap-2 text-xs text-[#777587]"><span className={`w-2 h-2 rounded-full ${taskId && !['FAILED', 'SUCCEEDED'].includes(status) ? 'bg-amber-500 animate-pulse' : 'bg-emerald-600'}`} />{taskId ? (labels[status] ?? status) : 'Workspace ready'}</span></div>
    <main className={`${inspectorOpen && (tab === 'flow' || tab === 'editor') ? 'xl:mr-80' : ''} ml-16 pt-28 pb-16 min-h-screen`}>
      {tab === 'forge' && <section className="px-8 max-w-7xl mx-auto -mt-6 mb-8"><div className="bg-white border border-[#c7c4d8] rounded-xl p-5 shadow-2xs"><div className="flex items-start justify-between gap-4"><div><p className="font-label-caps text-[#777587]">02 / EXPERIMENT INPUT</p><h2 className="font-headline-sm mt-2">Attach real data for the sandbox</h2><p className="text-xs text-[#777587] mt-1 max-w-2xl">GAN/CycleGAN reports require a real dataset or archive. The template image is never used as training data.</p></div><Database className="text-[#3525cd] shrink-0" size={20} /></div><div className="flex flex-col sm:flex-row gap-3 mt-4"><label htmlFor="nexus-input-file" className="flex-1 border border-dashed border-[#aaa7c4] bg-[#f9f9f8] rounded-lg px-3 py-3 cursor-pointer hover:border-[#3525cd] transition-colors"><span className="block text-sm font-semibold truncate">{inputFile ? inputFile.name : 'Choose dataset, archive, or input file'}</span><span className="block text-xs text-[#777587] mt-1">{inputFile ? `${sizeLabel(inputFile.size)} · mounted read-only` : 'Up to 512 MB'}</span></label><input id="nexus-input-file" type="file" className="hidden" onChange={(event) => selectInputFile(event.target.files?.[0] ?? null)} /><input aria-label="Input path" value={inputPath} onChange={(event) => setInputPath(event.target.value)} placeholder="data/input.bin" className="sm:w-44 border border-[#c7c4d8] rounded-lg px-3 py-2 text-sm bg-white outline-none focus:border-[#3525cd]" /></div></div></section>}
      {error && <div className="mx-8 mb-5 border border-red-200 bg-red-50 text-red-800 rounded-lg p-3 flex items-start gap-2 text-sm"><AlertTriangle size={17} className="shrink-0 mt-0.5" /><span className="flex-1">{error}</span><button onClick={() => setError('')}><X size={16} /></button></div>}
      {tab === 'forge' && <section className="px-8 max-w-7xl mx-auto animate-fade-in"><div className="mb-12"><p className="font-label-caps text-[#3525cd] tracking-widest">AUTOMATED REPORT FORGE</p><h1 className="font-display-lg mt-4 max-w-3xl">A report is more than a file. It is a traceable execution.</h1><p className="font-body-lg text-[#464555] mt-5 max-w-2xl">Nexus gives every document a visible pipeline from ingestion through sandbox execution, review, and final Word export.</p></div><div className="grid lg:grid-cols-[1.4fr_0.6fr] gap-8"><div className="bg-white border border-[#c7c4d8] rounded-xl p-7 shadow-2xs"><div className="flex items-center justify-between mb-5"><div><p className="font-label-caps text-[#777587]">01 / INGEST</p><h2 className="font-headline-sm mt-2">Import experiment report</h2></div><FileText className="text-[#3525cd]" /></div><div onDragEnter={() => setDragging(true)} onDragLeave={() => setDragging(false)} onDragOver={(event) => { event.preventDefault(); setDragging(true) }} onDrop={(event) => { event.preventDefault(); setDragging(false); selectFile(event.dataTransfer.files?.[0] ?? null) }} className={`border-2 border-dashed rounded-xl min-h-48 flex items-center justify-center text-center transition-colors ${dragging ? 'border-[#3525cd] bg-[#f0f0ff]' : file ? 'border-emerald-400 bg-emerald-50/50' : 'border-[#c7c4d8] bg-[#f9f9f8]'}`}><input id="nexus-report-file" type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" className="hidden" onChange={(event) => selectFile(event.target.files?.[0] ?? null)} /><label htmlFor="nexus-report-file" className="cursor-pointer px-8"><UploadCloud className="mx-auto text-[#3525cd] mb-3" size={34} /><strong className="block">{file ? file.name : 'Drop a Word report here, or choose a file'}</strong><span className="block text-sm text-[#777587] mt-2">{file ? `${sizeLabel(file.size)} · ${documentId ? 'Parsed' : 'Ready to parse'}` : '.docx only · max 20 MB'}</span></label></div><div className="flex flex-wrap items-center gap-3 mt-5"><button onClick={() => void handleUpload()} disabled={!file || busy || Boolean(documentId)} className="bg-[#3525cd] text-white px-5 py-2.5 rounded font-semibold flex items-center gap-2 disabled:opacity-40"><UploadCloud size={17} />{busy && status === 'UPLOADING' ? 'Parsing...' : 'Upload and parse'}</button><button onClick={() => void startAutomation()} disabled={!documentId || busy || Boolean(taskId)} className="border border-[#3525cd] text-[#3525cd] px-5 py-2.5 rounded font-semibold flex items-center gap-2 disabled:opacity-40"><Play size={16} />Start Agent</button><span className="text-xs text-[#777587]">{message}</span></div></div><div className="space-y-5"><div className="bg-[#3525cd] text-white rounded-xl p-6 shadow-sm"><p className="font-label-caps text-white/70">CURRENT STEP</p><h2 className="font-headline-sm mt-3">{documentId ? taskId ? (labels[status] ?? status) : 'Start Agent automation' : 'Choose a report'}</h2><p className="text-sm text-white/75 mt-3 leading-relaxed">{documentId ? 'The canonical document is ready. Start the traceable Agent pipeline when you are ready.' : 'Upload a DOCX experiment report to create a real task.'}</p>{documentId && !taskId && <button onClick={() => void startAutomation()} className="mt-5 bg-white text-[#3525cd] px-4 py-2 rounded font-semibold flex items-center gap-2"><ArrowRight size={16} />Start now</button>}</div><div className="bg-white border border-[#c7c4d8] rounded-xl p-5"><p className="font-label-caps text-[#777587] mb-4">PIPELINE GUARANTEES</p>{[['Tenant scoped', ShieldCheck], ['Docker isolated', Terminal], ['Review gated', ClipboardCheck]].map(([label, Icon]) => <div key={String(label)} className="flex items-center gap-2 text-sm text-[#464555] mb-3"><Icon size={16} className="text-[#3525cd]" />{String(label)}</div>)}</div></div></div>{documentId && <div className="mt-8"><p className="font-label-caps text-[#777587] mb-3">LIVE PIPELINE</p><StageTimeline activeStage={activeStage} status={status} percent={percent} /></div>}</section>}
      {tab === 'flow' && <section className="px-8 max-w-7xl mx-auto animate-fade-in"><div className="mb-8 flex items-start justify-between"><div><p className="font-label-caps text-[#3525cd] tracking-widest">FLOW VISUALIZATION</p><h1 className="font-headline-md mt-3">Agent execution pipeline</h1><p className="text-[#464555] mt-2 max-w-2xl">Every state transition is visible: queue, parse, plan, generate, run, review, and export.</p></div>{taskId && <span className="font-label-caps text-[#777587]">TASK {taskId.slice(0, 12)}</span>}</div><div className="bg-white border border-[#c7c4d8] rounded-xl p-6 shadow-2xs"><div className="flex items-center justify-between mb-6"><div className="flex items-center gap-3"><Bot className="text-[#3525cd]" /><strong>{file?.name || 'No report selected'}</strong></div><span className="font-label-caps text-[#3525cd]">{percent}%</span></div><StageTimeline activeStage={activeStage} status={status} percent={percent} /></div><div className="grid lg:grid-cols-[1fr_0.45fr] gap-6 mt-6"><div className="bg-white border border-[#c7c4d8] rounded-xl p-6"><div className="flex justify-between items-center mb-5"><div><p className="font-label-caps text-[#777587]">AUDITABLE EVENTS</p><h2 className="font-headline-sm mt-2">Live task activity</h2></div><Activity className="text-[#3525cd]" /></div><EventFeed activities={activities} /></div><div className="bg-white border border-[#c7c4d8] rounded-xl p-6"><p className="font-label-caps text-[#777587]">TASK DETAILS</p><dl className="mt-4 space-y-3 text-sm"><div className="flex justify-between gap-4"><dt className="text-[#777587]">Document</dt><dd className="font-medium text-right truncate">{documentId || '-'}</dd></div><div className="flex justify-between gap-4"><dt className="text-[#777587]">Status</dt><dd className="font-medium">{labels[status] ?? status}</dd></div><div className="flex justify-between gap-4"><dt className="text-[#777587]">Last phase</dt><dd className="font-medium">{activeStage}</dd></div></dl>{status === 'FAILED' && <button onClick={retry} className="mt-6 w-full border border-[#3525cd] text-[#3525cd] rounded px-3 py-2 text-sm flex items-center justify-center gap-2"><RefreshCw size={15} /> Retry task</button>}{status === 'SUCCEEDED' && <button onClick={onExport} className="mt-6 w-full bg-[#3525cd] text-white rounded px-3 py-2 text-sm flex items-center justify-center gap-2"><Download size={15} /> Download DOCX</button>}</div></div></section>}
      {tab === 'editor' && <section className="px-8 max-w-5xl mx-auto animate-fade-in"><div className="mb-8"><p className="font-label-caps text-[#3525cd] tracking-widest">DOCUMENT WORKSPACE</p><h1 className="font-headline-md mt-3">{file?.name || 'Report document'}</h1><p className="text-[#464555] mt-2">The AI works against canonical JSON; the final builder writes back to Word only after review.</p></div><article className="bg-white border border-[#c7c4d8] rounded-xl p-8 lg:p-14 shadow-2xs min-h-[520px]"><div className="flex items-center justify-between border-b border-[#e8e8e7] pb-5"><div><p className="font-label-caps text-[#777587]">CANONICAL DOCUMENT</p><h2 className="font-headline-md mt-2">{file?.name || 'Awaiting upload'}</h2></div><span className={`font-label-caps px-2 py-1 rounded ${status === 'SUCCEEDED' ? 'bg-emerald-100 text-emerald-800' : 'bg-[#eeeeed] text-[#464555]'}`}>{labels[status] ?? status}</span></div><div className="grid md:grid-cols-3 gap-4 mt-8">{[['Source', documentId ? 'Stored in MinIO' : 'Waiting', FileText], ['Execution', taskId ? `${percent}% complete` : 'Not started', Terminal], ['Release', status === 'SUCCEEDED' ? 'Ready to download' : 'Review pending', ShieldCheck]].map(([label, value, Icon]) => <div key={String(label)} className="border border-[#c7c4d8] rounded-lg p-4"><Icon size={18} className="text-[#3525cd]" /><p className="font-label-caps text-[#777587] mt-3">{String(label)}</p><strong className="block mt-1 text-sm">{String(value)}</strong></div>)}</div>{warnings.length > 0 && <div className="mt-8 bg-amber-50 border border-amber-200 rounded-lg p-4"><div className="flex items-center gap-2 text-amber-800 font-semibold text-sm"><AlertTriangle size={16} /> Parser notes</div><ul className="list-disc pl-5 mt-2 text-sm text-amber-900">{warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul></div>}<div className="mt-10 flex flex-wrap gap-3"><button onClick={() => setTab('flow')} className="border border-[#3525cd] text-[#3525cd] px-4 py-2 rounded font-semibold flex items-center gap-2"><Activity size={16} /> View execution</button>{status === 'SUCCEEDED' && <button onClick={onExport} className="bg-[#3525cd] text-white px-4 py-2 rounded font-semibold flex items-center gap-2"><Download size={16} /> Export final DOCX</button>}</div></article></section>}
      {tab === 'library' && <section className="px-8 max-w-6xl mx-auto animate-fade-in"><p className="font-label-caps text-[#3525cd] tracking-widest">AGENT LIBRARY</p><h1 className="font-display-lg mt-4">Tools behind the report pipeline.</h1><div className="grid md:grid-cols-2 lg:grid-cols-3 gap-5 mt-10">{[['Planner', 'Breaks the experiment into an explicit execution plan.', ClipboardCheck], ['Coder', 'Generates bounded, runnable experiment code.', Code2], ['Runtime', 'Runs code in the isolated Docker sandbox.', Terminal], ['Document', 'Maps answers and artifacts back to the canonical model.', FileText], ['Reviewer', 'Checks omissions and gates the Word release.', ShieldCheck], ['Storage', 'Keeps source, assets and output tenant-scoped.', Database]].map(([name, description, Icon]) => <div key={String(name)} className="bg-white border border-[#c7c4d8] rounded-xl p-5 hover:border-[#3525cd] transition-colors"><Icon className="text-[#3525cd]" size={22} /><h2 className="font-headline-sm text-lg mt-5">{String(name)}</h2><p className="text-sm text-[#464555] mt-2 leading-relaxed">{String(description)}</p></div>)}</div></section>}
      {tab === 'archive' && <AgentObservatory getAccessToken={accessToken} activeTaskId={taskId} onOpenTask={openArchivedTask} onRetryTask={(task) => void retryArchivedTask(task)} onExportTask={(task) => void exportArchivedTask(task)} onOpenForge={() => setTab('forge')} onSignOut={onSignOut} />}
    </main>
    {inspectorOpen && (tab === 'flow' || tab === 'editor') && <Inspector activities={activities} status={status} percent={percent} onRetry={retry} onExport={onExport} onSignOut={onSignOut} />}
  </div>
}

export default function ReportWorkspaceApp() {
  const [session, setSession] = useState<Session | null>(() => {
    const stored = sessionStorage.getItem('report-ai-auth')
    return stored ? JSON.parse(stored) as Session : null
  })

  function authenticated(value: AuthTokenResponse) {
    const next = { ...value, accessTokenExpiresAt: Date.now() + value.accessTokenExpiresInSeconds * 1000 }
    sessionStorage.setItem('report-ai-auth', JSON.stringify(next))
    setSession(next)
  }

  async function signOut() {
    if (session?.refreshToken) { try { await logout(session.refreshToken) } catch { /* local sign out remains safe */ } }
    sessionStorage.removeItem('report-ai-auth')
    setSession(null)
  }

  return session ? <ReportWorkspace session={session} onSignOut={() => void signOut()} /> : <AuthView onAuthenticated={authenticated} />
}
