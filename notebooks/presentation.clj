;; ---
;; title: Studying planning with a novel video game
;; author: Cvetomir Dimov
;; format:
;;   revealjs:
;;     theme: dracula
;;     slide-number: true
;; ---

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

;; ## Outline
;;
;; 1. Why study problem solving?
;; 2. Tik Tik
;; 3. Creating problems in Tik Tik
;; 4. Planning and goals of this project
;; 5. Study 1
;; 6. Study 2
;; 7. Conclusion

;; ## Problem solving
;;
;; Whenever we have a goal and we do not know how to reach it, we rely on our problem solving skills.
;;
;; Examples of problem solving:
;;
;; 1. Finding a solution to a math problem.
;; 2. Solving logical puzzles.
;; 3. Shopping for the week.
;; 4. Buying a car.

;; ## Tik Tik: A Problem Solving Video Game
^{:kindly/hide-code true}
(kind/hiccup 
 [:div {:style {:text-align "center"}}
  (kind/video {:src "notebooks/videos/Demo.webm"})])

;; ## Creating problems
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
  [:li "start and goal locations"]
  [:li "number of barriers"]
  [:li "number of barrier intersections"]])

;; ## Problem solver: Demonstration

^{:kindly/hide-code true}
(kind/image
 {:src "notebooks/images/LevelCreator.png"})

;; ## Problem solver: Summary
;;
;; 0. Input: fire and ice barriers; start and target locations of characters
;; 1. Identify segments that intersecting rays are split into
;; 2. Separate playing field into fire and ice regions => problem space
;; 3. Identify all possible moves for each state and save
;; 4. Search all possible paths exhaustively (cannot visit a state twice)
;; 5. Identify shortest path (i.e., optimal solution): *optimal solution length*
;; 6. Count total number of paths: *search space size*
;; 7. Visualize all accesible states and possible moves between them

;; ## Planning
;;
;; - Planning is the process of formulating an abstract sequence of operations towards some goal.
;; - Planning is just one strategy to solve problems. For example, when we solve math problems, we often apply learned procedures without any planning.
;; - People seem to construct plans more or less systematically in different tasks. Further research is needed to better understand what determines how we construct plans. 

;; ## Project goals
^{:kindly/hide-code true}
(kind/hiccup
   [:ol
    [:li "Identify how to incentivize more planning"]
    [:li "Create a problem set, which is:"
     [:ul
      [:li "within the general population's abilities"]
      [:li "varies systematically in difficulty"]
      [:li "takes about an hour to complete"]]]
    [:li "Identify problem parameters that determine level difficulty"]])

;; ## Study 1: Pilot
;; **Creating a problem set**

;; - systematically vary the *optimal solution length* (i.e., the minimum number of blockages necessary to solve a level)
;; - systematically vary *search space size* (i.e., the number of unique paths in a level)
;; - only one optimal solution

;; **Participants and procedures**

;; - *29 levels*: 9 warmup levels + 20 main levels (between 1 and 5 blockages)
;; - *46 subjects* (online platform Prolific)
;; - an extensive tutorial, followed by 29 levels
;; - about *1.5 hours* of total time
;; - provide *optimal solution length* and *blockage limit* (= 10 x OSL) for each level
;; - subjects received *1 bonus point* for a correct solution and *3 bonus points* for an optimal solution

^{:kindly/hide-code true}
(def subject-problem-summary
  (tc/dataset "data/study1/study1_subject_problem_summary.nippy"))

;; ## Subject performance
^{:kindly/hide-code true}
(def subject-summary
  (-> subject-problem-summary
      (tc/group-by [:subject])
      (tc/aggregate {:attempted-problems tc/row-count
                     :solved #(tcc/sum (:solved? %))
                     :solved-optimally #(tcc/sum (:optimal? %))})))

^{:kindly/hide-code true}
(kind/hiccup
 [:div {:style {:display "flex"}}
  [:div {:style {:flex "50%"}}
   (-> subject-summary
    (plotly/base {:=width 700 :=height 500})
    (plotly/layer-point
     {:=x :solved
      :=y :solved-optimally
      :=x-title "Number of problems solved"
      :=y-title "Number of problems solved optimally"
      }))]
  [:div {:style {:flex "50%"}}
   [:ul
    [:li "Only 25 out of 46 subjects attempted all levels"]
    [:li "Very few problems solved optimally"]]]])

;; ## Problem difficulty
^{:kindly/hide-code true}
(def problem-summary
  (-> subject-problem-summary
      (tc/group-by [:problem :optimal-solution-length :search-space :truncated-search-space])
      (tc/aggregate {:attempted-by tc/row-count
                     :solved-optimally #(tcc/sum (:optimal? %))
                     :median-solution-time #(tcc/median (:tot-duration %))})
      (tc// :optimal-solution-rate [:solved-optimally :attempted-by])))

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

;; ## Predictors of level difficulty
^{:kindly/hide-code true}
(kind/hiccup
 [:div {:style {:display "flex"}}
  [:div {:style {:flex "50%"}}
   [:image {:src "notebooks/images/study1_OptimalSolutionRate_Predictiors.png" :width 500 :height 500}]]
  [:div {:style {:flex "50%"}}
   [:ul
    [:li "Optimal solution length: r = -0.67 (the minimum number of blockages needed to solve a level)"]
    [:li "Search space size: r = -0.64 (the number of possible sequences of blockages that can be taken)"]
    [:li "Truncated search space size: r = -0.82 (the search space size up to length equal to that of the optimal solution)"]]]])

;; ## Logistic model prediction
^{:kindly/hide-code true}
(kind/hiccup
 [:div {:style {:display "flex"}}
  [:div {:style {:flex "50%"}}
   [:image {:src "notebooks/images/study1_OptimSolutionRate_Logistic.png" :width 500 :height 500}]]
  [:div {:style {:flex "50%"}}
   [:ul
    [:li "Only optimal solution length and truncated search space size significant."]
    [:li "Accounts for 63% of variance in problem difficulty."]
    ]]])

;; ## Study 1 conclusions
;;
;; - There is a high dropout rate 
;; - There are large individual differences in performance on Tik Tik
;; - Our problem set is within subject abilities, albeit a little difficult
;; - The truncated search space size is the strongest predictor of level difficulty, together with the optimal solution length

;; ## Study 2
;; Goals:
;;
;; 1. Introduce a *time limit* to reduce dropout rate
;; 2. Decrease blockage limit to *optimal solution length + 2*
;; 3. Make problems *easier*
;; 4. Systematically vary *optimal solution rate* and *truncated search space size*

;; Methods:

;; - *23 levels*
;; - *43 subjects* (online platform Prolific)
;; - about *1 hour* of total time
;; - provide *optimal solution length* and *blockage limit* for each level
;; - subjects received *1 bonus point* for a correct solution and *3 bonus points* for an optimal solution

;; ## Subject performance
^{:kindly/hide-code true}
(def subject-problem-summary-2
  (tc/dataset "data/study2/study2_subject_problem_summary.nippy"))

^{:kindly/hide-code true}
(def subject-summary-2
  (-> subject-problem-summary-2
      (tc/group-by [:subject])
      (tc/aggregate {:attempted-problems tc/row-count
                     :solved #(tcc/sum (:solved? %))
                     :solved-optimally #(tcc/sum (:optimal? %))})))


^{:kindly/hide-code true}
(kind/hiccup
 [:div {:style {:display "flex"}}
  [:div {:style {:flex "50%"}}
   (-> subject-summary-2
       (plotly/layer-point
        {:=x :solved
         :=y :solved-optimally
         :=x-title "Number of problems solved"
         :=y-title "Number of problems solved optimally"
         :=mark-size 10
         :=mark-color "black"}))]
  [:div {:style {:flex "30%"}}
   [:ul
    [:li "36 subjects out of 43 completed all levels."]
    [:li "Stong correlation between number of solved and optimally solved problems."]
    [:li "Levels that were solved (72.5%) were typically solved optimally (50.8%)"]
    [:li "Fraction of problems solved optimally between 20% and 80%."]
    ]]])

;; ## Predictors of level difficulty
^{:kindly/hide-code true}
(kind/hiccup
 [:div {:style {:display "flex"}}
  [:div {:style {:flex "50%"}}
   [:image {:src "notebooks/images/study2_OptimalSolutionRate_Predictiors.png" :width 500 :height 500}]]
  [:div {:style {:flex "50%"}}
   [:ul
    [:li "Optimal solution length: r = -0.52"]
    [:li "Search space size: r = -0.40"]
    [:li "Truncated search space size: r = -0.57"]]]])

;; ## Logistic model prediction
^{:kindly/hide-code true}
(kind/hiccup
 [:div {:style {:display "flex"}}
  [:div {:style {:flex "50%"}}
   [:image {:src "notebooks/images/study2_OptimSolutionRate_Logistic.png" :width 500 :height 500}]]
  [:div {:style {:flex "50%"}}
   [:ul
    [:li "Only optimal solution length and truncated search space size significant."]
    [:li "Accounts for 54% of variance in problem difficulty."]
    ]]])
 
;; ## Summary and conclusion

;; 1. *Tik Tik* is a simple *problem solving video game* with the potential for difficult problems
;; 2. We can find solutions with the *Tik Tik Solver*
;; 3. To incentivize participants to plan, we can introduce a *blockage limit*
;; 4. To reduce dropout rate, we can introduce a *time limit*
;; 5. Level difficulty in Tik Tik is a function of the *optimal solution length* and the *truncated search space size*

^{:kindly/hide-code true}
(kind/hiccup
 [:p "Made with:"
  [:ul
   [:li "Quil - Tik Tik Solver"]
   [:li "Noj - data analysis"]
   [:li "R - data analysis"]
   [:li "Clay - data analysis + presentation"]]])






