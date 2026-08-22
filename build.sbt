ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

// Sets the Maven/Ivy groupId for `sbt publishLocal`. Consumers (e.g. scala-ollama)
// resolve ws-console from ~/.ivy2/local using this coordinate. Aligns with the
// existing `idePackagePrefix` below.
ThisBuild / organization := "io.github.wickedsik"

// SBT's super-shell draws a progress indicator at the bottom of the terminal
// using ANSI cursor-positioning + clear sequences. For a TUI like ws-console
// that owns the alternate screen buffer, the super-shell's output interleaves
// with our own ANSI at unpredictable points, silently clearing rows that
// contain our persistent footers and toolbars. Disable it for this build.
ThisBuild / useSuperShell := false

lazy val root = (project in file("."))
  .settings(
    name := "ws-console",
    idePackagePrefix := Some("io.github.wickedsik.wsconsole"),
    // Publish the test classes (testkit + fakes) as a secondary artifact so
    // downstream consumers — e.g. scala-ollama — can depend on
    //   "io.github.wickedsik" %% "ws-console" % "0.1.0-SNAPSHOT" % Test classifier "tests"
    // and drive their own component render assertions through `CaptureTerminal`
    // and `FrameHarness` without copying source into their own tree.
    Test / publishArtifact := true
  )

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"          % "2.1.23",
  "dev.zio" %% "zio-streams"  % "2.1.23",
  "dev.zio" %% "zio-test"     % "2.1.23" % Test,
  "dev.zio" %% "zio-test-sbt" % "2.1.23" % Test
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
