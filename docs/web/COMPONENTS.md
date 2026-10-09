# EPIC-12: React Component Library & Design System

## Goal

Establish a shared React component library with a Synapse-specific design system
that ensures visual consistency across all web screens.

## 1. Design System Foundation

### Tokens
Define design tokens in `src/styles/tokens.ts`:

| Token Type | Examples |
|---|---|
| Colors | `primary-500`, `surface-100`, `text-primary` |
| Spacing | `xs` (0.25rem), `sm` (0.5rem), `md` (1rem), `lg` (1.5rem) |
| Typography | `font-display`, `font-body`, `text-heading-sm` |
| Border Radius | `rounded-sm`, `rounded-md`, `rounded-full` |
| Shadows | `shadow-sm`, `shadow-md`, `shadow-lg` |
| Transitions | `ease-in-out`, `duration-200` |

### Theme
- Light and Dark mode color schemes
- `ThemeProvider` using React Context
- Persisted to `localStorage` (mirrors mobile `SecureStorage`)
- System preference detection via `matchMedia`

## 2. Component Library

### UI Primitives (Radix UI wrappers)
```
src/components/ui/
  Button/
    Button.tsx
    Button.test.tsx
  Card/
    Card.tsx
    CardHeader.tsx
    CardContent.tsx
  Input/
    Input.tsx
  Avatar/
    Avatar.tsx
  Badge/
    Badge.tsx
  Separator/
    Separator.tsx
  Dialog/
    Dialog.tsx
  DropdownMenu/
    DropdownMenu.tsx
  Switch/
    Switch.tsx
  Checkbox/
    Checkbox.tsx
  Tabs/
    Tabs.tsx
  Tooltip/
    Tooltip.tsx
```

### Layout Components
```
src/components/layout/
  MobileShell.tsx          # Bottom nav + header
  TabletShell.tsx          # Nav rail
  DesktopShell.tsx         # Sidebar + 3-column
  PostCard.tsx             # Reusable post preview
  PostList.tsx             # Virtualized feed
  ChatInput.tsx            # Message composer
  MessageBubble.tsx        # Chat message UI
  FeedItem.tsx             # Story/Reel/Post item
```

## 3. Animation System

### Framer Motion Presets
Define reusable animation variants in `src/lib/animations.ts`:
- `fadeIn`, `fadeOut`
- `slideInLeft`, `slideInRight`
- `scaleIn`, `scaleOut`
- `staggerChildren`, `staggerItems`

### Micro-interactions
- Like button (heart beat animation)
- Follow button (morph on hover)
- Scroll-driven animations (opacity fade)
- Route transitions (shared element via `framer-motion`)

## 4. Storybook

Set up Storybook for component development and documentation:
- `@storybook/react-vite`
- Stories for all UI components
- Dark/Light mode toggle in Storybook toolbar
- Auto-generated docs

## 5. Testing

### Unit Tests
- Jest + React Testing Library
- Test every component in isolation
- Snapshot testing for regression
- `axe` for accessibility testing

### Visual Regression
- Percy.io or Chromatic integration
- Catch unintended UI changes

## Definition of Done
- [ ] `src/styles/tokens.ts` with full design token set
- [ ] `ThemeProvider` with light/dark modes
- [ ] 20+ shared UI components in `src/components/ui/`
- [ ] Layout shells (Mobile/Tablet/Desktop)
- [ ] Animation presets library
- [ ] Storybook configured and running
- [ ] 80%+ component test coverage
- [ ] Accessibility tests pass (`axe`)