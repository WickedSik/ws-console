# JNI Considerations for Terminal Manipulation

**Document Type:** Future Research / Decision Record
**Date:** 2025-11-08
**Status:** For Future Consideration
**Decision:** Pure Scala implementation chosen for initial release

---

## Executive Summary

This document analyzes the trade-offs between using JNI (Java Native Interface) for terminal manipulation versus a pure
Scala implementation using ANSI escape sequences and system commands.

**Current Decision:** Implement Layer 1 without JNI, using pure Scala + ZIO + ANSI sequences.

**Scope Limitation:** Legacy terminals (cmd.exe, old PowerShell, dumb terminals) are explicitly **not supported**. This
eliminates the primary use case for JNI.

---

## What is JNI and Why JLine3 Uses It

JNI (Java Native Interface) allows Java/Scala code to call native C/C++ libraries, providing:

- Direct access to operating system terminal APIs
- Windows Console API access
- ncurses/termios access on Unix systems
- Native signal handling

JLine3 uses JNI to provide:

- True raw mode on Windows (character-by-character input)
- Direct terminal control without subprocess overhead
- Advanced line editing capabilities
- Better cross-platform compatibility

---

## Trade-off Analysis

### Advantages of Using JNI (JLine3 Approach)

#### 1. Superior Windows Support

- **Direct Windows Console API access** for true raw mode
- Proper handling of special keys (Ctrl+C, arrow keys, function keys)
- Native cursor positioning without ANSI sequence limitations
- Better compatibility with older Windows versions (pre-Windows 10)
- Works reliably in cmd.exe, not just Windows Terminal

#### 2. Better Terminal Control

- Direct access to termios/ncurses on Unix systems
- More reliable raw mode control (no `stty` subprocess)
- Faster terminal size queries (direct system calls)
- More accurate capability detection
- Better signal handling (SIGWINCH, SIGINT, SIGTSTP)

#### 3. Performance Benefits

- No subprocess spawning overhead (`stty`, `tput`)
- Direct system calls = lower latency (~1-2ms vs ~5-10ms)
- Better event reading performance
- More efficient resource usage
- Faster capability detection

#### 4. Richer Feature Set

- Line editing with history support
- Advanced completion mechanisms
- Sophisticated input parsing
- Better Unicode handling on all platforms
- More terminal emulator compatibility

#### 5. Mature Implementation

- JLine3 is battle-tested (used by Maven, Groovy, Scala REPL)
- Handles edge cases across many platforms
- Well-documented behavior
- Community support

### Disadvantages of Using JNI (Why We're Avoiding It)

#### 1. Native Library Distribution Complexity

- **Must bundle platform-specific binaries:**
    - Linux x86_64 (.so)
    - Linux ARM (.so)
    - macOS Intel (.dylib)
    - macOS ARM (Apple Silicon) (.dylib)
    - Windows x86_64 (.dll)
    - Windows ARM (.dll)
- JAR becomes either platform-specific OR must include all platforms (larger size)
- Users must ensure correct native library for their platform
- Increases deployment package size significantly

#### 2. Build System Complexity

- **Requires C/C++ compilation toolchain:**
    - gcc/clang for Unix/macOS
    - MSVC or MinGW for Windows
    - Cross-compilation tools for multi-platform builds
- More complex CI/CD pipeline (must build for all platforms)
- Harder to reproduce builds locally
- Longer build times
- Need separate build environments for each platform

#### 3. Runtime Issues

- **Native library loading can fail:**
    - Wrong library path configuration
    - Permission issues
    - Missing dependencies (libc version mismatches)
    - Incompatible architectures
- Version mismatches between Java code and native library
- Harder to debug crashes (JVM crashes vs exceptions)
- Error messages less clear when native code fails
- Inconsistent behavior across platforms

#### 4. GraalVM Native Image Challenges

- **JNI requires special configuration:**
    - Must declare all native methods in reflection config
    - Not all JNI code works in native images
    - Additional build-time configuration complexity
    - May need static linking of native libraries
- Some JNI features don't work in native images at all
- Increases native image build time significantly
- Platform-specific native image builds required

#### 5. Security Concerns

- Native code bypasses JVM security sandbox
- Potential for buffer overflows and memory corruption
- Harder to audit security (C/C++ code)
- Supply chain risks (native binaries harder to verify)
- May be blocked in restricted environments

#### 6. Dependency Weight

- JLine3 JAR: ~700KB+ with native libraries
- Our pure Scala approach: ~100KB (estimated)
- More dependencies = larger attack surface
- More code to audit and maintain
- Harder to understand full dependency tree

#### 7. Platform Fragility

- Native code can break on OS updates
- Different behavior across platforms harder to test
- Library path configuration issues on different systems
- Compatibility matrix explosion (OS version × architecture)
- May not work in containerized environments without special setup

#### 8. Development Friction

- Developers need native toolchain to build from source
- Cross-platform development requires multiple environments
- Debugging native crashes is harder
- Slower feedback cycle (compile C/C++, rebuild JAR, test)
- Steeper learning curve for contributors

---

## Our Decision: Pure Scala Implementation

### What We're Building

**Technology Stack:**

- Pure Scala 3
- ZIO for effects and resource management
- ANSI escape sequences for terminal control
- `stty` for raw mode (Unix/Linux/macOS)
- `tput` for capability queries
- scala.sys.process for system command execution
- Direct stdin/stdout I/O via ZIO Console

**Platform Support:**

#### Supported: Modern Interactive Terminals

| Platform       | Terminal                                  | Support Level |
|----------------|-------------------------------------------|---------------|
| macOS          | Terminal.app, iTerm2                      | Full          |
| Linux          | GNOME Terminal, Konsole, Alacritty, Kitty | Full          |
| Windows 10+    | Windows Terminal                          | Full          |
| Cross-platform | VS Code terminal, JetBrains terminals     | Full          |
| SSH            | Any modern terminal client                | Full          |

#### NOT Supported (Out of Scope)

| Environment                  | Reason                              |
|------------------------------|-------------------------------------|
| cmd.exe                      | Legacy, no reliable ANSI support    |
| PowerShell (legacy)          | Pre-Windows 10, no Virtual Terminal |
| Dumb terminals               | No cursor control, no colors        |
| Linux raw console            | Limited capabilities                |
| Non-interactive environments | Library is interactive-only         |
| Pipes/redirected I/O         | Not a terminal                      |
| CI/CD pipelines              | Non-interactive                     |
| Docker containers (headless) | Non-interactive                     |

**This is a deliberate design decision to limit complexity.** We do not maintain fallback code paths for legacy or
non-interactive environments.

### Trade-offs Accepted

1. **No Legacy Windows Support**
    - cmd.exe users must switch to Windows Terminal
    - This is acceptable: Windows Terminal is free and default on Windows 11

2. **No Non-Interactive Mode**
    - Library fails fast if not in an interactive TTY
    - Users needing non-interactive output should use standard I/O

3. **Performance Overhead**
    - Spawning `stty` subprocess: ~5-10ms
    - Spawning `tput` subprocess: ~5-10ms
    - Acceptable for interactive use (humans don't notice 50ms)

### What We Gain

1. **Pure JVM Deployment**
    - Single JAR works on all platforms
    - No native library path configuration
    - No architecture-specific builds
    - Works in any Java environment

2. **Build Simplicity**
    - Pure Scala compilation (no C/C++)
    - Fast builds (~seconds vs minutes)
    - Easy to reproduce locally
    - Standard sbt build

3. **Runtime Reliability**
    - No native library loading failures
    - Clear JVM exceptions (no native crashes)
    - Consistent error messages
    - Predictable behavior

4. **GraalVM Compatibility**
    - Easier native image compilation
    - No JNI reflection configuration
    - Smaller native binaries
    - Better startup time

5. **Security**
    - Stay within JVM security model
    - No native code vulnerabilities
    - Easier to audit (pure Scala)
    - Simpler supply chain

6. **Maintainability**
    - Pure Scala code easier to understand
    - Standard debugging tools work
    - Easier for contributors
    - Faster development cycle

7. **Deployment Simplicity**
    - No Docker base image considerations
    - Works in sandboxed environments
    - No library path issues
    - Single artifact to manage

---

## Use Case Analysis: Why Pure Scala Works for ws-console

### What ws-console Is Building

ws-console is an **interactive terminal library** for building:

- Progress bars and spinners
- Dashboard layouts
- Text-based user interfaces
- Status displays
- Interactive menus

**Explicitly NOT for:**

- Non-interactive batch processing
- CI/CD pipeline output
- Logging frameworks
- Background services

### Why This Scope Simplifies Everything

1. **No fallback code paths**
    - We don't need to handle dumb terminals
    - We don't need to strip ANSI codes for pipes
    - We don't need graceful degradation logic

2. **Modern terminals are the baseline**
    - cmd.exe is not supported - period
    - Users on Windows must use Windows Terminal
    - This is reasonable: Windows Terminal is free and default on Windows 11

3. **Interactive-only means TTY-only**
    - We can assume a real terminal is attached
    - We can use cursor positioning freely
    - We can rely on Unicode and colors

4. **Reduced testing matrix**
    - Only test modern terminals
    - No need to test edge cases for legacy environments
    - Faster development, fewer bugs

### Performance: Is subprocess overhead acceptable?

**Yes, for interactive use:**

1. **Capability detection is one-time** (~100ms on startup is imperceptible to users)
2. **Terminal size cached** (only query on resize)
3. **Raw mode is one-time** (enter at start, exit at end)
4. **Humans don't notice <100ms delays** in interactive applications

---

## Future Paths

### Current Path: Pure Scala, Modern Terminals Only

**This is the final decision:**

- Modern interactive terminals only
- No legacy terminal support
- No non-interactive mode
- No JNI module planned

**Rationale:**

- Windows Terminal is now default on Windows 11
- Legacy terminal users can upgrade (it's free)
- Complexity reduction is a feature, not a limitation
- Smaller codebase = fewer bugs = faster development

### JNI: Explicitly NOT Planned

We will **not** add JNI support because:

1. **The use case is eliminated** - Legacy terminals are out of scope
2. **Windows Terminal exists** - Modern Windows has excellent ANSI support
3. **Complexity cost is too high** - Native libraries add significant maintenance burden
4. **Deployment simplicity is a core value** - Single JAR, works everywhere

If a user needs legacy Windows support, ws-console is not the right library for them.

---

## Implementation Path

### Phase 1: Core Implementation (Current)

1. Build with pure Scala + ZIO
2. Target modern interactive terminals only
3. Fail fast on unsupported environments
4. Ship minimal viable library

### Phase 2: Refinement

1. Gather feedback from users with supported terminals
2. Improve error messages for unsupported environments
3. Add more terminal emulators to tested list
4. Optimize performance for interactive use cases

### No Phase 3

There is no plan to add legacy support or JNI. This decision is final.

---

## Appendix: JNI Technical Notes (For Reference Only)

**Note:** JNI is NOT planned for ws-console. This section is retained for historical context only.

If someone were to fork this library and add JNI, they would need to consider:

### JNI Wrapper Libraries to Evaluate

1. **JNA (Java Native Access)**
    - Easier than raw JNI
    - No C/C++ compilation needed
    - Performance slightly lower than JNI
    - Good for simple system calls

2. **JNR (Java Native Runtime)**
    - Modern alternative to JNA
    - Better performance
    - Used by jnr-posix (Unix APIs)
    - More type-safe

3. **Panama Foreign Function API** (JEP 442, Java 19+)
    - Future standard replacement for JNI
    - No native code compilation
    - Better safety and performance
    - Still in preview (as of 2024)

### Native Libraries to Consider

1. **Unix/Linux:**
    - ncurses (terminal control)
    - termios (raw mode, terminal settings)
    - Direct ioctl calls

2. **Windows:**
    - Windows Console API (ReadConsoleInput, etc.)
    - Virtual Terminal sequences (SetConsoleMode)

3. **Cross-platform:**
    - libuv (event-driven I/O, used by Node.js)
    - Could provide unified API

### Build System

If JNI needed:

- Use sbt-jni plugin for cross-compilation
- Set up CI/CD for multiple platforms
- Create platform-specific artifact publishing
- Document build requirements clearly

---

## Conclusion

**Final Decision: Pure Scala implementation targeting modern interactive terminals only.**

Rationale:

1. **Simplicity is a feature** - No fallback paths, no legacy support, less code
2. **Modern terminals are ubiquitous** - Windows Terminal, iTerm2, GNOME Terminal are standard
3. **Interactive-only scope** - Non-interactive use is explicitly out of scope
4. **JNI is not needed** - The use case (legacy Windows) is eliminated by our scope decision
5. **Single JAR deployment** - No native library complexity

**This decision is final.** We will not add legacy terminal support or JNI.

---

## References

- **JLine3 Architecture:** https://github.com/jline/jline3
- **JNA Project:** https://github.com/java-native-access/jna
- **JNR Project:** https://github.com/jnr
- **Panama Foreign Function API:** https://openjdk.org/jeps/442
- **Windows Console API:** https://docs.microsoft.com/en-us/windows/console/
- **termios man page:** Unix terminal I/O
- **ncurses documentation:** https://invisible-island.net/ncurses/

---

**Document Version:** 1.0
**Last Updated:** 2025-11-08
**Status:** Living Document - Update as new information becomes available
