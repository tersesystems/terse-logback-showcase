import sbt.Keys.libraryDependencies

ThisBuild / organization := "com.tersesystems.showcase"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.8"
//ThisBuild / resolvers += Resolver.mavenLocal

val terseLogback = "1.1.0"

lazy val logging = (project in file("modules/logging")).settings(
  
  libraryDependencies += playCore,
  libraryDependencies += javaJdbc,

  libraryDependencies += "org.codehaus.janino" % "janino" % "3.1.7",
  libraryDependencies += "org.fusesource.jansi" % "jansi" % "2.4.0",

  libraryDependencies += "io.sentry" % "sentry-logback" % "1.7.30",

  libraryDependencies += "com.tersesystems.blacklite" % "blacklite-logback" % "1.2.0",
  libraryDependencies += "com.tersesystems.blacklite" % "blacklite-codec-zstd" % "1.2.0",

  libraryDependencies += "com.tersesystems.jmxbuilder" % "jmxbuilder" % "0.0.5",

  libraryDependencies += "com.tersesystems.logback" % "logback-budget" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-bytebuddy" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-honeycomb-okhttp" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-turbomarker" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-typesafe-config" % terseLogback,  
  libraryDependencies += "com.tersesystems.logback" % "logback-correlationid" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-exception-mapping" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-exception-mapping-providers" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-uniqueid-appender" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-tracing" % terseLogback
)

lazy val root = (project in file(".")).enablePlugins(PlayJava, JavaAgent).settings(
  name := "terse-logback-showcase",

  javaAgents += JavaAgent("com.tersesystems.logback" % "logback-bytebuddy" % terseLogback),
  libraryDependencies += guice,
).aggregate(logging).dependsOn(logging)
