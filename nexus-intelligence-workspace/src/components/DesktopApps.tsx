import { useEffect, useMemo, useState } from 'react'
import {
  ChevronLeft,
  ChevronRight,
  Plus,
  ShoppingBag,
  Sparkles,
  MessageCircle,
  Trash2
} from 'lucide-react'
import PortfolioFinder from './PortfolioFinder'
import MacNotesGlyph from './MacNotesGlyph'
import { DESKTOP_APP_BY_ID, type DesktopAppId } from '../appManifest'

export type { DesktopAppId } from '../appManifest'

export function ReportAiIcon({ small = false }: { small?: boolean }) {
  return <span className={`report-ai-icon ${small ? 'report-ai-icon-small' : ''}`} aria-hidden="true"><Sparkles size={small ? 18 : 28} strokeWidth={2.2} /></span>
}

export function NotesIcon() {
  return <span className="notes-app-icon" aria-hidden="true"><MacNotesGlyph size={50} /></span>
}

export function StoreIcon({ small = false }: { small?: boolean }) {
  return <span className={`store-app-icon ${small ? 'desktop-icon-small' : ''}`} aria-hidden="true"><ShoppingBag size={small ? 18 : 27} /></span>
}

export function RedditIcon({ small = false }: { small?: boolean }) {
  return <span className={`reddit-app-icon ${small ? 'desktop-icon-small' : ''}`} aria-hidden="true"><MessageCircle size={small ? 18 : 27} /></span>
}

type FinderProps = { openApp: (id: DesktopAppId) => void }

export function FinderApp({ openApp }: FinderProps) {
  return <PortfolioFinder openApp={openApp} />
}

export function NotesApp() {
  const [title, setTitle] = useState(() => localStorage.getItem('portfolio-note-title') || 'Project ideas')
  const [body, setBody] = useState(() => localStorage.getItem('portfolio-note-body') || 'Write down the next project, experiment, or feature here.')
  const [savedAt, setSavedAt] = useState('Saved locally')

  useEffect(() => {
    const timer = window.setTimeout(() => {
      localStorage.setItem('portfolio-note-title', title)
      localStorage.setItem('portfolio-note-body', body)
      setSavedAt(`Saved ${new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' }).format(new Date())}`)
    }, 250)
    return () => window.clearTimeout(timer)
  }, [body, title])

  return <div className="notes-app">
    <aside className="notes-sidebar"><div className="notes-sidebar-heading"><strong>Notes</strong><button aria-label="New note" title="New note" onClick={() => { setTitle('Untitled note'); setBody('') }}><Plus size={17} /></button></div><button className="note-list-item active"><strong>{title || 'Untitled note'}</strong><span>{body || 'No additional text'}</span></button></aside>
    <section className="note-editor"><header><span>{savedAt}</span><span>{body.trim() ? body.trim().split(/\s+/).length : 0} words</span></header><input className="note-title" value={title} onChange={(event) => setTitle(event.target.value)} aria-label="Note title" /><textarea value={body} onChange={(event) => setBody(event.target.value)} aria-label="Note body" /></section>
  </div>
}

type Operator = '+' | '-' | '*' | '/'

export function CalculatorApp() {
  const [display, setDisplay] = useState('0')
  const [stored, setStored] = useState<number | null>(null)
  const [operator, setOperator] = useState<Operator | null>(null)
  const [waiting, setWaiting] = useState(false)

  function calculate(left: number, right: number, operation: Operator) {
    if (operation === '+') return left + right
    if (operation === '-') return left - right
    if (operation === '*') return left * right
    return right === 0 ? Number.NaN : left / right
  }

  function digit(value: string) {
    setDisplay((current) => waiting || current === '0' || current === 'Error' ? value : `${current}${value}`)
    setWaiting(false)
  }

  function decimal() {
    if (waiting) { setDisplay('0.'); setWaiting(false); return }
    setDisplay((current) => current.includes('.') ? current : `${current}.`)
  }

  function choose(next: Operator) {
    const input = Number(display)
    if (stored !== null && operator && !waiting) {
      const result = calculate(stored, input, operator)
      setDisplay(Number.isFinite(result) ? String(Number(result.toPrecision(12))) : 'Error')
      setStored(result)
    } else setStored(input)
    setOperator(next)
    setWaiting(true)
  }

  function equals() {
    if (stored === null || !operator) return
    const result = calculate(stored, Number(display), operator)
    setDisplay(Number.isFinite(result) ? String(Number(result.toPrecision(12))) : 'Error')
    setStored(null); setOperator(null); setWaiting(true)
  }

  function clear() { setDisplay('0'); setStored(null); setOperator(null); setWaiting(false) }

  const keys = [
    ['AC', clear, 'utility'], ['+/-', () => setDisplay((current) => String(-Number(current))), 'utility'], ['%', () => setDisplay((current) => String(Number(current) / 100)), 'utility'], ['÷', () => choose('/'), 'operator'],
    ['7', () => digit('7'), 'number'], ['8', () => digit('8'), 'number'], ['9', () => digit('9'), 'number'], ['×', () => choose('*'), 'operator'],
    ['4', () => digit('4'), 'number'], ['5', () => digit('5'), 'number'], ['6', () => digit('6'), 'number'], ['−', () => choose('-'), 'operator'],
    ['1', () => digit('1'), 'number'], ['2', () => digit('2'), 'number'], ['3', () => digit('3'), 'number'], ['+', () => choose('+'), 'operator'],
    ['0', () => digit('0'), 'number zero'], ['.', decimal, 'number'], ['=', equals, 'operator']
  ] as const

  return <div className="calculator-app"><div className="calculator-display" aria-live="polite">{display}</div><div className="calculator-grid">{keys.map(([label, action, kind]) => <button key={label} className={`calculator-key ${kind}`} onClick={action}>{label}</button>)}</div></div>
}

export function CalendarApp() {
  const today = useMemo(() => new Date(), [])
  const [month, setMonth] = useState(() => new Date(today.getFullYear(), today.getMonth(), 1))
  const days = useMemo(() => {
    const first = new Date(month.getFullYear(), month.getMonth(), 1)
    const start = new Date(first)
    start.setDate(1 - first.getDay())
    return Array.from({ length: 42 }, (_, index) => { const date = new Date(start); date.setDate(start.getDate() + index); return date })
  }, [month])

  function moveMonth(delta: number) { setMonth((current) => new Date(current.getFullYear(), current.getMonth() + delta, 1)) }
  const heading = new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' }).format(month)

  return <div className="calendar-app"><header><div><span>Calendar</span><strong>{heading}</strong></div><div><button onClick={() => moveMonth(-1)} aria-label="Previous month"><ChevronLeft size={17} /></button><button onClick={() => setMonth(new Date(today.getFullYear(), today.getMonth(), 1))}>Today</button><button onClick={() => moveMonth(1)} aria-label="Next month"><ChevronRight size={17} /></button></div></header><div className="calendar-weekdays">{['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((day) => <span key={day}>{day}</span>)}</div><div className="calendar-grid">{days.map((date) => { const isToday = date.toDateString() === today.toDateString(); const outside = date.getMonth() !== month.getMonth(); return <span key={date.toISOString()} className={`${outside ? 'outside' : ''} ${isToday ? 'today' : ''}`}>{date.getDate()}</span> })}</div></div>
}

export function TrashApp() {
  return <div className="trash-app"><span><Trash2 size={42} /></span><strong>Trash is empty</strong><p>Deleted portfolio items will appear here.</p></div>
}

export function ReportAiApp() {
  return <iframe title="Nexus experiment workspace" src="/workspace?embedded=1" className="report-ai-frame" />
}

export function StoreApp() {
  return <iframe title="Commerce Store" src="/react-ecommerce-store/" className="portfolio-app-frame" />
}

export function RedditApp() {
  return <iframe title="Reddit Client" src="/typescript-reddit-clone/" className="portfolio-app-frame" />
}

export function appTitle(id: DesktopAppId) {
  return DESKTOP_APP_BY_ID[id].title
}

export function appIcon(id: DesktopAppId) {
  if (id === 'finder') return <img src="/desktop/finder.png" alt="" />
  if (id === 'reportai') return <ReportAiIcon />
  if (id === 'store') return <StoreIcon />
  if (id === 'reddit') return <RedditIcon />
  if (id === 'notes') return <NotesIcon />
  if (id === 'calculator') return <img src="/desktop/calculator.png" alt="" />
  if (id === 'calendar') return <img src="/desktop/calendar.png" alt="" />
  if (id === 'weather') return <img src="/mac-icons/weather.png" alt="" />
  if (id === 'photos') return <img src="/mac-icons/photos.png" alt="" />
  return <span className="mac-trash-icon"><Trash2 size={31} /></span>
}

export function appContent(id: DesktopAppId, openApp: (id: DesktopAppId) => void) {
  if (id === 'finder') return <FinderApp openApp={openApp} />
  if (id === 'reportai') return <ReportAiApp />
  if (id === 'store') return <StoreApp />
  if (id === 'reddit') return <RedditApp />
  if (id === 'notes') return <NotesApp />
  if (id === 'calculator') return <CalculatorApp />
  if (id === 'calendar') return <CalendarApp />
  return <TrashApp />
}
