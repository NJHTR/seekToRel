import { useEffect, useMemo, useState } from 'react'
import {
  Activity,
  ArrowRight,
  Bot,
  Check,
  CheckCircle2,
  ChevronRight,
  CircleDashed,
  Code2,
  GitBranch,
  GitCommitHorizontal,
  Layers3,
  LockKeyhole,
  Play,
  RotateCcw,
  ShieldCheck,
  Split,
  Terminal,
  Timer,
  UploadCloud,
  Users,
  XCircle
} from 'lucide-react'

type AgentControlRoomProps = {
  file?: File | null
  documentId?: string
  taskId?: string
  status?: string
  percent?: number
  onOpenForge: () => void
  onStartPipeline: () => void
  onOpenFlow: () => void
}

type AgentStatus = 'running' | 'queued' | 'done' | 'blocked'

type Agent = {
  id: string
  name: string
  role: string
  status: AgentStatus
  progress: number
  branch: string
  sandbox: string
  color: string
  task: string
  code: string
  log: string[]
}

const baseAgents: Agent[] = [
  {
    id: 'review-01',
    name: 'Sentinel',
    role: 'Safety reviewer',
    status: 'done',
    progress: 100,
    branch: 'review/intake-1',
    sandbox: 'review-sbox-04',
    color: 'bg-teal-600',
    task: 'Checked file type, prompt injection, and experiment relevance.',
    code: 'policy.scan(document, ruleset="experiment-safe-v3")',
    log: ['09:42:11  DOCX signature verified', '09:42:13  0 executable payloads found', '09:42:17  score=96  decision=allow']
  },
  {
    id: 'planner-02',
    name: 'Atlas',
    role: 'Plan architect',
    status: 'running',
    progress: 68,
    branch: 'plan/gan-ablation',
    sandbox: 'plan-sbox-11',
    color: 'bg-indigo-600',
    task: 'Splitting 4 questions into serial and parallel execution units.',
    code: 'graph = planner.decompose(report, strategy="dependency-aware")',
    log: ['09:43:02  Parsed 4 questions', '09:43:06  dependency edge Q2 -> Q4', '09:43:09  parallel groups=2  confidence=0.91']
  },
  {
    id: 'runtime-07',
    name: 'Quanta',
    role: 'Experiment runner',
    status: 'running',
    progress: 42,
    branch: 'run/q1-baseline',
    sandbox: 'run-sbox-18',
    color: 'bg-orange-500',
    task: 'Running baseline training with the mounted experiment dataset.',
    code: 'python train.py --config configs/baseline.yaml --seed 42',
    log: ['09:43:21  image=scientific:2026.08 locked', '09:43:28  dataset mounted read-only', '09:43:34  epoch=12/30  loss=0.184']
  },
  {
    id: 'review-02',
    name: 'Arbiter',
    role: 'Second-pass reviewer',
    status: 'queued',
    progress: 0,
    branch: 'review/second-pass',
    sandbox: 'review-sbox-22',
    color: 'bg-rose-500',
    task: 'Will challenge first-pass exclusions and decide whether to recover them.',
    code: 'arbiter.recheck(excluded_items, evidence=first_review)',
    log: ['Waiting for first-pass verdict and plan snapshot.']
  }
]

const pipelineSteps = [
  { id: 'upload', label: 'Upload', caption: 'source document', icon: UploadCloud },
  { id: 'screen-1', label: 'Safety 1', caption: 'permissive screen', icon: ShieldCheck },
  { id: 'score', label: 'Score', caption: 'summary + rating', icon: Activity },
  { id: 'screen-2', label: 'Safety 2', caption: 'strict recovery', icon: RotateCcw },
  { id: 'plan', label: 'Plan', caption: 'dependency graph', icon: GitBranch },
  { id: 'dispatch', label: 'Dispatch', caption: 'agent allocation', icon: Users },
  { id: 'run', label: 'Execute', caption: 'sandbox + logs', icon: Terminal },
  { id: 'release', label: 'Release', caption: 'review + export', icon: CheckCircle2 }
]

const gitEvents = [
  { hash: '8f4a2c1', time: '09:43:34', agent: 'Quanta', action: 'checkpoint', detail: 'Persisted epoch 12 metrics and stdout', tone: 'orange' },
  { hash: '17bca90', time: '09:43:09', agent: 'Atlas', action: 'plan', detail: 'Added dependency edge Q2 -> Q4', tone: 'indigo' },
  { hash: 'b0e91aa', time: '09:42:17', agent: 'Sentinel', action: 'review', detail: 'First-pass verdict: allow with notes', tone: 'teal' },
  { hash: '4d2c6fe', time: '09:42:03', agent: 'System', action: 'ingest', detail: 'Created immutable source snapshot', tone: 'slate' }
]

function statusLabel(status: AgentStatus) {
  return status === 'running' ? 'Running' : status === 'queued' ? 'Queued' : status === 'done' ? 'Complete' : 'Blocked'
}

function statusIcon(status: AgentStatus) {
  if (status === 'done') return <CheckCircle2 size={14} />
  if (status === 'blocked') return <XCircle size={14} />
  if (status === 'running') return <CircleDashed size={14} className="animate-spin" />
  return <Timer size={14} />
}

export default function AgentControlRoom({
  file,
  documentId,
  taskId,
  status = 'IDLE',
  percent = 0,
  onOpenForge,
  onStartPipeline,
  onOpenFlow
}: AgentControlRoomProps) {
  const [agents, setAgents] = useState(baseAgents)
  const [selectedAgentId, setSelectedAgentId] = useState('runtime-07')
  const [selectedStep, setSelectedStep] = useState('screen-2')
  const [reviewMode, setReviewMode] = useState<'overview' | 'reasons'>('overview')
  const [simulating, setSimulating] = useState(false)
  const [localProgress, setLocalProgress] = useState(Math.max(42, percent))

  useEffect(() => {
    if (percent > 0) setLocalProgress((value) => Math.max(value, percent))
  }, [percent])

  useEffect(() => {
    if (!simulating) return
    const timer = window.setInterval(() => {
      setLocalProgress((value) => {
        const next = Math.min(100, value + 4)
        setAgents((current) => current.map((agent) => agent.id === 'runtime-07'
          ? { ...agent, progress: next, status: next >= 100 ? 'done' : 'running' }
          : agent.id === 'planner-02' && next > 70
            ? { ...agent, progress: Math.min(100, next + 22), status: next >= 92 ? 'done' : 'running' }
            : agent))
        if (next >= 100) setSimulating(false)
        return next
      })
    }, 800)
    return () => window.clearInterval(timer)
  }, [simulating])

  const selectedAgent = useMemo(() => agents.find((agent) => agent.id === selectedAgentId) ?? agents[0], [agents, selectedAgentId])
  const activeStepIndex = status === 'SUCCEEDED' ? pipelineSteps.length - 1 : Math.min(pipelineSteps.length - 1, localProgress > 70 ? 6 : 4)
  const hasSource = Boolean(file || documentId)

  return (
    <section className="px-4 sm:px-8 max-w-[1500px] mx-auto pb-12 animate-fade-in">
      <div className="flex flex-col xl:flex-row xl:items-end justify-between gap-5 mb-7">
        <div>
          <p className="font-label-caps text-[#3525cd] tracking-[0.18em]">MISSION CONTROL / TRACE 8F4A</p>
          <h1 className="font-headline-md mt-3 text-[#171824]">Experiment intake & agent observability</h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-[#5e6070]">A review-gated workspace for turning one uploaded report into a dependency-aware plan, bounded agent runs, and an exportable evidence trail.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="inline-flex items-center gap-2 rounded-full border border-teal-200 bg-teal-50 px-3 py-2 text-xs font-semibold text-teal-800"><span className="h-2 w-2 rounded-full bg-teal-500" />{hasSource ? 'Source mounted' : 'Waiting for source'}</span>
          <button onClick={onOpenForge} className="inline-flex items-center gap-2 rounded-md border border-[#c9c7db] bg-white px-3 py-2 text-sm font-semibold text-[#343444] hover:border-[#3525cd]"><UploadCloud size={15} /> Upload report</button>
          <button onClick={() => { setSimulating(true); onStartPipeline() }} className="inline-flex items-center gap-2 rounded-md bg-[#171824] px-3 py-2 text-sm font-semibold text-white hover:bg-[#2b2d3d]"><Play size={15} /> {taskId ? 'Resume agents' : 'Start review flow'}</button>
        </div>
      </div>

      <div className="grid grid-cols-2 xl:grid-cols-4 gap-3 mb-6">
        {[
          { label: 'Safety gate', value: '1 / 2', detail: 'second pass queued', accent: 'text-teal-700', icon: ShieldCheck },
          { label: 'Plan coverage', value: '86%', detail: '4 questions mapped', accent: 'text-indigo-700', icon: Layers3 },
          { label: 'Agents online', value: '3 / 4', detail: '2 running · 1 queued', accent: 'text-orange-700', icon: Bot },
          { label: 'Agent Git', value: '12', detail: 'immutable events today', accent: 'text-[#6d4b9e]', icon: GitCommitHorizontal }
        ].map(({ label, value, detail, accent, icon: Icon }) => <div key={label} className="border border-[#d8d6e3] bg-white p-4 shadow-[0_6px_18px_rgba(31,31,48,0.04)]"><div className="flex items-center justify-between"><span className="font-label-caps text-[10px] text-[#777587]">{label}</span><Icon size={16} className={accent} /></div><strong className={`block mt-3 text-2xl ${accent}`}>{value}</strong><span className="mt-1 block text-xs text-[#777587]">{detail}</span></div>)}
      </div>

      <div className="border border-[#d8d6e3] bg-white p-4 sm:p-5 mb-6 overflow-x-auto">
        <div className="flex min-w-[900px] items-center justify-between gap-1">
          {pipelineSteps.map((step, index) => {
            const Icon = step.icon
            const done = index < activeStepIndex || status === 'SUCCEEDED'
            const active = index === activeStepIndex && status !== 'SUCCEEDED'
            return <div key={step.id} className="flex min-w-[100px] flex-1 items-center">
              <button onClick={() => setSelectedStep(step.id)} className="group flex flex-col items-center text-center w-full">
                <span className={`grid h-9 w-9 place-items-center rounded-full border-2 transition-colors ${done ? 'border-teal-500 bg-teal-500 text-white' : active ? 'border-[#3525cd] bg-[#f0efff] text-[#3525cd]' : 'border-[#d8d6e3] bg-[#fafafa] text-[#9695a4]'} ${selectedStep === step.id ? 'ring-4 ring-[#eceaff]' : ''}`}>{done ? <Check size={16} /> : <Icon size={16} />}</span>
                <span className={`mt-2 text-xs font-semibold ${active ? 'text-[#3525cd]' : done ? 'text-teal-700' : 'text-[#646575]'}`}>{step.label}</span>
                <span className="mt-0.5 text-[10px] text-[#9594a1]">{step.caption}</span>
              </button>
              {index < pipelineSteps.length - 1 && <span className={`h-px flex-1 ${index < activeStepIndex ? 'bg-teal-400' : 'bg-[#dedde7]'}`} />}
            </div>
          })}
        </div>
      </div>

      <div className="grid xl:grid-cols-[0.9fr_1.2fr_0.95fr] gap-5 items-start">
        <section className="border border-[#d8d6e3] bg-white">
          <div className="border-b border-[#e7e5ee] px-5 py-4 flex items-start justify-between"><div><p className="font-label-caps text-[10px] text-[#777587]">REVIEW GATE</p><h2 className="mt-1 text-base font-semibold">Two-pass report review</h2></div><LockKeyhole size={18} className="text-teal-700" /></div>
          <div className="flex border-b border-[#e7e5ee] px-5"><button onClick={() => setReviewMode('overview')} className={`border-b-2 px-1 py-3 mr-5 text-xs font-semibold ${reviewMode === 'overview' ? 'border-[#3525cd] text-[#3525cd]' : 'border-transparent text-[#777587]'}`}>Verdicts</button><button onClick={() => setReviewMode('reasons')} className={`border-b-2 px-1 py-3 text-xs font-semibold ${reviewMode === 'reasons' ? 'border-[#3525cd] text-[#3525cd]' : 'border-transparent text-[#777587]'}`}>Why items moved</button></div>
          {reviewMode === 'overview' ? <div className="p-5 space-y-4">
            <div className="border-l-4 border-teal-500 bg-teal-50/60 p-3"><div className="flex items-center justify-between"><strong className="text-sm text-teal-900">First pass · Sentinel</strong><span className="text-sm font-bold text-teal-800">96 / 100</span></div><p className="mt-1 text-xs leading-5 text-teal-900/75">Permissive screen accepted the report and flagged 2 low-confidence claims for evidence review.</p><span className="mt-2 inline-flex rounded-full bg-white px-2 py-1 text-[10px] font-bold uppercase tracking-wide text-teal-700">Allowed with notes</span></div>
            <div className="border-l-4 border-orange-400 bg-orange-50/70 p-3"><div className="flex items-center justify-between"><strong className="text-sm text-orange-950">Second pass · Arbiter</strong><span className="text-sm font-bold text-orange-800">Queued</span></div><p className="mt-1 text-xs leading-5 text-orange-950/70">Challenges exclusions, checks recovery evidence, and returns only the items that should rejoin the main line.</p><button onClick={() => setSelectedStep('screen-2')} className="mt-3 inline-flex items-center gap-1 text-xs font-bold text-orange-800 hover:underline">Inspect recovery queue <ChevronRight size={13} /></button></div>
            <div className="grid grid-cols-3 gap-2 pt-1"><div className="bg-[#f7f7fa] p-2"><strong className="block text-lg text-[#171824]">14</strong><span className="text-[10px] text-[#777587]">claims read</span></div><div className="bg-[#f7f7fa] p-2"><strong className="block text-lg text-[#171824]">2</strong><span className="text-[10px] text-[#777587]">recovery candidates</span></div><div className="bg-[#f7f7fa] p-2"><strong className="block text-lg text-[#171824]">0</strong><span className="text-[10px] text-[#777587]">unsafe payloads</span></div></div>
          </div> : <div className="p-5 space-y-3"><p className="text-xs leading-5 text-[#5e6070]">Every exclusion keeps a machine-readable reason so the second reviewer can challenge the exact decision instead of rereading the entire report.</p>{['Claim C-04 · missing control group', 'Image table · unsupported resolution', 'Question Q-03 · duplicate of Q-01'].map((item, index) => <div key={item} className="flex gap-3 border border-[#ebe9f2] p-3"><span className="grid h-5 w-5 shrink-0 place-items-center rounded-full bg-orange-100 text-[10px] font-bold text-orange-800">{index + 1}</span><div><strong className="block text-xs text-[#343444]">{item}</strong><span className="mt-1 block text-[11px] leading-4 text-[#777587]">Reason recorded by Sentinel · confidence {index === 0 ? '0.58' : '0.64'}</span></div></div>)}</div>}
        </section>

        <section className="border border-[#d8d6e3] bg-white">
          <div className="border-b border-[#e7e5ee] px-5 py-4 flex items-start justify-between"><div><p className="font-label-caps text-[10px] text-[#777587]">PLAN GRAPH / {selectedStep === 'plan' ? 'INSPECTING' : 'LIVE SNAPSHOT'}</p><h2 className="mt-1 text-base font-semibold">Dependency-aware execution plan</h2></div><GitBranch size={18} className="text-indigo-700" /></div>
          <div className="p-5">
            <div className="flex items-center gap-2 text-[10px] font-semibold uppercase tracking-wide text-[#777587] mb-3"><span className="inline-flex items-center gap-1 rounded-full bg-indigo-50 px-2 py-1 text-indigo-700"><Split size={12} /> Parallel</span><span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-1 text-slate-700"><ArrowRight size={12} /> Serial dependency</span></div>
            <div className="grid grid-cols-[1fr_30px_1fr] items-center gap-y-3">
              <PlanNode title="Q1 · baseline" subtitle="independent" status="done" tone="teal" /><ArrowRight size={15} className="mx-auto text-teal-500" /><PlanNode title="Q3 · compare" subtitle="waits for Q1" status="queued" tone="slate" />
              <PlanNode title="Q2 · ablation" subtitle="independent" status="running" tone="indigo" /><ArrowRight size={15} className="mx-auto text-indigo-400" /><PlanNode title="Q4 · synthesis" subtitle="waits for Q2 + Q3" status="queued" tone="slate" />
            </div>
            <div className="mt-5 flex items-center justify-between border-t border-[#ebe9f2] pt-4"><span className="text-xs text-[#777587]">2 parallel lanes · 1 serial join</span><button onClick={onOpenFlow} className="inline-flex items-center gap-1 text-xs font-semibold text-[#3525cd] hover:underline">Open full flow <ChevronRight size={13} /></button></div>
          </div>
        </section>

        <section className="border border-[#d8d6e3] bg-white">
          <div className="border-b border-[#e7e5ee] px-5 py-4 flex items-start justify-between"><div><p className="font-label-caps text-[10px] text-[#777587]">AGENT FLEET</p><h2 className="mt-1 text-base font-semibold">Who is doing what</h2></div><Users size={18} className="text-orange-600" /></div>
          <div className="p-3 space-y-2">{agents.map((agent) => <button key={agent.id} onClick={() => setSelectedAgentId(agent.id)} className={`w-full border p-3 text-left transition-colors ${selectedAgentId === agent.id ? 'border-[#3525cd] bg-[#f7f6ff]' : 'border-[#ebe9f2] hover:border-[#b9b5e4]'}`}><div className="flex items-start gap-3"><span className={`grid h-8 w-8 shrink-0 place-items-center rounded-md text-white ${agent.color}`}><Bot size={15} /></span><span className="min-w-0 flex-1"><span className="flex items-center justify-between gap-2"><strong className="truncate text-xs">{agent.name}</strong><span className={`inline-flex items-center gap-1 text-[10px] font-semibold ${agent.status === 'done' ? 'text-teal-700' : agent.status === 'blocked' ? 'text-rose-700' : agent.status === 'running' ? 'text-indigo-700' : 'text-[#777587]'}`}>{statusIcon(agent.status)}{statusLabel(agent.status)}</span></span><span className="mt-0.5 block truncate text-[10px] text-[#777587]">{agent.role}</span><span className="mt-2 block h-1 rounded-full bg-[#ebe9f2]"><span className={`block h-full rounded-full ${agent.status === 'done' ? 'bg-teal-500' : agent.status === 'running' ? 'bg-indigo-500' : 'bg-[#c9c7d6]'}`} style={{ width: `${agent.progress}%` }} /></span><span className="mt-1 block text-[10px] text-[#777587]">{agent.progress}% · {agent.task}</span></span></div></button>)}</div>
        </section>
      </div>

      <div className="grid xl:grid-cols-[1.25fr_0.75fr] gap-5 mt-5">
        <section className="border border-[#d8d6e3] bg-[#171824] text-white">
          <div className="flex items-start justify-between border-b border-white/10 px-5 py-4"><div><p className="font-label-caps text-[10px] text-white/50">LIVE AGENT INSPECTOR</p><h2 className="mt-1 text-base font-semibold">{selectedAgent.name} <span className="font-normal text-white/45">/ {selectedAgent.role}</span></h2></div><span className="inline-flex items-center gap-1.5 rounded-full border border-white/15 px-2 py-1 text-[10px] text-white/65"><span className={`h-1.5 w-1.5 rounded-full ${selectedAgent.status === 'running' ? 'bg-orange-400 animate-pulse' : selectedAgent.status === 'done' ? 'bg-teal-400' : 'bg-slate-400'}`} />{statusLabel(selectedAgent.status)}</span></div>
          <div className="grid md:grid-cols-[1fr_0.82fr] gap-5 p-5"><div><div className="flex items-center justify-between text-xs text-white/55"><span>Current command</span><span>{selectedAgent.progress}%</span></div><div className="mt-2 h-2 rounded-full bg-white/10"><div className="h-full rounded-full bg-orange-400 transition-all" style={{ width: `${selectedAgent.progress}%` }} /></div><pre className="mt-4 overflow-x-auto border border-white/10 bg-black/20 p-4 text-xs leading-6 text-teal-200"><code><span className="text-white/35">$ </span>{selectedAgent.code}</code></pre><div className="mt-3 flex flex-wrap gap-2 text-[10px] text-white/55"><span className="inline-flex items-center gap-1 rounded bg-white/10 px-2 py-1"><GitBranch size={11} />{selectedAgent.branch}</span><span className="inline-flex items-center gap-1 rounded bg-white/10 px-2 py-1"><Terminal size={11} />{selectedAgent.sandbox}</span><span className="inline-flex items-center gap-1 rounded bg-white/10 px-2 py-1"><Timer size={11} />12m 08s</span></div></div><div><div className="mb-2 flex items-center gap-2 text-xs font-semibold text-white/70"><Terminal size={14} className="text-orange-300" /> stdout / audit log</div><div className="border border-white/10 bg-black/20 p-3 font-mono text-[11px] leading-5 text-white/65">{selectedAgent.log.map((line) => <div key={line} className="whitespace-pre-wrap">{line}</div>)}<div className="mt-2 text-orange-300">▌ waiting for next event</div></div></div></div>
        </section>

        <section className="border border-[#d8d6e3] bg-white">
          <div className="flex items-start justify-between border-b border-[#e7e5ee] px-5 py-4"><div><p className="font-label-caps text-[10px] text-[#777587]">AGENT GIT / AUDIT TRAIL</p><h2 className="mt-1 text-base font-semibold">Every action has a commit</h2></div><GitCommitHorizontal size={18} className="text-[#6d4b9e]" /></div>
          <div className="p-5">{gitEvents.map((event, index) => <div key={event.hash} className="relative flex gap-3 pb-5 last:pb-0"><div className="flex w-4 flex-col items-center"><span className={`z-10 mt-0.5 h-2.5 w-2.5 rounded-full ${event.tone === 'orange' ? 'bg-orange-500' : event.tone === 'indigo' ? 'bg-indigo-500' : event.tone === 'teal' ? 'bg-teal-500' : 'bg-slate-400'}`} />{index < gitEvents.length - 1 && <span className="h-full w-px bg-[#e2e0ea]" />}</div><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><code className="text-[11px] font-bold text-[#6d4b9e]">{event.hash}</code><span className="text-[10px] text-[#9998a6]">{event.time}</span><span className="rounded bg-[#f4f1f9] px-1.5 py-0.5 text-[10px] font-semibold text-[#6d4b9e]">{event.action}</span></div><strong className="mt-1 block text-xs text-[#343444]">{event.agent}</strong><p className="mt-0.5 text-[11px] leading-4 text-[#777587]">{event.detail}</p></div></div>)}<button onClick={onOpenFlow} className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-[#3525cd] hover:underline">Open complete audit history <ChevronRight size={13} /></button></div>
        </section>
      </div>

      <div className="mt-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 border border-[#d8d6e3] bg-[#faf9fd] px-5 py-4"><div className="flex items-start gap-3"><span className="mt-0.5 grid h-8 w-8 place-items-center rounded-md bg-[#e8e5ff] text-[#3525cd]"><Code2 size={16} /></span><div><strong className="block text-sm">{file?.name || 'No report selected'}</strong><span className="mt-1 block text-xs text-[#777587]">{taskId ? `Task ${taskId.slice(0, 12)} · ${status}` : 'Upload a DOCX to create an immutable source snapshot and begin the review gates.'}</span></div></div><div className="flex items-center gap-2"><span className="text-xs text-[#777587]">Overall progress <strong className="text-[#343444]">{Math.round(localProgress)}%</strong></span><button onClick={onOpenFlow} className="inline-flex items-center gap-1 rounded-md border border-[#c9c7db] bg-white px-3 py-2 text-xs font-semibold text-[#343444]">Open flow <ArrowRight size={14} /></button></div></div>
    </section>
  )
}

function PlanNode({ title, subtitle, status, tone }: { title: string; subtitle: string; status: 'done' | 'running' | 'queued'; tone: 'teal' | 'indigo' | 'slate' }) {
  const styles = tone === 'teal' ? 'border-teal-200 bg-teal-50' : tone === 'indigo' ? 'border-indigo-200 bg-indigo-50' : 'border-[#e5e3ec] bg-[#fafafa]'
  const text = tone === 'teal' ? 'text-teal-800' : tone === 'indigo' ? 'text-indigo-800' : 'text-[#686879]'
  return <div className={`border p-3 ${styles}`}><div className="flex items-center gap-2"><span className={`h-2 w-2 rounded-full ${status === 'done' ? 'bg-teal-500' : status === 'running' ? 'bg-indigo-500 animate-pulse' : 'bg-[#c6c4d0]'}`} /><strong className={`text-xs ${text}`}>{title}</strong></div><span className="mt-1 block text-[10px] text-[#777587]">{subtitle}</span></div>
}
