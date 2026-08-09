# Architecture

The shell builds or serves each app into an isolated static entry. Assets and
routes are namespaced; app-specific state remains inside the app. Shared shell
capabilities are explicit and versioned.
