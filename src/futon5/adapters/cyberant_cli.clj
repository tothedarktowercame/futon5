(ns futon5.adapters.cyberant-cli
  "CLI to generate cyberant configs from patterns or module programs."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.adapters.cyberant :as cyber]))

(defn- usage []
  (str/join
   "\n"
   ["Cyberant generator (pattern-aware)"
    ""
    "Usage:"
    "  clj -M -m futon5.adapters.cyberant-cli [options]"
    ""
    "Options:"
    "  --pattern ID         Flexiarg pattern id (repeatable)."
    "  --sigil SIGIL        Sigil to convert to a cyberant (repeatable)."
    "  --program EDN        EDN map of module->pattern-id."
    "  --program-file PATH  EDN file containing module->pattern-id."
    "  --base-sigil SIGIL   Base sigil to seed defaults for program composition."
    "  --out PATH           Write EDN output to file."
    "  --help               Show this message."
    ""
    "Examples:"
    "  clj -M -m futon5.adapters.cyberant-cli --pattern iching/hexagram-44-gou"
    "  clj -M -m futon5.adapters.cyberant-cli --program '{:policy \"iching/hexagram-44-gou\" :adapt \"iching/hexagram-43-guai\"}'"
    "  clj -M -m futon5.adapters.cyberant-cli --sigil 工 --sigil 土 --out resources/ants/sigil-cyberants.edn"
    "  clj -M -m futon5.adapters.cyberant-cli --program-file programs/ant.edn --base-sigil 土"] ))

(defn- parse-args
  [args]
  (loop [args args
         opts {:patterns [] :sigils []}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--pattern" flag)
          (recur (rest more) (update opts :patterns conj (first more)))

          (= "--sigil" flag)
          (recur (rest more) (update opts :sigils conj (first more)))

          (= "--program" flag)
          (recur (rest more) (assoc opts :program (first more)))

          (= "--program-file" flag)
          (recur (rest more) (assoc opts :program-file (first more)))

          (= "--base-sigil" flag)
          (recur (rest more) (assoc opts :base-sigil (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- read-program
  [{:keys [program program-file]}]
  (cond
    program (edn/read-string program)
    program-file (edn/read-string (slurp program-file))
    :else nil))

(defn -main [& args]
  (let [{:keys [patterns sigils base-sigil out help unknown] :as opts} (parse-args args)
        program (read-program opts)]
    (cond
      help (println (usage))
      unknown (do (println (str "Unknown flag: " unknown))
                  (println (usage)))
      (and (empty? patterns) (empty? sigils) (nil? program))
      (do (println "Provide --pattern or --program.")
          (println (usage)))
      :else
      (let [output (cond
                     (seq program)
                     {:generated-at (.toString (java.time.Instant/now))
                      :kind :pattern-program
                      :program program
                      :base-sigil base-sigil
                      :cyberant (cyber/pattern-program->cyberant program
                                                               :base-sigil base-sigil)}

                     (seq patterns)
                     {:generated-at (.toString (java.time.Instant/now))
                      :kind :pattern-list
                      :patterns (vec patterns)
                      :cyberants (cyber/batch-patterns patterns)}

                     (seq sigils)
                     {:generated-at (.toString (java.time.Instant/now))
                      :kind :sigil-list
                      :sigils (vec sigils)
                      :cyberants (cyber/batch-convert sigils)})
            rendered (pr-str output)]
        (if out
          (spit out rendered)
          (println rendered))))))
