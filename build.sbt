ThisBuild / tlBaseVersion := "1.0"
ThisBuild / startYear := Some(2022)
ThisBuild / developers += tlGitHubDev("armanbilge", "Arman Bilge")
ThisBuild / tlFatalWarningsInCi := false
ThisBuild / crossScalaVersions := Seq("2.12.15", "3.1.1", "2.13.8")
ThisBuild / githubWorkflowJavaVersions := List("8", "11").map(JavaSpec.temurin(_))
ThisBuild / githubWorkflowBuildMatrixAdditions += "sjsStage" -> List("FastOptStage", "FullOptStage")
ThisBuild / githubWorkflowBuildMatrixExclusions +=
  MatrixExclude(Map("project" -> "rootJVM", "sjsStage" -> "FullOptStage"))
ThisBuild / githubWorkflowBuildSbtStepPreamble += "set Global/scalaJSStage := ${{ matrix.sjsStage }}"

lazy val root = tlCrossRootProject.aggregate(hpack)

lazy val hpack = crossProject(JVMPlatform, JSPlatform)
  .in(file("hpack"))
  .settings(
    name := "hpack",
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "2.7.0" % Test,
      "io.circe" %%% "circe-parser" % "0.14.1" % Test,
      "com.lihaoyi" %%% "sourcecode" % "0.2.7" % Test,
    ),
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "junit" % "junit" % "4.13.2" % Test,
      "com.github.sbt" % "junit-interface" % "0.13.3" % Test,
    )
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
  )
  .jvmEnablePlugins(NoPublishPlugin)
  .jsEnablePlugins(ScalaJSJUnitPlugin)
