(ns codox.md.build
  "Build-time helper for generating Markdown docs into JAR resources.

   Uses tools.build's create-basis to resolve the project's dependencies
   plus Codox, then loads them into the current JVM via DynamicClassLoader.
   This lets doc generation run in-process during `clojure -T:build ci`.

   Usage from build.clj:
     (doc/generate-docs {:lib lib :version version ...})"
  (:require [clojure.java.io :as io]
            [clojure.tools.build.api :as b])
  (:import [clojure.lang DynamicClassLoader]))

(def ^:private codox-deps
  "Codox + compatible tools.namespace for the project basis.
   Codox 0.10.8 ships with tools.namespace 0.2.11, but the build JVM
   (via tools.build) has 1.5.0 already loaded. We pin 1.5.0 to avoid
   classloading conflicts."
  {'codox/codox                       {:mvn/version "0.10.8"}
   'org.clojure/tools.namespace       {:mvn/version "1.5.0"}})

(defn- load-project-deps!
  "Resolve the project's deps + Codox via create-basis, then add all
   classpath roots to a DynamicClassLoader on the current thread."
  [source-paths]
  (let [basis (b/create-basis {:extra {:deps codox-deps}})
        roots (:classpath-roots basis)
        parent (.getContextClassLoader (Thread/currentThread))
        dcl    (DynamicClassLoader. parent)]
    (.setContextClassLoader (Thread/currentThread) dcl)
    ;; Add all resolved classpath entries
    (doseq [root roots]
      (.addURL dcl (.toURL (.toURI (io/file root)))))
    ;; Add source paths (for Codox to require namespaces)
    (doseq [p source-paths]
      (.addURL dcl (.toURL (.toURI (io/file p)))))))

(defn generate-docs
  "Generate Markdown API docs into target/doc-resources/.

   Resolves the project's dependencies and Codox via create-basis,
   loads them into the current JVM, then runs Codox with the
   Markdown writer.

   Opts:
     :lib          - qualified symbol, e.g. 'com.dcj/clj-oa3
     :version      - version string
     :description  - library description (optional)
     :source-paths - vector of source dirs (default [\"src\"])
     :source-uri   - source link template (optional)
                     Supports {filepath}, {line}, {version}, {git-commit}
     :doc-paths    - vector of guide dirs (default [\"doc\"])

   Writes to target/doc-resources/docs/<group-id>/<artifact-id>/"
  [{:keys [lib version description source-paths source-uri doc-paths]
    :or {source-paths ["src"] doc-paths ["doc"]}}]
  (load-project-deps! source-paths)
  ;; Dynamically require codox (now on classloader via create-basis)
  (require 'codox.main)
  (let [codox-generate (resolve 'codox.main/generate-docs)
        group          (or (namespace lib) (name lib))
        artifact       (name lib)
        out-path       (str "target/doc-resources/docs/" group "/" artifact)]
    (codox-generate
     {:language     :clojure
      :name         (str lib)
      :version      version
      :description  (or description "")
      :source-paths source-paths
      :doc-paths    doc-paths
      :output-path  out-path
      :writer       'codox.writer.markdown/write-docs
      :source-uri   source-uri})))
