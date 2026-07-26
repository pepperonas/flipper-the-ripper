# Contributing to Flipper the Ripper

Thanks for your interest in improving **Flipper the Ripper** — an open-source Android app (Kotlin, Jetpack Compose, Material 3) that downloads publicly accessible videos from Instagram, YouTube and TikTok using a bundled yt-dlp engine.

This document describes how to set up your environment, the conventions we follow, and what your pull request needs to pass before it can be merged. Please read it fully before opening a PR.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By participating, you are expected to uphold it. Report unacceptable behavior to <martin.pfeffer@celox.io>.

## Prerequisites

- **Android Studio** (latest stable release recommended).
- **JDK 17** — required by the Gradle toolchain. Verify with `java -version`.
- **Android SDK 35** installed (compile/target SDK 35, min SDK 24).
- Git and a GitHub account.

The package namespace is `io.celox.flipperripper`.

## Getting Started

1. Fork `pepperonas/flipper-the-ripper` on GitHub and clone your fork:
   ```bash
   git clone https://github.com/<your-username>/flipper-the-ripper.git
   cd flipper-the-ripper
   ```
2. Add the upstream remote so you can keep your fork in sync:
   ```bash
   git remote add upstream https://github.com/pepperonas/flipper-the-ripper.git
   ```
3. Open the project in Android Studio and let it sync, or build from the command line:
   ```bash
   ./gradlew build
   ```

The first build downloads dependencies (including the youtubedl-android / yt-dlp engine) and may take a few minutes.

## Branching

Create a topic branch off an up-to-date `main`:

```bash
git fetch upstream
git checkout -b feat/short-description upstream/main
```

Use a short, descriptive branch name prefixed by type, for example:

- `feat/tiktok-batch-download`
- `fix/notification-crash-api34`
- `docs/update-readme`
- `chore/bump-ytdlp`
- `refactor/download-queue`
- `test/downloader-edge-cases`

## Commit Messages

We use [Conventional Commits](https://www.conventionalcommits.org/), written in **English**:

```
<type>(<optional scope>): <short summary>

<optional body explaining what and why>

<optional footer, e.g. "Closes #123">
```

Common types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`, `build`.

Examples:

```
feat(download): add TikTok slideshow support
fix(ui): prevent crash when clipboard is empty
test(engine): cover yt-dlp version-update path
```

Keep the summary line in the imperative mood and under ~72 characters. Code and commits are in English.

## Coding Standards & Quality Tools

Style and quality are enforced by tooling — please run it locally before pushing.

- **Spotless (ktlint)** — code formatting. Auto-fix most issues with:
  ```bash
  ./gradlew spotlessApply
  ```
- **detekt** — static analysis. Note that detekt enforces `ForbiddenComment`, so **no `TODO` / `FIXME` comments may remain** in committed code. Track follow-up work in GitHub issues instead.
- **Kover** — code coverage, gated at **80% line coverage** (`koverVerifyDebug`).

Follow the existing architecture and Jetpack Compose / Material 3 patterns already in the codebase. Prefer small, focused changes.

## Running Checks Locally

Run all of the following before you push — CI runs the same commands and will reject anything that fails:

```bash
# Build
./gradlew build

# Unit tests
./gradlew testDebugUnitTest

# Formatting + static analysis
./gradlew spotlessCheck detekt

# Coverage gate (80% line coverage)
./gradlew koverVerifyDebug
```

A convenient way to run the full gate in one go:

```bash
./gradlew spotlessCheck detekt testDebugUnitTest koverVerifyDebug
```

## Tests

New features and bug fixes **should include tests**. Add or extend unit tests under the appropriate source set so that:

- the new behavior is covered, and
- the overall project stays at or above the **80% line-coverage gate** (`koverVerifyDebug`).

If a change is genuinely untestable (e.g. pure UI glue), say so in the PR description.

## Pull Request Process

1. Rebase your branch on the latest `upstream/main` and resolve any conflicts.
2. Make sure the full local check suite passes (see above).
3. Push your branch and open a PR against `pepperonas/flipper-the-ripper:main`.
4. Fill in the PR description: what changed, why, and how you tested it. Link related issues.
5. Ensure **all CI checks pass** — build, lint (Spotless/ktlint), detekt, unit tests, and the coverage gate. PRs with failing checks will not be merged.
6. Address review feedback with additional commits (avoid force-pushing during active review unless asked; a final squash/cleanup is fine).

Once the checks are green and a maintainer approves, your PR will be merged. Thank you for contributing!

## Reporting Security Issues

Do **not** open a public issue for security vulnerabilities. See [SECURITY.md](SECURITY.md) for how to report them privately.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE) that covers this project.
