ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

// Sets the Maven/Ivy groupId. Consumers (e.g. scala-ollama) resolve
// ws-console from ~/.ivy2/local using this coordinate after `sbt publishLocal`.
// Aligns with the `idePackagePrefix` below.
ThisBuild / organization := "io.github.wickedsik"

// SBT's super-shell draws a progress indicator at the bottom of the terminal
// using ANSI cursor-positioning + clear sequences. For a TUI like ws-console
// that owns the alternate screen buffer, the super-shell's output interleaves
// with our own ANSI at unpredictable points, silently clearing rows that
// contain our persistent footers and toolbars. Disable it for this build.
ThisBuild / useSuperShell := false

// `idePackagePrefix` is read by IntelliJ, not by sbt; without this sbt's
// lintUnused check warns about it on every load.
Global / excludeLintKeys += idePackagePrefix

val zioVersion = "2.1.23"

lazy val commonSettings = Seq(
  idePackagePrefix := Some("io.github.wickedsik.wsconsole"),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
)

lazy val zioTestDeps = Seq(
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test
)

// Aggregates the modules so `sbt test` / `sbt publishLocal` at the root reach
// all of them. Holds no sources of its own.
lazy val root = (project in file("."))
  .aggregate(core, testkit, tests, demo)
  .settings(
    name := "ws-console-root",
    publish / skip := true
  )

// The library. Published as `ws-console`.
lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "ws-console",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion
    )
  )

// Test infrastructure for consumers: `CaptureTerminal`, `AnsiGrid`,
// `GridAssertions`, `FrameHarness`, `RenderHarness`. Published as
// `ws-console-testkit`; consumers add it `% Test`. zio-test is a compile
// dependency here because the testkit's own API is built on it.
lazy val testkit = (project in file("testkit"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "ws-console-testkit",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test" % zioVersion,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test
    )
  )

// The core specs. A separate module because they use the testkit, and the
// testkit depends on core: keeping them in core would be a project cycle.
lazy val tests = (project in file("tests"))
  .dependsOn(core, testkit % Test)
  .settings(commonSettings)
  .settings(
    name := "ws-console-tests",
    publish / skip := true,
    libraryDependencies ++= zioTestDeps
  )

// The demo application. Run with `scripts/run-demo.sh`, never `sbt run`.
lazy val demo = (project in file("demo"))
  .dependsOn(core, testkit % Test)
  .settings(commonSettings)
  .settings(
    name := "ws-console-demo",
    publish / skip := true,
    libraryDependencies ++= zioTestDeps
  )
