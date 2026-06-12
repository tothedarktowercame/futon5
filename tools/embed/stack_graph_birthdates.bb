#!/usr/bin/env bb
;; stack_graph_birthdates.bb — futon3c ns-dependency graph (HEAD) + per-ns git BIRTH date.
;; Feeds the "evolving surface" viz: does the stack grow at the edges? claude-6.
(require '[clojure.java.shell :refer [sh]] '[clojure.string :as str] '[cheshire.core :as json])
(def REPO "/home/joe/code/futon3c")
(defn git [& a] (:out (apply sh "git" "-C" REPO a)))

(defn ns-block [c]
  (when-let [i (str/index-of c "(ns ")]
    (loop [j i d 0]
      (if (>= j (count c)) (subs c i)
          (let [ch (nth c j) d2 (cond (= ch \() (inc d) (= ch \)) (dec d) :else d)]
            (if (and (= ch \)) (zero? d2)) (subs c i (inc j)) (recur (inc j) d2)))))))

;; first-appearance time per file, one pass
(def birth
  (loop [lines (str/split-lines (git "log" "--diff-filter=A" "--name-only" "--reverse" "--format=@%ct"))
         ct nil acc {}]
    (if (empty? lines) acc
        (let [l (first lines)]
          (cond (str/starts-with? l "@") (recur (rest lines) (subs l 1) acc)
                (str/blank? l) (recur (rest lines) ct acc)
                :else (recur (rest lines) ct (if (acc (str/trim l)) acc (assoc acc (str/trim l) ct))))))))

(def files (->> (str/split-lines (git "ls-tree" "-r" "--name-only" "HEAD"))
                (filter #(re-find #"^(src|dev)/.*\.cljc?$" %))
                (remove #(re-find #"/test/|_test\." %))))

(let [parsed (for [f files
                   :let [c (git "show" (str "HEAD:" f))
                         own (second (re-find #"\(ns\s+([a-z0-9.*+!?<>=_-]+)" c))
                         blk (or (ns-block c) "")
                         reqs (set (map second (re-seq #"\[\s*([a-z][a-z0-9.*+!?<>=_-]+)" blk)))]
                   :when own]
               {:ns own :file f :birth (get birth f) :reqs reqs})
      nsset (set (map :ns parsed))
      bynsr (into {} (map (juxt :ns identity) parsed))
      nodes (vec (for [p parsed] {:id (:ns p) :birth (some-> (:birth p) Integer/parseInt)}))
      edges (vec (distinct (for [p parsed r (:reqs p) :when (and (nsset r) (not= (:ns p) r))]
                             (vec (sort [(:ns p) r])))))]
  (spit "/tmp/f3c-stack-graph.json" (json/generate-string {:nodes nodes :edges edges}))
  (let [bs (keep :birth nodes)]
    (println "nodes" (count nodes) "edges" (count edges)
             "birth-range" (when (seq bs) [(apply min bs) (apply max bs)])
             "-> /tmp/f3c-stack-graph.json")))
