import sbt.Keys.managedSourceDirectories

import com.typesafe.sbt.packager.archetypes.ServerLoader.Systemd

name := "ophan-battle root project"

scalaVersion in ThisBuild := "2.11.8"

def env(key: String, default: String): String = Option(System.getenv(key)).getOrElse(default)

lazy val root = project.in(file(".")).
  aggregate(ophanBattleJS, ophanBattleJVM).
  settings(
    publish := {},
    publishLocal := {}
  )

lazy val ophanBattle = crossProject.in(file(".")).
  settings(
    name := "ophan-battle",
    version := "0.1-SNAPSHOT"
  ).jvmConfigure(_.enablePlugins(PlayScala,BuildInfoPlugin,RiffRaffArtifact)).jvmSettings(
  libraryDependencies ++= Seq(
    "com.vmunier" %% "scalajs-scripts" % "1.0.0",
    "com.lihaoyi" %% "upickle" % "0.4.4",
    "com.lihaoyi" %% "autowire" % "0.2.6",
    "com.gu" %% "content-api-scala-client" % "8.2.1",
    "com.amazonaws" % "aws-java-sdk-sts" % "1.10.73",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
    "com.amazonaws" % "amazon-kinesis-client" % "1.7.2"
  ),
  buildInfoKeys := Seq[BuildInfoKey](
    name
  ),

  serverLoading in Debian := Systemd,

  debianPackageDependencies := Seq("openjdk-8-jre-headless"),

  javaOptions in Universal ++= Seq(
    "-Dpidfile.path=/dev/null",
    "-J-XX:MaxRAMFraction=2",
    "-J-XX:InitialRAMFraction=2",
    "-J-XX:MaxMetaspaceSize=500m",
    "-J-XX:+PrintGCDetails",
    "-J-XX:+PrintGCDateStamps",
    s"-J-Xloggc:/var/log/${name.value}/gc.log"
  ),

  maintainer := "Roberto Tyley <roberto.tyley@theguardian.com>",

  packageSummary := "Ophan Battle service",

  packageDescription := """Ophan-Battle appserver""",

  riffRaffPackageType := (packageBin in Debian).value,

  riffRaffUploadArtifactBucket := Option("riffraff-artifact"),

  riffRaffUploadManifestBucket := Option("riffraff-builds"),

  scroogeThriftOutputFolder in Compile := sourceManaged.value / "thrift",
  managedSourceDirectories in Compile += (scroogeThriftOutputFolder in Compile).value
).jsSettings(
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.1",
    "com.lihaoyi" %%% "upickle" % "0.4.4",
    "com.lihaoyi" %%% "autowire" % "0.2.6"
  )
).jsConfigure(_.enablePlugins(ScalaJSWeb))

lazy val ophanBattleJS = ophanBattle.js

lazy val ophanBattleJVM = ophanBattle.jvm.settings(
  scalaJSProjects := Seq(ophanBattleJS),
  pipelineStages in Assets := Seq(scalaJSPipeline)
)
