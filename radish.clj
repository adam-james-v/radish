(defn bulb
  [cpts]
  (let [beza (apply path/bezier cpts)
        bezb (apply path/bezier (flip-y cpts))
        lt (path/line (first cpts) (first (flip-y cpts)))
        lb (path/line (last cpts) (last (flip-y cpts)))
        shape (tf/merge-paths beza bezb)
        ctr (tf/centroid shape)]
    (-> shape
        (tf/rotate 270)
        (tf/translate (utils/v* ctr [-1 -1])))))

(defn leaf
  [h & {:keys [mirror] :or {mirror false}}]
  (let [m (if mirror -1 1)
        main-pts [[0 0] [(* 0.125 h m) (* 0.275 h)] [0 h]]
        main (apply path/bezier main-pts)
        swoop-pts [[0 (* 0.125 h)]         [(* 0.03 h m) (* -0.15 h)]
                   [(* 0.2125 h m) (* -0.175 h)] [(* 0.3 h m) 0]]
        swoop (-> (apply path/bezier swoop-pts)
                  (tf/translate (utils/v* [(* -1) -1] (last swoop-pts))))]
    (tf/merge-paths
     main
     swoop
     (-> swoop (tf/rotate (* 270 m)) (tf/translate [(* -0.1375 h m) (* 0.315 h)]))
     (-> (apply path/bezier (drop-last swoop-pts))
         (tf/rotate (* 315 m)) (tf/translate [(* -0.175 h m) (* 0.515 h)]))
     (-> (apply path/bezier (drop-last swoop-pts))
         (tf/rotate (* 330 m)) (tf/translate [(* -0.1 h m) (* 0.825 h)])))))

(defn linear-gradient
  [deg col-a col-b]
  (let [[x1 y1] (utils/rotate-pt-around-center [0 50] deg [50 50])
        [x2 y2] (utils/rotate-pt-around-center [100 50] deg [50 50])
        id (gensym "gradient-")]
    [:linearGradient {:id id
                      :x1 (str x1 "%")
                      :y1 (str y1 "%")
                      :x2 (str x2 "%")
                      :y2 (str y2 "%")}
     [:stop {:offset "0%" :stop-color col-a}]
     [:stop {:offset "100%" :stop-color col-b}]]))

(def radish-bulb
  (let [gradient (linear-gradient 230 "rgb(244,131,120)" "rgb(235,120,196)")
        gradient-id (get-in gradient [1 :id])
        shadows (tf/merge-paths
                 (-> (path/line [0 0] [10 10]) (tf/translate [12 30]))
                 (-> (path/line [0 0] [6 6]) (tf/translate [28 26]))
                 (-> (path/line [0 0] [6 6]) (tf/translate [7 45]))
                 (-> (path/line [0 0] [3 3]) (tf/translate [-31 -21])))
        roots (tf/merge-paths
               (path/bezier [0 61] [-8 71] [0 84]))]
    (-> (bulb [[0 0] [45 75] [90 50] [102 0]])
        (tf/merge-paths shadows roots)
        (tf/style {:fill "none"
                   :stroke-width 7
                   :stroke-linecap "round"
                   :stroke (str "url(#" gradient-id ")")})
        (->> (list [:defs gradient])))))

(def radish-leaves
  (let [gradient (linear-gradient 103 "rgb(120,202,106)" "rgb(182,192,174)")
        gradient-id (get-in gradient [1 :id])]
    (->
     (tf/merge-paths
      (-> (leaf 80 :mirror true) (tf/rotate 3) (tf/translate [-2 -145]))
      (-> (leaf 100 :mirror true) (tf/rotate 12) (tf/translate [14 -97]))
      (-> (leaf 160 :mirror false) (tf/rotate -11) (tf/translate [-20 -158])))
     (tf/style {:fill "none"
                :stroke-width 6
                :stroke-linecap "round"
                :stroke (str "url(#" gradient-id ")")})
     (->> (list [:defs gradient])))))

(let [[[_ leaves-grad] leaves] radish-leaves
      [[_ bulb-grad] bulb] radish-bulb]
  (-> (el/g
       #_(-> (el/rect 500 500)
           (tf/style {:fill "lavender"}))
       leaves
       (-> bulb (tf/translate [0 38]) (tf/style {:fill "rgba(244,131,120,0.2)"})))
      (tf/translate [200 220])
      (->> (list [:defs leaves-grad bulb-grad]))
      (svg 400 400)
      (svg! "radish.svg")))
