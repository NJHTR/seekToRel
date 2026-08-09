import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const workspaceDirectory = path.resolve(scriptDirectory, '..', '..')
const desktopDirectory = path.resolve(scriptDirectory, '..')
const applications = [
  { directory: 'react-ecommerce-store', mount: 'react-ecommerce-store' },
  { directory: 'typescript-reddit-clone', mount: 'typescript-reddit-clone' }
]

function buildApplications() {
  for (const application of applications) {
    const result = spawnSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['run', 'build'], {
      cwd: path.join(workspaceDirectory, application.directory),
      encoding: 'utf8',
      stdio: 'inherit',
      shell: process.platform === 'win32'
    })
    if (result.error) throw result.error
    if (result.status !== 0) process.exit(result.status ?? 1)
  }
}

function copyApplications() {
  for (const application of applications) {
    const source = path.join(workspaceDirectory, application.directory, 'build')
    const target = path.join(desktopDirectory, 'dist', application.mount)
    if (!existsSync(source)) throw new Error(`Missing build for ${application.directory}. Run npm run build:portfolio-apps first.`)
    rmSync(target, { recursive: true, force: true })
    mkdirSync(path.dirname(target), { recursive: true })
    cpSync(source, target, { recursive: true })
  }
}

const action = process.argv[2]
if (action === 'build') buildApplications()
else if (action === 'copy') copyApplications()
else throw new Error(`Unsupported action: ${action}`)
