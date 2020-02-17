# Terse Logback Showcase

This is an example project to show how to integrate logging in an application using [terse-logback](https://github.com/tersesystems/terse-logback).

It is a [Play application](https://www.playframework.com/documentation/2.8.x/JavaHome) running on Java.

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
* Database: This writes out database output to a database, using a tap filter.

Note that the database appender lets you query by correlation id, and will write out all data including trace data, while reaping old statements.  This is so there is a history of logging data available that will show you the sequence of events at a debug level in the event of an error.

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