# EPIC-11: TypeScript Type Generation & API Contracts

## Goal

Generate type-safe TypeScript definitions for the Supabase backend, ensuring
the web front-end maintains the same type discipline as the Kotlin shared module.

## 1. Schema Type Generation

### Command
```bash
npx supabase gen types typescript --project-id $PROJECT_ID --schema public > src/types/supabase.ts
```

### Output
- Full TypeScript type definitions for all tables, views, functions, and enums
- Generated types include:
  - `Database` (root type)
  - `Tables<>` and `TablesInsert<>` / `TablesUpdate<>` helpers
  - `Functions`, `Enums`, `ViewParams`

### Integration
- Import in Supabase client initialization:
```ts
import type { Database } from './types/supabase'
const supabase = createClient<Database>(SUPABASE_URL, SUPABASE_ANON_KEY)
```

## 2. API Contract Layer

### Repository Pattern
Mirror the Kotlin `Repository` interfaces in `src/lib/repositories/`:

| Kotlin Repository | Web Equivalent |
|---|---|
| `AuthRepository` | `authRepository.ts` |
| `PostRepository` | `postRepository.ts` |
| `ChatRepository` | `chatRepository.ts` |
| `UserRepository` | `userRepository.ts` |

### Data Transfer Objects
- DTOs defined in `src/types/` (generated types + custom extensions)
- Mappers in `src/lib/mappers/` (DTO → UI model, mirroring Kotlin mappers)

## 3. Generated Hook Helpers

Use TanStack Query to auto-generate type-safe hooks from Supabase queries:

```ts
// src/hooks/generated/
useUsers, usePosts, useMessages, useConversations
```

These wrap the repository calls with automatic caching and type inference.

## 4. CI Integration

- Run type generation on `main` branch merge → commit updated types
- Verify `tsc --noEmit` passes in CI pipeline
- Alert on schema drift (types out of sync)

## Definition of Done
- [ ] `generate:types` script in `package.json`
- [ ] `src/types/supabase.ts` generated and committed
- [ ] Supabase client typed with `Database`
- [ ] Repository interfaces mirror Kotlin domain contracts
- [ ] Mappers for DTO → UI model conversion
- [ ] CI runs `tsc --noEmit` and fails on type errors