import sbt.Keys.libraryDependencies

organization in ThisBuild := "com.tersesystems.showcase"
version in ThisBuild := "1.0-SNAPSHOT"
scalaVersion in ThisBuild := "2.12.10"

val terseLogback = "0.15.3"

lazy val logging = (project in file("modules/logging")).settings(
  resolvers += Resolver.bintrayRepo("tersesystems", "maven"),
  resolvers += Resolver.mavenLocal,

  libraryDependencies += playCore,
  libraryDependencies += javaJdbc,

  libraryDependencies += "org.codehaus.janino" % "janino" % "3.0.11",
  libraryDependencies += "org.fusesource.jansi" % "jansi" % "1.17.1",

  libraryDependencies += "io.sentry" % "sentry-logback" % "1.7.30",

  libraryDependencies += "com.tersesystems.jmxbuilder" % "jmxbuilder" % "0.0.2",

  libraryDependencies += "com.tersesystems.logback" % "logback-budget" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-bytebuddy" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" %% "logback-honeycomb-playws" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-turbomarker" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-typesafe-config" % terseLogback,  
  libraryDependencies += "com.tersesystems.logback" % "logback-correlationid" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-exception-mapping" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-exception-mapping-providers" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-ringbuffer" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-uniqueid-appender" % terseLogback,
  libraryDependencies += "com.tersesystems.logback" % "logback-tracing" % terseLogback,
)

lazy val root = (project in file(".")).enablePlugins(PlayJava, JavaAgent, FlywayPlugin).settings(
  name := "terse-logback-showcase",

  flywayUrl := "jdbc:h2:mem:terse-logback",
  flywayUser := "sa",
  flywayPassword := "",
  flywayLocations += "db/migration",

  javaAgents += JavaAgent("com.tersesystems.logback" % "logback-bytebuddy" % terseLogback),

  libraryDependencies += "com.h2database" % "h2" % "1.4.200",
  libraryDependencies += guice,
).aggregate(logging).dependsOn(logging)
