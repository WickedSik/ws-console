# JNI Considerations for Terminal Manipulation

**Document Type:** Future Research / Decision Record
**Date:** 2025-11-08
**Status:** For Future Consideration
**Decision:** Pure Scala implementation chosen for initial release

---

## Executive Summary

This document analyzes the trade-offs between using JNI (Java Native Interface) for terminal manipulation versus a pure Scala implementation using ANSI escape sequences and system commands.

**Current Decision:** Implement Layer 1 without JNI, using pure Scala + ZIO + ANSI sequences.

**Future Option:** Add optional JNI module if Windows support requirements change.

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

#### Unix/Linux/macOS
✅ Full raw mode support via `stty -icanon min 1 -echo`
✅ Complete ANSI escape sequence support
✅ Mouse tracking (if terminal supports it)
✅ Resize events via SIGWINCH
✅ Terminal size via `stty size`
✅ Capability detection via `tput`

#### Windows 10+
✅ ANSI escape sequences (Windows Terminal, PowerShell)
⚠️ **Line-buffered input only** (no character-by-character)
⚠️ Limited special key detection
⚠️ No mouse tracking in cmd.exe
✅ Works in Windows Terminal and modern PowerShell

#### Non-TTY (Pipes, Redirection)
✅ Graceful fallback (no ANSI codes)
✅ Plain text output
✅ No terminal control attempted

### What We Sacrifice

1. **Windows Raw Mode**
   - Windows users must press Enter to submit input
   - Cannot do character-by-character input on Windows
   - Arrow keys require Enter to process
   - Ctrl+C may not work as expected in some scenarios

2. **Performance Overhead**
   - Spawning `stty` subprocess: ~5-10ms
   - Spawning `tput` subprocess: ~5-10ms
   - Total capability detection: ~50-100ms vs ~10-20ms with JNI

3. **Signal Handling Robustness**
   - SIGWINCH handling via sun.misc.Signal (may not work everywhere)
   - Less robust interrupt handling
   - Platform-specific signal differences

4. **Feature Completeness**
   - No line editing history
   - No advanced completion
   - Simpler input parsing

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

ws-console is a **terminal UI library** for building:
- Progress bars and spinners
- Dashboard layouts
- Text-based user interfaces
- Status displays
- Interactive menus (with Enter to select)

### Why Raw Mode Limitations Are Acceptable

1. **TUI applications don't need line editing**
   - We're not building a REPL
   - We're not building a command-line editor
   - Components render continuously, not waiting for input

2. **Event model differs from REPLs**
   - Most TUI apps use timer-based rendering (30-60 FPS)
   - Input is processed in batches
   - Character-by-character input not critical

3. **Windows users have good terminals now**
   - Windows Terminal is default on Windows 11
   - PowerShell has excellent ANSI support
   - cmd.exe usage declining

4. **Workarounds exist**
   - Windows users can use Windows Terminal
   - WSL provides full Unix terminal
   - PowerShell provides good experience

5. **Documentation can set expectations**
   - Clear platform support matrix
   - Known limitations documented
   - Recommended terminals specified

### Performance: Is subprocess overhead acceptable?

**Yes, because:**

1. **Capability detection is one-time** (~100ms on startup is fine)
2. **Terminal size cached** (only query on resize or timeout)
3. **Raw mode is one-time** (enter at start, exit at end)
4. **Terminal I/O is not the bottleneck** (network, disk, computation are slower)
5. **Frame rates are 30-60 FPS** (16-33ms per frame >> 10ms subprocess)

**Measurement needed:**
- Benchmark `stty` vs JNI on various systems
- Profile real TUI application performance
- Measure impact on frame rate

---

## Future Paths

### Option 1: Pure Scala Only (Current Plan)

**Keep it simple forever:**
- Document Windows limitations clearly
- Recommend Windows Terminal for Windows users
- Focus on excellent Unix/macOS support
- Accept line-buffered input on Windows

**When this works:**
- Target audience is primarily Unix/macOS/Linux
- Windows users can use modern terminals
- Simplicity is more valuable than feature completeness

### Option 2: Optional JNI Module (Future Enhancement)

**Create modular architecture:**
```
ws-console-core     (pure Scala, current implementation)
ws-console-native   (optional JNI enhancement)
```

**Users choose:**
```scala
// Simple deployment (pure Scala)
libraryDependencies += "com.ws" %% "ws-console-core" % "1.0.0"

// Enhanced Windows support (with JNI)
libraryDependencies += "com.ws" %% "ws-console-native" % "1.0.0"
```

**Implementation strategy:**
- Core library provides trait-based abstraction
- Native module provides enhanced implementations
- Factory selects best available implementation
- Graceful fallback if native module not present

**When to consider:**
- Windows users report significant pain
- Enterprise customers require Windows support
- Community contributes JNI implementation
- Funding available for multi-platform testing

### Option 3: Hybrid Approach (If Needed)

**Platform-specific JARs:**
```
ws-console-core         (shared code)
ws-console-unix         (Unix implementation)
ws-console-windows      (Windows JNI)
ws-console-fallback     (ANSI only)
```

**Auto-detection at runtime:**
- Library detects platform
- Loads appropriate implementation
- Falls back gracefully

---

## Decision Criteria for Future Reconsideration

**Consider adding JNI if:**

1. **Windows Market Share > 30%** of ws-console users
2. **User Complaints > 10** about Windows limitations per month
3. **Enterprise Customer Requirement** with funding
4. **Community Contribution** of well-tested JNI module
5. **Performance Issues** proven to be subprocess-related

**Don't add JNI if:**

1. **Current solution works** for 95% of users
2. **Windows Terminal adoption** continues to grow
3. **Complexity cost** outweighs benefit
4. **Maintenance burden** too high for team size
5. **Alternative solutions** (like WSL) are sufficient

---

## Recommended Path Forward

### Phase 1: Pure Scala Implementation (Now)
1. Build Layer 1 with pure Scala
2. Excellent Unix/macOS support
3. Acceptable Windows support (line-buffered)
4. Document limitations clearly
5. Ship and gather feedback

### Phase 2: Measure and Learn (3-6 months)
1. Collect user feedback on Windows experience
2. Measure actual performance bottlenecks
3. Track Windows vs Unix user ratio
4. Identify most-requested features

### Phase 3: Decide (6-12 months)
1. Evaluate feedback data
2. Assess community interest in JNI module
3. Make informed decision on JNI investment
4. If needed, design modular architecture

### Phase 4: Enhance (If Needed)
1. Design ws-console-native module
2. Implement JNI bindings
3. Create platform-specific builds
4. Maintain both implementations

---

## Technical Notes for Future JNI Implementation

If we do decide to add JNI later, consider:

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

**Current Decision: Pure Scala implementation is the right choice for ws-console Layer 1.**

Rationale:
1. Simplicity aligns with library goals
2. Performance trade-offs are acceptable
3. Platform support is sufficient for target users
4. Can add JNI later if needed (modular design)
5. Pure Scala reduces barriers to adoption

**Future Decision Point: Re-evaluate in 6-12 months based on user feedback.**

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
