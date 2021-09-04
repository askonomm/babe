# Babe

A data oriented static site generator.

## Build it yourself

Requires Java 11+, leiningen 2+ and GraalVM with Native Image to be installed.

1. Clone the repo
2. Run `lein uberjar` to create the `target/babe.jar` file
3. Run `lein native` to create a native binary file `target/babe`.

Feel free to move the babe native binary to a global path somewhere and just run it in any directory with your site
where you static site is as `babe` and optionally run a watcher with `babe watch`, which will build the site on any file
changes automatically.