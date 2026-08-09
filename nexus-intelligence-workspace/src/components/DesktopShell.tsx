import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react'
import { BatteryFull, ChevronRight, FolderOpen, Minimize2, Network, Power, Settings2, X } from 'lucide-react'
import { appContent, appIcon, appTitle, NotesIcon, ReportAiIcon } from './DesktopApps'
import WallpaperGallery, { wallpaperUrl, wallpapers } from './WallpaperGallery'
import { WeatherApp } from './WeatherApp'
import { DESKTOP_APP_BY_ID, DESKTOP_DOCK_APP_IDS, DESKTOP_APP_MANIFESTS, type DesktopAppId } from '../appManifest'

type WindowState = { id: DesktopAppId; open: boolean; minimized: boolean; maximized: boolean; x: number; y: number; z: number }

const initialWindows: Record<DesktopAppId, WindowState> = Object.fromEntries(
  DESKTOP_APP_MANIFESTS.map((manifest, index) => [manifest.id, {
    id: manifest.id,
    open: false,
    minimized: false,
    maximized: false,
    x: manifest.defaultWindow.x,
    y: manifest.defaultWindow.y,
    z: 20 + index,
  }]),
) as Record<DesktopAppId, WindowState>

function currentTime() {
  return new Intl.DateTimeFormat('en-US', { weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date())
}

function DockItem({ id, index, hoveredIndex, active, onHover, onClick, children }: { id: DesktopAppId; index: number; hoveredIndex: number | null; active: boolean; onHover: (index: number | null) => void; onClick: () => void; children: ReactNode }) {
  const distance = hoveredIndex === null ? 99 : Math.abs(hoveredIndex - index)
  return <button className={`mac-dock-item original-dock-item ${distance === 0 ? 'hovered' : distance === 1 ? 'distance-1' : distance === 2 ? 'distance-2' : ''}`} onMouseEnter={() => onHover(index)} onMouseLeave={() => onHover(null)} onFocus={() => onHover(index)} onBlur={() => onHover(null)} onClick={onClick} aria-label={appTitle(id)}>
    <span className="mac-dock-tooltip">{appTitle(id)}</span>{children}{active && <span className="mac-dock-dot" />}
  </button>
}

export default function DesktopShell() {
  const [wallpaper, setWallpaper] = useState(() => {
    const saved = localStorage.getItem('portfolio-wallpaper')
    return wallpapers.find((item) => item.file === saved) ?? wallpapers[0]
  })
  const [windows, setWindows] = useState<Record<DesktopAppId, WindowState>>(initialWindows)
  const [activeApp, setActiveApp] = useState<DesktopAppId>('finder')
  const [menu, setMenu] = useState<'apple' | 'file' | null>(null)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [now, setNow] = useState(currentTime)
  const [hoveredDock, setHoveredDock] = useState<number | null>(null)
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null)
  const [booting, setBooting] = useState(() => sessionStorage.getItem('portfolio-booted') !== '1')
  const [dragState, setDragState] = useState<{ id: DesktopAppId; offsetX: number; offsetY: number } | null>(null)
  const windowRefs = useRef<Partial<Record<DesktopAppId, HTMLDivElement | null>>>({})
  const zCounter = useRef(40)

  useEffect(() => {
    const timer = window.setInterval(() => setNow(currentTime()), 30_000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    if (!booting) return
    const timer = window.setTimeout(() => { sessionStorage.setItem('portfolio-booted', '1'); setBooting(false) }, 2100)
    return () => window.clearTimeout(timer)
  }, [booting])

  useEffect(() => {
    localStorage.setItem('portfolio-wallpaper', wallpaper.file)
  }, [wallpaper])

  useEffect(() => {
    if (!dragState) return
    const move = (event: globalThis.PointerEvent) => {
      const element = windowRefs.current[dragState.id]
      if (!element || windows[dragState.id].maximized) return
      const maxX = Math.max(8, window.innerWidth - element.offsetWidth - 8)
      const maxY = Math.max(32, window.innerHeight - element.offsetHeight - 86)
      setWindows((current) => ({ ...current, [dragState.id]: { ...current[dragState.id], x: Math.min(maxX, Math.max(8, event.clientX - dragState.offsetX)), y: Math.min(maxY, Math.max(34, event.clientY - dragState.offsetY)) } }))
    }
    const stop = () => setDragState(null)
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', stop, { once: true })
    return () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', stop) }
  }, [dragState, windows])

  function focusApp(id: DesktopAppId) {
    zCounter.current += 1
    setActiveApp(id)
    setWindows((current) => ({ ...current, [id]: { ...current[id], z: zCounter.current } }))
  }

  function openApp(id: DesktopAppId) {
    zCounter.current += 1
    setActiveApp(id)
    setMenu(null); setSettingsOpen(false); setContextMenu(null)
    setWindows((current) => ({ ...current, [id]: { ...current[id], open: true, minimized: false, z: zCounter.current } }))
  }

  function closeApp(id: DesktopAppId) {
    setWindows((current) => ({ ...current, [id]: { ...current[id], open: false, minimized: false } }))
  }

  function minimizeApp(id: DesktopAppId) {
    setWindows((current) => ({ ...current, [id]: { ...current[id], minimized: true } }))
  }

  function toggleMaximize(id: DesktopAppId) {
    focusApp(id)
    setWindows((current) => ({ ...current, [id]: { ...current[id], maximized: !current[id].maximized } }))
  }

  function beginDrag(id: DesktopAppId, event: ReactPointerEvent<HTMLDivElement>) {
    if (windows[id].maximized || event.button !== 0) return
    const rect = windowRefs.current[id]?.getBoundingClientRect()
    if (!rect) return
    focusApp(id)
    setDragState({ id, offsetX: event.clientX - rect.left, offsetY: event.clientY - rect.top })
  }

  function toggleMenu(next: 'apple' | 'file') {
    setMenu((current) => current === next ? null : next)
    setSettingsOpen(false); setContextMenu(null)
  }

  function appBody(id: DesktopAppId) {
    if (id === 'photos') return <WallpaperGallery selected={wallpaper} onSelect={setWallpaper} />
    if (id === 'weather') return <WeatherApp />
    return appContent(id, openApp)
  }

  const openWindows = (Object.values(windows) as WindowState[]).filter((entry) => entry.open)

  return <div className="desktop-shell original-macos-shell" style={{ backgroundImage: `url(${wallpaperUrl(wallpaper.file)})` }} onPointerDown={() => { setMenu(null); setSettingsOpen(false); setContextMenu(null) }} onContextMenu={(event) => { event.preventDefault(); setContextMenu({ x: Math.min(event.clientX, window.innerWidth - 230), y: Math.min(event.clientY, window.innerHeight - 280) }) }}>
    <header className="mac-menu-bar original-menu-bar" onPointerDown={(event) => event.stopPropagation()}>
      <div className="mac-menu-left">
        <button className="mac-menu-button mac-apple-button" onClick={() => toggleMenu('apple')} aria-label="Apple menu"><img src="/mac-icons/apple.png" alt="" /></button>
        <span className="mac-active-app">{appTitle(activeApp)}</span>
        <button className={`mac-menu-button ${menu === 'file' ? 'mac-menu-button-active' : ''}`} onClick={() => toggleMenu('file')}>File</button>
        <span className="mac-menu-label">Edit</span><span className="mac-menu-label">View</span><span className="mac-menu-label">Go</span><span className="mac-menu-label">Window</span><span className="mac-menu-label">Help</span>
      </div>
      <div className="mac-menu-right"><Network size={15} aria-label="Network connected" /><BatteryFull size={17} aria-label="Battery" /><span>{now}</span><button className="mac-menu-button mac-control-button" onClick={() => setSettingsOpen((open) => !open)} aria-label="Control Center"><Settings2 size={16} /></button></div>
      {menu === 'apple' && <div className="mac-menu-popover mac-apple-popover"><button onClick={() => openApp('finder')}><FolderOpen size={15} /> About Mac for Web</button><div className="mac-menu-divider" /><button onClick={() => openApp('reportai')}><ReportAiIcon small /> Open Nexus</button><button onClick={() => openApp('photos')}><img src="/mac-icons/photos.png" alt="" /> System Settings</button><button onClick={() => openApp('notes')}><NotesIcon /> Open Notes</button><div className="mac-menu-divider" /><button onClick={() => { sessionStorage.removeItem('portfolio-booted'); window.location.reload() }}><Power size={15} /> Restart</button></div>}
      {menu === 'file' && <div className="mac-menu-popover mac-file-popover"><button onClick={() => openApp('finder')}><FolderOpen size={15} /> Open Projects</button><button onClick={() => openApp('reportai')}><ReportAiIcon small /> Open Nexus</button><div className="mac-menu-divider" /><button onClick={() => minimizeApp(activeApp)} disabled={!windows[activeApp].open}><Minimize2 size={15} /> Minimize</button><button onClick={() => closeApp(activeApp)} disabled={!windows[activeApp].open}><X size={15} /> Close Window</button></div>}
      {settingsOpen && <div className="original-control-center"><div className="control-toggles"><button className="active"><Network size={17} /><span><strong>Wi-Fi</strong><small>Connected</small></span></button><button className="active"><Settings2 size={17} /><span><strong>Animations</strong><small>On</small></span></button></div><button className="control-wallpaper" onClick={() => openApp('photos')}><img src={wallpaperUrl(wallpaper.file)} alt="" /><span><strong>{wallpaper.name}</strong><small>Dynamic Wallpaper</small></span><ChevronRight size={17} /></button></div>}
    </header>

    <main className="desktop-stage original-desktop-stage" onPointerDown={(event) => event.stopPropagation()}>
      {openWindows.map((entry) => !entry.minimized && <section key={entry.id} ref={(element) => { windowRefs.current[entry.id] = element }} className={`desktop-app-window original-app-window desktop-app-${entry.id} ${entry.maximized ? 'desktop-app-window-maximized' : ''}`} style={entry.maximized ? { zIndex: entry.z } : { left: entry.x, top: entry.y, zIndex: entry.z, width: DESKTOP_APP_BY_ID[entry.id].defaultWindow.width, height: DESKTOP_APP_BY_ID[entry.id].defaultWindow.height }} onPointerDown={() => focusApp(entry.id)} role="application" aria-label={appTitle(entry.id)}>
        <div className="desktop-window-titlebar original-window-titlebar" onPointerDown={(event) => beginDrag(entry.id, event)} onDoubleClick={() => toggleMaximize(entry.id)}>
          <div className="traffic-lights"><button className="traffic traffic-red" onClick={(event) => { event.stopPropagation(); closeApp(entry.id) }} aria-label={`Close ${appTitle(entry.id)}`}><img src="/mac-icons/close.svg" alt="" /></button><button className="traffic traffic-yellow" onClick={(event) => { event.stopPropagation(); minimizeApp(entry.id) }} aria-label={`Minimize ${appTitle(entry.id)}`}><img src="/mac-icons/minimize.svg" alt="" /></button><button className="traffic traffic-green" onClick={(event) => { event.stopPropagation(); toggleMaximize(entry.id) }} aria-label={`Maximize ${appTitle(entry.id)}`}><img src="/mac-icons/stretch.svg" alt="" /></button></div>
          <div className="desktop-window-title"><span>{appTitle(entry.id)}</span></div>
          <div className="desktop-window-actions"><button onClick={(event) => { event.stopPropagation(); toggleMaximize(entry.id) }} aria-label={`Toggle ${appTitle(entry.id)} fullscreen`}><img src="/mac-icons/fullscreen.png" alt="" /></button></div>
        </div>
        <div className="desktop-window-content">{appBody(entry.id)}</div>
      </section>)}

      {openWindows.length === 0 && <div className="desktop-welcome original-desktop-welcome"><span>NJHTR</span><strong>Mac for Web</strong><small>Open an application from the Dock</small></div>}

      {contextMenu && <div className="desktop-context-menu" style={{ left: contextMenu.x, top: contextMenu.y }} onPointerDown={(event) => event.stopPropagation()}><button onClick={() => openApp('finder')}>New Finder Window</button><div /><button onClick={() => openApp('finder')}>Get Info</button><button onClick={() => openApp('photos')}>Change Desktop Background...</button><div /><button>Use Stacks</button><button>Sort By <ChevronRight size={13} /></button><button>Show View Options</button></div>}

      <nav className="mac-dock original-mac-dock" aria-label="Dock" onPointerDown={(event) => event.stopPropagation()}>
        {DESKTOP_DOCK_APP_IDS.map((id, index) => <span key={id} className={`dock-entry ${id === 'photos' || id === 'weather' || id === 'trash' ? 'dock-entry-divided' : ''}`}><DockItem id={id} index={index} hoveredIndex={hoveredDock} active={windows[id].open} onHover={setHoveredDock} onClick={() => openApp(id)}>{appIcon(id)}</DockItem></span>)}
      </nav>
    </main>

    {booting && <div className="mac-boot-screen"><img src="/mac-icons/applelogo.png" alt="Apple" /><div><span /></div></div>}
  </div>
}
