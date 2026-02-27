(ns futon5.ct.tensor-diagrams
  "Reusable tensor diagram library for executable CT/MMCA tensor pipelines."
  (:require [futon5.ct.dsl :as dsl]))

(def pass-sigil-row
  (dsl/primitive-diagram {:name :pass-sigil-row
                          :domain [:sigil-row]
                          :codomain [:old-row]}))

(def sigil-row->tensor
  (dsl/primitive-diagram {:name :sigil-row->tensor
                          :domain [:sigil-row]
                          :codomain [:tensor]}))

(def tensor->bitplanes
  (dsl/primitive-diagram {:name :tensor->bitplanes
                          :domain [:tensor]
                          :codomain [:bitplanes]}))

(def step-bitplanes
  (dsl/primitive-diagram {:name :step-bitplanes
                          :domain [:bitplanes]
                          :codomain [:bitplanes-next]}))

(def bitplanes->tensor
  (dsl/primitive-diagram {:name :bitplanes->tensor
                          :domain [:bitplanes-next]
                          :codomain [:tensor-next]}))

(def tensor->sigil-row
  (dsl/primitive-diagram {:name :tensor->sigil-row
                          :domain [:tensor-next]
                          :codomain [:new-row]}))

(def gate-sigil-row
  (dsl/primitive-diagram {:name :gate-sigil-row
                          :domain [:old-row :new-row :phenotype]
                          :codomain [:gated-row]}))

(def pass-seed
  (dsl/primitive-diagram {:name :pass-seed
                          :domain [:seed]
                          :codomain [:seed*]}))

(def pass-run-index
  (dsl/primitive-diagram {:name :pass-run-index
                          :domain [:run-index]
                          :codomain [:run-index*]}))

(def pass-summary
  (dsl/primitive-diagram {:name :pass-summary
                          :domain [:summary]
                          :codomain [:summary*]}))

(def gen-history->top-sigils
  (dsl/primitive-diagram {:name :gen-history->top-sigils
                          :domain [:gen-history]
                          :codomain [:top-sigils :sigil-counts]}))

(def top-sigils->cyber-ant
  (dsl/primitive-diagram {:name :top-sigils->cyber-ant
                          :domain [:top-sigils :seed* :run-index*]
                          :codomain [:cyber-ant]}))

(def summary->aif-score
  (dsl/primitive-diagram {:name :summary->aif-score
                          :domain [:summary*]
                          :codomain [:aif]}))

(def sigil-step-diagram
  (dsl/compose-diagrams
   sigil-row->tensor
   tensor->bitplanes
   step-bitplanes
   bitplanes->tensor
   tensor->sigil-row))

(def sigil-step-gated-diagram
  (dsl/compose-diagrams
   (dsl/tensor-diagrams
    pass-sigil-row
    sigil-step-diagram)
   gate-sigil-row))

(def sigil-step-with-branch-diagram
  (dsl/tensor-diagrams
   pass-sigil-row
   sigil-step-diagram))

(def tensor-transfer-pack-diagram
  (dsl/compose-diagrams
   (dsl/tensor-diagrams
    gen-history->top-sigils
    pass-seed
    pass-run-index
    pass-summary)
   (dsl/tensor-diagrams
    (dsl/identity-diagram :top-sigils)
    (dsl/identity-diagram :sigil-counts)
    top-sigils->cyber-ant
    summary->aif-score)))

(def diagram-library
  {:sigil-step sigil-step-diagram
   :sigil-step-gated sigil-step-gated-diagram
   :sigil-step-with-branch sigil-step-with-branch-diagram
   :tensor-transfer-pack tensor-transfer-pack-diagram})

(defn available-diagrams
  "Return available tensor diagram ids."
  []
  (vec (sort (keys diagram-library))))

(defn diagram
  "Resolve a tensor diagram by id."
  [id]
  (or (get diagram-library id)
      (throw (ex-info "Unknown tensor diagram id"
                      {:id id
                       :available (available-diagrams)}))))
