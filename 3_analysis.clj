;; ## Load libraries
(ns analysis
  (:require [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [scicloj.clay.v2.api :as clay]
            [clojure.java.io :as io]
            [scicloj.tableplot.v1.hanami :as hanami]
            [aerial.hanami.templates :as ht]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tech.v3.dataset.print :as print]
            [scicloj.metamorph.core :as mm]
            [clojure.set :as set]
            [tech.v3.dataset.modelling :as ds-mod]
            [fastmath.ml.regression :as reg]
            [fastmath.stats :as stats]
            [jsonista.core :as j]))

;; ## Helper functions for linear regression (from Noj book)
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

(def study "study1")

;; ## Demographics: Pipeline
(defn demographics-pipeline [data]
  (-> data
      (tc/aggregate {:n tc/row-count
                     :males #(-> % :sex (tcc/eq "Male") tcc/sum)
                     :min-age #(tcc/reduce-min (:age %))
                     :max-age #(reduce max (:age %))
                     :mean-age #(tcc/mean (:age %))})))

;; ## Demographics: Load data
(def subject-data
  (tc/dataset (str "2_Processed_data/" study "/" study "_subject_data.nippy")))

(demographics-pipeline subject-data)

;; ## Per subject and per problem summary pipeline
(defn subject-problem-summary-pipeline [attempts problem-characterization study]
  (-> attempts
      (tc/group-by [:subject :problem])
      (tc/aggregate {:attempts tc/row-count
                     :tot-duration
                     (fn [data] (if (= study "study1")
                                  (reduce + (data :duration))
                                  (tcc/reduce-max (data :duration))))
                     :blocks-tot #(last (:blocks %))
                     :solved? #(last (:success %))})
      (tc/order-by [:subject :problem])
      (tc/left-join problem-characterization [:problem])
      (tc/eq :optimal? [:blocks-tot :optimal-solution-length])))

;; ## Compute per problem and per subject summary
(def problem-characterization
  (tc/dataset "2_Processed_data/problem_characterization_studies12.nippy"))

(if (= study "study2")
  (def attempts
    (-> (str "2_Processed_data/" study "/" study "_attempts.nippy")
        tc/dataset
        (tc/map-columns :success [:success :duration]
                        (fn [suc dur] (if (> dur 180) false suc)))
        (tc/map-columns :duration [:duration]
                        #(if (> % 180) 180 %))))
  (def attempts
    (tc/dataset (str "2_Processed_data/" study "/" study "_attempts.nippy"))))

(def subject-problem-summary
  (subject-problem-summary-pipeline attempts problem-characterization study))

(tc/write! subject-problem-summary (str "2_Processed_data/" study "/" study "_subject_problem_summary.csv"))
(tc/write! subject-problem-summary (str "2_Processed_data/" study "/" study "_subject_problem_summary.nippy"))

;; ## Individual differences
(def subject-summary
  (-> subject-problem-summary
      (tc/group-by [:subject])
      (tc/aggregate {:attempted-problems tc/row-count
                     :solved #(tcc/sum (:solved? %))
                     :solved-optimally #(tcc/sum (:optimal? %))})))

(-> subject-summary
    (plotly/layer-point
     {:=x :solved
      :=y :solved-optimally
      :=x-title "Number of problems solved"
      :=y-title "Number of problems solved optimally"
      :=mark-size 10
      :=mark-color "black"}))

(-> subject-summary
    (#(stats/correlation (:solved %) (:solved-optimally %))))

(def n-problems (-> subject-problem-summary :problem set count))

(def subjects-attempted-all
  (-> subject-summary
      (tc/select-rows #(= n-problems (:attempted-problems %)))
      :subject
      set))

;; ## Problem characterization
(def problem-summary
  (-> subject-problem-summary
      (tc/select-rows #(subjects-attempted-all (:subject %)))
      (tc/group-by [:problem])
      (tc/aggregate {:attempted-by tc/row-count
                     :solved-by #(tcc/sum (:solved? %))
                     :optimal_blocks #(first (:optimal %))
                     :median-attempts #(tcc/median (:blocks-tot %))
                     :solved-optimally #(tcc/sum (:optimal? %))
                     :median-t #(tcc/median (:tot-duration %))})
      (tc// :fraction-optimal [:solved-optimally :attempted-by])
      (tc// :fraction-solved [:solved-by :attempted-by])))

;; Number of attempts
(-> problem-summary
    (#(stats/correlation (:median-attempts %) (:fraction-optimal %))))

(-> problem-summary
    (#(stats/correlation (:median-t %) (:fraction-optimal %))))

(-> problem-summary
    (#(stats/correlation (:median-attempts %) (:median-t %))))

(-> problem-summary
    (plotly/base {:=x :median-t
                  :=y :median-attempts})
    (plotly/layer-point
     {:=mark-size 10
      :=mark-color "black"
      :=y-title "Median number of attempts"
      :=x-title "Median solution time (s)"
      :=name "data"})
    (plotly/layer-smooth {:=name "fit"}))

(-> problem-summary
    (plotly/base
     {:=x :median-attempts
      :=y :fraction-optimal})    
    (plotly/layer-point
     {:=mark-size 10
      :=mark-color "black"
      :=y-title "Optimal solution rate"
      :=x-title "Median number of attempts"
      :=name "data"})
    (plotly/layer-smooth {:=name "prediction"}))

(-> problem-summary
    (plotly/base
     {:=x :median-t
      :=y :fraction-optimal})
    (plotly/layer-point
     {:=mark-size 10
      :=mark-color "black"
      :=y-title "Optimal solution rate"
      :=x-title "Median solution time (s)"
      :=name "data"})
    (plotly/layer-smooth {:=name "prediction"}))

;; => fraction solved optimally seems to be a good measure of problem difficulty

;; ## Predictors of problem difficulty
(defn prepend0 [num]
  (let [str-num (str num)]
    (if (= (count str-num) 1)
      (str "00" str-num)
      (if (= (count str-num) 2)
        (str "0" str-num)
        str-num))))

(def problems-and-stats
  (-> problem-summary
      (tc/left-join problem-characterization :problem)))

(-> problems-and-stats
    (plotly/layer-point
     {:=x :index
      :=y :fraction-optimal
      :=mark-size 10
      :=mark-color "black"
      :=x-title "Optimal solution length"
      :=y-title "Optimal solution rate"}))

(-> problems-and-stats
    (plotly/layer-point
     {:=x :optimal-solution-length
      :=y :fraction-optimal
      :=mark-size 10
      :=mark-color "black"
      :=x-title "Optimal solution length"
      :=y-title "Optimal solution rate"}))

(-> problems-and-stats
    (plotly/layer-point
     {:=x :search-space
      :=y :fraction-optimal
      :=mark-size 10
      :=mark-color "black"
      :=x-title "Search space size"
      :=y-title "Optimal solution rate"}))

(-> problems-and-stats
    (plotly/layer-point
     {:=x :trimmed-search-space
      :=y :fraction-optimal
      :=mark-size 10
      :=mark-color "black"
      :=x-title "Trimmed search space size"
      :=y-title "Optimal solution rate"}))

(def mlr-model
  (->  problems-and-stats
       (tc/drop-rows #(= 1 (:optimal-solution-length %)))
       (tc/select-columns [:fraction-optimal :optimal-solution-length :trimmed-search-space])
       (ds-mod/set-inference-target :fraction-optimal)
       lm))

(summary mlr-model)
