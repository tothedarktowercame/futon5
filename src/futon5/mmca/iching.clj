(ns futon5.mmca.iching
  "Parser and loader for the 64 I Ching (易經) hexagrams used as a base MMCA rule catalog."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private hexagram-source "reference/pg25501.txt")

(defn- clean-lines [text]
  (-> text
      (str/replace "\r" "")
      (str/split-lines)))

(defn- header-line? [s]
  (let [trim (str/trim s)]
    (boolean (re-matches #"第.*卦" trim))))

(defn- collect-sections [lines]
  (loop [[line & more] lines
         current nil
         sections []]
    (cond
      (nil? line)
      (cond-> sections current (conj current))

      (header-line? line)
      (let [section {:header (str/trim line) :lines []}]
        (recur more section (cond-> sections current (conj current))))

      (nil? current)
      (recur more current sections)

      :else
      (recur more (update current :lines conj (str/trimr line)) sections))))

(defn- strip-leading-blanks [lines]
  (drop-while str/blank? lines))

(defn- take-judgement [lines]
  (let [lines (strip-leading-blanks lines)
        judgement (take-while (complement str/blank?) lines)
        rest-lines (drop (count judgement) lines)]
    [(->> judgement (map str/trim) (str/join "\n"))
     rest-lines]))

(defn- line-entry? [s]
  (boolean (re-matches #"^(初|上|用|六|七|八|九).+：.*" s)))

(defn- extract-lines [lines]
  (loop [[line & more] (strip-leading-blanks lines)
         acc []]
    (cond
      (nil? line) [acc []]
      (str/blank? line) (recur more acc)
      (some #(str/starts-with? (str/trim line) %) ["彖曰" "象曰" "文言曰"]) [acc (cons line more)]
      (line-entry? (str/trim line))
      (let [[label text] (str/split (str/trim line) #"[：:]" 2)]
        (recur more (conj acc {:label label :text (some-> text str/trim)})))
      :else [acc (cons line more)])))

(defn- extract-block [lines prefix]
  (let [lines (strip-leading-blanks lines)]
    (if (and (seq lines) (str/starts-with? (str/trim (first lines)) prefix))
      (let [[block rest-lines]
            (split-with #(not (str/blank? (str/trim %))) lines)
            content (->> block (map str/trim) (str/join "\n"))]
        [content rest-lines])
      [nil lines])))

(defn- parse-section [idx {:keys [header lines]}]
  (let [content (strip-leading-blanks lines)
        name (first content)
        remainder (strip-leading-blanks (rest content))
        [judgement remainder] (take-judgement remainder)
        [line-statements remainder] (extract-lines remainder)
        [tuanzhuan remainder] (extract-block remainder "彖曰")
        [xiangzhuan remainder] (extract-block remainder "象曰")
        [wenzhiyan remainder] (extract-block remainder "文言曰")
        notes (->> remainder
                   (map str/trim)
                   (remove str/blank?)
                   (str/join "\n"))]
    {:index (inc idx)
     :header header
     :name name
     :judgement (not-empty judgement)
     :lines (vec line-statements)
     :commentary (cond-> {}
                    tuanzhuan (assoc :tuanzhuan tuanzhuan)
                    xiangzhuan (assoc :xiangzhuan xiangzhuan)
                    wenzhiyan (assoc :wenzhiyan wenzhiyan))
     :notes (not-empty notes)}))

(defn parse-hexagrams
  "Parse the Gutenberg 易經 text into a structured 64-entry catalog."
  ([] (parse-hexagrams (slurp (io/file hexagram-source))))
  ([text]
   (let [lines (clean-lines text)
         sections (->> (collect-sections lines)
                       (filter #(seq (:lines %)))
                       (take 64))]
     (->> sections
          (map-indexed parse-section)
          vec))))

(def core-hexagrams
  "Lazily parsed vector of the 64 classical hexagrams."
  (delay (parse-hexagrams)))

(defn hexagram
  "Lookup a parsed hexagram by zero-based index or name."
  [query]
  (let [data @core-hexagrams]
    (cond
      (integer? query) (nth data query)
      (string? query)
      (some (fn [entry] (when (= (:name entry) query) entry)) data)
      :else nil)))
