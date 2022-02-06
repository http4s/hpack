ThisBuild / tlBaseVersion := "1.0"
ThisBuild / startYear := Some(2022)
ThisBuild / organization := "com.armanbilge"
ThisBuild / tlFatalWarningsInCi := false
ThisBuild / githubWorkflowJavaVersions := List("8", "11").map(JavaSpec.temurin(_))

lazy val root = tlCrossRootProject.aggregate(hpack)

lazy val hpack = crossProject(JVMPlatform, JSPlatform)
  .in(file("hpack"))
  .settings(
    name := "hpack",
    mimaPreviousArtifacts := Set.empty,
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "junit" % "junit" % "4.13.2" % Test,
      "com.github.sbt" % "junit-interface" % "0.13.3" % Test,
      "org.mockito" % "mockito-core" % "1.9.5" % Test,
      "com.google.code.gson" % "gson" % "2.3.1" % Test,
    ),
    doc / javacOptions ~= { _.filterNot(_ == "-Xlint:all") },
  )
  .jsEnablePlugins(ScalaJSJUnitPlugin)
