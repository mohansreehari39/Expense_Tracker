# Implementation

Application source code: the Android app, the Windows server/dashboard app,
and the shared Kotlin Multiplatform core modules (`core-model`, `core-sync`,
`core-domain`) described in [`Arch/06-tech-stack.md`](../Arch/06-tech-stack.md).

Build order is Core Logic → Windows App → Android App. Each doc below is
a concrete build plan (project structure, dependencies, milestones) for
implementing the matching [`Design/`](../Design/README.md) doc; actual
source code has not been written yet.

1. [01-core-logic-implementation.md](01-core-logic-implementation.md)
2. [02-windows-app-implementation.md](02-windows-app-implementation.md)
3. [03-android-app-implementation.md](03-android-app-implementation.md)
