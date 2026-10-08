ThisBuild / scalaVersion := "3.3.6"

// Publishing metadata for Maven Central. `version` is not set here: sbt-dynver
// derives it from git tags (`v0.1.0` -> `0.1.0`), and sbt-ci-release supplies
// `publishTo` and credentials. Do not define `version`, `publishTo`,
// `publishMavenStyle` or `credentials` in this build.
//
// `organization` is the Maven groupId; it aligns with `idePackagePrefix` below.
inThisBuild(
  List(
    organization := "io.github.wickedsik",
    // Written into the POM so consumers' build tools reject incompatible
    // evictions: 0.y.z -> a change in y is breaking, a change in z is not.
    versionScheme := Some("early-semver"),
    homepage := Some(url("https://github.com/WickedSik/ws-console")),
    licenses := List(
      "LGPL-3.0-or-later" -> url("https://www.gnu.org/licenses/lgpl-3.0.html")
    ),
    developers := List(
      Developer(
        "WickedSik",
        "Jurriën Dokter",
        "jurriendokter@gmail.com",
        url("https://github.com/WickedSik")
      )
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/WickedSik/ws-console"),
        "scm:git:git@github.com:WickedSik/ws-console.git"
      )
    )
  )
)

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
    description := "ZIO-native library for building rich terminal interfaces on modern ANSI terminals",
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
    description := "Test infrastructure for ws-console: capture terminal, ANSI grid decoding and render harnesses",
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
