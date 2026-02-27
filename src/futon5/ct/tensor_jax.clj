(ns futon5.ct.tensor-jax
  "Optional JAX/XLA-backed tensor stepping backend.

   This backend is intentionally opt-in (`:backend :jax`) and keeps the
   default Clojure path unchanged for deterministic baseline behavior."
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.ca.core :as ca]))

(def ^:private repo-root
  (System/getProperty "user.dir"))

(def ^:private tensor-tool
  (str repo-root "/tools/tensor/jax_step.py"))

(defn- resolve-python
  []
  (let [tensor-python (some-> (System/getenv "FUTON5_TENSOR_PYTHON") str/trim)
        tpg-python (some-> (System/getenv "FUTON5_TPG_PYTHON") str/trim)
        venv-python (str repo-root "/.venv-tpg/bin/python3")]
    (cond
      (seq tensor-python) {:path tensor-python :source :env-tensor}
      (seq tpg-python) {:path tpg-python :source :env-tpg}
      (.exists (io/file venv-python)) {:path venv-python :source :venv-tpg}
      :else {:path "python3" :source :path})))

(defn- normalize-bit [x]
  (cond
    (= x 0) 0
    (= x 1) 1
    (= x \0) 0
    (= x \1) 1
    (= x false) 0
    (= x true) 1
    :else (throw (ex-info "Expected binary value (0/1) for JAX backend"
                          {:value x
                           :type (type x)}))))

(defn- normalize-bitplanes [bitplanes]
  (let [planes (mapv (fn [plane] (mapv normalize-bit plane)) bitplanes)]
    (when-not (= 8 (count planes))
      (throw (ex-info "JAX backend requires exactly 8 bitplanes"
                      {:plane-count (count planes)})))
    (let [widths (set (map count planes))]
      (when-not (= 1 (count widths))
        (throw (ex-info "All bitplanes must have equal width for JAX backend"
                        {:widths (vec widths)}))))
    planes))

(defn step-bitplanes-jax
  "Run one tensor bitplane step via Python+JAX.

   opts:
   - :wrap? (default true)
   - :boundary-bit (default 0)
   - :python (optional explicit interpreter path)"
  ([bitplanes rule-sigil] (step-bitplanes-jax bitplanes rule-sigil {}))
  ([bitplanes rule-sigil {:keys [wrap? boundary-bit python]
                          :or {wrap? true boundary-bit 0}}]
   (let [tool-file (io/file tensor-tool)
         _ (when-not (.exists tool-file)
             (throw (ex-info "JAX tensor tool script not found"
                             {:script tensor-tool
                              :hint "Expected tools/tensor/jax_step.py"})))
         bitplanes (normalize-bitplanes bitplanes)
         boundary-bit (normalize-bit boundary-bit)
         {:keys [path source]} (if (seq python)
                                 {:path python :source :explicit}
                                 (resolve-python))
         payload {:bitplanes bitplanes
                  :rule-bits (ca/bits-for rule-sigil)
                  :wrap? (boolean wrap?)
                  :boundary-bit boundary-bit}
         input (json/generate-string payload)
         result (try
                  (shell/sh path tensor-tool :in input)
                  (catch java.io.IOException e
                    {:exit 127
                     :err (str "Failed to execute Python interpreter `" path "`: " (.getMessage e))
                     :out ""}))
         err-msg (str/trim (or (:err result) ""))
         out-msg (str/trim (or (:out result) ""))]
     (when-not (zero? (:exit result))
       (throw (ex-info "JAX tensor step failed"
                       {:backend :jax
                        :python path
                        :python-source source
                        :script tensor-tool
                        :exit (:exit result)
                        :error (if (seq err-msg)
                                 err-msg
                                 (str "Process exited with code " (:exit result)))
                        :stdout out-msg
                        :hint "Install tensor backend deps in .venv-tpg or set FUTON5_TENSOR_PYTHON."})))
     (let [parsed (try
                    (json/parse-string out-msg true)
                    (catch Exception e
                      (throw (ex-info "Failed to parse JAX tensor output JSON"
                                      {:backend :jax
                                       :python path
                                       :stdout out-msg
                                       :error (.getMessage e)}))))
           out (get parsed :bitplanes-next)]
       (when-not (sequential? out)
         (throw (ex-info "JAX tensor output missing :bitplanes-next"
                         {:backend :jax
                          :output parsed})))
       (normalize-bitplanes out)))))
