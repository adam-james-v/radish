# Radish
A kinda-cool org-mode -> interactive blog post tool written with and for Clojure(script).

Here's an example post created with this tool:

<a href="https://adam-james-v.github.io/posts/radish-basic-example/index.html">Radish Basic Example</a>


## Why Radish?
A friend sent me a meme about radishes while I was thinking of a name for this project. I chuckled, then figured it's a good enough name for a small project like this.

## Current Limitations
The only build method so far is 'basic-build', which relies on a simple script executed in your browser by scittle after the page loads. It has no dependency loading capabilities and thus is limited to executing Clojurescript code that only relies on core libraries.

A more complete build process is underway where the idea is to compile dependencies using the Clojurescript compiler and creating a page via that process.
