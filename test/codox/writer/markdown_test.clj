(ns codox.writer.markdown-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [codox.writer.markdown :as md]))

(def sample-project
  {:name        "com.example/test-lib"
   :version     "1.2.3"
   :description "A test library"
   :output-path nil ;; set per test
   :namespaces
   [{:name 'example.core
     :doc  "Core namespace for example."
     :publics
     [{:name     'greet
       :type     :function
       :doc      "Returns a greeting string."
       :arglists '([name] [name greeting])
       :line     10
       :file     "example/core.clj"
       :path     "src/example/core.clj"}
      {:name     'version
       :type     :var
       :doc      "Current library version."
       :line     5
       :file     "example/core.clj"
       :path     "src/example/core.clj"}
      {:name     'old-fn
       :type     :function
       :doc      "This is deprecated."
       :arglists '([x])
       :line     20
       :file     "example/core.clj"
       :path     "src/example/core.clj"
       :deprecated "1.0.0"
       :added    "0.1.0"}
      {:name     'defwidget
       :type     :macro
       :doc      "Define a widget."
       :arglists '([name & body])
       :line     30
       :file     "example/core.clj"
       :path     "src/example/core.clj"}]}
    {:name 'example.proto
     :doc  "Protocol definitions."
     :publics
     [{:name    'Renderable
       :type    :protocol
       :doc     "Things that can be rendered."
       :line    5
       :file    "example/proto.clj"
       :path    "src/example/proto.clj"
       :members [{:name     'render
                  :type     :function
                  :doc      "Render to string."
                  :arglists '([this])
                  :line     7
                  :file     "example/proto.clj"
                  :path     "src/example/proto.clj"}
                 {:name     'render-to
                  :type     :function
                  :doc      "Render to output."
                  :arglists '([this out])
                  :line     9
                  :file     "example/proto.clj"
                  :path     "src/example/proto.clj"}]}]}]
   :documents
   [{:name    "getting-started"
     :title   "Getting Started"
     :format  :markdown
     :content "# Getting Started\n\nWelcome to the library.\n"}]
   :source-uri "https://github.com/example/test-lib/blob/{version}/{filepath}#L{line}"
   :git-commit (delay "abc123")})

(defn- with-temp-dir [f]
  (let [dir (io/file (System/getProperty "java.io.tmpdir")
                     (str "codox-md-test-" (System/nanoTime)))]
    (.mkdirs dir)
    (try
      (f (.getAbsolutePath dir))
      (finally
        (doseq [f (reverse (file-seq dir))]
          (.delete f))))))

(deftest test-write-docs-creates-files
  (with-temp-dir
    (fn [dir]
      (md/write-docs (assoc sample-project :output-path dir))
      (testing "index.md exists"
        (is (.exists (io/file dir "index.md"))))
      (testing "namespace files exist"
        (is (.exists (io/file dir "example.core.md")))
        (is (.exists (io/file dir "example.proto.md"))))
      (testing "guide files exist"
        (is (.exists (io/file dir "guides" "getting-started.md")))))))

(deftest test-index-content
  (with-temp-dir
    (fn [dir]
      (md/write-docs (assoc sample-project :output-path dir))
      (let [content (slurp (io/file dir "index.md"))]
        (testing "YAML frontmatter"
          (is (str/starts-with? content "---"))
          (is (str/includes? content "library: com.example/test-lib"))
          (is (str/includes? content "version: 1.2.3")))
        (testing "namespace listing"
          (is (str/includes? content "[example.core](example.core.md)"))
          (is (str/includes? content "[example.proto](example.proto.md)")))))))

(deftest test-namespace-content
  (with-temp-dir
    (fn [dir]
      (md/write-docs (assoc sample-project :output-path dir))
      (let [content (slurp (io/file dir "example.core.md"))]
        (testing "namespace header"
          (is (str/includes? content "# example.core")))
        (testing "namespace docstring"
          (is (str/includes? content "Core namespace for example.")))
        (testing "function with arglists"
          (is (str/includes? content "### `greet`"))
          (is (str/includes? content "(greet ")))
        (testing "var type"
          (is (str/includes? content "### `version`"))
          (is (str/includes? content "_var_")))
        (testing "macro type"
          (is (str/includes? content "### `defwidget`"))
          (is (str/includes? content "_macro_")))
        (testing "deprecation"
          (is (str/includes? content "**Deprecated**"))
          (is (str/includes? content "Added in 0.1.0")))
        (testing "source links"
          (is (str/includes? content "https://github.com/example/test-lib/blob/1.2.3/src/example/core.clj#L10")))))))

(deftest test-protocol-members
  (with-temp-dir
    (fn [dir]
      (md/write-docs (assoc sample-project :output-path dir))
      (let [content (slurp (io/file dir "example.proto.md"))]
        (testing "protocol heading"
          (is (str/includes? content "### `Renderable`"))
          (is (str/includes? content "_protocol_")))
        (testing "protocol members"
          (is (str/includes? content "#### Protocol Members"))
          (is (str/includes? content "### `render`"))
          (is (str/includes? content "### `render-to`")))))))

(deftest test-guide-content
  (with-temp-dir
    (fn [dir]
      (md/write-docs (assoc sample-project :output-path dir))
      (let [content (slurp (io/file dir "guides" "getting-started.md"))]
        (is (str/includes? content "Welcome to the library."))))))
