ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.2"

lazy val root = (project in file("."))
  .settings(
    name := "pnpmailbot",
    libraryDependencies ++= Dependencies.all,
    assembly / assemblyJarName := "pnpmailbot.jar",
  )

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "application.conf"            => MergeStrategy.discard
  case x => MergeStrategy.first
}