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

;; ## Study 1 Analysis
;; ### Demographics
(defn demographics-pipeline [data]
  (-> data
      (tc/aggregate {:n tc/row-count
                     :males #(-> % :sex (tcc/eq "Male") tcc/sum)
                     :min-age #(tcc/reduce-min (:age %))
                     :max-age #(reduce max (:age %))
                     :mean-age #(tcc/mean (:age %))})))

(def subject-data
  (tc/dataset (str "data/study1/study1_subject_data.nippy")))

(demographics-pipeline subject-data)

;; ## Individual differences
(def subject-problem-summary
  (tc/dataset "data/study1/study1_subject_problem_summary.nippy"))

(def subject-summary
  (-> subject-problem-summary
      (tc/group-by [:subject])
      (tc/aggregate {:solved #(tcc/sum (:solved? %))
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

;; ### Problem characterization
(def problem-summary
  (-> subject-problem-summary
      (tc/group-by [:problem :optimal-solution-length :search-space :truncated-search-space])
      (tc/aggregate {:attempted-by tc/row-count
                     :solved-by #(tcc/sum (:solved? %))
                     :mean-blocks #(tcc/mean (:blocks-tot %))
                     :solved-optimally #(tcc/sum (:optimal? %))
                     :median-t #(tcc/median (:tot-duration %))})
      (tc// :optimal-solution-rate [:solved-optimally :attempted-by])
      (tc// :solution-rate [:solved-by :attempted-by])))

;; Number of attempts
(-> problem-summary
    (#(stats/correlation-matrix
       (list (:median-t %) (:mean-blocks %) (:optimal-solution-rate %) (:solution-rate %)))))

(-> problem-summary
    (plotly/splom
     {:=colnames [:median-t :mean-blocks :optimal-solution-rate :solution-rate]
      :=height 600
      :=width 600}))

;; ### Predictors of problem difficulty
(-> problem-summary
    (plotly/layer-point
     {:=x :optimal-solution-length
      :=y :optimal-solution-rate
      :=mark-size 10
      :=mark-color "black"
      :=x-title "Optimal solution length"
      :=y-title "Optimal solution rate"}))

(-> problem-summary
    (plotly/layer-point
     {:=x :search-space
      :=y :optimal-solution-rate
      :=mark-size 10
      :=mark-color "black"
      :=x-title "Search space size"
      :=y-title "Optimal solution rate"}))

(-> problem-summary
    (plotly/layer-point
     {:=x :truncated-search-space
      :=y :optimal-solution-rate
      :=mark-size 10
      :=mark-color "black"
      :=x-title "Truncated search space size"
      :=y-title "Optimal solution rate"}))

(def logistic-model
  (reg/glm
   ;; ys - a "column" sequence of y values:
   (-> subject-problem-summary
       (tc/map-columns :optimal [:optimal?] #(if % 1 0))
       :optimal)
   (-> subject-problem-summary
       (tc/select-columns [:optimal-solution-length :trimmed-search-space])
       tc/rows)
   {:family :binomial
    :tol 0.5}))

(select-keys logistic-model [:model :beta :coefficients])

;; ## Study 2 Analysis
;; ### Demographics

(def subject-data
  (tc/dataset (str "data/study2/study2_subject_data.nippy")))

(demographics-pipeline subject-data)

;; ## Individual differences
(def subject-problem-summary
  (tc/dataset "data/study2/study2_subject_problem_summary.nippy"))

(def subject-summary
  (-> subject-problem-summary
      (tc/group-by [:subject])
      (tc/aggregate {:solved #(tcc/sum (:solved? %))
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

;; ### Problem characterization
(def problem-summary
  (-> subject-problem-summary
      (tc/group-by [:problem :optimal-solution-length :search-space :truncated-search-space])
      (tc/aggregate {:attempted-by tc/row-count
                     :solved-by #(tcc/sum (:solved? %))
                     :mean-blocks #(tcc/mean (:blocks-tot %))
                     :solved-optimally #(tcc/sum (:optimal? %))
                     :median-t #(tcc/median (:tot-duration %))})
      (tc// :optimal-solution-rate [:solved-optimally :attempted-by])
      (tc// :solution-rate [:solved-by :attempted-by])))

;; Number of attempts
(-> problem-summary
    (#(stats/correlation-matrix
       (list (:median-t %) (:mean-blocks %) (:optimal-solution-rate %) (:solution-rate %)))))

(-> problem-summary
    (plotly/splom
     {:=colnames [:median-t :mean-blocks :optimal-solution-rate :solution-rate]
      :=height 600
      :=width 600}))

;; ### Predictors of problem difficulty
(-> problem-summary
    (plotly/layer-point
     {:=x :optimal-solution-length
      :=y :optimal-solution-rate
      :=mark-size 10
      :=mark-color "black"
      :=x-title "Optimal solution length"
      :=y-title "Optimal solution rate"}))

(-> problem-summary
    (plotly/layer-point
     {:=x :search-space
      :=y :optimal-solution-rate
      :=mark-size 10
      :=mark-color "black"
      :=x-title "Search space size"
      :=y-title "Optimal solution rate"}))

(-> problem-summary 
    (plotly/layer-point
     {:=x :truncated-search-space
      :=y :optimal-solution-rate
      :=mark-size 10
      :=mark-color "black"
      :=x-title "Truncated search space size"
      :=y-title "Optimal solution rate"}))

(def logistic-model
  (reg/glm
   ;; ys - a "column" sequence of y values:
   (-> subject-problem-summary
       (tc/map-columns :optimal [:optimal?] #(if % 1 0))
       :optimal)
   (-> subject-problem-summary
       (tc/select-columns [:optimal-solution-length :trimmed-search-space])
       tc/rows)
   {:family :binomial
    :tol 0.5}))

(select-keys logistic-model [:model :beta :coefficients])
