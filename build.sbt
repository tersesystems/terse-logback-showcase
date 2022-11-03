import sbt.Keys.libraryDependencies

ThisBuild / organization := "com.tersesystems.showcase"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.8"
ThisBuild / resolvers += Resolver.mavenLocal

val terseLogback = "1.1.1-SNAPSHOT"

lazy val logging = (project in file("modules/logging")).settings(
  
  libraryDependencies += playCore,

  libraryDependencies += "org.codehaus.janino" % "janino" % "3.1.8",
  libraryDependencies += "org.fusesource.jansi" % "jansi" % "2.4.0",

  // https://docs.sentry.io/platforms/java/usage/
  libraryDependencies += "io.sentry" % "sentry" % "6.6.0",

  // https://github.com/tersesystems/blacklite/
  libraryDependencies += "com.tersesystems.blacklite" % "blacklite-logback" % "1.2.2",

  // https://tersesystems.github.io/terse-logback/1.1.0/guide/instrumentation/
  libraryDependencies += "com.tersesystems.logback" % "logback-bytebuddy" % terseLogback,

  // https://tersesystems.github.io/terse-logback/1.1.0/guide/typesafeconfig/
  libraryDependencies += "com.tersesystems.logback" % "logback-typesafe-config" % terseLogback,

  // https://tersesystems.github.io/terse-logback/1.1.0/guide/uniqueid/
  libraryDependencies += "com.tersesystems.logback" % "logback-uniqueid-appender" % terseLogback,

  libraryDependencies += "com.squareup.okhttp3" % "logging-interceptor" % "4.10.0",

  // https://tersesystems.github.io/terse-logback/1.1.0/guide/tracing/
  libraryDependencies += "com.tersesystems.logback" % "logback-tracing" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-honeycomb-okhttp" % terseLogback
)

lazy val root = (project in file(".")).enablePlugins(PlayJava, JavaAgent).settings(
  name := "terse-logback-showcase",

  javaAgents += JavaAgent("com.tersesystems.logback" % "logback-bytebuddy" % terseLogback),
  libraryDependencies += guice,
).aggregate(logging).dependsOn(logging)
