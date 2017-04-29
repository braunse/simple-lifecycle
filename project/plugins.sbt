addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.4")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.0")
addSbtPlugin("no.arktekk.sbt" % "aether-deploy" % "0.18.2")

// workaround for java.lang.NoClassDefFoundError: org/apache/commons/lang3/Validate
libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-lang3" % "3.5"
)