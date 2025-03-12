;; ---
;; title: Towards a calibrated problem set for Tik Tik
;; author: Cvetomir Dimov
;; ---

;; To do:
;; - small introduction to problem
;; - practical implication
;; - introduce linear regression

;; ## Load libraries
(ns presentation
  (:require [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tech.v3.dataset.modelling :as ds-mod]
            [fastmath.ml.regression :as reg]))

;; ## The Task: Tik Tik
(kind/hiccup 
 [:div {:style {:text-align "center"}}
  (kind/video {:youtube-id "giovWiYbZ4A"
               :iframe-width 700 :iframe-height 460})])

;; ## Project goal
;; 1. Create a problem set, which is:
;; - within the general population's abilities
;; - varies systemamically in difficulty
;; - takes about an hour to complete
;; 2. Identify level parameters that determine level difficulty

;; ## Designing levels
(kind/hiccup
 [:div {:style {:display "flex"}}
  [:div {:style {:flex "50%"}}
   [:image {:src "https://i.imgur.com/0ACMfwb.png"}]]
  [:div {:style {:flex "50%"}}
   [:image {:src "https://i.imgur.com/rjf42UA.png"}]]])
;; Number of barriers and number of barrier intersections.

;; ## Level solver (in Clojure & Quil)
(kind/image
 {:src "https://i.imgur.com/E0I4ACR.png"})

;; ## Study
;; - 29 levels: 9 warmup levels + 20 main levels (between 1 and 5 blockages)
;; - 49 subjects (online platform Prolific)
;; - about 1.5 hours of total time

;; ## Load data
(def tiktik-data
  (tc/dataset "pilot1_data.nippy"
              {:dataset-name "Tik Tik data"}))
tiktik-data

;; ## Summarize by subject and level
(def subject-level-summary
  (-> tiktik-data
      (tc/group-by [:user :level])
      (tc/aggregate {:attempts tc/row-count
                     :tot-duration #(reduce + (% :duration)) 
                     :blocks-tot #(last (:blocks %))
                     :solved? #(last (:success %))})
      (tc/order-by [:user :level])))
subject-level-summary

;; ## Loading level characterization
(def levels-characterization
  (tc/dataset "levels_characterization.nippy"))

(def subject-level-summary+
  (-> subject-level-summary
      (tc/left-join levels-characterization [:level])
      (tc/drop-columns [:levels_characterization.nippy.level])
      (tc/eq :optimal? [:blocks-tot :optimal-solution-length])))
(tc/column-names subject-level-summary+)

;; ## Subject performance
(def subject-summary
  (-> subject-level-summary+
      (tc/group-by [:user])
      (tc/aggregate {:attempted-levels tc/row-count
                     :solved #(tcc/sum (:solved? %))
                     :solved-optimally #(tcc/sum (:optimal? %))})))
subject-summary

;; ## Subject performance (cont'd)
(-> subject-summary
    (plotly/layer-point
     {:=x :solved
      :=y :solved-optimally
      :=x-title "Number of problems solved"
      :=y-title "Number of problems solved optimally"}))

;; ## Level performance
(def level-summary
  (-> subject-level-summary+
      (tc/group-by [:level])
      (tc/aggregate {:attempted-by tc/row-count
                     :median_attempts #(tcc/median (:blocks-tot %))
                     :solved-optimally #(tcc/sum (:optimal? %))
                     :median_t #(tcc/median (:tot-duration %))})
      (tc// :fraction-optimal [:solved-optimally :attempted-by])
      (tc/left-join levels-characterization [:level])
      (tc/drop-columns [:levels_characterization.nippy.level])))
(tc/column-names level-summary)

;; ## Number of attempts and solution time
(-> level-summary
    (plotly/base {:=x :median_t
                  :=y :median_attempts})
    (plotly/layer-smooth {:=name "Linear fit"})
    (plotly/layer-point {:=name "Data"
                         :=y-title "Median number of attempts"
                         :=x-title "Median time spent in level (s)"}))

;; ## Optimal solution frequency and number of attempts
(-> level-summary
    (plotly/base {:=x :median_attempts
                  :=y :fraction-optimal})
    (plotly/layer-smooth {:=name "Linear fit"})
    (plotly/layer-point {:=name "Data"
                         :=y-title "Relative frequency of optimal solution"
                         :=x-title "Median number of attempts"}))

;; ## Summary on Level difficulty
;; - The various measures of level difficulty highly correlate
;; - We will use the frequecy of optimal solution as a measure of level difficulty
;; - We still needs to find predictors of level difficulty, which include:
;;   - optimal solution length
;;   - search space size
;;   - trimmed search space size

;; ## Number of blockages in optimal solution
(-> level-summary
    (plotly/base {:=y :fraction-optimal
                  :=x :optimal-solution-length})
    (plotly/layer-smooth {:=name "Linear fit"})
    (plotly/layer-point {:=name "Data"
                         :=y-title "Relative frequency of optimal solution"
                         :=x-title "Optimal solution length"}))

;; ## Search space size
(-> level-summary
    (plotly/base {:=y :fraction-optimal
                  :=x :search-space})
    (plotly/layer-smooth {:=name "Linear fit"})
    (plotly/layer-point {:=name "Data"
                         :=y-title "Relative frequency of optimal solution"
                         :=x-title "Search space size"}))

;; ## Trimmed search space size
(-> level-summary
    (plotly/base {:=y :fraction-optimal
                  :=x :trimmed-search-space})
    (plotly/layer-smooth {:=name "Linear fit"})
    (plotly/layer-point {:=name "Data"
                         :=y-title "Relative frequency of optimal solution"
                         :=x-title "Trimmed search space size"}))

;; ## Load helper functions for linear regression
(defn summary
  "Generate a summary of a linear model."
  [lmdata]
  (kind/code
   (with-out-str
     (println
      lmdata))))

(defn lm
  "Compute a linear regression model for `dataset`.
  The first column marked as target is the target.
  All the columns unmarked as target are the features.
  The resulting model is of type `fastmath.ml.regression.LMData`,
  created via [Fastmath](https://github.com/generateme/fastmath).
  See [fastmath.ml.regression.lm](https://generateme.github.io/fastmath/clay/ml.html#lm)
  for `options`."
  ([dataset]
   (lm dataset nil))
  ([dataset options]
   (let [inference-column-name (-> dataset
                                   ds-mod/inference-target-column-names
                                   first)
         ds-without-target (-> dataset
                               (tc/drop-columns [inference-column-name]))]
     (reg/lm
      ;; ys
      (get dataset inference-column-name)
      ;; xss
      (tc/rows ds-without-target)
      ;; options
      (merge {:names (-> ds-without-target
                         tc/column-names
                         vec)}
             options)))))

;; ## Linear regression
(def mlr-model
  (-> level-summary
      (tc/select-columns [:fraction-optimal
                          :optimal-solution-length
                          :trimmed-search-space])
      (ds-mod/set-inference-target :fraction-optimal)
      lm))

;; ## Linear regression (cont'd)
(summary mlr-model)

;; ## Conclusion
;; - There are large individual differences in performance on Tik Tik
;; - Our problem set is within subject abilities, albeit a little difficult
;; - The trimmed search space size is the strongest predictor of level difficulty, together with the number of blockages in optimal solution
