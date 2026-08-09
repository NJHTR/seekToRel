/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_REPORT_AI_API_ORIGIN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
