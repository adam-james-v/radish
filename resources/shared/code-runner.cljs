(require '[reagent.core :as r]
         '[reagent.dom :as rdom])

(defn editor
  [id state]
  (let [lines (str/split-lines @state)
        cm (.fromTextArea  js/CodeMirror
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

(defn run-src
  [elem]
  (let [id (gensym "src-")
        src-str (.-innerText elem)
        parent (.-parentNode elem)
        state (r/atom src-str)]
    (rdom/render [:textarea {:id id} src-str] parent)
    (editor id state)
    (rdom/render [result-component state] parent)))

(defn run! []
  (let [blocks (vec (.getElementsByClassName js/document "src-clojure"))]
    (mapv run-src blocks)))

(run!)
