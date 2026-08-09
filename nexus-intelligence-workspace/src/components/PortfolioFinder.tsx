import { useMemo, useState } from 'react'
import {
  AppWindow,
  Calculator,
  CalendarDays,
  ChevronRight,
  CloudSun,
  FileText,
  Folder,
  FolderOpen,
  Grid2X2,
  HardDrive,
  Image,
  MonitorSmartphone,
  MoreHorizontal,
  Search,
  ShoppingBag,
  Sparkles,
  UsersRound,
} from 'lucide-react'
import '../finder.css'
import MacNotesGlyph from './MacNotesGlyph'

export type PortfolioAppId = 'reportai' | 'store' | 'reddit' | 'notes' | 'calculator' | 'calendar' | 'weather' | 'photos'

type PortfolioFinderProps = {
  openApp: (id: PortfolioAppId) => void
}

type Project = {
  id: PortfolioAppId
  name: string
  summary: string
  stack: string
  Icon: typeof Sparkles
  accent: string
}

const projects: Project[] = [
  { id: 'reportai', name: 'Nexus', summary: 'AI agent platform for complete experiment reports', stack: 'Java 21 · Spring Boot · React', Icon: Sparkles, accent: 'violet' },
  { id: 'store', name: 'Commerce Store', summary: 'A polished storefront and checkout exploration', stack: 'React · TypeScript', Icon: ShoppingBag, accent: 'amber' },
  { id: 'reddit', name: 'Reddit Client', summary: 'A thoughtful community product interface', stack: 'TypeScript · React', Icon: UsersRound, accent: 'coral' },
]

const apps: Array<{ id: PortfolioAppId; name: string; Icon: typeof Sparkles; color: string }> = [
  { id: 'reportai', name: 'Nexus', Icon: Sparkles, color: 'violet' },
  { id: 'store', name: 'Store', Icon: ShoppingBag, color: 'amber' },
  { id: 'reddit', name: 'Reddit', Icon: UsersRound, color: 'coral' },
  { id: 'notes', name: 'Notes', Icon: MacNotesGlyph, color: 'yellow' },
  { id: 'calculator', name: 'Calculator', Icon: Calculator, color: 'graphite' },
  { id: 'calendar', name: 'Calendar', Icon: CalendarDays, color: 'red' },
  { id: 'weather', name: 'Weather', Icon: CloudSun, color: 'sky' },
  { id: 'photos', name: 'Photos', Icon: Image, color: 'rainbow' },
]

export default function PortfolioFinder({ openApp }: PortfolioFinderProps) {
  const [section, setSection] = useState<'projects' | 'applications'>('projects')
  const [query, setQuery] = useState('')

  const filteredProjects = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return projects
    return projects.filter((project) => `${project.name} ${project.summary} ${project.stack}`.toLowerCase().includes(normalized))
  }, [query])

  const filteredApps = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return apps
    return apps.filter((app) => app.name.toLowerCase().includes(normalized))
  }, [query])

  const isProjects = section === 'projects'
  const itemCount = isProjects ? filteredProjects.length : filteredApps.length

  return (
    <div className="portfolio-finder">
      <aside className="portfolio-finder-sidebar">
        <div className="portfolio-finder-sidebar-section">
          <span className="portfolio-finder-sidebar-label">Favorites</span>
          <button className={isProjects ? 'portfolio-finder-nav active' : 'portfolio-finder-nav'} onClick={() => setSection('projects')}>
            <FolderOpen size={16} />
            <span>Projects</span>
          </button>
          <button className={!isProjects ? 'portfolio-finder-nav active' : 'portfolio-finder-nav'} onClick={() => setSection('applications')}>
            <Grid2X2 size={16} />
            <span>Applications</span>
          </button>
        </div>
        <div className="portfolio-finder-sidebar-section">
          <span className="portfolio-finder-sidebar-label">Locations</span>
          <button className="portfolio-finder-nav"><HardDrive size={16} /><span>NJHTR's Mac</span></button>
          <button className="portfolio-finder-nav"><MonitorSmartphone size={16} /><span>Desktop</span></button>
        </div>
        <div className="portfolio-finder-sidebar-section portfolio-finder-tags">
          <span className="portfolio-finder-sidebar-label">Tags</span>
          <span><i className="portfolio-finder-tag blue" />In progress</span>
          <span><i className="portfolio-finder-tag orange" />Experiments</span>
        </div>
      </aside>

      <section className="portfolio-finder-main">
        <header className="portfolio-finder-toolbar">
          <div className="portfolio-finder-location"><Folder size={17} fill="currentColor" /><strong>{isProjects ? 'Projects' : 'Applications'}</strong><ChevronRight size={15} /></div>
          <label className="portfolio-finder-search"><Search size={15} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search" aria-label="Search projects and applications" /></label>
        </header>

        <div className="portfolio-finder-scroll">
          <div className="portfolio-finder-heading">
            <div>
              <span>{isProjects ? 'Portfolio' : 'On this Mac'}</span>
              <h2>{isProjects ? 'Featured work' : 'Applications'}</h2>
            </div>
            <button type="button" aria-label="More options" title="More options"><MoreHorizontal size={19} /></button>
          </div>

          {isProjects ? (
            <div className="portfolio-project-grid">
              {filteredProjects.map(({ id, name, summary, stack, Icon, accent }) => (
                <button className="portfolio-project-card" key={id} onClick={() => openApp(id)}>
                  <span className={`portfolio-project-icon ${accent}`}><Icon size={27} /></span>
                  <span className="portfolio-project-copy"><strong>{name}</strong><small>{summary}</small><em>{stack}</em></span>
                  <ChevronRight className="portfolio-project-arrow" size={17} />
                </button>
              ))}
              {filteredProjects.length === 0 && <EmptyState label="No matching projects" />}
            </div>
          ) : (
            <div className="portfolio-app-grid">
              {filteredApps.map(({ id, name, Icon, color }) => (
                <button key={id} className="portfolio-app-tile" onClick={() => openApp(id)}>
                  <span className={`portfolio-app-icon ${color}`}><Icon size={27} /></span>
                  <span>{name}</span>
                </button>
              ))}
              {filteredApps.length === 0 && <EmptyState label="No matching applications" />}
            </div>
          )}
        </div>

        <footer className="portfolio-finder-status">{itemCount} {itemCount === 1 ? 'item' : 'items'} · {isProjects ? 'Projects' : 'Applications'}</footer>
      </section>
    </div>
  )
}

function EmptyState({ label }: { label: string }) {
  return <div className="portfolio-finder-empty"><AppWindow size={25} /><span>{label}</span><small>Try a different search term.</small></div>
}
