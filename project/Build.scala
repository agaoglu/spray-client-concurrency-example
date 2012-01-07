import sbt._
import cc.spray.revolver.RevolverPlugin._
import Keys._

object Build extends sbt.Build {
  import Dependencies._


  lazy val myProject = Project("spray-client-concurrency-example", file("."))
    .settings(Revolver.settings: _*)
    .settings(
      organization  := "com.example",
      version       := "0.1.0-SNAPSHOT",
      scalaVersion  := "2.9.1",
      scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
      resolvers     ++= Dependencies.resolutionRepos,
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Compile.sprayServer,
        Compile.sprayClient,
        Compile.sprayCan,
        Compile.sprayJson,
        Test.specs2,
        Container.akkaSlf4j,
        Container.slf4j,
        Container.logback
      )
    )
    
}

object Dependencies {
  val resolutionRepos = Seq(
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    ScalaToolsSnapshots
  )

  object V {
    val akka    = "1.3-RC5"
    val spray   = "0.9.0-SNAPSHOT"
    val specs2  = "1.6.1"
    val slf4j   = "1.6.1"
    val logback = "0.9.29"
  }

  object Compile {
    val akkaActor   = "se.scalablesolutions.akka" %  "akka-actor"      % V.akka    % "compile"
    val sprayServer = "cc.spray"                  %  "spray-server"    % V.spray   % "compile"
    val sprayClient = "cc.spray"                  %  "spray-client"    % V.spray   % "compile"
    val sprayCan    = "cc.spray.can"              %  "spray-can"       % "0.9.2-SNAPSHOT"   % "compile"
    val sprayJson   = "cc.spray.json"             %  "spray-json_2.9.1"      % "1.1.0-SNAPSHOT"   % "compile"
  }

  object Test {
    val specs2      = "org.specs2"                %% "specs2"          % V.specs2  % "test"
  }

  object Container {
    val akkaSlf4j   = "se.scalablesolutions.akka" %  "akka-slf4j"      % V.akka
    val slf4j       = "org.slf4j"                 %  "slf4j-api"       % V.slf4j
    val logback     = "ch.qos.logback"            %  "logback-classic" % V.logback
  }
}