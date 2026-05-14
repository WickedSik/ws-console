ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

// SBT's super-shell draws a progress indicator at the bottom of the terminal
// using ANSI cursor-positioning + clear sequences. For a TUI like ws-console
// that owns the alternate screen buffer, the super-shell's output interleaves
// with our own ANSI at unpredictable points, silently clearing rows that
// contain our persistent footers and toolbars. Disable it for this build.
ThisBuild / useSuperShell := false

lazy val root = (project in file("."))
  .settings(
    name := "ws-console",
    idePackagePrefix := Some("io.github.wickedsik.wsconsole")
  )

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"          % "2.1.23",
  "dev.zio" %% "zio-streams"  % "2.1.23",
  "dev.zio" %% "zio-test"     % "2.1.23" % Test,
  "dev.zio" %% "zio-test-sbt" % "2.1.23" % Test
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
