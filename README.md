# Babe

A data oriented static site generator built to enable the creation of sites that require more complex logic by allowing
you to create new templating data based on the content, and mold it into a variety of shapes as you see fit, using a
JSON configuration to do that.

## Install

To-do.

## Usage

To-do.

## Build it yourself

Requires Java 11+, leiningen 2+ and GraalVM with Native Image to be installed.

1. Clone the repo
2. Run `lein uberjar` to create the `target/babe.jar` file
3. Run `lein native` to create a native binary file `target/babe`.

Now you have a native binary that you can use. You can, of course, just use the JAR file as well
via `java -jar babe.jar` if you want.

## Documentation

- [Check out a sample site](https://github.com/askonomm/bien.ee)
- [Creating content](#)
- [Templating](#)