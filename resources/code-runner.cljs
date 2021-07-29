(require '[reagent.core :as r]
         '[reagent.dom :as rdom])

(defn editor
  [id state]
  (let [cm (.fromTextArea  js/CodeMirror
                           (.getElementById js/document id)
                           #js {:mode "clojure"
                                :theme "nord"
                                :lineNumbers true
                                :smartIndent true
                                :tabSize 2})]
    (.on cm "change" (fn [_ _]
                       (reset! state (.getValue cm))))
    (.setSize cm "auto" "auto")))

(defn renderable-element?
  [elem]
  (and (vector? elem)
       (keyword? (first elem))
       (not= (str (first elem)) ":")
       (not (str/includes? (str (first elem)) "/"))
       (not (re-matches #"[0-9.#].*" (name (first elem))))
       (re-matches #"[a-zA-Z0-9.#]+" (name (first elem)))))

(defn renderable?
  [elem]
  (when (or (renderable-element? elem) (seq? elem))
    (let [[k props content] elem
          [props content] (if (and (nil? content)
                                   (not (map? props)))
                            [nil props]
                            [props content])]
      (cond
        (seq? elem) (not (empty? (filter renderable? elem)))
        (seq? content) (not (empty? (filter renderable? content)))
        :else (or (renderable-element? content)
                  (renderable-element? elem)
                  (string? content)
                  (number? content))))))

(defn result-component
  [state]
  (fn [state]
    (let [result (try (js/scittle.core.eval_string @state)
                      (catch :default e
                        (.-message e)))]
      [:div.result
       [:pre
        [:div "RESULT:"]
        [:code (if result (str result) "nil")]
        (when (renderable? result) [:div result])]])))

(def current-ns (atom `'~'user))

(defn contains-ns?
  [s]
  (str/includes? s "(ns "))

(defn extract-ns
  [src-str]
  (->> src-str
       (#(str "[" % "]"))
       read-string
       (filter (fn [[sym & _]] (= sym 'ns))) ;; drop any code that isn't a ns decl
       last
       second))
  
(defn run-src
  [elem]
  (let [id (gensym "src-")
        src-str (.-innerText elem)
        parent (.-parentNode elem)
        this-ns (if (contains-ns? src-str)
                  `'~(extract-ns src-str)
                  @current-ns)
        xf-src-str (str "(in-ns " this-ns ")\n\n" src-str)
        state (r/atom xf-src-str)]
    (reset! current-ns this-ns) 
    (rdom/render [:textarea {:id id} xf-src-str] parent)
    (editor id state)
    (rdom/render [result-component state] parent)))

(defn run! []
  (let [blocks (vec (.getElementsByClassName js/document "src-clojure"))]
    (mapv run-src blocks)))

(run!)
