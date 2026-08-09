export type DesktopAppId =
  | 'finder'
  | 'reportai'
  | 'store'
  | 'reddit'
  | 'notes'
  | 'calculator'
  | 'calendar'
  | 'weather'
  | 'photos'
  | 'trash'

export type DesktopAppKind = 'native' | 'embedded'

export interface DesktopAppManifest {
  readonly id: DesktopAppId
  readonly title: string
  readonly kind: DesktopAppKind
  readonly dock: boolean
  readonly defaultWindow: {
    readonly width: number
    readonly height: number
    readonly x: number
    readonly y: number
  }
  readonly capabilities: readonly string[]
}

/**
 * The shell's application contract. New apps should be added here first; the
 * desktop should not need another hard-coded title, window size or Dock entry.
 */
export const DESKTOP_APP_MANIFESTS: readonly DesktopAppManifest[] = [
  { id: 'finder', title: 'Finder', kind: 'native', dock: true, defaultWindow: { width: 860, height: 520, x: 94, y: 76 }, capabilities: ['portfolio.read'] },
  { id: 'photos', title: 'Photos', kind: 'native', dock: true, defaultWindow: { width: 880, height: 650, x: 210, y: 92 }, capabilities: ['wallpaper.write'] },
  { id: 'reportai', title: 'Nexus', kind: 'embedded', dock: true, defaultWindow: { width: 1180, height: 730, x: 58, y: 46 }, capabilities: ['report.upload', 'task.read', 'task.execute', 'report.export'] },
  { id: 'store', title: 'Store', kind: 'embedded', dock: true, defaultWindow: { width: 1120, height: 700, x: 86, y: 60 }, capabilities: ['iframe.navigate'] },
  { id: 'reddit', title: 'Reddit', kind: 'embedded', dock: true, defaultWindow: { width: 1120, height: 700, x: 104, y: 72 }, capabilities: ['iframe.navigate'] },
  { id: 'notes', title: 'Notes', kind: 'native', dock: true, defaultWindow: { width: 780, height: 520, x: 170, y: 86 }, capabilities: ['notes.read', 'notes.write'] },
  { id: 'calculator', title: 'Calculator', kind: 'native', dock: true, defaultWindow: { width: 330, height: 500, x: 230, y: 92 }, capabilities: [] },
  { id: 'calendar', title: 'Calendar', kind: 'native', dock: true, defaultWindow: { width: 500, height: 500, x: 280, y: 82 }, capabilities: [] },
  { id: 'weather', title: 'Weather', kind: 'native', dock: true, defaultWindow: { width: 880, height: 650, x: 164, y: 70 }, capabilities: ['network.read'] },
  { id: 'trash', title: 'Trash', kind: 'native', dock: true, defaultWindow: { width: 500, height: 320, x: 330, y: 126 }, capabilities: ['portfolio.delete'] },
]

export const DESKTOP_APP_BY_ID: Readonly<Record<DesktopAppId, DesktopAppManifest>> =
  Object.fromEntries(DESKTOP_APP_MANIFESTS.map((manifest) => [manifest.id, manifest])) as Record<DesktopAppId, DesktopAppManifest>

export const DESKTOP_DOCK_APP_IDS: readonly DesktopAppId[] = DESKTOP_APP_MANIFESTS
  .filter((manifest) => manifest.dock)
  .map((manifest) => manifest.id)
