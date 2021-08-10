<img src="https://github.com/adam-james-v/radish/blob/main/doc/radish.svg" alt="A flat-style vecor illustration of a radish." width="300">

# Radish
A kinda-cool org-mode -> interactive blog post tool written with and for Clojure(script).

Here are two example posts created with this tool:

<a href="https://adam-james-v.github.io/posts/radish-basic-example/index.html">Radish Basic Example</a> - (Basic Build) Uses Scittle with no external dependencies incorporated into the build.


<a href="https://adam-james-v.github.io/posts/radish-logo/index.html">Radish Logo</a> - (Advanced Build) Uses Radish to automatically create and compile a shadow-cljs project to incorporate external CLJS dependencies into the (cloned) Scittle interpreter.


## Why Radish?
A friend sent me a meme about radishes while I was thinking of a name for this project. I chuckled, then figured it's a good enough name for a small project like this.

## Usage
If you have a recent installation of [Babashka](https://github.com/babashka/babashka), you can run a few useful babashka tasks.

 - `bb run-main -i your-org-file.org` -> run the src with clojure
 - `bb run-uber -i your-org-file.org` -> build and run an uberjar with 
 - `bb uberjar` -> create **radish.jar** in the project root directory without running

Once you've built the uberjar, you can use it via:

`java -jar radish.jar -i your-org-file.org`

If you don't have or want to use Babashka, the following commands should work:

 - `clojure -M -m radish.main -i your-org-file.org` -> run the src with clojure
 - `clojure -X:uberjar` -> build the uberjar
 - `java -jar radish.jar -i your-org-file.org` -> run the uberjar, as stated previously.

Radish will create a directory in the same dir as the org file, with a name derived from the Title of the org file. Inside will be all necessary .js and .css files alongside a generated index.html. You should be able to upload this directory to your site and have a working page.

## Compiling to Native Image
You can compile this project with GraalVM's native-image.

First, make sure you have GraalVM / native-image installed, then set your env variables:

```
export GRAALVM_HOME=/Users/adam/Downloads/graalvm-ce-java11-21.1.0/Contents/Home
export JAVA_HOME=$GRAALVM_HOME 

```

NOTE: Change the path to match where you've downloaded/installed GraalVM

Then, run the native-image task with Babashka.

`bb native-image`

If the build succeeds, you should find the binary in `build/radish`.

NOTE: This is still fairly new territory for me, so the build script(s) could likely be cleaned up a bit yet. However, the final binary should work the same as the src or uberjar.

`./radish -i your-org-file.org`

## Current Limitations
The only build method so far is 'basic-build', which relies on a simple script executed in your browser by scittle after the page loads. It has no dependency loading capabilities and thus is limited to executing Clojurescript code that only relies on core libraries.

A more complete build process is underway where the idea is to compile dependencies using the Clojurescript compiler and creating a page via that process.
