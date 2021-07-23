# Radish
A kinda-cool org-mode -> interactive blog post tool written with and for Clojure(script).

Here's an example post created with this tool:

<a href="https://adam-james-v.github.io/posts/radish-basic-example/index.html">Radish Basic Example</a>

## Why Radish?
A friend sent me a meme about radishes while I was thinking of a name for this project. I chuckled, then figured it's a good enough name for a small project like this.

## Usage
If you have a recent installation of [Babashka](https://github.com/babashka/babashka), you can run a few useful babashka tasks.

 - `bb run-main -i your-org-file.org` -> run the src with clojure
 - `bb run-uber -i your-org-file.org` -> build and run an uberjar with 
 - `bb uberjar` -> create **radish.jar** in the project root directory without running

Once you've built the uberjar, you can use it via:

`java -jar radish.jar -i your-org-file.org`

I am working on a GraalVM native image, but that's not quite working yet.

If you don't have or want to use Babashka, the following commands should work:

 - `clojure -M -m radish.main -i your-org-file.org` -> run the src with clojure
 - `clojure -X:uberjar` -> build the uberjar
 - `java -jar radish.jar -i your-org-file.org` -> run the uberjar, as stated previously.


## Current Limitations
The only build method so far is 'basic-build', which relies on a simple script executed in your browser by scittle after the page loads. It has no dependency loading capabilities and thus is limited to executing Clojurescript code that only relies on core libraries.

A more complete build process is underway where the idea is to compile dependencies using the Clojurescript compiler and creating a page via that process.
