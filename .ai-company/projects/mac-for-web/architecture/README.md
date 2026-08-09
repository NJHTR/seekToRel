# Architecture

The shell is a React/Vite host. Each app is registered through an explicit
manifest with id, label, icon, entry URL, capabilities and health behavior.
Embedded apps stay isolated by iframe or a documented component boundary. The
shell must not reach into an app's DOM to implement app behavior.
