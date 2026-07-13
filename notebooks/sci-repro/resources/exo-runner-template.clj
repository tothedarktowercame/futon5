(require '[futon5.wiring.runtime :as rt])
(let [tmpdir (System/getProperty "java.io.tmpdir")
      input (clojure.edn/read-string (slurp (str tmpdir "/exo-xcheck-input.edn")))
      wiring (rt/load-wiring (:wiring-path input))
      result (rt/run-wiring {:wiring wiring
                              :genotype (:genotype input)
                              :generations (:generations input)
                              :collect-metrics? false})
      gen-history (:gen-history result)]
  (spit (str tmpdir "/exo-xcheck-output.edn")
        (pr-str gen-history)))
