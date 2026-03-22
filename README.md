# codox-md

[![Clojars Project](https://img.shields.io/clojars/v/com.dcj/codox-md.svg)](https://clojars.org/com.dcj/codox-md)

A [Codox](https://github.com/weavejester/codox) writer that generates **Markdown** documentation instead of HTML, designed for embedding API docs in Clojure library JARs as classpath resources.

## Why

Clojure libraries ship source in JARs, but not documentation. `codox-md` adds Markdown API docs to your JAR at build time, under a namespaced path (`docs/<group>/<artifact>/`) that avoids conflicts with other libraries. These docs can then be discovered and browsed at runtime using [clj-doc-browse](https://github.com/dcj/clj-doc-browse).

## How it works

`codox-md` provides two things:

1. **`codox.writer.markdown`** — A Codox-compatible writer that emits `.md` files instead of HTML
2. **`codox.md.build`** — A build-time helper that integrates with `clojure -T:build`, using `create-basis` and `DynamicClassLoader` to load your project's dependencies and Codox in-process

## Quick start

### 1. Add codox-md to your `:build` alias

```clojure
;; deps.edn
:build {:deps {io.github.clojure/tools.build
               {:git/tag "v0.10.10" :git/sha "deedd62"}
               slipset/deps-deploy {:mvn/version "0.2.2"}
               com.dcj/codox-md {:mvn/version "0.1.0"}}
        :ns-default build}
```

### 2. Wire doc generation into your `build.clj`

```clojure
(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [codox.md.build :as doc]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'com.example/my-lib)
(def version "0.1.0")
(def class-dir "target/classes")

(defn ci [opts]
  ;; ... (test, delete target, write pom) ...
  (let [opts (jar-opts opts)]
    (b/write-pom opts)

    ;; Generate Markdown API docs
    (doc/generate-docs {:lib lib :version version
                        :description (:description opts)
                        :source-uri (str (get-in opts [:scm :url])
                                         "/blob/{git-commit}/{filepath}#L{line}")})

    ;; Include generated docs in the JAR
    (b/copy-dir {:src-dirs ["resources" "src" "target/doc-resources"]
                 :target-dir class-dir})
    (b/jar opts))
  opts)
```

### 3. Build your JAR

```bash
clojure -T:build ci
```

Your JAR now contains:

```
docs/com.example/my-lib/
  index.md              # Library overview + namespace listing
  my.namespace.md       # Full API docs per namespace
  guides/               # Optional, from doc/ directory
```

## Generated Markdown format

Each namespace file includes:

- Namespace docstring
- All public vars sorted alphabetically
- Type badges (function, macro, protocol, multimethod, var)
- Full arglists as Clojure code blocks
- Docstrings
- Source links (using `{git-commit}` for stable GitHub URLs)
- Deprecation and version annotations
- Protocol members nested under their protocol

## Source URI templates

The `:source-uri` option supports these placeholders:

| Placeholder | Value |
|---|---|
| `{filepath}` | Path relative to project root (e.g. `src/my/ns.clj`) |
| `{classpath}` | Classpath-relative path (e.g. `my/ns.clj`) |
| `{line}` | Line number |
| `{version}` | Project version string |
| `{git-commit}` | Current HEAD commit SHA |

## Requirements

- Clojure 1.12+ (uses `DynamicClassLoader` for in-process dep loading)
- [Codox](https://github.com/weavejester/codox) 0.10.8 (loaded dynamically at build time, not a compile-time dependency)

## AI assistant integration (Claude Code)

Libraries with embedded docs become queryable by AI coding assistants. With [Claude Code](https://claude.com/claude-code) and [clojure-mcp](https://github.com/nicpjocson/clojure-mcp), a `/deps-docs` skill lets Claude read your dependency docs at the REPL instead of relying on training data or web search.

### Setup

Create `~/.claude/commands/deps-docs.md` with these capabilities:

- `/deps-docs` — list libraries with embedded docs on the classpath
- `/deps-docs some.namespace` — fetch API docs for a namespace
- `/deps-docs group/artifact` — list documented namespaces in a library
- `/deps-docs search query` — full-text search across all docs
- `/deps-docs setup` — add clj-doc-browse to a project's `:nrepl` alias and CLAUDE.md

The skill uses the clojure-mcp nREPL eval tool to call `doc.browse` functions. When Claude needs to understand a dependency's API, it can pull the docs directly instead of guessing. This is especially valuable for private or niche libraries that aren't in the model's training data.

See the [deps-docs skill source](https://gist.github.com/dcj/7fda34341c40ac71d7e08bcce7f82aac) for the full implementation.

## Related projects

- [clj-doc-browse](https://github.com/dcj/clj-doc-browse) — Runtime classpath scanner that discovers and reads these embedded docs
- [clj-doc-browse.el](https://github.com/dcj/clj-doc-browse-el) — Emacs/CIDER integration for browsing docs from the REPL

## License

Copyright (C) 2026 Clark Communications Corporation

[MIT License](LICENSE)
