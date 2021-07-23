(ns radish.main
  (:require [clojure.string :as str]
            [clojure.zip :as zip]
            [clojure.java.shell :refer [sh]]
            [clojure.tools.cli :as cli]
            [hiccup.core :refer [html]]
            [hiccup.page :as page]
            [orgmode.core :as org]
            [orgmode.html :refer [hiccupify *user-src-fn*]])
  (:gen-class))

(defn find-title
  [org-str]
  (let [lines (str/split-lines org-str)
        f #(str/starts-with? (str/upper-case %) "#+TITLE")
        title (->> lines
                   (filter f)
                   first)]
     (str/join " "
               (if title
                 (-> title (str/split #" ") rest)
                 (-> (first lines) (str/split #" ") rest)))))

(defn safe-name
  [title]
  (-> title
      str/lower-case
      (str/replace #";" "-")
      (str/replace #" " "-")))

(defn find-author
  [org-str]
  (let [lines (str/split-lines org-str)
        f #(str/starts-with? (str/upper-case %) "#+AUTHOR")
        title (->> lines
                   (filter f)
                   first)]
    (when title
      (str/join " " (-> title (str/split #" ") rest)))))

;; https://ravi.pckl.me/short/functional-xml-editing-using-zippers-in-clojure/
(defn tree-edit
  [zipper matcher editor]
  (loop [loc zipper]
    (if (zip/end? loc)
      (zip/root loc)
      (if-let [matcher-result (matcher loc)]
        (let [new-loc (zip/edit loc editor)]
          (if (not (= (zip/node new-loc) (zip/node loc)))
            (recur (zip/next new-loc))))
        (recur (zip/next loc))))))

(defn match-result?
  [loc]
  (let [node (zip/node loc)
        s (-> node :content first)]
    (when s
      (str/starts-with?
       (str/upper-case s)
       "#+RESULT"))))

(defn remove-result
  [node]
  (let [new-content (drop 2 (:content node))]
    (assoc node :content (vec new-content))))

(defn remove-results
  [org]
  (let [org-zipper (org/zip org)]
    (tree-edit org-zipper match-result? remove-result)))

(defn org->site
  [org-str]
  (let [title (find-title org-str)
        author (find-author org-str)
        org-content
        [:body
         [:header [:h1 title]]
         [:main (-> org-str
                    org/parse-str
                    remove-results
                    hiccupify)]
         [:footer
          (when author
            [:p "Written by " [:span {:style {:font-style "italic"}} author]])
          [:p "Generated by "
           [:span {:style {:font-weight "bold"}}
            [:a {:href "https://github.com/adam-james-v/radish"} "radish"]]]]]]
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
      (page/include-js
       "https://cdn.jsdelivr.net/gh/borkdude/scittle@0.0.2/js/scittle.js"
       "https://unpkg.com/react@17/umd/react.production.min.js"
       "https://unpkg.com/react-dom@17/umd/react-dom.production.min.js"
       "https://cdn.jsdelivr.net/gh/borkdude/scittle@0.0.2/js/scittle.reagent.js")
      [:script {:type "application/x-scittle"}
       (slurp (clojure.java.io/resource "code-runner.cljs"))]]
     org-content)))

(defn src-fn
  [x]
  (let [class (str "src-" (first (:attribs x)))]
    [:div.code-container
     [:pre {:class class} (str/join "\n" (:content x))]]))

(defn basic-build!
  [org-str]
  (let [name (safe-name (find-title org-str))
        index (binding [*user-src-fn* src-fn] (org->site org-str))]
    (sh "mkdir" "-p" name)
    (doseq [file ["style.css"
                  "codemirror.css"
                  "nord.css"
                  "codemirror.js"
                  "clojure.js"]]
      (spit (str name "/" file) (slurp (clojure.java.io/resource file))))
    (spit (str name "/index.html") index)
    ;; sh uses futures in different threads, so shut them down
    (shutdown-agents)))

(def cli-options
  [["-i" "--infile FNAME" "The file to be compiled."
    :default nil]
   ["-h" "--help"]])

(defn -main
  [& args]
  (let [parsed (cli/parse-opts args cli-options)
        {:keys [:infile :help]} (:options parsed)
        [in _] (when infile (str/split infile #"\."))]
    (cond
      help
      (do (println "Usage:")
          (println (:summary parsed)))
          
      (nil? infile)
      (println "Please specify an input file")
      
      :else
      (let [org-str (slurp infile)
            outdir (safe-name (find-title org-str))
            msg (str "Compiling " infile " into directory " outdir ".")]
        (println msg)
        (basic-build! org-str)
        (println "Success! Have a nice day :)")))))
