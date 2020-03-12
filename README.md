# Terse Logback Showcase

This is an example project to show how to integrate logging in an application using [terse-logback](https://github.com/tersesystems/terse-logback).

It is a [Play application](https://www.playframework.com/documentation/2.8.x/JavaHome) running on Java.

## Heroku

There is a running demo on [https://terse-logback-showcase.herokuapp.com/](https://terse-logback-showcase.herokuapp.com).  It is loaded on demand, so it may take a while to start up the first time you hit the application.

You can also deploy your own application if you have a Heroku account:

[![Deploy to Heroku](https://www.herokucdn.com/deploy/button.png)](https://heroku.com/deploy)

## Running

You will need [sbt](https://www.scala-sbt.org/) installed to run.

```
$ sbt
```

This gets you into the [Play console](https://www.playframework.com/documentation/2.8.x/PlayConsole).

To start the play application, type `run`, and then click on http://localhost:9000/ in a browser when prompted.

```
[terse-logback-showcase] $ run

--- (Running the application, auto-reloading is enabled) ---

[info] p.c.s.AkkaHttpServer - Listening for HTTP on /0:0:0:0:0:0:0:0:9000

(Server started, use Enter to stop and go back to the console...)

FfRhBqPfTYg6O0Qbm7EAAA 20:41:50.040 [INFO ] p.a.d.DefaultDBApi -  Database [logging] initialized at jdbc:h2:mem:terse-logback;DB_CLOSE_DELAY=-1
FfRhBqPfTZU6O0Qbm7EAAA 20:41:50.054 [INFO ] p.a.d.HikariCPConnectionPool -  Creating Pool for datasource 'logging'
FfRhBqPfTaE6O0Qbm7EAAA 20:41:50.065 [INFO ] c.z.h.HikariDataSource -  HikariPool-1 - Starting...
FfRhBqPfTaE6O0Qbm7EAAB 20:41:50.065 [INFO ] c.z.h.HikariDataSource -  jdbc-appender-pool-1582000909357 - Starting...
FfRhBqPfTbE6O0Qbm7EAAA 20:41:50.081 [INFO ] c.z.h.HikariDataSource -  HikariPool-1 - Start completed.
FfRhBqPfTbg6O0Qbm7EAAA 20:41:50.089 [INFO ] c.z.h.HikariDataSource -  jdbc-appender-pool-1582000909357 - Start completed.
FfRhBqPfTf46O0Qbm7EAAA 20:41:50.159 [INFO ] a.e.s.Slf4jLogger -  Slf4jLogger started
FfRhBqPfTkI6O0Qbm7EAAA 20:41:50.227 [INFO ] p.a.h.EnabledFilters -  Enabled Filters (see <https://www.playframework.com/documentation/latest/Filters>):

    play.filters.csrf.CSRFFilter
    play.filters.headers.SecurityHeadersFilter
    play.filters.hosts.AllowedHostsFilter

FfRhBqPfTmg6O0Qbm7EAAA 20:41:50.264 [INFO ] play.api.Play -  Application started (Dev) (no global state)
```

You'll be able to see the web page and see the logs page as well.

## Logging Configuration

There are four logging appenders configured.  

* Text: this writes out line oriented output to `logs/application.log`.
* JSON: this writes out JSON output to `logs/application.json`.
* Console: this writes ANSI (colored) line output to stdout.
* JDBC: This writes out JSON to a database through JDBC.

## Backtracing with Ring Buffers

The root logging level is set to `TRACE`, and the JDBC appender is configured with a ring buffer which stores diagnostic logging (`DEBUG` & `TRACE`) events.

When the ring buffer empties, it writes out the contents of the ringbuffer to the database appender.  This avoids unnecessary writes to the database, so diagnostic information is only dumped on request.    This means that you get the complete logs for a request on an error, but only on an error.  I am calling this feature "backtracing", because you can go back and see the TRACE and DEBUG messages for a request when necessary.

Note that the database appender lets you query by correlation id, and will periodically reap older logs.  The database used here is an H2 database which is in memory only, but you can also use PostgreSQL or other databases that have first class JSON support.

## Custom Request Factory

There is a small modification to swap out Play's `RequestFactory` so that a start time attribute is included on the initial request.  This means that all requests can be sent in as traces, since traces have duration, and is useful in error reporting, since Play's error handling presents the original request prior to any filter/action processing.

## Error Reporting with Backtraces

Error Reporting is done through the error handlers.  There are two integrations, [Sentry](https://sentry.io/welcome/) and [Honeycomb](https://www.honeycomb.io/).

Error reporting makes use of diagnostic log entries tied to the request id, which is treated as the correlation id.

### Sentry Integration

The `SentryHandler` uses the out of the box [sentry client](https://docs.sentry.io/clients/java/) to send error information to Sentry.

Backtraces are sent by querying the `LogEntryFinder` for log entries matching the request id, and mapping them into [breadcrumbs](https://docs.sentry.io/enriching-error-data/breadcrumbs/?platform=java).  The Breadcrumb API doesn't have a "TRACE" level, so all breadcrumbs must be logged at "DEBUG".

### Honeycomb Integration

The `HoneycombHandler` uses the [event API](https://docs.honeycomb.io/api/events/) through [terse-logback honeycomb client](https://tersesystems.com/blog/2019/08/22/tracing-with-logback-and-honeycomb/).  

All errors are sent in as traces to Honeycomb using [manual tracing](https://docs.honeycomb.io/working-with-your-data/tracing/send-trace-data/#manual-tracing), with the error as the root span.  Backtraces are sent by sending [span events](https://docs.honeycomb.io/working-with-your-data/tracing/send-trace-data/#span-events), since log entries do not have a duration in themselves.

## H2 Browser

Play comes with an in-memory database called [H2](https://h2database.com/html/main.html) and there is a [browser built in](https://www.playframework.com/documentation/2.8.x/Developing-with-the-H2-Database) for convenience.

From inside the sbt console:

```
[terse-logback-showcase] $ h2-browser
TCP server running at tcp://127.0.1.1:9092 (only local connections)
PG server running at pg://127.0.1.1:5435 (only local connections)
Web Console server running at http://127.0.1.1:8082 (only local connections)
```

A browser will come up.  When the Play application is running (and only then), you can enter the following JDBC URL `jdbc:h2:mem:terse-logback` and click connect, and then enter SQL statements.

## JMX Support

You can change the logging level at runtime through [JMX](https://docs.oracle.com/javase/tutorial/jmx/overview/index.html).  A full description is [here](https://tersesystems.com/blog/2019/12/24/controlling-logging-in-a-running-jvm/).

There are three main options to connect through JMX: [Zulu Mission Control](https://www.azul.com/products/zulu-mission-control/), [VisualVM](https://visualvm.github.io/), and [Hawt](https://hawt.io/).

### Zulu Mission Control

[Zulu Mission Control](https://www.azul.com/products/zulu-mission-control/) is a rebranded version of Java Mission Control.  MBeans functionality is out of the box.

### VisualVM

[VisualVM](https://visualvm.github.io/) is a tool for profiling and monitoring the JDK.  The MBean support is available [as a plugin](https://visualvm.github.io/plugins.html).

TabularData is rendered a little differently than in Zulu Mission Control.

### Hawt

[Hawt](https://hawt.io/) is an application server that connects to Jolokia and gives an HTML admin UI.  You will need to install Jolokia as a Java agent and run Play in production mode for this to work.  See https://github.com/wsargent/play-scala-with-jmx for an example of how to do this.