(ns fruitprocessing.facts
  "Reference facts for fruit & vegetable processing: jurisdiction storage-time
  limits and evidence-checklist requirements, and finished-product
  post-processing storage-temperature windows by product category (canned /
  frozen / dried). This namespace contains pure lookup functions for
  regulatory/food-safety compliance checks -- the Governor calls these to
  independently validate proposals against jurisdiction/product
  requirements; the advisor's confidence is NOT sufficient to override
  these checks."
  (:require [clojure.set :as set]))

(def jurisdictions
  "Fruit & vegetable processing jurisdictions and their maximum
  post-processing storage time (days) and required documentation/evidence
  checklist. The EU entry additionally requires a scheduled-process record
  for low-acid canned products (21 CFR 113-equivalent botulism-risk
  control) because EFSA mandates it for imports as well as domestic
  production."
  {"US"
   {:id "US"
    :name "United States (FDA/FSMA)"
    :storage-time-max-days 7
    :required-evidence
    [:harvest-lot-record      ;; grower/lot provenance & harvest date
     :temperature-log         ;; post-processing storage temperature records
     :storage-time-record     ;; time from processing-complete to shipment
     :sanitation-log          ;; pre-processing plant sanitation
     :residue-screening-pass  ;; pesticide/herbicide MRL screening result
     :traceability-record]}   ;; harvest-to-shipment chain of custody

   "JP"
   {:id "JP"
    :name "日本 (厚生労働省・食品衛生法)"
    :storage-time-max-days 5
    :required-evidence
    [:harvest-lot-record
     :temperature-log
     :storage-time-record
     :sanitation-log
     :residue-screening-pass
     :traceability-record]}

   "EU"
   {:id "EU"
    :name "European Union (EFSA)"
    :storage-time-max-days 5
    :required-evidence
    [:harvest-lot-record
     :temperature-log
     :storage-time-record
     :sanitation-log
     :residue-screening-pass
     :traceability-record
     :low-acid-canning-process-record]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(defn required-evidence-satisfied?
  "Verify that all required-evidence items are present in the batch's
  checklist. Returns true only if every item in the jurisdiction's
  required-evidence list is present in the batch's checklist."
  [jurisdiction-id checklist]
  (let [j (jurisdiction-by-id jurisdiction-id)]
    (if-not j
      false
      (let [required (set (:required-evidence j))
            present (set checklist)]
        (set/subset? required present)))))

(def product-types
  "Valid finished fruit & vegetable product categories (canned / frozen /
  dried) and their post-processing storage-temperature windows. This actor
  never operates or schedules the retort/blanch/freeze/dry process itself
  (that authority belongs exclusively to licensed, process-authority-
  certified plant operators -- see the Governor's `op-not-allowed`/hard
  block on processing-line control); these windows govern only a FINISHED
  batch's storage/holding temperature after processing is complete."
  {"canned-tomato"
   {:id "canned-tomato"
    :name "トマト缶詰"
    :cold-chain-temp-min-c 0.0
    :cold-chain-temp-max-c 35.0}

   "canned-green-beans"
   {:id "canned-green-beans"
    :name "グリーンビーンズ缶詰"
    :cold-chain-temp-min-c 0.0
    :cold-chain-temp-max-c 35.0}

   "frozen-peas"
   {:id "frozen-peas"
    :name "冷凍グリーンピース"
    :cold-chain-temp-min-c -22.0
    :cold-chain-temp-max-c -18.0}

   "frozen-corn"
   {:id "frozen-corn"
    :name "冷凍コーン"
    :cold-chain-temp-min-c -22.0
    :cold-chain-temp-max-c -18.0}

   "dried-apricot"
   {:id "dried-apricot"
    :name "ドライアプリコット"
    :cold-chain-temp-min-c 5.0
    :cold-chain-temp-max-c 25.0}})

(defn product-type-by-id [id]
  (get product-types id))
