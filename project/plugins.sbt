addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

// Publishing to Maven Central. Brings in sbt-dynver (version from git tags)
// and sbt-pgp (artifact signing). Releases run from CI on `v*` tags.
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.1")
