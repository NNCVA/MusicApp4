---
name: kotlin-lsp
description: Use when working in Kotlin projects, editing .kt or .kts files, diagnosing Kotlin compile errors, or needing Kotlin language-server-aware navigation and validation.
---

# Kotlin LSP

## Overview

Use Kotlin project tooling and, when available, `kotlin-lsp` for code intelligence in `.kt` and `.kts` projects. Prefer the repository's Gradle, Maven, compiler, formatter, and test commands as the source of truth.

## Tool Discovery

Before editing Kotlin code, inspect the project shape:

- Look for `build.gradle`, `build.gradle.kts`, `settings.gradle`, `settings.gradle.kts`, `gradlew`, `pom.xml`, `src/main/kotlin`, and `src/test/kotlin`.
- Check whether `kotlin-lsp` is available with `command -v kotlin-lsp`.
- If missing and the user asks for setup, suggest `brew install JetBrains/utils/kotlin-lsp` on macOS.
- Do not assume an LSP bridge exists inside Codex; use shell validation and file search unless a callable LSP tool is available.

## Workflow

1. Read nearby Kotlin files and build configuration before changing code.
2. Preserve package names, visibility, nullability contracts, coroutine behavior, annotations, serialization names, and public APIs unless the user asks otherwise.
3. Follow local style for imports, expression bodies, data classes, sealed types, extension functions, and test frameworks.
4. Prefer project commands:
   - Gradle wrapper: `./gradlew test`, `./gradlew check`, or the narrowest relevant task.
   - Maven: `mvn test` or the narrowest relevant module command.
   - Script-only files: `kotlinc -script file.kts` when appropriate.
5. For Android Kotlin projects, use the existing Android/Gradle tasks and avoid introducing desktop-only assumptions.
6. Report the exact validation command used, or say why validation could not be run.

## Kotlin-Specific Cautions

- Treat nullability changes as behavior changes.
- Be careful with `suspend`, `Flow`, dispatcher usage, structured concurrency, and cancellation semantics.
- Avoid changing `equals`, `hashCode`, `copy`, or serialization behavior when touching data classes.
- Do not reorder initialization or lazy delegates casually; initialization order can be observable.
- Keep Java interop annotations and JVM names stable unless explicitly requested.
