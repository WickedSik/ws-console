ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .settings(
    name := "ws-console",
    idePackagePrefix := Some("io.github.wickedsik.wsconsole")
  )

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "2.1.23"
)
