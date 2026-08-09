import { useEffect } from 'react'
import DesktopShell from './components/DesktopShell'
import ReportWorkspace from './components/ReportWorkspace'

export default function App() {
  const isWorkspace = window.location.pathname === '/workspace'

  useEffect(() => {
    document.title = isWorkspace ? 'Nexus - Experiment Report Automation' : 'Mac for Web - Portfolio Desktop'
  }, [isWorkspace])

  // Nexus is also addressable directly for a clean iframe boundary. The root
  // route is the Mac for Web shell that launches the app.
  return isWorkspace ? <ReportWorkspace /> : <DesktopShell />
}
