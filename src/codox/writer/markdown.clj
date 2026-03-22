(ns codox.writer.markdown
  "Codox writer that generates Markdown files instead of HTML.
   Designed for embedding API docs in JARs as classpath resources."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

;;; --- Source URI ---

(defn- source-uri [project ns var]
  (when-let [uri-template (:source-uri project)]
    (let [uri (cond
                (string? uri-template) uri-template
                (map? uri-template)
                (some (fn [[re template]]
                        (when (re-find (re-pattern re) (str (:path var)))
                          template))
                      uri-template))]
      (when uri
        (-> uri
            (str/replace "{filepath}" (str (or (:path var) "")))
            (str/replace "{classpath}" (str (or (:file var) "")))
            (str/replace "{basename}" (or (some-> (:file var)
                                                  (str/split #"/")
                                                  last)
                                          ""))
            (str/replace "{line}" (str (:line var)))
            (str/replace "{version}" (or (:version project) ""))
            (str/replace "{git-commit}" (str (force (:git-commit project)))))))))

;;; --- Var rendering ---

(defn- type-label [var]
  (case (:type var)
    :macro         "macro"
    :multimethod   "multimethod"
    :protocol      "protocol"
    :var           "var"
    :function      "function"
    nil))

(defn- render-arglists [var]
  (when-let [arglists (seq (:arglists var))]
    (str "```clojure\n"
         (str/join "\n" (map (fn [args]
                               (str "(" (:name var) " " (pr-str args) ")"))
                             arglists))
         "\n```")))

(defn- render-var [project ns var]
  (let [badges (cond-> []
                 (type-label var) (conj (str "_" (type-label var) "_"))
                 (:added var)      (conj (str "Added in " (:added var)))
                 (:deprecated var) (conj (str "**Deprecated** " (:deprecated var)))
                 (source-uri project ns var)
                 (conj (str "[source](" (source-uri project ns var) ")")))
        parts  (cond-> [(str "### `" (:name var) "`")]
                 (seq badges)    (conj (str "> " (str/join " | " badges)))
                 (:arglists var) (conj (render-arglists var))
                 (:doc var)      (conj (str/trimr (:doc var)))
                 (seq (:members var))
                 (conj (str "#### Protocol Members\n"
                            (str/join "\n"
                                      (map (partial render-var project ns)
                                           (sort-by :name (:members var)))))))]
    (str (str/join "\n" parts) "\n")))

;;; --- Namespace rendering ---

(defn- render-namespace [project ns]
  (let [header  (str "# " (:name ns) "\n")
        doc     (when (:doc ns) (str "\n" (:doc ns) "\n"))
        publics (sort-by :name (:publics ns))
        api     (when (seq publics)
                  (str "\n## Public API\n\n"
                       (str/join "\n" (map (partial render-var project ns) publics))))]
    (str header doc api)))

;;; --- Index rendering ---

(defn- first-line [s]
  (when s
    (first (str/split-lines (str/trim s)))))

(defn- render-index [project]
  (let [{:keys [name version description namespaces]} project
        frontmatter (str "---\n"
                         "library: " name "\n"
                         "version: " version "\n"
                         (when description
                           (str "description: " (pr-str description) "\n"))
                         "---\n")
        header      (str "\n# " name "\n")
        desc        (when description (str "\n" description "\n"))
        ns-listing  (when (seq namespaces)
                      (str "\n## Namespaces\n\n"
                           (str/join "\n"
                                     (map (fn [ns]
                                            (str "- [" (:name ns) "]("
                                                 (:name ns) ".md)"
                                                 (when-let [d (first-line (:doc ns))]
                                                   (str " -- " d))))
                                          (sort-by :name namespaces)))))]
    (str frontmatter header desc ns-listing "\n")))

;;; --- Guide rendering ---

(defn- render-guide [{:keys [title content]}]
  content)

;;; --- Writer entry point ---

(defn write-docs
  "Codox writer function. Takes the project map with :namespaces and
   :documents populated by the Codox reader, writes Markdown files
   to :output-path."
  [{:keys [output-path namespaces documents] :as project}]
  (println "Generating Markdown docs to" output-path)

  ;; Write index
  (let [index-file (io/file output-path "index.md")]
    (io/make-parents index-file)
    (spit index-file (render-index project)))

  ;; Write namespace pages
  (doseq [ns namespaces]
    (let [ns-file (io/file output-path (str (:name ns) ".md"))]
      (spit ns-file (render-namespace project ns))))

  ;; Write guides
  (when (seq documents)
    (doseq [doc documents]
      (let [doc-file (io/file output-path "guides" (str (:name doc) ".md"))]
        (io/make-parents doc-file)
        (spit doc-file (render-guide doc)))))

  (println "Wrote" (+ 1 (count namespaces) (count documents)) "Markdown files"))
