# What's this?
This is the course project of *Principles and Practice of Compiler Construction* at Tsinghua University in the autumn term 2019-2020, the new Decaf compiler.
For more details about the Decaf compiler but not the course project in that term, please refer to the following "The New Decaf Compiler" section or [decaf-lang/decaf](https://github.com/decaf-lang/decaf).

## What are the features in the autumn term 2019-2020?

We added these features to the Decaf compiler:
- Abstract classes and methods.
- Variable type deduction.
- First-class functions, including function type and variable, **lambda expression**, and a more general function calling method.
- Detection of division by zero.

For more details, please refer to https://decaf-project.gitbook.io/decaf-2019/ or https://github.com/xumingkuan/decaf-2019-project.

## What can I learn from this repository?

Compilers have many details.
If you are engaged in the course *Principles and Practice of Compiler Construction* and wish to get a full mark in this project,
you'd better think them over.
You can read my full-mark code (although not guaranteed to be completely bug-free) for reference.
You can use the commented code in [LLParser.java](https://github.com/xumingkuan/decaf-2019/blob/master/src/main/java/decaf/frontend/parsing/LLParser.java) for debugging in PA1-B.
If you are using Windows like me, you can use [test.bat](https://github.com/xumingkuan/decaf-2019/blob/master/test.bat) to run your custom tests.

If you are not engaged in this course, you can estimate the workload of this course in this semester by [this commit](https://github.com/xumingkuan/decaf-2019/commit/08aafd51abf4575a7267877012c8803f0093ad6c) :)
Besides this repository, [decaf-lang/decaf](https://github.com/decaf-lang/decaf) may be a better place to have a glance at the Decaf compiler.

## Why isn't there PA4 & PA5? I saw them in the Decaf 2019 project.
They are optional. It's sufficient to get an A by doing PA1-A, PA1-B, PA2, PA3, and getting a high score in the final exam.
Good luck!

# The New Decaf Compiler

<img src="https://github.com/decaf-lang/decaf/wiki/images/decaf-logo-h.svg?sanitize=true" width="300" align=center></img>

Decaf is a Java-like, but much smaller programming language mainly for educational purpose.
We now have at least three different implementations of the compiler in Java, Scala and Rust.
Since the standard language has quite a limited set of language features, students are welcome to add their own new features.

## Getting Started

This project requires JDK 12.

Other dependencies will be automatically downloaded from the maven central repository by the build script.

## Build

First install the latest version (>= 5.4) [gradle](https://gradle.org).

Type the standard Gradle build command in your CLI:

```sh
gradle build
```

The built jar will be located at `build/libs/decaf.jar`.

Or, import the project in a Java IDE (like IDEA or Eclipse, or your favorite VS Code) and use gradle plugin, if available.

## Run

In your CLI, type

```sh
java -jar --enable-preview build/libs/decaf.jar -h
```

to display the usage help.

Possible targets/tasks are:

- PA1: parse source code and output the pretty printed tree, or error messages
- PA1-LL: like PA1, but use hand-coded LL parsing algorithm, with the help of a LL table generator [ll1pg](https://github.com/paulzfm/ll1pg)
- PA2: type check and output the pretty printed scopes, or error messages
- PA3: generate TAC (three-address code), dump it to a .tac file, and then output the execution result using our built-in simulator
- PA4: currently same with PA3, will be reserved for students to do a bunch of optimizations on TAC
- PA5: (default target) allocate registers and emit assembly code, currently we are using a very brute-force algorithm and only generates MIPS assembly code (with pseudo-ops, and no delayed branches)

To run the MIPS assembly code, you may need [spim](http://spimsimulator.sourceforge.net), a MIPS32 simulator.
For Mac OS users, simply install `spim` with `brew install spim` and run with `spim -file your_file.s`.

## Releases

See https://github.com/decaf-lang/decaf/releases for releases, including separate frameworks for PA1 -- PA3.

## Materials

We have a couple of Chinese documents on the language specification and implementation outlines:

- https://decaf-lang.gitbook.io
- https://decaf-project.gitbook.io

## Development & Contribution

In future, we will develop on (possibly variates of) development branches,
and only merge release versions into the master branch.

Issues and pull requests for fixing bugs are welcome. However, adding new language features will not be considered, because that's students' work!
