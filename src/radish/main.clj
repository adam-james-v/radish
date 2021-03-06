(ns radish.main
  (:require [clojure.string :as str]
            [clojure.zip :as zip]
            [clojure.java.shell :refer [sh]]
            [clojure.tools.cli :as cli]
            [hiccup.core :refer [html]]
            [hiccup.page :as page]
            [orgmode.core :as org]
            [orgmode.html :refer [hiccupify *user-src-fn*]]
            [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as server])
  (:gen-class))

;; https://ravi.pckl.me/short/functional-xml-editing-using-zippers-in-clojure/
(defn tree-edit
  [zipper matcher editor]
  (loop [loc zipper]
    (if (zip/end? loc)
      (zip/root loc)
      (if-let [matcher-result (matcher loc)]
        (let [new-loc (zip/edit loc editor)]
          (if (not (= (zip/node new-loc) (zip/node loc)))
            (recur (zip/next new-loc))
            (recur (zip/next loc))))
        (recur (zip/next loc))))))

(defn get-nodes
  [zipper matcher]
  (loop [loc zipper
         acc []]
    (if (zip/end? loc)
      acc
      (if (matcher loc)
        (recur (zip/next loc) (conj acc (zip/node loc)))
        (recur (zip/next loc) acc)))))

(defn match-src?
  [loc]
  (let [node (zip/node loc)
        {:keys [block]} node]
    (= block :src)))

(defn match-result?
  [loc]
  (let [node (zip/node loc)
        s (-> node :content first)]
    (when s
      (str/starts-with?
       (str/upper-case s)
       "#+RESULT"))))

(defn match-deps-old?
  [loc]
  (let [node (zip/node loc)
        {:keys [block content]} node]
    (and (= block :src)
         (str/includes? (apply str content) ":deps"))))

;; read-string breaks when trying to read lines with ;;comments as it
;; ignores them and basically thinks its reading an empty string (EOF error)
;; read-string also chokes on a shebang line '#!/usr/bin/bb' for example

(defn match-deps?
  [loc]
  (let [node (zip/node loc)
        {:keys [block attribs]} node]
    (when (and (= block :src) (contains? (set attribs) "clojure"))
      (let [xcontent (->> node
                          :content
                          (remove #(str/starts-with? % ";")))
            src (read-string (str "[" (apply str xcontent) "]"))
            maybe-deps (filter map? src)]
        (not (empty? (filter #(contains? % :deps) maybe-deps)))))))

(defn match-ns?
  [loc]
  (let [node (zip/node loc)
        {:keys [type content]} node
        fl (->> content
                (filter string?)
                (filter #(not= (str/trim %) ""))
                first)]
    (and (= type :block)
         (str/starts-with? fl "(ns"))))

(defn match-headlines?
  [loc]
  (let [node (zip/node loc)]
    (= (:type node) :headline)))

;; don't remove the entire node as the #+RESULT is within a paragraph
;; which means there may be required content following the results.
(defn- remove-result
  [node]
  (let [new-content (drop 2 (:content node))]
    (assoc node :content (vec new-content))))

(defn remove-results
  [org]
  (let [org-zipper (org/zip org)]
    (tree-edit org-zipper match-result? remove-result)))

(defn- retag-deps-node
  [node]
  (let [new-attribs ["clojure-ref"]]
    (assoc node :attribs new-attribs)))

(defn retag-deps
  [org]
  (let [org-zipper (org/zip org)]
    (tree-edit org-zipper match-deps? retag-deps-node)))

(defn get-headlines
  [org]
  (map :text (get-nodes (org/zip org) match-headlines?)))

(defn get-title
  [org-str]
  (let [lines (str/split-lines org-str)
        headlines (filter #(not (str/starts-with? % ";"))
                          (get-headlines (org/parse-str org-str)))
        f #(str/starts-with? (str/upper-case %) "#+TITLE")
        title (->> lines (filter f) first)]
    (if title
      (str/join " " (-> title (str/split #" ") rest))
      (first headlines))))

(defn safe-name
  [title]
  (-> title
      str/lower-case
      (str/replace #";" "-")
      (str/replace #" " "-")))

(defn get-author
  [org-str]
  (let [lines (str/split-lines org-str)
        f #(str/starts-with? (str/upper-case %) "#+AUTHOR")
        title (->> lines
                   (filter f)
                   first)]
    (when title
      (str/join " " (-> title (str/split #" ") rest)))))

(defn get-deps
  [org-str]
  (let [m (-> org-str
              org/parse-str
              org/zip
              (get-nodes match-deps?)
              first)]
    (when m
      (->> m
           :content
           (apply str)
           read-string
           (#(apply dissoc % (remove #{:deps} (keys %))))))))

;; we abuse org-mode syntax only once for allowing inline radish config.
;; we just add 'radish-config' to a src block and we can get it via the attribs list
;; it means nothing in org-mode context, but will also not cause issues (I am pretty sure)
(defn get-radish-config
  [org-str]
  (let [srcs (-> org-str
                 org/parse-str
                 org/zip
                 (get-nodes match-src?)
                 (->> (filter
                       (fn [{:keys [attribs]}]
                         (not (empty? (filter #{"radish-config"} attribs)))))))]
    (if (empty? srcs)
      {}
      (->> srcs
           first
           :content
           (apply str)
           (#(str/replace % "'" "")) ;; hack to prevent (quote 'sym) issue. in comment-src-ndes
           read-string))))

(defn- keep-require
  [ns-form]
  (first
   (filter
    (fn [el]
      (when (seq? el)
        (= (first el) :require)))
    ns-form)))

;; NOTE: potentially I should make this return a map to capture refers, aliases, exludes, etc.
(defn get-namespace-requires
  "Returns a vector of all required namespaces from all ns declarations, dropping aliases, refers, excludes."
  [org-str]
  (let [nodes-list (-> org-str
                       org/parse-str
                       org/zip
                       (get-nodes match-ns?))]
    (->> nodes-list
         (mapcat :content)
         (remove #(str/starts-with? % ";"))
         (apply str)
         (#(str "[" % "]"))
         read-string
         (filter (fn [[sym & _]] (= sym 'ns))) ;; drop any code that isn't a ns decl
         (map keep-require)
         (mapcat rest)
         (map first)
         (into #{})
         vec)))

(def blacklisted-namespaces
  #{'hiccup.core
    'clojure.java.shell
    'clojure.java.io
    'clojure.tools.cli})

(defn- blacklisted?
  [req-entry]
  (blacklisted-namespaces (first req-entry)))

(defn- clean-namespace-decl
  [node]
  (let [to-remove (map name blacklisted-namespaces)
        src (->> node
                 :content
                 (apply str)
                 (#(str "[" % "]"))
                 read-string)
        ns-decl (->> src
                     (filter (fn [[sym & _]] (= sym 'ns)))
                     first
                     vec)
        ns-decl-idx (->> src
                         (take-while (fn [[sym & _]] (= sym 'ns)))
                         count
                         dec)
        req-idx (->> ns-decl
                     (take-while #(not (when (seqable? %) (= (first %) :require))))
                     count)
        reqs (->> ns-decl
                  (filter #(when (seqable? %) (= (first %) :require)))
                  first
                  rest
                  (remove blacklisted?)
                  (into '[:require])
                  (apply list))
        xf-ns-decl (apply list (assoc ns-decl req-idx reqs))
        xf-src (apply list (assoc src ns-decl-idx xf-ns-decl))
        xf-src-str (apply str (map #(with-out-str (clojure.pprint/pprint %)) xf-src))
        xf-content (-> xf-src-str
                       (str/replace "(ns\n" "(ns")
                       (str/replace "(:require\n" "(:require")
                       str/split-lines
                       vec)]
  (assoc node :content xf-content)))

(defn clean-namespace-decls
  [org]
  (let [org-zipper (org/zip org)]
    (tree-edit org-zipper match-ns? clean-namespace-decl)))

;; gdaythisisben from Twitch
;; (__(o_o)__)
;; meditating with parens

;; for more trustworthy results, it may be wise to implement this
;; with a proper reader that adjusts the src, instead of simple string replacements
;; for example, this will break if a user has a space between the open paren
;; and the fn name. That may be rare, but it's not impossible, and could be very confusing.
(defn- comment-content
  ([sym-list content] (comment-content (rest sym-list) (first sym-list) content))
  ([sym-list sym content]
   (if sym
     (let [new-content (->> content
                            ;; this can still match incorrect fns...
                            (mapv #(str/replace % (str "(defn " sym) (str "#_(defn " sym)))
                            (mapv #(str/replace % (str "(" sym " ") (str "#_(" sym " "))))]
       (recur (vec (rest sym-list)) (first sym-list) new-content))
     content)))

(defn- comment-src-node
  [sym-list node]
  (let [new-content (comment-content sym-list (:content node))]
    (assoc node :content new-content)))

(defn comment-src-nodes
  [org sym-list]
  (let [sym-list (filterv #(not= (str/trim (name %)) "") sym-list)
        org-zipper (org/zip org)
        edit-fn (partial comment-src-node sym-list)]
    (tree-edit org-zipper match-src? edit-fn)))

(defn src-fn
  [x]
  (let [class (str "src-" (first (:attribs x)))]
    [:div.code-container
     [:pre {:class class} (str/join "\n" (:content x))]]))

(defn org-content*
  [org-str]
  (let [title (get-title org-str)
        author (get-author org-str)
        config (get-radish-config org-str)]
    (-> org-str
        org/parse-str
        retag-deps
        remove-results
        clean-namespace-decls
        (comment-src-nodes (:exclude-fns config))
        hiccupify)))

(defn org-content
  [org-str]
  (let [title (get-title org-str)
        author (get-author org-str)
        config (get-radish-config org-str)]
    (list
     [:header [:h1 title]]
     [:main (-> org-str
                org/parse-str
                retag-deps
                remove-results
                clean-namespace-decls
                (comment-src-nodes (:exclude-fns config))
                hiccupify)]
     [:footer
      (when author
        [:p "Written by " [:span {:style {:font-style "italic"}} author]])
      [:p "Generated by "
       [:span {:style {:font-weight "bold"}}
        [:a {:href "https://github.com/adam-james-v/radish"} "radish"]]]])))

(defn org->site
  ([org-str] (org->site org-str nil))
  ([org-str advanced?]
   (let [title (get-title org-str)
         author (get-author org-str)
         org-content (into [:body] (org-content org-str))
         code-runner-str (slurp (clojure.java.io/resource "code-runner.cljs"))]
     (page/html5
      [:head
       [:meta {:charset "utf-8"}]
       [:title title]
       (page/include-css
        "style.css"
        "codemirror.css"
        "nord.css")
       (page/include-js
        "codemirror.js"
        "clojure.js")
       ;; Always include React and ReactDOM
       (page/include-js
        "react.production.min.js"
        "react-dom.production.min.js"
        #_"https://unpkg.com/react@17/umd/react.production.min.js"
        #_"https://unpkg.com/react-dom@17/umd/react-dom.production.min.js")
       (if advanced?
         ;; include compiled js
         (page/include-js
          "radish.js"
          "radish.reagent.js")
         ;; use scittle for basic build
         (page/include-js
          "https://cdn.jsdelivr.net/gh/borkdude/scittle@0.0.2/js/scittle.js"
          "https://cdn.jsdelivr.net/gh/borkdude/scittle@0.0.2/js/scittle.reagent.js"))
       [:script {:type "application/x-scittle"}
        (if advanced?
          (str/replace code-runner-str "js/scittle.core.eval_string" "js/radish.core.eval_string")
          code-runner-str)]]
      org-content))))

(defn- rand-col
  []
  (str "rgb("
       (+ 100 (rand-int 155)) ","
       (+ 100 (rand-int 155)) ","
       (+ 100 (rand-int 155)) ")"))

(defn- rand-cols-css
  []
  (str/join "\n" [":root {"
                  "  --bg: #ffecdc;"
                  (str "  --col01: " (rand-col) ";")
                  (str "  --col02: " (rand-col) ";")
                  (str "  --col03: " (rand-col) ";")
                  (str "  --col04: " (rand-col) ";")
                  "}\n\n"]))

(defn basic-build!
  [org-str]
  (let [name (safe-name (get-title org-str))
        index (binding [*user-src-fn* src-fn] (org->site org-str))
        style (str (rand-cols-css)
                   (slurp (clojure.java.io/resource "shared/style.css")))]
    (sh "mkdir" "-p" name)
    (doseq [file ["codemirror.css"
                  "nord.css"
                  "codemirror.js"
                  "clojure.js"
                  "react.production.min.js"
                  "react-dom.production.min.js"]]
      (spit (str name "/" file) (slurp (clojure.java.io/resource (str "shared/" file)))))
    (spit (str name "/style.css") style)
    (spit (str name "/index.html") index)))

;; extra deps for the shadow-cljs compilation
(def cljs-deps
  '{borkdude/sci         {:mvn/version "0.2.6"}
    reagent/reagent {:mvn/version "1.0.0"}
    thheller/shadow-cljs {:mvn/version "2.14.0"}
    cljsjs/react {:mvn/version "17.0.2-0"}
    cljsjs/react-dom {:mvn/version "17.0.2-0"}
    cljsjs/react-dom-server {:mvn/version "17.0.2-0"}})

;; deps to dissoc b/c they won't work or aren't needed in the browser
(def clj-deps ['hiccup/hiccup
               'org.clojure/clojure
               'org.clojure/tools.cli])

(defn prepare-deps
  [org-str]
  (-> (get-deps org-str)
      (update :deps (partial apply dissoc) clj-deps)
      (update :deps merge cljs-deps)))

(defn prepare-namespace
  [org-str]
  (->> org-str
       get-namespace-requires
       (remove blacklisted-namespaces)
       vec))

(defn- ns-publics-wrap
  [sym]
  `(ns-publics '~sym))

(defn- ns-symbol-wrap
  [sym]
  `'~(identity sym))

(defn radish-ns-src-str
  [org-str]
  (let [reqs (prepare-namespace org-str)
        req-fn #(apply assoc {} ((juxt ns-symbol-wrap ns-publics-wrap) %))
        req-maps (map req-fn reqs)]
    (str/join "\n"
              ["(ns radish.radns"
               (str (seq (concat [:require] (map vector reqs))) ")")
               "(def my-ns-map"
               (str (apply merge req-maps) ")")])))

(defn shadow-cljs-config
  [org-str]
  (let [name (safe-name (get-title org-str))]
    {:builds
     {:main
      {:target :browser
       :js-options
       {:resolve {"react" {:target :global
                           :global "React"}
                  "react-dom" {:target :global
                               :global "ReactDOM"}}}
       :modules
       {:radish {:entries ['radish.core]}
        :radish.reagent {:entries ['radish.reagent]
                         :depends-on #{:radish}}}
       :output-dir "compiled"
       :devtools   {:repl-pprint true}}}}))

(defn- prepare-radish-project!
  [org-str]
  (let [name (str (safe-name (get-title org-str)) "-build")
        src-dest  (str name "/src/radish")]
    (sh "mkdir" "-p" name)
    (sh "mkdir" "-p" src-dest)
    
    (doseq [file ["core.cljs"
                  "error.cljs"
                  "reagent.cljs"]]
      (spit (str name "/src/radish/" file)
            (slurp (clojure.java.io/resource (str "advanced/" file)))))

    (spit (str name "/package.json") "{}")
    (spit (str name "/shadow-cljs.edn") (shadow-cljs-config org-str))
    (spit (str name "/deps.edn") (prepare-deps org-str))
    (spit (str name "/src/radish/radns.cljs") (radish-ns-src-str org-str))))

(defn- run-radish-build!
  [org-str]
  (prepare-radish-project! org-str)
  (let [name (str (safe-name (get-title org-str)) "-build")
        config (shadow-cljs-config org-str)]
    ;; don't know how to do this within same process yet
    (sh "/usr/local/bin/clojure" "-M" "-m" "shadow.cljs.devtools.cli" "release" ":main"
        :dir name)))

(defn advanced-build!
  [org-str {:keys [fast? keep-build?] :or {fast? false
                                           keep-build? false}}]
  (let [name (safe-name (get-title org-str))
        build-name (str name "-build")
        index (binding [*user-src-fn* src-fn] (org->site org-str :advanced))
        style (str (rand-cols-css)
                   (slurp (clojure.java.io/resource "shared/style.css")))]
    (when-not fast?
      (run-radish-build! org-str)
      (sh "mkdir" "-p" name)
      (sh "cp"
          (str build-name "/compiled/radish.js")
          (str build-name "/compiled/radish.reagent.js")
          name)
      (when keep-build? (sh "rm" "-rf" build-name))
      (doseq [file ["codemirror.css"
                    "nord.css"
                    "codemirror.js"
                    "clojure.js"
                    "react.production.min.js"
                    "react-dom.production.min.js"]]
        (spit (str name "/" file) (slurp (clojure.java.io/resource (str "shared/" file)))))
      (spit (str name "/style.css") style))
    (spit (str name "/index.html") index)))

(def cli-options
  [["-i" "--infile FNAME" "The file to be compiled."
    :default nil]
   ["-f" "--fast" "Re-compile only the index.html file"]
   ["-k" "--keep" "Keep the Shadow-cljs build project directory."]
   ["-h" "--help"]])

(defn- requires-advanced?
  [org-str]
  (let [org (org/parse-str org-str)]
    (not (empty? (get-nodes (org/zip org) match-deps?)))))

(defn -main
  [& args]
  (let [parsed (cli/parse-opts args cli-options)
        {:keys [:infile :fast :help :keep]} (:options parsed)
        [in _] (when infile (str/split infile #"\."))]
    (cond
      help
      (do (println "Usage:")
          (println (:summary parsed)))
          
      (nil? infile)
      (println "Please specify an input file")
      
      :else
      (let [org-str (slurp infile)
            outdir (safe-name (get-title org-str))
            msg (str "\nCompiling " infile " into directory " outdir ".")]
        (if (requires-advanced? org-str)
          (do
            (println "Detected external dependencies, running advanced build.")
            (when fast (println "Fast Flag Enabled, re-compiling index.html only."))
            (println msg)
            (advanced-build! org-str {:fast? fast :keep-build? keep}))
          (do
            (println "No external dependencies detected, running basic build.")
            (println msg)
            (basic-build! org-str)))
        (println "Success! Have a nice day :)"))))
  ;; sh uses futures in different threads, so shut them down to prevent delayed exit
  ;; calling sh in REPL doesn't have the hanging issue, so shutdown agents here.
  (shutdown-agents))
