ThisBuild / tlBaseVersion := "1.0"
ThisBuild / startYear := Some(2022)
ThisBuild / tlCiReleaseTags := false
ThisBuild / tlCiReleaseBranches := Seq.empty
ThisBuild / tlFatalWarningsInCi := false

lazy val root = project.in(file(".")).aggregate(hpack).enablePlugins(NoPublishPlugin)

lazy val hpack = project
  .in(file("hpack"))
  .settings(
    name := "hpack",
    libraryDependencies ++= Seq(
      "com.github.sbt" % "junit-interface" % "0.13.3" % Test,
      "org.mockito" % "mockito-core" % "1.9.5" % Test,
      "com.google.code.gson" % "gson" % "2.3.1" % Test,
    ),
    mimaPreviousArtifacts := Set.empty,
    Test / fork := true,
    Test / javaOptions += {
      if (sys.props("java.version").startsWith("1.11."))
        List(
          "--add-opens=java.base/java.lang=ALL-UNNAMED"
        ).mkString(" ")
      else ""
    },
  )
