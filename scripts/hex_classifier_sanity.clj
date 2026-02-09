(ns hex-classifier-sanity
  (:require [clojure.string :as str]
            [futon5.hexagram.metrics :as hex]
            [futon5.scripts.output :as out]))

(defn- usage []
  (str/join
   "\n"
   ["Sanity-check hexagram classifiers on synthetic grids."
    ""
    "Usage:"
    "  bb -cp futon5/src futon5/scripts/hex_classifier_sanity.clj [options]"
    ""
    "Options:"
    "  --step N        Grid step (default 0.05)."
    "  --rank N        projection-rank for signature grid (default 1.0)."
    "  --out-prefix P  Output prefix (default /tmp/hex-classifier-sanity-<ts>)."
    "  --help          Show this message."]))

(defn- parse-double* [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--step" flag)
          (recur (rest more) (assoc opts :step (parse-double* (first more))))

          (= "--rank" flag)
          (recur (rest more) (assoc opts :rank (parse-double* (first more))))

          (= "--out-prefix" flag)
          (recur (rest more) (assoc opts :out-prefix (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- frange [start end step]
  (loop [v start
         out []]
    (if (> v end)
      out
      (recur (+ v step) (conj out v)))))

(defn- grid-csv [rows header out-path]
  (out/spit-text!
   out-path
   (str (str/join "," header)
        "\n"
        (str/join
         "\n"
         (map (fn [row]
                (str/join "," row))
              rows)))))

(defn -main [& args]
  (let [{:keys [help unknown step rank out-prefix]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [step (double (or step 0.05))
            rank (double (or rank 1.0))
            out-prefix (or out-prefix (format "/tmp/hex-classifier-sanity-%d"
                                             (System/currentTimeMillis)))
            values (frange 0.0 1.0 step)
            sig-rows (for [alpha values
                           gap values
                           :let [clazz (hex/signature->hexagram-class
                                        {:alpha-estimate alpha
                                         :spectral-gap gap
                                         :projection-rank rank})]]
                       [(format "%.2f" alpha)
                        (format "%.2f" gap)
                        (format "%.2f" rank)
                        (name clazz)])
            sig-counts (frequencies (map last sig-rows))
            params-rows (for [u values
                              m values
                              :let [clazz (hex/params->hexagram-class
                                           {:update-prob u
                                            :match-threshold m})]]
                          [(format "%.2f" u)
                           (format "%.2f" m)
                           (name clazz)])
            params-counts (frequencies (map last params-rows))
            sig-out (str out-prefix "-signature.csv")
            params-out (str out-prefix "-params.csv")]
        (grid-csv sig-rows ["alpha" "gap" "rank" "class"] sig-out)
        (grid-csv params-rows ["update_prob" "match_threshold" "class"] params-out)
        (println "Signature grid counts:" sig-counts)
        (println "Params grid counts:" params-counts))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
