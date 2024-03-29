import sbt.Keys.libraryDependencies
import com.typesafe.sbt.packager.docker._

ThisBuild / organization := "com.tersesystems.showcase"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.10"
//ThisBuild / resolvers += Resolver.mavenLocal

val terseLogback = "1.1.1"

lazy val logging = (project in file("modules/logging")).settings(

  libraryDependencies += playCore,

  // https://tersesystems.github.io/terse-logback/1.1.0/guide/uniqueid/
  libraryDependencies += "com.tersesystems.logback" % "logback-uniqueid-appender" % terseLogback,

  // https://github.com/tersesystems/blacklite/
  libraryDependencies += "com.tersesystems.blacklite" % "blacklite-logback" % "1.2.2",
)

lazy val root = (project in file(".")).enablePlugins(PlayJava, JavaAgent).settings(
  name := "terse-logback-showcase",

  // Set up for running "sbt docker:publishLocal"
  dockerExposedPorts := Seq(80),
  dockerBaseImage := "ibm-semeru-runtimes:open-17-jre-focal", // fit into 256MB
  dockerChmodType := DockerChmodType.UserGroupWriteExecute,
  Universal / javaOptions ++= Seq(
    "-J-XX:MaxRAM=70m",
    "-J--add-opens=java.base/java.lang=ALL-UNNAMED",
    "-J--add-opens=java.base/sun.security.ssl=ALL-UNNAMED",
    "-J--add-opens=java.base/sun.security.util=ALL-UNNAMED",
    "-Dpidfile.path=/dev/null",
    "-Dconfig.resource=application.prod.conf"
  ),

  libraryDependencies += ws, // used for calling cat service

  libraryDependencies += "org.codehaus.janino" % "janino" % "3.1.8",
  libraryDependencies += "org.fusesource.jansi" % "jansi" % "2.4.0",

  // https://docs.sentry.io/platforms/java/usage/
  libraryDependencies += "io.sentry" % "sentry" % "6.6.0",

  // https://tersesystems.github.io/terse-logback/1.1.0/guide/instrumentation/
  libraryDependencies += "com.tersesystems.logback" % "logback-bytebuddy" % terseLogback,

  // https://tersesystems.github.io/terse-logback/1.1.0/guide/budget/
  libraryDependencies += "com.tersesystems.logback" % "logback-budget" % terseLogback,

  // https://tersesystems.github.io/terse-logback/1.1.0/guide/typesafeconfig/
  libraryDependencies += "com.tersesystems.logback" % "logback-typesafe-config" % terseLogback,

  libraryDependencies += "com.squareup.okhttp3" % "logging-interceptor" % "4.10.0",

  // https://tersesystems.github.io/terse-logback/1.1.0/guide/tracing/
  libraryDependencies += "com.tersesystems.logback" % "logback-tracing" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-honeycomb-okhttp" % terseLogback,

  javaAgents += JavaAgent("com.tersesystems.logback" % "logback-bytebuddy" % terseLogback),
  libraryDependencies += guice,
).aggregate(logging).dependsOn(logging)
