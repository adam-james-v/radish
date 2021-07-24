(ns radish.code-runner-test
  (:require [clojure.test :as t :refer [deftest is]]
            [clojure.string :as str]))

;; cheeky hack to get two fns from the script
(def code-runner-src
  (let [src-str (str/replace
                 (slurp "resources/code-runner.cljs") #"#js" "")]
    (filter
     (fn [[_ sym & _]]
       (#{'renderable-element?
          'renderable?} sym))
     (read-string (str "[" src-str "]")))))

(doseq [f code-runner-src] (eval f))

(deftest renderable
  (is (renderable? [:asdf]))
  (is (renderable? [:circle {:r 20}]))
  (is (renderable? [:p]))
  (is (renderable? [:p "hello"]))
  (is (renderable? [:p {:style {:color "blue"}} "hello"]))
  (is (renderable? [:div [:p "hello"]]))
  (is (renderable? [:div (repeat 10 [:p "hello"])]))
  (is (not (renderable? [])))
  (is (not (renderable? [:3 "weird"])))
  (is (not (renderable? [:!ab "weird"])))
  (is (not (renderable? [:a!b "weird"])))
  (is (not (renderable? [:ab! "weird"])))
  (is (not (renderable? [:a$b "weird"])))
  (is (not (renderable? [:ab$ "weird"])))
  (is (not (renderable? [:a%b "weird"])))
  (is (not (renderable? [:ab% "weird"])))
  (is (not (renderable? [:a&b "weird"])))
  (is (not (renderable? [:ab& "weird"])))
  (is (not (renderable? [:a*b "weird"])))
  (is (not (renderable? [:ab* "weird"])))
  (is (not (renderable? [:a|b "weird"])))
  (is (not (renderable? [:ab| "weird"])))
  (is (not (renderable? [:a/b "weird"])))
  (is (not (renderable? (list :p "hi"))))
  (is (not (renderable? ["asdf" "wasd"])))
  (is (not (renderable? [[:p "hi"]])))
  (is (not (renderable? [[:p "hi"] [:p "hello"]]))))
