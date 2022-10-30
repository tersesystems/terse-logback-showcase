import sbt.Keys.libraryDependencies

ThisBuild / organization := "com.tersesystems.showcase"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.8"
//ThisBuild / resolvers += Resolver.mavenLocal

val terseLogback = "1.1.0"

lazy val logging = (project in file("modules/logging")).settings(
  
  libraryDependencies += playCore,
  libraryDependencies += javaJdbc,

  libraryDependencies += "org.codehaus.janino" % "janino" % "3.1.8",
  libraryDependencies += "org.fusesource.jansi" % "jansi" % "2.4.0",

  libraryDependencies += "io.sentry" % "sentry" % "6.6.0",

  libraryDependencies += "com.tersesystems.blacklite" % "blacklite-logback" % "1.2.2",

  libraryDependencies += "com.tersesystems.logback" % "logback-bytebuddy" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-honeycomb-okhttp" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-turbomarker" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-typesafe-config" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-uniqueid-appender" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-tracing" % terseLogback
)

lazy val root = (project in file(".")).enablePlugins(PlayJava, JavaAgent).settings(
  name := "terse-logback-showcase",

  javaAgents += JavaAgent("com.tersesystems.logback" % "logback-bytebuddy" % terseLogback),
  libraryDependencies += guice,
).aggregate(logging).dependsOn(logging)
