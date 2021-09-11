# Babe

A data oriented static site generator built to enable the creation of sites that require more complex logic by allowing
you to create new templating data based on the content, and mold it into a variety of shapes as you see fit, using a
JSON configuration to do that.

## Install

### Locally

```shell
curl -s https://raw.githubusercontent.com/askonomm/babe/master/installer.sh | bash -s
```

You can then run babe as `./babe` or `./babe watch`, given that the Babe executable is in the current working directory.

### Globally

```shell
curl -s https://raw.githubusercontent.com/askonomm/babe/master/installer.sh | bash -s -- -g
```

You can then run babe as `babe` or `babe watch` from anywhere.

## Usage

### Quick start

To get a quick start for your project, run `babe init` in a directory you want, and it will create
the [base project](https://github.com/askonomm/babe-base-project) files for you in that directory. If you want it to create a new directory for you, run `babe init {directory-name}` and it will do just that.

### Building

To build a Babe site, simply navigate to the directory where your Babe site is (where the babe.json file is) and
run `babe`.

### Watching

To watch a Babe site, which means Babe will listen for any file changes and build the site automatically on any change (
useful for developing), simply navigate to the directory where your Babe site is (where the babe.json file is) and
run `babe watch`.

### More

- [Check out a sample site](https://github.com/askonomm/bien.ee)
- [Creating content](https://github.com/askonomm/babe/blob/master/doc/content.md)
- [Creating templating data](https://github.com/askonomm/babe/blob/master/doc/templating_data.md)

## Compile Babe yourself

Requires Java 11+, leiningen 2+ and GraalVM with Native Image to be installed.

1. Clone the repo
2. Run `lein uberjar` to create the `target/babe.jar` file
3. Run `lein native` to create a native binary file `target/babe`.

Now you have a native binary that you can use. You can, of course, just use the JAR file as well
via `java -jar babe.jar` if you want, thereby skipping step 3.