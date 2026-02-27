(ns pod-eigs
  (:require [babashka.pods :as pods]))

(defonce ^:private pod
  (delay (pods/load-pod "./pod-eigs/target/release/pod-eigs")))

(defn eigenvalues
  "Compute eigenvalues for a 6x6 matrix.

  The pod declares JSON format, so Babashka handles EDN<->JSON conversion.
  Callers can pass EDN maps directly.

  Examples:
    (eigenvalues {:data (repeat 36 0) :symmetric true})
    (eigenvalues {:rows [[1 0 0 0 0 0]
                         [0 1 0 0 0 0]
                         [0 0 1 0 0 0]
                         [0 0 0 1 0 0]
                         [0 0 0 0 1 0]
                         [0 0 0 0 0 1]]
                  :symmetric true})"
  [m]
  (force pod)
  (pod.eigs/eigenvalues m))
