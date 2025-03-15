;; ---
;; title: Towards a calibrated problem set for Tik Tik
;; author: Cvetomir Dimov
;; format:
;;   revealjs: 
;;     theme: dracula
;; ---

;; ## Collaborative Problem Solving
^{:kindly/hide-code true}
(ns presentation
  (:require [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.tableplot.v1.plotly :as plotly]
            [scicloj.metamorph.ml :as ml]
            [scicloj.ml.tribuo]
            [tech.v3.dataset.modelling :as ds-mod]
            [fastmath.ml.regression :as reg]))

;; 1. *What is it?*
;; - a group goal that needs to be accomplished through problem solving
;; - a single individual cannot solve the problem alone
;; - no routine plan is available for the problem

;; ## Collaborative Problem Solving
;; 2. *How important is it?*
;;
;; One of the **essential** 21st century skills (according to AT21CS)
;;
;; 3. *Why is it so important?*
;; 
;; Many contemporary problems require teams of individuals with different expertise and background

;; ## Collaborative Planning
;; - envision alternative courses of action
;; - select and commit to one of these courses of action
;; - these can be jointly costructed and must be jointly agreed upon in the case of collaborative planning
;; - the problem difficulty is a function of, among others, how many alternative courses of action there are

;; ## Tik Tik: A Collaborative Planning Task
^{:kindly/hide-code true}
(kind/hiccup 
 [:div {:style {:text-align "center"}}
  (kind/video {:src "notebooks/videos/Demo.webm"})])

;; ## Project goal
;; 1. Create a problem set, which is:
;; - within the general population's abilities
;; - varies systematically in difficulty
;; - takes about an hour to complete
;; 2. Identify level parameters that determine level difficulty

;; ## Designing problems
^{:kindly/hide-code true}
(kind/hiccup
 [:div {:style {:display "flex"}}
  [:div {:style {:flex "50%"}}
   [:image {:src "notebooks/images/Level103001.png"}]]
  [:div {:style {:flex "50%"}}
   [:image {:src "notebooks/images/Level119044.png"}]]])
;; Factors that affect problem difficulty:
^{:kindly/hide-code true}
(kind/hiccup
 [:ul
  [:li "number of barriers and number of barrier intersections"]
  [:li "start and goal locations"]])

;; ## Varying problem difficulty systematically
;; - vary the minimum number of blockages necessary to solve a level (**optimal solution length**)
;; - vary how many unique courses of action there are in a level (**search space size**)

;; ## Problem creator and solver
^{:kindly/hide-code true}
(kind/image
 {:src "notebooks/images/LevelCreator.png"})

;; ## Study
;; - *29 levels*: 9 warmup levels + 20 main levels (between 1 and 5 blockages)
;; - *46 subjects* (online platform Prolific)
;; - about *1.5 hours* of total time
;; - there is a **blockage limit** and an optimal blockage number (i.e., **optimal solution length**) for each level
;; - subjects received *1 bonus point* for a correct solution and *3 bonus points* for an optimal solution


^{:kindly/hide-code true}
(def tiktik-data-study1
  (tc/dataset "notebooks/data/study1/study1_attempts.nippy"
              {:dataset-name "Tik Tik study 1 data"}))

^{:kindly/hide-code true}
(def subject-problem-summary
  (-> tiktik-data-study1
      (tc/group-by [:subject :problem])
      (tc/aggregate {:attempts tc/row-count
                     :tot-duration #(reduce + (% :duration)) 
                     :blocks-tot #(last (:blocks %))
                     :solved? #(last (:success %))})
      (tc/order-by [:subject :problem])))

^{:kindly/hide-code true}
(def problems-characterization
  (tc/dataset "notebooks/data/problem_characterization_studies12.nippy"))

^{:kindly/hide-code true}
(def subject-problem-summary+
  (-> subject-problem-summary
      (tc/left-join problems-characterization [:problem])
      (tc/eq :optimal? [:blocks-tot :optimal-solution-length])))

;; ## Subject performance
^{:kindly/hide-code true}
(def subject-summary
  (-> subject-problem-summary+
      (tc/group-by [:subject])
      (tc/aggregate {:attempted-problems tc/row-count
                     :solved #(tcc/sum (:solved? %))
                     :solved-optimally #(tcc/sum (:optimal? %))})))

^{:kindly/hide-code true}
(-> subject-summary
    (plotly/base {:=width 700 :=height 500})
    (plotly/layer-point
     {:=x :solved
      :=y :solved-optimally
      :=x-title "Number of problems solved"
      :=y-title "Number of problems solved optimally"
      }))

;; ## Problem difficulty
^{:kindly/hide-code true}
(def problem-summary
  (-> subject-problem-summary+
      (tc/group-by [:problem])
      (tc/aggregate {:attempted-by tc/row-count
                     :median_attempts #(tcc/median (:blocks-tot %))
                     :solved-optimally #(tcc/sum (:optimal? %))
                     :median-solution-time #(tcc/median (:tot-duration %))})
      (tc// :optimal-solution-rate [:solved-optimally :attempted-by])
      (tc/left-join problems-characterization [:problet])))

^{:kindly/hide-code true}
(kind/hiccup
 [:div {:style {:display "flex"}}
  [:div {:style {:flex "50%"}}
   (-> problem-summary
       (plotly/base {:=x :median-solution-time
                     :=y :optimal-solution-rate})
       (plotly/layer-smooth {:=name "Linear fit"})
       (plotly/layer-point {:=name "Data"
                            :=x-title "Median solution time"
                            :=y-title "Optimal solution rate"}))]
  [:div {:style {:flex "50%"}}
   [:ul
    [:li "Problems vary widely in difficulty"]
    [:li "More difficult problems take longer and are more rarely solved optimally"]]]])

;; ## Which problem properties predict how difficult it is?
;; We still needs to find predictors of level difficulty, which include:
;;
;;  - optimal solution length - the minimum number of blockages needed to solve a level
;;  - search space size - the number of possible sequences of blockages that can be taken
;;  - trimmed search space size - the search space size up to length equal to that of the optimal solution

;; ## Predictors of level difficulty
#_(def logistic-model
    (reg/glm
     ;; ys - a "column" sequence of `y` values:
     (-> subject-problem-summary+
         (tc/map-columns :optimal [:optimal?] #(if % 1 0))
         :optimal)
     ;; xss - a sequence of "rows", each containing `x` values:
     ;; (one `x` per row, in our case):
     (-> subject-problem-summary+
         (tc/select-columns [:optimal-solution-length :trimmed-search-space])
         tc/rows)
     ;; options
     {:family :gaussian}))

^{:kindly/hide-code true}
(kind/hiccup
 [:div {:style {:display "flex"}}
  [:div {:style {:flex "50%"}}
   [:image {:src "notebooks/images/study1_OptimalSolutionRate_Predictiors.png" :width 500 :height 500}]]
  [:div {:style {:flex "50%"}}
   [:ul
    [:li "Optimal solution length: r = -0.67"]
    [:li "Search space size: r = -0.64"]
    [:li "Trimmed search space size: r = -0.82"]]]])

;; ## A quick introduction to logistic regression
^{:kindly/hide-code true}
(kind/hiccup
 [:div {:style {:display "flex"}}
  [:div {:style {:flex "50%"}}
   [:ul
    [:li "The logistic model is an S-shaped curve that ranges between 0 and 1"]
    [:li "A logistic regression finds the logistic model that best describes a set of points"]]]
  [:div {:style {:flex "50%"}}
   [:image {:src "notebooks/images/LogisticCurve.jpg"}]]])

;; ## Logistic model prediction
^{:kindly/hide-code true}
(kind/image
 {:src "notebooks/images/study1_OptimSolutionRate_Logistic.png"
  :height 500})

;; ## Summary
;; - There are large individual differences in performance on Tik Tik
;; - Our problem set is within subject abilities, albeit a little difficult
;; - The **trimmed search space size** is the strongest predictor of level difficulty, together with the **optimal solution length**

;; ## Conclusion
;; - A callibrated problem set will enable us to measure collaborative planning performance precisely
;; - When combined with measures of various cognitive and social abilities, it will allow us to determine the relative contribution of these abilities.

^{:kindly/hide-code true}
(kind/hiccup [:small [:p "Made with: Quil (problem solver), Noj libraries (data analysis), R (data analysis), Clay (data analysis + presentation)"]])
