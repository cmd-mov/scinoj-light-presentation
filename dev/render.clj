(ns render
  (:require [scicloj.clay.v2.api :as clay]))

(clay/make! {:source-path ["notebooks/presentation.clj"]
             :target [:quarto :reveljs]
             :base-target-path "docs"})
