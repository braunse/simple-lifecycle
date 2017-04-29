import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

organization := "de.sebbraun.helpers"

name := "simple-lifecycle"

scalaVersion := "2.12.2"

crossScalaVersions := Seq("2.11.11", "2.12.2")

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.25",
  "org.slf4j" % "slf4j-simple" % "1.7.25" % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

licenses := Seq(
  "MIT" -> url("http://opensource.org/licenses/MIT")
)

developers := List(
  Developer("braunse",
    "Sébastien Braun",
    "sebastien@sebbraun.de",
    url("https://github.com/braunse"))
)

scmInfo := Some(ScmInfo(
  url("https://github.com/braunse/simple-lifecycle/tree/master"),
  connection = "scm:git:https://github.com/braunse/simple-lifecycle",
  devConnection = Some("scm:git:ssh://ssh@github.com/braunse/simple-lifecycle.git")
))

homepage := Some(url("https://github.com/braunse/simple-lifecycle"))

pomIncludeRepository := { _ => false }

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  } else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}

publishMavenStyle := true

disablePlugins(AetherPlugin)

enablePlugins(SignedAetherPlugin)

overridePublishSignedBothSettings

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
  setNextVersion,                         // : ReleaseStep
  commitNextVersion,                      // : ReleaseStep
  pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
)

releasePublishArtifactsAction := PgpKeys.publishSigned.value
