ThisBuild / organization := "DAPEX"

ThisBuild / version := "1.0.0"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.10",
  libraryDependencies ++= Dependencies.all,
  resolvers += Resolver.githubPackages("TheDiscProg"),
  githubOwner := "TheDiscProg",
  githubRepository := "rabbitmq-tester",
  addCompilerPlugin(
    ("org.typelevel" %% "kind-projector" % "0.13.2").cross(CrossVersion.full)
  ),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
)

lazy val base = (project in file("base"))
  .settings(
    commonSettings,
    name := "base",
    scalacOptions ++= Scalac.options,
    coverageExcludedPackages := Seq(
      "<empty>",
      ".*.entities.*"
    ).mkString(";")
  )

lazy val guardrail = (project in file("guardrail"))
  .settings(
    commonSettings,
    name := "guardrail",
    Compile / guardrailTasks := List(
      ScalaServer(
        file("swagger.yaml"),
        pkg = "dapex.guardrail",
        framework = "http4s",
        tracing = false,
        imports = List(
          "eu.timepit.refined.types.string.NonEmptyString"
        )
      )
    ),
    coverageExcludedPackages := Seq(
      "<empty>",
      ".*guardrail.*"
    ).mkString(";")
  )
  .dependsOn(base % "test->test; compile->compile")

lazy val root = (project in file("."))
  .enablePlugins(
    ScalafmtPlugin
  )
  .settings(
    commonSettings,
    name := "rabbitmq-tester",
    Compile / doc / sources := Seq.empty,
    scalacOptions ++= Scalac.options,
    Compile / mainClass := Some("dapex.MainApp"),
    coverageExcludedPackages := Seq(
      "<empty>"
    ).mkString(";"),
    coverageExcludedFiles := Seq(
      "<empty>",
      ".*MainApp.*",
      ".*AppServer.*"
    ).mkString(";"),
    coverageFailOnMinimum := true,
    coverageMinimumStmtTotal := 92,
    coverageMinimumBranchTotal := 100
  )
  .aggregate(base, guardrail)
  .dependsOn(base % "test->test; compile->compile")
  .dependsOn(guardrail % "test->test; compile->compile")

addCommandAlias("clntst", ";clean;scalafmt;test:scalafmt;test;")
addCommandAlias("cvrtst", ";clean;scalafmt;test:scalafmt;coverage;test;coverageReport;")

