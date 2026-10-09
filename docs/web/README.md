# Synapse Web — Planning Documents

This directory contains planning artifacts for the web version of Synapse Social.

## Current Architecture Decision

After evaluating options, the **web front-end is being built with React + TypeScript + Vite**,
not Kotlin/Wasm Compose. See the trade-off analysis in `ARCHITECTURE.md`.

The existing `:shared` Kotlin Multiplatform module continues to house:
- Domain layer (use cases, interfaces, pure models)
- Data layer (Supabase clients, SQLDelight DAOs, DTOs, mappers)
- Platform implementations for Android, iOS, and Desktop

For web, TypeScript types are generated directly from Supabase (see `TYPES.md`).

## Documents

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — Stack selection rationale
- [`TYPES.md`](./TYPES.md) — TypeScript type generation from Supabase schema
- [`COMPONENTS.md`](./COMPONENTS.md) — Shared component library inventory