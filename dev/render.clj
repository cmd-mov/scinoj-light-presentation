(ns render
  (:require [scicloj.clay.v2.api :as clay]))

(clay/make! {:source-path ["notebooks/presentation.clj"]
             :format [:quarto :revealjs]
             :base-target-path "docs"})
