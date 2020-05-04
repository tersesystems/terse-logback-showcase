# Terse Logback Showcase

This is an example project to show how to integrate logging in an application using [terse-logback](https://github.com/tersesystems/terse-logback).

It is a [Play application](https://www.playframework.com/documentation/2.8.x/JavaHome) written using Play's Java API.

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

## Flake ID Support

All logging events have a [flake id](https://tersesystems.github.io/terse-logback/guide/uniqueid/) generated and rendered in the appropriate format across different channels.  This allows you to get a general overview from console and text files, and be able to drill down to a full view with JSON data.

For example, in `application.log`, you'll see a single line that starts with `FfwJtsNHYSw6O0Qbm7EAAA`:

```text
FfwJtsNHYSw6O0Qbm7EAAA 2020-03-14T05:30:14.965+0000 [INFO ] play.api.db.HikariCPConnectionPool in play-dev-mode-akka.actor.default-dispatcher-7 - Creating Pool for datasource 'logging'
````

You can search for this string in `application.json` and see more detail on the log record:

```json
{"id":"FfwJtsNHYSw6O0Qbm7EAAA","relative_ns":20921024,"tse_ms":1584163814965,"start_ms":null,"@timestamp":"2020-03-14T05:30:14.965Z","@version":"1","message":"Creating Pool for datasource 'logging'","logger_name":"play.api.db.HikariCPConnectionPool","thread_name":"play-dev-mode-akka.actor.default-dispatcher-7","level":"INFO","level_value":20000}
```

Flake IDs are also k-ordered, meaning that they are "roughly time-ordered when sorted lexicographically."

## Relative Nanoseconds

All logging events also contain a nanosecond increment, using `System.nanoTime` as the base, relative to the JVM start time.  This value may be negative to begin with, but always increments.  

`LoggingEvent` already has a timestamp associated with it, but that timestamp is generated by `System.currentTimeMillis`. For most appenders, this would be fine, but appending to ringbuffer is so fast that several events can be appended in the same millisecond, exceeding the resolution and making direct time comparison difficult when those records are dumped to JDBC.  Adding a `relative_ns` field provides resolution down to the nanosecond, [sort of](https://shipilev.net/blog/2014/nanotrusting-nanotime/).

Technically, sorting on the flake id would also sort correctly, but would not solve the problem of determining *how much after* the second record was, and so the best thing to do here is add an extra field.

For example, here's two different records with the same millisecond.

```json
{"id":"FfwJtsNLLWo6O0Qbm7EAAA","relative_ns":11808036,"tse_ms":1584163603315,"start_ms":null,"@timestamp":"2020-03-14T05:26:43.315Z","@version":"1","message":"HikariPool-2 - Start completed.","logger_name":"com.zaxxer.hikari.HikariDataSource","thread_name":"play-dev-mode-akka.actor.default-dispatcher-7","level":"INFO","level_value":20000}
{"id":"FfwJtsNLLWo6O0Qbm7EAAB","relative_ns":11981656,"tse_ms":1584163603315,"start_ms":null,"@timestamp":"2020-03-14T05:26:43.315Z","@version":"1","message":"jdbc-appender-pool-1584163602961 - Start completed.","logger_name":"com.zaxxer.hikari.HikariDataSource","thread_name":"logback-appender-ASYNC_JDBC-2","level":"INFO","level_value":20000}
```

Note that the timestamp is `2020-03-14T05:26:43.315Z` and the time since epoch is `1584163603315`.  The flake ids distinguish between log entries by using a counter when millisecond precision is exceeded, so the first record is `FfwJtsNLLWo6O0Qbm7EAAA` ending in `A` and the second record is `FfwJtsNLLWo6O0Qbm7EAAB` ending in `B`.  The relative time tells us exactly how much time has elapsed between the two events: `11981656 - 11808036` is 0.17362 milliseconds.  

## Logging Instrumentation with Byte Buddy

One of the more fun things you can do with Terse Logback is to instrument jar files to add logging entry/exit statements at run time.  The full documentation is [here](https://tersesystems.github.io/terse-logback/guide/instrumentation/).

Note that will need to run Play in production mode to load the byte-buddy agent.  The easiest way to do this is to run `sbt stage` and then run the script:

```bash
export PLAY_APP_SECRET=some-long-secret-to-appease-the-entropy-gods
target/universal/stage/bin/terse-logback-showcase -Dconfig.resource=application.prod.conf
```

See the `Procfile` for reference.

So, as an example, let's say that we suspect that there's a bug in the [assets builder](https://www.playframework.com/documentation/2.8.x/AssetsOverview#Working-with-public-assets) code base.  We instrument the `controllers.AssetsBuilder` class by adding the following to the `logback.conf` file:

```hocon
logback.bytebuddy {
  service-name = "terse-logback-showcase"

  tracing {
    # This class doesn't have any tracing built into it, but we can add it using instrumentation
    "controllers.AssetsBuilder" = ["*"]
  }
}
```
 
This means that AssetsBuilder will generate log records at `TRACE` level with messages like:

```text
entering: controllers.AssetsBuilder.play$api$mvc$Results$_setter_$Continue_$eq(play.api.mvc.Result) with arguments=[Result(100, Map())] from source Assets.scala:715
exiting: controllers.AssetsBuilder.play$api$mvc$Results$_setter_$Continue_$eq(play.api.mvc.Result) with arguments=[Result(100, Map())] => (return_type=void return_value=null) from source Assets.scala:715
```

As with the other trace messages, these will all be written out to the ringbuffer, and will be dumped on error.  Note that these traces will not have a correlation id identifying them as part of the request, as the correlation id is added in application code.

## System Information

[System information](https://tersesystems.github.io/terse-logback/guide/systeminfo/) is provided using `logback-sigar`, which uses [Hyperic Sigar](https://github.com/hyperic/sigar) as a back end to provide CPU, memory and load information.

Hyperic Sigar is a Java agent that only runs in production mode, so if you try to run in development mode you will get "Sigar is not registered!" error messages.

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

You can change the logging level at runtime through [JMX](https://docs.oracle.com/javase/tutorial/jmx/overview/index.html) with [JMXConfigurator](http://logback.qos.ch/manual/jmxConfig.html) but it is a single bean that requires you to enter the logger and level as arguments.  Most of the boilerplate is under the hood, so you can extend a controller and call [registerWithJMX](https://github.com/tersesystems/terse-logback-showcase/blob/master/app/controllers/AbstractController.java#L38) to show and change the logger for that controller in JMX.

A full description is [here](https://tersesystems.com/blog/2019/12/24/controlling-logging-in-a-running-jvm/) -- the blog post goes into the implementation and also how to work with JMX over HTTP using Jolokia.

There are three main options to connect through JMX: [Zulu Mission Control](https://www.azul.com/products/zulu-mission-control/), [VisualVM](https://visualvm.github.io/), and [Hawt](https://hawt.io/).

### Zulu Mission Control

[Zulu Mission Control](https://www.azul.com/products/zulu-mission-control/) is a rebranded version of Java Mission Control.  MBeans functionality is out of the box.

### VisualVM

[VisualVM](https://visualvm.github.io/) is a tool for profiling and monitoring the JDK.  The MBean support is available [as a plugin](https://visualvm.github.io/plugins.html).

TabularData is rendered a little differently than in Zulu Mission Control.

### Hawt

[Hawt](https://hawt.io/) is an application server that connects to Jolokia and gives an HTML admin UI.  You will need to install Jolokia as a Java agent and run Play in production mode for this to work.  See https://github.com/wsargent/play-scala-with-jmx for an example of how to do this.
