# Implementation

Application source code: the Android app, the Windows server/dashboard app,
and the shared Kotlin Multiplatform core modules (`core-model`, `core-sync`,
`core-domain`) described in [`Arch/06-tech-stack.md`](../Arch/06-tech-stack.md)
— one subfolder per subsystem, matching [`Design/`](../Design/README.md).

Build order is Core → Windows → Android. None of these are populated yet;
each subfolder's README explains what will go there.

1. [`Core/`](Core/README.md)
2. [`Windows/`](Windows/README.md)
3. [`Android/`](Android/README.md)
