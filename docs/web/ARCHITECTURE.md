# EPIC-01: Synapse Web — Module Setup & Architecture

> **Stack change:** Previous plan used Kotlin/Wasm Compose. This has been replaced
> with **React + TypeScript + Vite** for full flexibility, robustness, and beautiful UI.

## Goal

Establish the web module that reuses the `:shared` KMP backend and delivers a
full Synapse Social experience in the browser using React.

## 1. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | TypeScript 5.x | Type safety; matches existing Kotlin type discipline |
| Framework | React 18+ | Mature, flexible, huge ecosystem |
| Build Tool | Vite 5.x | Fast HMR, tree-shaking, native ES modules |
| Styling | Tailwind CSS 3 | Full CSS flexibility, design-token driven |
| Animation | Framer Motion | Production-grade animations |
| Components | Radix UI (headless) + custom design system | Unstyled primitives + Synapse branding |
| Backend SDK | `@supabase/supabase-js` | Direct Supabase integration; same backend as mobile |
| Server State | TanStack Query | Automatic caching, refetching, pagination |
| Client State | Zustand | Lightweight global state (auth, UI prefs) |
| Routing | TanStack Router | Type-safe file-based routing |
| Forms | React Hook Form + Zod | Type-safe validation |
| Testing | Jest + RTL + Cypress | Unit, integration, and E2E |
| Types | Generated from Supabase schema | Type safety across frontend/backend |

## 2. Module Structure

```
web/
  package.json          # React + Vite + TS + Tailwind + deps
  tsconfig.json         # Strict mode
  vite.config.ts        # Vite config (proxy, env, build)
  index.html           # Root HTML
  public/              # Static assets (favicon, manifest, etc.)
  src/
    main.tsx           # Entry point; render(<App />)
    app/
      router.tsx       # TanStack Router definition
      providers.tsx    # QueryClientProvider, ThemeProvider
    features/
      auth/            # Login, OAuth redirect, session management
      home/            # Feed, Reels, Notifications tabs
      inbox/           # Chat list + message thread
      search/          # User/hashtag/post search
      reels/           # Vertical video feed
      post-detail/     # Single post + comments
      create-post/     # Post creation form (text, image, video)
      profile/         # User profile + posts grid
      settings/        # Settings hub + sub-screens
    components/
      ui/              # Shared UI primitives (Button, Card, etc.)
      layout/          # Shells (MobileShell, TabletShell, DesktopShell)
      feed/            # PostCard, PostList, etc.
    hooks/             # Custom hooks
    lib/               # Supabase client, utilities
    types/             # Generated from Supabase schema
    styles/            # Tailwind config + global CSS
```

## 3. DI / State Wiring

- **Zustand** store for auth state: `useAuthStore` (mirrors `AuthRepository`)
- **TanStack Query** for server data (posts, messages, profiles)
- **No Koin** on web — use standard React provider pattern
  - `:shared` Kotlin modules remain for Android/iOS/Desktop
  - Web has its own state/data layer via Supabase JS client

## 4. Build Configuration

`web/package.json` scripts:
- `dev` → `vite` (localhost:3000)
- `build` → `vite build` (dist/)
- `preview` → `vite preview`
- `test:unit` → `jest`
- `test:e2e` → `cypress run`
- `typecheck` → `tsc --noEmit`
- `lint` → `eslint src --fix`
- `generate:types` → `supabase gen types typescript`

## 5. Platform Adaptation Matrix (Mobile → Web)

| Mobile capability | Web equivalent | Status |
|---|---|---|
| Native secure keystore | HttpOnly cookies + secure storage via Supabase | ✅ |
| SQLDelight native driver | Supabase queries (no local DB needed) | ✅ |
| File picker (`Intent`) | HTML `<input type="file">` + File API | ✅ |
| Camera capture | `navigator.mediaDevices.getUserMedia` | ✅ |
| Voice recording | MediaRecorder API | ✅ |
| Biometric auth | WebAuthn / Credential Management | 🟡 v2 |
| Push notifications | Web Push API / Service Worker | 🟡 v2 |
| OAuth redirect | Supabase Auth redirect + callback | ✅ |
| Deep links | URL routing (TanStack Router) | ✅ |
| ExoPlayer video | HTML5 `<video>` / HLS.js | ✅ |
| Live presence | Supabase Realtime (works on web) | ✅ |

## 6. Responsive Layout Strategy

- **< 640px (mobile web):** Bottom navigation bar (Feed / Reels / Inbox / Search / Profile)
- **640–1024px (tablet web):** Left navigation rail (permanent)
- **> 1024px (desktop web):** Permanent left sidebar + 3-column layout
  - Left: Navigation rail
  - Center: Feed / content
  - Right: Chat sidebar / info panel

## 7. Theming

- Tailwind CSS with custom Synapse color palette
- `prefers-color-scheme` media query for initial dark/light mode
- Theme toggle persisted to `localStorage` (mirrors `SecureStorage` on mobile)
- Custom CSS properties for design tokens (spacing, typography, border radius)

---

## Definition of Done (this epic):
- [ ] `web/` project initialises (`npm install && npm run dev`)
- [ ] Routing shell with 5 main tabs (Feed, Reels, Search, Inbox, Profile)
- [ ] Auth gate: unauthenticated → login screen; authenticated → main feed
- [ ] TanStack Query wired to Supabase client
- [ ] Responsive navigation shell (bottom bar / rail / sidebar)
- [ ] Tailwind theming with light/dark mode
- [ ] `npm run typecheck` and `npm run lint` pass clean